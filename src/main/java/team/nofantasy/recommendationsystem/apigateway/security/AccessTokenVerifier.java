package team.nofantasy.recommendationsystem.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.JwtSecretConfigurationProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies access tokens issued by auth-service. Works offline: the denylist is checked separately.
 */
@Component
public class AccessTokenVerifier {
    static final String TYPE_CLAIM = "type";
    static final String ACCESS_TYPE = "ACCESS";

    private final SecretKey secretKey;

    /**
     * @throws IllegalArgumentException if the secret is shorter than 64 UTF-8 bytes, which HS512 requires.
     */
    public AccessTokenVerifier(JwtSecretConfigurationProperties jwtSecretConfigurationProperties) {
        String secret = jwtSecretConfigurationProperties.secret();
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < 64) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 64 UTF-8 bytes for HS512");
        }

        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * @return the token if its signature and expiry are valid, and it is an access token;
     * empty for a refresh token, a foreign or expired token, or anything that isn't a JWT.
     */
    public Optional<AccessToken> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

            if (!ACCESS_TYPE.equals(claims.get(TYPE_CLAIM, String.class)) ||
                    claims.getSubject() == null || claims.getId() == null) {
                return Optional.empty();
            }

            return Optional.of(new AccessToken(UUID.fromString(claims.getSubject()), claims.getId()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
