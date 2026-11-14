# api-gateway

The single entry point for the frontend. It checks access tokens, routes requests to the services and tells them who the user is.

## Project

Recommendation System is an entry for Hack&Change 2026, Alfa-Bank case «Альфа-Курс: знания в нужный момент», track 1: personal recommendations of educational materials for entrepreneurs. A vendor LLM ranks and explains materials; the services around it supply context, check its answers and record what users do.

| Repository | Role |
|---|---|
| `api-gateway` | Single entry point. Checks access tokens, routes `/api/v1/**`, sets `X-User-Id`. |
| `auth-service` | Registration, login, access and refresh JWT, logout denylist. |
| `profile-service` | Business profile of the user (onboarding answers). |
| `catalog-service` | Catalog of materials from the case data, with Russian full-text search. |
| `metric-service` | Raw user events and the metrics computed from them. |
| `recommendation-service` | Builds recommendations with the LLM. |
| `frontend` | Angular web app. |
| `Documentation` | Architecture docs. Only developers commit there; agents never do. |

All repositories live in the GitHub organization `Hack-Challenges-recommendation-system` and are **public**.

## Rules for every change

These come from the case. Breaking one can disqualify the submission.

1. **The case data never goes into git or a Docker image**: not the dump, not a copy, not an extract. Services read it from a path mounted at runtime; `/data/` is in `.gitignore`. Synthetic test data written by the team may be committed.
2. **No keys, tokens or passwords in git.** Read them from environment variables; `env.example` files list names only.
3. **External APIs, such as the LLM, get only the fragments they need.** No user ids, names, emails or other personal data, and no paid or closed materials.
4. **Paid materials:** show only a short benefit and the provided link, never their content.
5. **Everything must start with one `docker compose up` from the README.** A submission that doesn't start scores 0. Don't add infrastructure or a required external service without agreeing on it first.
6. **Simple UI, no intrusive notifications.**

## Commits

Every commit follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/), in the style the history already uses:

    <type>(<scope>): <what was changed>

- **Type:** `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style` or `revert`.
- **Scope:** optional; the area that changed, e.g. `(jwt)`, `(kafka)`, `(migrations)`, `(controllers)`, `(CI/CD)`.
- **Description:** English, past tense, e.g. `fix(cookie): logout cookie name was fixed`.
- **Breaking change:** `!` after the type or scope, plus a `BREAKING CHANGE:` footer saying what callers must change.
- One logical change per commit. Commit to `develop`; `main` receives merges from `develop`.

## Stack

- Spring Cloud Gateway Server WebFlux 5.0.4 (Spring Cloud 2025.1.4) on **Spring Boot 4.0.9**, Java 25. Base package `team.nofantasy.recommendationsystem.apigateway`, entry point `Main`.
- Boot 4.0 instead of the 4.1.2 of the other services: Spring Cloud 2025.1 is the newest GA release train, and it supports Boot 4.0. Move to Boot 4.1 together with Spring Cloud 2026.0 once that is GA.
- Reactive (WebFlux), no database. jjwt verifies tokens; reactive Redis holds the denylist.
- Same build layout as the other services: `<finalName>app</finalName>`, the same `Dockerfile`, `.dockerignore`, `.gitignore` and `.github/workflows/ci-cd.yml`.

## How a request is handled

`filter/AuthenticationFilter`, a global filter, runs for every routed request:

1. Any `X-User-Id` sent by the client is removed.
2. `security/AccessTokenVerifier` checks `Authorization: Bearer <token>`: the HS512 signature with `JWT_SECRET` (the same value auth-service uses), the expiry, the claim `type` = `ACCESS` and a UUID in `sub`. Refresh tokens are signed with the same key, so the `type` check is what stops them.
3. The token is rejected if Redis has `jwt:denylist:<jti>`, which auth-service writes on logout.
4. A valid token sets `X-User-Id` to `sub`. Without one, a protected route answers 401 with `WWW-Authenticate: Bearer`, and a public route goes through anonymously.
5. If Redis doesn't answer, a protected route answers 503 instead of letting the token through.

`GatewayConfiguration` answers CORS preflight requests from `CORS_ALLOWED_ORIGINS`, with credentials, because the refresh token is a cookie. `GET /api/v1/health` is the health check of the gateway itself. The health endpoints of the services aren't routed: they all share that path.

## Routes and public requests

Both live in `application.yaml`. Every service listens on 8080 and serves under `/api/v1`, so paths are forwarded unchanged.

| Path prefix | Service | URL variable |
|---|---|---|
| `/api/v1/auth/**`, `/api/v1/user/**`, `/api/v1/session/**` | auth-service | `AUTH_SERVICE_URL` |
| `/api/v1/profiles/**` | profile-service | `PROFILE_SERVICE_URL` |
| `/api/v1/materials/**` | catalog-service | `CATALOG_SERVICE_URL` |
| `/api/v1/events/**`, `/api/v1/metrics/**` | metric-service | `METRIC_SERVICE_URL` |
| `/api/v1/recommendations/**` | recommendation-service; 120 s timeout for LLM answers | `RECOMMENDATION_SERVICE_URL` |

The URL variables default to the compose service names, e.g. `http://auth-service:8080`.

Public requests (`app.security.public-routes`): `POST /api/v1/user`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `DELETE /api/v1/auth/logout`, `GET /api/v1/materials/**` and `GET /api/v1/metrics/**`. Everything else needs a valid access token. When a service gets a new path prefix, add a route for it, and add a public-route entry only if the request needs no user.

## Configuration

| Variable | Default |
|---|---|
| `JWT_SECRET` | none, required: at least 64 bytes, the same value as in auth-service |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, `secret`: the Redis of auth-service |
| `CORS_ALLOWED_ORIGINS` | localhost and 127.0.0.1 on any port, and `https://cson.site`. Comma-separated Spring origin patterns; `[*]` means any port |
| `*_SERVICE_URL` | see the routes table |

`docker compose up -d` starts the Redis shared with auth-service.

## Build and test

- `mvn -B -ntp verify` runs all tests; the integration test needs Docker for Redis.
- `GatewayIntegrationTest` runs the real gateway in front of a stand-in upstream that echoes the path and the `X-User-Id` it received, so it checks the forwarded headers, not just status codes.
- Unit tests: `AccessTokenVerifierTest`, `PublicRoutesTest`.
- `mvn -B -ntp -DskipTests package` builds `target/app.jar`, which the `Dockerfile` copies.
