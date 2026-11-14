package team.nofantasy.recommendationsystem.apigateway.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jwt")
public record JwtSecretConfigurationProperties(
        String secret
) {
}
