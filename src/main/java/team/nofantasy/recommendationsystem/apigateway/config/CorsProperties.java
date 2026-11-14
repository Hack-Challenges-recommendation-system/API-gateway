package team.nofantasy.recommendationsystem.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param allowedOrigins origins of the frontend when it isn't served from the same origin, e.g. {@code http://localhost:4200}.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
