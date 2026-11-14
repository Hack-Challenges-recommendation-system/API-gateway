package team.nofantasy.recommendationsystem.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.JwtSecretConfigurationProperties;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.PublicRoutesConfigurationProperties;
import team.nofantasy.recommendationsystem.apigateway.security.AccessTokenVerifier;
import team.nofantasy.recommendationsystem.apigateway.security.PublicRoutes;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis is mocked here, because the integration test can't take a real one down.
 */
class AuthenticationFilterTest {
    private static final String SECRET = "unit-test-secret-".repeat(5);

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final AuthenticationFilter filter = new AuthenticationFilter(
            new AccessTokenVerifier(new JwtSecretConfigurationProperties(SECRET)),
            new PublicRoutes(new PublicRoutesConfigurationProperties(List.of("GET /api/v1/materials/**"))),
            redis
    );
    private final AtomicBoolean reachedUpstream = new AtomicBoolean();

    private String bearer() {
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(UUID.randomUUID().toString())
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .claim("type", "ACCESS")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();

        return "Bearer " + token;
    }

    private MockServerWebExchange run(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header(HttpHeaders.AUTHORIZATION, bearer())
        );

        filter.filter(exchange, e -> {
            reachedUpstream.set(true);
            return Mono.empty();
        }).block();

        return exchange;
    }

    private String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    @Test
    void redisFailureAnswers503EvenOnPublicRoute() {
        when(redis.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("redis is down")));

        for (String path : List.of("/api/v1/profiles/me", "/api/v1/materials/m-1")) {
            reachedUpstream.set(false);
            MockServerWebExchange exchange = run(path);

            assertThat(exchange.getResponse().getStatusCode()).as(path).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(reachedUpstream).as(path).isFalse();
            assertThat(body(exchange)).contains("\"status\":503");
        }
    }

    @Test
    void denylistedTokenAnswers401EvenOnPublicRoute() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(true));

        for (String path : List.of("/api/v1/profiles/me", "/api/v1/materials/m-1")) {
            reachedUpstream.set(false);
            MockServerWebExchange exchange = run(path);

            assertThat(exchange.getResponse().getStatusCode()).as(path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
            assertThat(reachedUpstream).as(path).isFalse();
            assertThat(body(exchange)).contains("\"status\":401");
        }
    }

    @Test
    void validTokenReachesUpstream() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));

        run("/api/v1/profiles/me");

        assertThat(reachedUpstream).isTrue();
    }
}