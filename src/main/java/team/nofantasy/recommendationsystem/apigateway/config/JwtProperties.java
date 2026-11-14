package team.nofantasy.recommendationsystem.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret HS512 secret, the same {@code JWT_SECRET} that auth-service signs tokens with.
 */
@ConfigurationProperties("jwt")
public record JwtProperties(
        String secret
) {
}
