# api-gateway

The single entry point for the frontend. It checks access tokens, routes requests to the services and tells them who the user is.

**Status:** empty repository; nothing is implemented yet.

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

## Plan

- Spring Cloud Gateway (`spring-cloud-starter-gateway-server-webflux`) on Spring Boot 4.1.2, base package `team.nofantasy.recommendationsystem.apigateway`, entry point `Main`. Pick the Spring Cloud release train that matches Boot 4.1. Unlike the other services it's reactive (WebFlux) and has no database.
- Use the build layout of the other services: the same `pom.xml` structure with `<finalName>app</finalName>`, the same `Dockerfile`, `.dockerignore`, `.gitignore` and `.github/workflows/ci-cd.yml`.

## Token check

For every route that isn't public:

1. Read `Authorization: Bearer <token>`.
2. Verify the HS512 signature with `JWT_SECRET`, the same value auth-service uses, and the expiry.
3. Require the claim `type` = `ACCESS`. Refresh tokens are signed with the same key, so without this check a 30-day refresh token would pass.
4. Reject the token if Redis has the key `jwt:denylist:<jti>` (set on logout). This check is required.
5. Remove any `X-User-Id` that came from the client, then set `X-User-Id` to the `sub` claim.

Answer 401 if any step fails.

## Public routes

`POST /api/v1/user` (registration), `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `DELETE /api/v1/auth/logout`, `GET /api/v1/materials/**`, `GET /api/v1/metrics/**` and `GET /api/v1/*/health`. Everything else needs a valid access token.

## Routes

Every service listens on 8080 inside the compose network and serves under `/api/v1`, so no path rewriting is needed.

| Path prefix | Service |
|---|---|
| `/api/v1/auth/**`, `/api/v1/user/**`, `/api/v1/session/**` | auth-service |
| `/api/v1/profiles/**` | profile-service |
| `/api/v1/materials/**` | catalog-service |
| `/api/v1/events/**`, `/api/v1/metrics/**` | metric-service |
| `/api/v1/recommendations/**` | recommendation-service (planned) |

Allow CORS for the frontend origin, with credentials, because the refresh token travels in a cookie.
