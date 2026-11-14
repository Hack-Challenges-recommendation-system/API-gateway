package team.nofantasy.recommendationsystem.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param allowedOriginPatterns origins allowed to call the API from a browser, as Spring origin patterns:
 *                              {@code http://localhost:[*]} allows every port of localhost.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(
        List<String> allowedOriginPatterns
) {
}
