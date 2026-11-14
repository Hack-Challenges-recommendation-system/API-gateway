package team.nofantasy.recommendationsystem.apigateway.filter;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import team.nofantasy.recommendationsystem.apigateway.error.ErrorResponseWriter;
import team.nofantasy.recommendationsystem.apigateway.security.AccessToken;
import team.nofantasy.recommendationsystem.apigateway.security.AccessTokenVerifier;
import team.nofantasy.recommendationsystem.apigateway.security.PublicRoutes;

import java.util.Optional;
import java.util.UUID;

/**
 * Authenticates every routed request and tells the services who the user is.
 *
 * <ol>
 *     <li>Any {@code X-User-Id} sent by the client is removed.</li>
 *     <li>A valid, not denylisted access token sets {@code X-User-Id} to its {@code sub} claim.</li>
 *     <li>Without such a token a protected route answers 401; a public route goes through anonymously.</li>
 *     <li>If Redis can't be asked about the denylist, a protected route answers 503.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {
    public static final String USER_ID_HEADER = "X-User-Id";
    /**
     * Same prefix as in auth-service, which stores the {@code jti} of logged-out access tokens.
     */
    public static final String DENYLIST_PREFIX = "jwt:denylist:";
    private static final String BEARER_PREFIX = "Bearer ";

    private enum DenylistCheck {
        ALLOWED,
        DENYLISTED,
        UNAVAILABLE
    }

    private final AccessTokenVerifier accessTokenVerifier;
    private final PublicRoutes publicRoutes;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    @NullMarked
    public Mono<Void> filter(@NotNull ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        boolean isPublic = publicRoutes.isPublic(request.getMethod(), request.getPath().pathWithinApplication());

        Optional<AccessToken> accessToken = bearerToken(request).flatMap(accessTokenVerifier::verify);
        return accessToken.map(
                token -> checkDenylist(token).flatMap(
                        check -> switch (check) {
                            case ALLOWED -> chain.filter(forward(exchange, token.userId()));
                            case DENYLISTED -> reject(exchange, HttpStatus.UNAUTHORIZED);
                            case UNAVAILABLE -> reject(exchange, HttpStatus.SERVICE_UNAVAILABLE);
                        }
                )
        ).orElseGet(() -> isPublic ? chain.filter(forward(exchange, null)) : reject(exchange, HttpStatus.UNAUTHORIZED));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private static Optional<String> bearerToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        return Optional.of(authorization.substring(BEARER_PREFIX.length()).trim());
    }

    private Mono<DenylistCheck> checkDenylist(AccessToken accessToken) {
        return redisTemplate.hasKey(DENYLIST_PREFIX + accessToken.jti())
                .map(denylisted -> denylisted ? DenylistCheck.DENYLISTED : DenylistCheck.ALLOWED)
                .onErrorResume(
                        e -> {
                            log.atError()
                            .setMessage("Denylist check failed")
                            .setCause(e)
                            .log();
                            return Mono.just(DenylistCheck.UNAVAILABLE);
                        }
                );
    }

    private static ServerWebExchange forward(ServerWebExchange exchange, @Nullable UUID userId) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    if (userId != null) {
                        headers.set(USER_ID_HEADER, userId.toString());
                    } else {
                        headers.remove(USER_ID_HEADER);
                    }
                })
                .build();

        return exchange.mutate().request(request).build();
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        log.atInfo()
                .setMessage("Request is rejected")
                .addKeyValue("status", status.value())
                .addKeyValue("method", exchange.getRequest().getMethod())
                .addKeyValue("path", exchange.getRequest().getPath().value())
                .log();

        exchange.getResponse().setStatusCode(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        return ErrorResponseWriter.write(
                exchange,
                status,
                status == HttpStatus.UNAUTHORIZED ? "Authentication is required" : "The service is temporarily unavailable"
        );
    }
}
