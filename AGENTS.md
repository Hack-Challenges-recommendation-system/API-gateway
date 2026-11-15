# api-gateway

The single entry point for the frontend. It checks access tokens, routes requests to the services and tells them who the user is.

**Read the shared guide first:** [AGENTS.md in Documentation](https://github.com/Hack-Challenges-recommendation-system/Documentation/blob/develop/AGENTS.md) covers the project, its architecture, the rules from the case (the case data and keys never go into git; this repository is public) and the commit conventions. Locally it is `AGENTS.md` in your clone of the Documentation repository. This file adds only what is specific to api-gateway.

## Stack

- Spring Cloud Gateway Server WebFlux 5.0.4 (Spring Cloud 2025.1.4) on **Spring Boot 4.0.9**, Java 25. Base package `team.nofantasy.recommendationsystem.apigateway`, entry point `Main`.
- Boot 4.0 instead of the 4.1.2 of the other services: Spring Cloud 2025.1 is the newest GA release train, and it supports Boot 4.0. Move to Boot 4.1 together with Spring Cloud 2026.0 once that is GA.
- Reactive (WebFlux), no database. jjwt verifies tokens; reactive Redis holds the denylist.
- Same build layout as the other services: `<finalName>app</finalName>`, the same `Dockerfile`, `.dockerignore`, `.gitignore` and `.github/workflows/ci-cd.yml`.

## How a request is handled

`filter/AuthenticationFilter`, a global filter, runs for every routed request:

1. Any `X-User-Id` sent by the client is removed.
2. `security/AccessTokenVerifier` checks `Authorization: Bearer <token>`: the HS512 signature with `JWT_SECRET` (the same value auth-service uses), the expiry, the claim `type` = `ACCESS` and a UUID in `sub`. Refresh tokens are signed with the same key, so the `type` check is what stops them.
3. The token is rejected with 401 if Redis has `jwt:denylist:<jti>`, which auth-service writes on logout. This holds for public routes too.
4. A valid token sets `X-User-Id` to `sub`. Without a token, or with one that fails verification, a protected route answers 401 with `WWW-Authenticate: Bearer`, and a public route goes through anonymously.
5. If Redis doesn't answer while a valid token is checked, the request answers 503, public or not, instead of letting the token through.

`CorsConfiguration` answers CORS preflight requests from `CORS_ALLOWED_ORIGINS`, with credentials, because the refresh token is a cookie. `GET /api/v1/health` is the health check of the gateway itself. The health endpoints of the services aren't routed: they all share that path.

## Routes and public requests

Both live in `application.yaml`. Every service listens on 8080 and serves under `/api/v1`, so paths are forwarded unchanged.

| Path prefix                                                | Service                                               | URL variable                 |
|------------------------------------------------------------|-------------------------------------------------------|------------------------------|
| `/api/v1/auth/**`, `/api/v1/user/**`, `/api/v1/session/**` | auth-service                                          | `AUTH_SERVICE_URL`           |
| `/api/v1/profile/**`                                       | profile-service                                       | `PROFILE_SERVICE_URL`        |
| `/api/v1/materials/**`                                     | catalog-service                                       | `CATALOG_SERVICE_URL`        |
| `/api/v1/events/**`, `/api/v1/metrics/**`                  | metric-service                                        | `METRIC_SERVICE_URL`         |
| `/api/v1/recommendations/**`                               | recommendation-service; 120 s timeout for LLM answers | `RECOMMENDATION_SERVICE_URL` |

The URL variables default to the compose service names, e.g. `http://auth-service:8080`.

Public requests (`app.security.public-routes`): `POST /api/v1/user`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `DELETE /api/v1/auth/logout`, `GET /api/v1/materials/**` and `GET /api/v1/metrics/**`. Everything else needs a valid access token. When a service gets a new path prefix, add a route for it, and add a public-route entry only if the request needs no user.

## Configuration

| Variable                                     | Default                                                                                                                    |
|----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `JWT_SECRET`                                 | none, required: at least 64 bytes, the same value as in auth-service                                                       |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, `secret`: the Redis of auth-service                                                                   |
| `CORS_ALLOWED_ORIGINS`                       | localhost and 127.0.0.1 on any port, and `https://cson.site`. Comma-separated Spring origin patterns; `[*]` means any port |
| `*_SERVICE_URL`                              | see the routes table                                                                                                       |

`docker compose up -d` starts the Redis shared with auth-service.

## Build and test

- `mvn -B -ntp verify` runs all tests; the integration test needs Docker for Redis.
- `GatewayIntegrationTest` runs the real gateway in front of a stand-in upstream that echoes the path and the `X-User-Id` it received, so it checks the forwarded headers, not just status codes.
- Unit tests: `AccessTokenVerifierTest`, `PublicRoutesTest`.
- `mvn -B -ntp -DskipTests package` builds `target/app.jar`, which the `Dockerfile` copies.
