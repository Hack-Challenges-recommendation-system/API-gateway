package team.nofantasy.recommendationsystem.apigateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * The real gateway in front of a stand-in upstream that echoes the path and the {@code X-User-Id} it received.
 * Redis is real, so the denylist check runs as in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class GatewayIntegrationTest {
    private static final DisposableServer UPSTREAM = HttpServer.create()
            .port(0)
            .handle((request, response) -> response
                    .header("Content-Type", "application/json")
                    .sendString(Mono.just("{\"method\": \"%s\", \"path\": \"%s\", \"userId\": \"%s\"}".formatted(
                            request.method().name(),
                            request.uri(),
                            request.requestHeaders().get("X-User-Id")
                    ))))
            .bindNow();

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Value("${jwt.secret}")
    private String secret;

    @DynamicPropertySource
    static void routeEveryServiceToUpstream(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + UPSTREAM.port();

        for (String service : List.of("AUTH", "PROFILE", "CATALOG", "METRIC", "RECOMMENDATION")) {
            registry.add(service + "_SERVICE_URL", () -> url);
        }
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.disposeNow();
    }

    private String token(String type, UUID userId, String jti, Instant expiresAt, String signingSecret) {
        return Jwts.builder()
                .id(jti)
                .subject(userId.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .claim("type", type)
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();
    }

    private String accessToken(UUID userId, String jti) {
        return token("ACCESS", userId, jti, Instant.now().plusSeconds(300), secret);
    }

    @Test
    void publicRouteWorksWithoutTokenAndDropsSpoofedUserId() {
        webTestClient.get().uri("/api/v1/materials/m-1")
                .header("X-User-Id", UUID.randomUUID().toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/materials/m-1")
                .jsonPath("$.userId").isEqualTo("null");
    }

    @Test
    void protectedRouteWithoutTokenIsUnauthorized() {
        webTestClient.get().uri("/api/v1/profiles/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Test
    void accessTokenSetsUserIdInsteadOfSpoofedOne() {
        UUID userId = UUID.randomUUID();

        webTestClient.get().uri("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(userId, UUID.randomUUID().toString()))
                .header("X-User-Id", UUID.randomUUID().toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/profiles/me")
                .jsonPath("$.userId").isEqualTo(userId.toString());
    }

    @Test
    void refreshExpiredAndForeignTokensAreRejected() {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();

        for (String rejected : List.of(
                token("REFRESH", userId, jti, Instant.now().plusSeconds(300), secret),
                token("ACCESS", userId, jti, Instant.now().minusSeconds(1), secret),
                token("ACCESS", userId, jti, Instant.now().plusSeconds(300), "another-secret-".repeat(6)),
                "not-a-jwt"
        )) {
            webTestClient.get().uri("/api/v1/events/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + rejected)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Test
    void loggedOutTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("jwt:denylist:" + jti, "").block();

        webTestClient.get().uri("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(userId, jti))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void onlyTheListedMethodsArePublic() {
        webTestClient.post().uri("/api/v1/user").exchange().expectStatus().isOk();
        webTestClient.delete().uri("/api/v1/user").exchange().expectStatus().isUnauthorized();
        webTestClient.post().uri("/api/v1/materials").exchange().expectStatus().isUnauthorized();
        webTestClient.delete().uri("/api/v1/auth/logout").exchange().expectStatus().isOk();
    }

    @Test
    void everyServiceIsRouted() {
        String bearer = "Bearer " + accessToken(UUID.randomUUID(), UUID.randomUUID().toString());

        for (String path : List.of(
                "/api/v1/session", "/api/v1/profiles/me", "/api/v1/materials/search",
                "/api/v1/events/me", "/api/v1/metrics/experiment", "/api/v1/recommendations/me"
        )) {
            webTestClient.get().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.path").isEqualTo(path);
        }
    }

    @Test
    void unknownPathIsNotFound() {
        webTestClient.get().uri("/api/v1/unknown").exchange().expectStatus().isNotFound();
    }

    @Test
    void gatewayAnswersItsOwnHealthCheck() {
        webTestClient.get().uri("/api/v1/health").exchange().expectStatus().isOk();
    }

    @Test
    void corsPreflightFromFrontendIsAnswered() {
        webTestClient.options().uri("/api/v1/profiles/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }
}
