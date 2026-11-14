package team.nofantasy.recommendationsystem.apigateway.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.JwtSecretConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenVerifierTest {
    private static final String SECRET = "integration-test-secret-".repeat(4);

    private final AccessTokenVerifier verifier = new AccessTokenVerifier(new JwtSecretConfigurationProperties(SECRET));
    private final UUID userId = UUID.randomUUID();

    /**
     * A token shaped like the ones auth-service issues.
     */
    private JwtBuilder token(String type, String secret) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .claim("type", type)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512);
    }

    @Test
    void acceptsAccessToken() {
        String jti = UUID.randomUUID().toString();

        assertThat(verifier.verify(token("ACCESS", SECRET).id(jti).compact()))
                .contains(new AccessToken(userId, jti));
    }

    @Test
    void rejectsRefreshToken() {
        assertThat(verifier.verify(token("REFRESH", SECRET).compact())).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        String expired = token("ACCESS", SECRET).expiration(Date.from(Instant.now().minusSeconds(1))).compact();

        assertThat(verifier.verify(expired)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        assertThat(verifier.verify(token("ACCESS", "another-secret-".repeat(6)).compact())).isEmpty();
    }

    @Test
    void rejectsTokensWithoutUsableClaims() {
        assertThat(verifier.verify(token("ACCESS", SECRET).subject("not-a-uuid").compact())).isEmpty();
        assertThat(verifier.verify(token("ACCESS", SECRET).subject(null).compact())).isEmpty();
        assertThat(verifier.verify(token("ACCESS", SECRET).id(null).compact())).isEmpty();
        assertThat(verifier.verify(token(null, SECRET).compact())).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(verifier.verify("not-a-jwt")).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> new AccessTokenVerifier(new JwtSecretConfigurationProperties("too-short")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessTokenVerifier(new JwtSecretConfigurationProperties(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
