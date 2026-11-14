package team.nofantasy.recommendationsystem.apigateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import team.nofantasy.recommendationsystem.apigateway.config.SecurityProperties;

import java.util.List;
import java.util.Locale;

/**
 * Requests that need no access token, from {@code app.security.public-routes}.
 */
@Component
public class PublicRoutes {
    private record Rule(HttpMethod method, PathPattern pattern) {
    }

    private final List<Rule> rules;

    /**
     * @throws IllegalArgumentException if an entry doesn't look like {@code "GET /api/v1/materials/**"}.
     */
    public PublicRoutes(SecurityProperties securityProperties) {
        List<String> entries = securityProperties.publicRoutes() == null ? List.of() : securityProperties.publicRoutes();

        this.rules = entries.stream().map(PublicRoutes::parse).toList();
    }

    private static Rule parse(String entry) {
        String[] parts = entry.trim().split("\\s+", 2);

        if (parts.length != 2) {
            throw new IllegalArgumentException("A public route must look like 'GET /api/v1/materials/**': " + entry);
        }

        return new Rule(
                HttpMethod.valueOf(parts[0].toUpperCase(Locale.ROOT)),
                PathPatternParser.defaultInstance.parse(parts[1])
        );
    }

    public boolean isPublic(HttpMethod method, PathContainer path) {
        return rules.stream().anyMatch(rule -> rule.method().equals(method) && rule.pattern().matches(path));
    }
}
