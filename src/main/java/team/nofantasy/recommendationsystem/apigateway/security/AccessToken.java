package team.nofantasy.recommendationsystem.apigateway.security;

import java.util.UUID;

/**
 * A verified access token.
 *
 * @param userId the {@code sub} claim; forwarded to the services as {@code X-User-Id}.
 * @param jti the token id; auth-service denylists it on logout.
 */
public record AccessToken(
        UUID userId,
        String jti
) {
}
