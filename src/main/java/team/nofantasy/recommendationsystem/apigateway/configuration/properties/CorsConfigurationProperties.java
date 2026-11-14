package team.nofantasy.recommendationsystem.apigateway.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app.cors")
public record CorsConfigurationProperties(
        List<String> allowedOriginPatterns
) {
}
