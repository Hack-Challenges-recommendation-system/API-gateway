package team.nofantasy.recommendationsystem.apigateway.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param publicRoutes requests that need no access token,
 * as {@code "METHOD /path/pattern"}, e.g. {@code "GET /api/v1/materials/**"}.
 */
@ConfigurationProperties("app.security")
public record PublicRoutesConfigurationProperties(
        List<String> publicRoutes
) {
}
