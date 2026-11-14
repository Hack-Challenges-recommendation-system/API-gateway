package team.nofantasy.recommendationsystem.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.PublicRoutesConfigurationProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicRoutesTest {
    private final PublicRoutes publicRoutes = new PublicRoutes(new PublicRoutesConfigurationProperties(List.of(
            "GET /api/v1/materials/**",
            "post /api/v1/user"
    )));

    private boolean isPublic(HttpMethod method, String path) {
        return publicRoutes.isPublic(method, PathContainer.parsePath(path));
    }

    @Test
    void matchesMethodAndPattern() {
        assertThat(isPublic(HttpMethod.GET, "/api/v1/materials")).isTrue();
        assertThat(isPublic(HttpMethod.GET, "/api/v1/materials/m-1")).isTrue();
        assertThat(isPublic(HttpMethod.POST, "/api/v1/user")).as("method is case-insensitive in config").isTrue();
    }

    @Test
    void otherMethodsAndPathsAreProtected() {
        assertThat(isPublic(HttpMethod.POST, "/api/v1/materials")).isFalse();
        assertThat(isPublic(HttpMethod.DELETE, "/api/v1/user")).isFalse();
        assertThat(isPublic(HttpMethod.PUT, "/api/v1/user/password")).isFalse();
        assertThat(isPublic(HttpMethod.GET, "/api/v1/profiles/me")).isFalse();
    }

    @Test
    void emptyConfigMakesEverythingProtected() {
        PublicRoutes none = new PublicRoutes(new PublicRoutesConfigurationProperties(null));

        assertThat(none.isPublic(HttpMethod.GET, PathContainer.parsePath("/api/v1/materials"))).isFalse();
    }

    @Test
    void refusesMalformedEntry() {
        assertThatThrownBy(() -> new PublicRoutes(new PublicRoutesConfigurationProperties(List.of("/api/v1/materials"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
