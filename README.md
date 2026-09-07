# 🔐 Spring Boot JWT Security Template

A runnable starter for stateless JWT authentication and role-based authorization on Spring Boot 3.5
and Spring Security 6: `SecurityConfig`, `JwtTokenProvider`, the authentication filter, the entry
point and access-denied handler, a `@RestControllerAdvice`, refresh-token revocation, account
lockout, OpenAPI, and a test suite that proves the rules rather than describing them.

> **This is a starting point, not production-grade.** Real deployments need HTTPS everywhere, secret
> rotation, rate limiting, monitoring and a security review. See
> [What this template does not do](#-what-this-template-does-not-do).

---

## 📑 Contents

- [Quick start](#-quick-start)
- [Request flow](#-request-flow)
- [Project structure](#-project-structure)
- [Domain model](#-domain-model)
- [Endpoints and authorization matrix](#-endpoints-and-authorization-matrix)
- [Response shapes](#-response-shapes)
- [Error codes](#-error-codes)
- [Business rules and why they are written that way](#-business-rules-and-why-they-are-written-that-way)
- [Security exception handling: two paths](#-security-exception-handling-two-paths)
- [curl walkthrough](#-curl-walkthrough)
- [Swagger UI](#-swagger-ui)
- [Testing](#-testing)
- [Secret handling](#-secret-handling)
- [Log hygiene](#-log-hygiene)
- [Submission checklist](#-submission-checklist)
- [Stretch goals](#-stretch-goals)
- [What this template does not do](#-what-this-template-does-not-do)
- [Naming conventions](#-naming-conventions)

---

## 🚀 Quick start

### Prerequisites

- **Java 17+**
- **PostgreSQL** running locally (or point `DB_URL` elsewhere)
- Gradle wrapper is included

### 1. Local configuration

There is **no usable default signing secret**. A real default would be committed here, which means
any deployment that forgot to override it would sign tokens with a key that is public on GitHub —
and would start up cleanly, so nobody would notice. The application refuses to start until a secret
of at least 32 bytes is supplied, and reports that as an `APPLICATION FAILED TO START` block with an
instruction rather than a nested bean-creation stack trace (`JwtSecretFailureAnalyzer`).

The quickest way to supply it, along with the database credentials, is a git-ignored local file:
Spring Boot reads `./config/application-{profile}.yml` automatically, at higher precedence than
anything on the classpath.

```yaml
# config/application-dev.yml   (git-ignored via /config/ - never commit it)
jwt:
  secret: "<openssl rand -base64 48>"

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/spring_app_dev
    username: postgres
    password: <your password>
```

Name it `application-dev.yml`, **not** `application.yml`. An unscoped `./config/application.yml`
outranks `src/test/resources/application.yml` as well, which silently points the whole test suite at
Postgres instead of H2 and fails a dozen unrelated tests with "Unable to determine Dialect". Tests
run with no active profile, so a dev-scoped file cannot reach them — and
`SpringAppApplicationTests` asserts the suite is on H2 in case that ever changes.

Environment variables work equally well, and are what real deployments should use:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export DB_URL="jdbc:postgresql://localhost:5432/spring_app_dev"
export DB_USERNAME="postgres"
export DB_PASSWORD="your-password"
```

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
```

Database credentials used to be committed in `application-dev.yml`. **That password is still in this
repository's git history and must be treated as compromised — rotate it.**

### 2. Give it a fresh database

The `users` table changed shape (single `role` enum column; no `roles`/`permissions` join tables).
`ddl-auto: update` adds columns but never drops them, and it cannot add a `NOT NULL` column to a
table that already has rows — so pointing this at a database built by the previous schema fails
with `column "enabled" of relation "users" contains null values` and leaves the table
half-migrated.

Simplest, and destroys nothing: a new database.

```sql
CREATE DATABASE spring_app_dev;
```

If you are updating an existing `spring_app_dev`, the `refresh_tokens` table also changed shape
(`token` → `token_id`, holding the JWT's `jti`). Drop it and let Hibernate rebuild it — it holds
only live sessions, so the cost is that everyone logs in again:

```sql
DROP TABLE IF EXISTS refresh_tokens;
```

To reuse an existing database whose data you do not need, drop the old tables and let Hibernate
recreate them. **This deletes those accounts:**

```sql
DROP TABLE IF EXISTS user_roles, role_permissions, roles, permissions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
```

To keep existing accounts, migrate instead: add `role`, `enabled`, `locked` and
`failed_login_count` as nullable, backfill them from `status`, `account_locked` and
`failed_login_attempts`, collapse the old role names into `USER`/`MANAGER`/`ADMIN`, then set
`NOT NULL`. For anything you care about, that belongs in a reviewed migration (Flyway/Liquibase),
not in a README snippet.

### 3. Run

```bash
./gradlew bootRun     # http://localhost:8088
./gradlew test        # 109 tests
./gradlew build
```

On startup with `app.seed.enabled=true` (the default in the `dev` profile), three accounts are
created — signup can only ever produce a `USER`, so `MANAGER` and `ADMIN` have to come from
somewhere else:

| Email | Password | Role |
|---|---|---|
| `admin@example.com` | `Admin12345` | `ADMIN` |
| `manager@example.com` | `Manager12345` | `MANAGER` |
| `user@example.com` | `User12345` | `USER` |

Development conveniences only. Override `app.seed.*-password`, or turn the seeder off, anywhere that
matters.

---

## 🔄 Request flow

```
                        ┌─────────────────────────────────────────────────────────┐
  Authorization:        │                  SECURITY FILTER CHAIN                  │
  Bearer <jwt>  ──────► │                                                         │
                        │  JwtAuthenticationFilter                                │
                        │    • reads the bearer token                             │
                        │    • verifies signature + issuer + type (HS256)         │
                        │    • builds CustomUserDetails from the claims           │
                        │    • on failure: records A004 / A005 on the request     │
                        │      and continues UNAUTHENTICATED (writes no response) │
                        │                          │                              │
                        │                          ▼                              │
                        │  authorizeHttpRequests   ──── denied, anonymous ──────► CustomAuthenticationEntryPoint
                        │    • the route table                                    │      401 JSON  A001/A004/A005
                        │                          │                              │
                        │                          ├──── denied, authenticated ──► CustomAccessDeniedHandler
                        │                          │                              │      403 JSON  A002
                        └──────────────────────────┼──────────────────────────────┘
                                                   ▼
                        ┌─────────────────────────────────────────────────────────┐
                        │            DISPATCH (controller + services)             │
                        │                                                         │
                        │  @PreAuthorize("hasRole('ADMIN')")  ── denied ────────► GlobalApiExceptionHandler
                        │  ApiException / BadCredentials / Locked / ...  ───────►      403/401/409/... JSON
                        └─────────────────────────────────────────────────────────┘
```

The filter never writes a response. It records *why* a token was rejected and lets the chain
continue, which keeps two decisions where they belong: whether the endpoint needed authentication at
all (the route table), and what the error body looks like (the entry point). That is also what makes
`A004` (expired) distinguishable from `A005` (invalid) — without the request attribute, both would
collapse into one generic 401.

---

## 📁 Project structure

```
src/main/java/com/spring/app/
├── SpringAppApplication.java
│
├── config/
│   ├── SecurityConfig.java            # SecurityFilterChain, BCrypt, CORS, the route table
│   ├── JpaAuditingConfig.java         # AuditorAware<Long> from SecurityContextHolder
│   ├── OpenApiConfig.java             # bearerAuth scheme -> the Authorize button
│   ├── JwtSecretFailureAnalyzer.java  # readable startup failure for a missing/short secret
│   ├── DataSeeder.java                # dev-only MANAGER/ADMIN accounts
│   ├── SeedProperties.java
│   └── MvcConfig.java
│
├── security/
│   ├── JwtTokenProvider.java              # mint + verify access tokens, mint refresh values
│   ├── JwtAuthenticationFilter.java       # bearer header -> SecurityContext
│   ├── CustomUserDetails.java             # UserDetails + getId()
│   ├── CustomUserDetailsService.java      # loads by email
│   ├── CustomAuthenticationEntryPoint.java # 401 JSON (filter chain)
│   └── CustomAccessDeniedHandler.java      # 403 JSON (filter chain)
│
├── exception/
│   ├── ErrorCode.java                 # every code, status and message in one enum
│   ├── ErrorResponse.java             # the single error body
│   ├── ApiException.java              # application failure carrying an ErrorCode
│   ├── AuthenticationErrorCodes.java  # AuthenticationException -> ErrorCode, shared by both paths
│   ├── JwtSecretConfigurationException.java  # missing or too-short signing secret
│   ├── GlobalApiExceptionHandler.java # @RestControllerAdvice (incl. method security)
│   └── BusinessException.java, ResourceNotFoundException.java, ResourceAlreadyExistsException.java
│
├── domain/
│   ├── BaseEntity.java                # createdAt/updatedAt + createdBy/modifiedBy
│   ├── user/       User, UserRepository
│   ├── token/      RefreshToken, RefreshTokenRepository,
│   │                RefreshTokenStore (port), JpaRefreshTokenStore
│   └── book/       Book, BookRepository
│
├── enums/Role.java                    # USER, MANAGER, ADMIN
│
├── payload/
│   ├── auth/  SignupRequest, LoginRequest, RefreshTokenRequest, AuthResponse
│   ├── user/  UserResponse
│   └── book/  BookRequest, BookResponse
│
├── service/
│   ├── auth/  AuthService, AuthServiceImpl, LoginAttemptService, RefreshTokenCleanupJob
│   └── book/  BookService, BookServiceImpl
│
├── controller/
│   ├── auth/  AuthController
│   ├── book/  BookController
│   └── user/  MeController
│
├── common/     ApiResponse, AbstractRestController, PaginatedResponse, ...
├── helper/     AuthHelper
└── util/       ImageUtil, ObjectUtils
```

---

## 🧱 Domain model

```
User
  id, email (unique, lower-cased), password (BCrypt), username,
  role (enum, EnumType.STRING), enabled, locked, failedLoginCount,
  lastLoginAt, createdAt/updatedAt/createdBy/modifiedBy

RefreshToken
  id, userId, tokenId (the token's jti, unique), expiresAt, revoked, createdAt

Book
  id, title, author, isbn, createdAt/updatedAt/createdBy/modifiedBy
```

### Why the role is `@Enumerated(EnumType.STRING)`

`EnumType.ORDINAL` persists the declaration index (`0`, `1`, `2`…). Insert or reorder a constant and
every existing row silently re-points at a different role: add a value above `ADMIN` and yesterday's
`ADMIN` rows become `MANAGER`. For a column that decides authorization, that is a
privilege-escalation bug written into the schema, and nothing in the code fails loudly when it
happens.

`EnumType.STRING` persists the name, so rows stay meaningful independently of declaration order, the
column is readable in ad-hoc SQL and audits, and a constant that is renamed or deleted blows up at
read time instead of quietly resolving to the wrong role. The same reasoning is recorded as a
comment on the field itself.

### Both tokens are JWTs; only one of them is stateless

| | Access token | Refresh token |
|---|---|---|
| Format | HS256 JWT, `typ: access` | HS256 JWT, `typ: refresh` |
| Lifetime | 5 minutes | 8 hours |
| Claims | `iss`, `sub`, `jti`, `iat`, `exp`, `typ`, `email`, `username`, `role` | the same, minus the profile claims |
| Verified by | signature + issuer + `exp` — no storage touched | the same, **and** a row in `refresh_tokens` |
| Revocable | no | yes |

Access tokens are stateless: verifying one touches no storage, which is what makes the hot path
cheap. Nothing can revoke them, which is exactly why they last **5 minutes** — with no revocation
mechanism, the lifetime *is* the exposure window. A stolen access token is usable until it expires,
so that number is the honest upper bound on the damage, and 5 minutes is short enough that a
blacklist buys little (see the trade-off below).

Refresh tokens carry the same signature but cannot be left purely stateless, because logout has to
be able to end a session and a signature cannot be withdrawn. They last **8 hours** — hours, not
days: being revocable is not the same as being safe, since a stolen refresh token keeps minting
access tokens until somebody notices, and nobody notices for six days. So every refresh token is issued with
a `jti` that is recorded in `refresh_tokens`, and the exchange checks both: the signature proves the
token is genuine and unexpired, the row proves it has not been revoked since. Neither alone is
enough — a `jti` with no verified signature is just a string the caller invented. That lookup is
paid only on the refresh path.

**Only the `jti` is stored, never the token.** The row proves a token was issued and says whether it
is still live, but is useless to whoever obtains it: a database dump cannot be replayed, because the
signature it would need is not in the database.

The two types are not interchangeable, and that matters more than it looks: both are signed by the
same key, so the `typ` claim is the only thing stopping a refresh token from authenticating every
endpoint, or an access token — the one handed to every call the client makes — from minting fresh
pairs forever. Presenting the wrong one gives `A005` on a protected endpoint and `A008` at
`/auth/refresh`.

### Which claims are set, and which are enforced

| Claim | Access | Refresh | Enforced when verifying |
|---|:---:|:---:|---|
| `iss` | ✓ | ✓ | must equal the configured issuer |
| `sub` (user id) | ✓ | ✓ | must parse as a `Long` |
| `jti` | ✓ | ✓ | refresh: must match a live row in `refresh_tokens` |
| `iat` | ✓ | ✓ | — |
| `exp` | ✓ | ✓ | must be **present** and in the future |
| `typ` | ✓ | ✓ | must match the path it is presented on |
| `email` | ✓ | — | must be present and non-blank |
| `username` | ✓ | — | must be present and non-blank |
| `role` | ✓ | — | must be a known `Role` |

Every claim the principal is built from is required, not read with a plain `get()`. The difference
matters: `claims.get("email", String.class)` on a token without that claim returns `null` and
happily produces a principal whose login identifier is null — `getUsername()` returns `null`, the
access-denied audit line logs `null`, and `/api/v1/me` answers with a null email. A missing `role`
is worse still, which is why it fails closed rather than defaulting.

`aud` and `nbf` are deliberately not set. `nbf` would equal `iat` for a token that is valid
immediately, so it would assert nothing; `aud` earns its place once more than one service verifies
with the same key, and then it has to be *validated* (`requireAudience`) rather than merely
present — setting it without checking it is decoration.

### Expiry is required, not just honoured

Every token this service mints carries `exp`, and every request that presents one has it checked —
the filter runs on each request, verification is stateless, and nothing about a previous pass is
remembered. An expired access token is `A004` on every attempt.

There is a subtlety that is easy to get wrong, and this template got it wrong until it was tested
for: **JWT makes `exp` optional, and a parser reading a token without it concludes the token never
expires.** jjwt enforces `exp` only when the claim is present. So honouring expiry is not the same
as enforcing it — with the claim absent, the check is opt-out by omission, and a correctly signed
token with no `exp` authenticates forever. `JwtTokenProvider.parseClaims` therefore requires the
claim:

```java
if (claims.getExpiration() == null) {
    // Not ExpiredJwtException: the token is not expired, it is malformed.
    throw new MalformedJwtException("Token has no exp claim");
}
```

That resolves to `A005`, not `A004`, on purpose: `A004` tells a client to refresh, and refreshing
would not help a token that never had an expiry. Nothing here mints such a token — the point is
that verification does not have to trust that, and would keep holding if another service signed
with the same key, or if a library default changed.

### Expired records are swept

`RefreshTokenCleanupJob` deletes records that can no longer authenticate anything:

```yaml
app:
  refresh-token-cleanup:
    cron: "0 */30 * * * *"   # "-" disables it
```

This is not tidiness for its own sake. Rotation writes one record per refresh, and at a 5-minute
access token an active client refreshes about twelve times an hour — roughly 96 records per user
per working day, none of them ever read again once expired. A store with per-key TTLs would expire
them for free; a table has to be swept.

Expired records only. A revoked-but-unexpired record is left until its natural expiry, because it
is the trail of a rotation and deleting it early erases the evidence that a token existed. Either
way the client sees `A008`: an unknown `jti` and a revoked `jti` are the same answer.

### Changing the lifetimes

```yaml
jwt:
  access-token-ttl: 5m     # shorter = smaller exposure window, more refresh traffic
  refresh-token-ttl: 8h    # longer = fewer logins, longer life for a stolen token
```

Bound as `Duration`, so `5m`, `90s`, `8h` and `7d` all work. Units are in the value rather than the
property name on purpose: a bare `900` under `access-ttl-seconds` is exactly the value someone
changes to `15` believing it means minutes.

One consequence worth planning for: at a 5-minute access TTL, an active client refreshes about 12
times an hour, and rotation writes a new `refresh_tokens` row and revokes the old one each time.
Rows accumulate at roughly 96 per user per 8-hour session, and nothing prunes them —
`RefreshTokenRepository.deleteExpiredBefore` exists but has no caller. On a real deployment that
wants a scheduled sweep; see [What this template does not do](#-what-this-template-does-not-do).

### Why the token response carries no user data

Login and refresh return tokens and nothing else:

```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 300
  },
  "status": { "code": 200, "message": "Success" }
}
```

The caller already knows who it just authenticated as. The access token carries the id, email and
role for anything that needs to render a UI, and `GET /api/v1/me` returns the profile on demand.
Repeating the account in every login and every refresh response only widens where it gets copied:
proxy access logs, browser storage, crash reports, analytics payloads. Signup still returns the
created user, because that is the resource it just created.

---

## 🔑 Endpoints and authorization matrix

| Method | Path | anonymous | USER | MANAGER | ADMIN | Enforced by |
| --- | --- | :---: | :---: | :---: | :---: | --- |
| `POST` | `/api/v1/auth/signup` | ✅ | ✅ | ✅ | ✅ | route table |
| `POST` | `/api/v1/auth/login` | ✅ | ✅ | ✅ | ✅ | route table |
| `POST` | `/api/v1/auth/refresh` | ✅ | ✅ | ✅ | ✅ | route table |
| `POST` | `/api/v1/auth/logout` | ❌ 401 | ✅ | ✅ | ✅ | route table |
| `GET` | `/api/v1/me` | ❌ 401 | ✅ | ✅ | ✅ | route table |
| `GET` | `/api/v1/books` | ✅ | ✅ | ✅ | ✅ | route table |
| `GET` | `/api/v1/books/{id}` | ✅ | ✅ | ✅ | ✅ | route table |
| `POST` | `/api/v1/books` | ❌ 401 | ❌ 403 | ✅ 201 | ✅ 201 | route table → `AccessDeniedHandler` |
| `PUT` | `/api/v1/books/{id}` | ❌ 401 | ❌ 403 | ✅ 200 | ✅ 200 | route table → `AccessDeniedHandler` |
| `DELETE` | `/api/v1/books/{id}` | ❌ 401 | ❌ 403 | ❌ 403 | ✅ 204 | `@PreAuthorize` → `@RestControllerAdvice` |
| `*` | `/api/v1/admin/**` | ❌ 401 | ❌ 403 | ❌ 403 | ✅ | route table |

`DELETE` is guarded by method security while `POST`/`PUT` are guarded by the route table **on
purpose**: it means both failure paths are exercised by real endpoints, and both are proven to
return the same `A002` JSON.

---

## 📦 Response shapes

**Success** uses the envelope this project already had (`common/ApiResponse`):

```json
{
  "data": { "id": 1, "email": "jane@example.com", "role": "USER" },
  "status": { "code": 200, "message": "Success" }
}
```

**Errors** are flat, so a client can read `code` without unwrapping anything:

```json
{
  "code": "A003",
  "message": "Invalid email or password",
  "status": 401,
  "path": "/api/v1/auth/login"
}
```

Three things are deliberately absent from the error body:

- **No exception class name, stack trace or SQL.** That describes the server's internals: it is
  reconnaissance for an attacker and noise for a client. It goes to the log instead.
- **No timestamp.** A failed login must be byte-identical whether the email is unknown or the
  password is wrong, and a per-response timestamp would make every body unique. The server clock is
  already in the log line.
- **No echo of the submitted values.** `fieldErrors` carries messages only — otherwise a validation
  failure on signup would return the submitted password.

---

## 🧾 Error codes

| Code | HTTP | Meaning |
|---|---|---|
| `U001` | 404 | User not found |
| `U002` | 409 | Email already registered |
| `A001` | 401 | Authentication required (no token) |
| `A002` | 403 | Authenticated, but not permitted |
| `A003` | 401 | Invalid credentials (unknown email **or** wrong password) |
| `A004` | 401 | Access token expired |
| `A005` | 401 | Access token invalid (bad signature, tampered, wrong type/issuer) |
| `A006` | 403 | Account locked after too many failed logins |
| `A007` | 403 | Account disabled |
| `A008` | 401 | Refresh token unknown or revoked |
| `A009` | 401 | Refresh token expired |
| `B001` | 404 | Book not found (templated: `Book not found: 42`) |
| `V001` | 400 | Validation failed (`fieldErrors` present) |
| `C001` | 400 | Request body unreadable |
| `C002` | 405 | Method not allowed |
| `C003` | 415 | Unsupported media type |
| `C004` | 413 | Payload too large |
| `C005` | 404 | Resource not found |
| `C006` | 409 | Resource conflict |
| `C007` | 400 | Business rule violation |
| `S001` | 500 | Unexpected error |

Clients branch on `code`, never on `message`: the code is the contract, the message is free to be
reworded or localised.

### Raising one

`ErrorCode` owns its code, its HTTP status and its message, and can build its own exception:

```java
throw ErrorCode.BOOK_NOT_FOUND.exception(id);   // 404, B001, "Book not found: 42"
```

Messages may carry `MessageFormat` placeholders, filled in by `exception(Object...)`. Two rules
apply, and `ErrorCodeTest` enforces both rather than trusting a comment:

- **Arguments are identifiers only** — never a password, a token, an email or any other submitted
  value. They land in the response body, so interpolating those would re-open the enumeration and
  value-echo holes the rest of the design closes. The set of templated codes is an allowlist in the
  test; adding a placeholder anywhere else fails the build.
- **No `A0xx` code is templated.** A failed login must be byte-identical between an unknown email
  and a wrong password, and a message that varies with its input cannot be.

When the detail is for operators rather than callers, pass it as an internal message instead — it
reaches the log, while the client still reads the code's own wording:

```java
throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Signup lost the unique-email race");
// log:      Signup lost the unique-email race
// response: {"code":"U002","message":"An account with this email already exists", ...}
```

`formatMessage()` with no arguments returns the message untouched rather than running it through
`MessageFormat`, which treats a single quote as an escape character — formatting `"Doesn't exist"`
with no arguments would otherwise return `"Doesnt exist"`.

---

## 📜 Business rules and why they are written that way

### Duplicate email on signup → 409 `U002`

Checked with `existsByEmail`, and again by the unique index — two concurrent signups can both pass
the first check, so `DataIntegrityViolationException` is caught and reported as the same conflict
rather than a 500.

### Wrong email *or* wrong password → 401 `A003`, identical message for both

Three mechanisms have to agree for this to hold:

1. `hideUserNotFoundExceptions` (left on, and stated explicitly in `SecurityConfig`) converts
   `UsernameNotFoundException` into `BadCredentialsException`.
2. `GlobalApiExceptionHandler` maps `BadCredentialsException` to one code and one message.
3. `ErrorResponse` has no timestamp or other varying field, so the two bodies are **byte-identical**
   — asserted in `AuthFlowIntegrationTest#failedLoginsAreIndistinguishable`.

`LoginRequest` also deliberately does *not* apply the signup password rules. Rejecting a malformed
password with a 400 before checking it would turn login into a password-policy oracle.

### Signup always assigns `ROLE_USER`; a `role` in the body is impossible

`SignupRequest` has no `role` property, so Jackson has nowhere to bind one (unknown properties are
ignored explicitly, not by relying on a framework default that someone could flip). `AuthServiceImpl`
hard-codes `Role.USER` on the entity it builds, so there is no path from the payload to that value at
all. Elevating an account is an out-of-band operation.

### Logout revokes the refresh token; a later refresh with it is 401

`POST /api/v1/auth/logout` revokes every refresh token the caller holds. Refreshing afterwards
returns `A008`. Refresh also **rotates**: exchanging a token revokes it, so each refresh token works
exactly once — a stolen token is usable only until the legitimate client refreshes, and the theft
leaves a trace instead of granting indefinite quiet access.

The access token stays valid until it expires. That is the cost of statelessness, and the trade-off
is discussed under [Stretch goals](#-stretch-goals).

### Five consecutive failed logins lock the account → 403 `A006`

The counter lives on `User.failedLoginCount` and is written by `LoginAttemptService` in its **own
transaction** (`REQUIRES_NEW`). If the increment shared the transaction that then throws
`BadCredentialsException`, the rollback would undo it and the account would never lock — a lockout
that quietly does nothing is worse than none, because everyone believes it is there.

The fifth failure returns `A006` rather than another `A003`, so the user is told why further attempts
will not help. **Trade-off:** that response does reveal the account exists. The alternative (always
`A003`) keeps the enumeration guarantee but leaves a locked-out user with no explanation. A
successful login clears the counter.

---

## 🧯 Security exception handling: two paths

Handling only one path is the classic mistake, and it leaves HTML error pages leaking out of a JSON
API.

| Failure | Thrown / detected | Handled by | Result |
|---|---|---|---|
| No or bad token on a protected route | `ExceptionTranslationFilter` | `CustomAuthenticationEntryPoint` | 401 JSON `A001`/`A004`/`A005` |
| Route table refuses an authenticated caller | `ExceptionTranslationFilter` | `CustomAccessDeniedHandler` | 403 JSON `A002` |
| `@PreAuthorize` refuses the call | inside the dispatch | `GlobalApiExceptionHandler` | 403 JSON `A002` |
| `ApiException`, `BadCredentials`, `Locked`, validation, … | inside the dispatch | `GlobalApiExceptionHandler` | the matching code |
| Authentication provider itself fails (DB down) | either path | shared mapping → `S001` | 500 JSON, stack trace logged |

Registered like this:

```java
.exceptionHandling(ex -> ex
        .authenticationEntryPoint(authenticationEntryPoint)
        .accessDeniedHandler(accessDeniedHandler))
```

A request refused by `authorizeHttpRequests` never reaches a controller, so no `@ExceptionHandler`
can see it — that is why the filter-chain handlers exist. A `@PreAuthorize` denial happens after the
chain has already allowed the request, so no handler in the chain can see it — that is why the advice
also handles `AccessDeniedException`. Spring Security 6 throws `AuthorizationDeniedException`, a
subclass, so one handler covers both.

### One mapping, both paths

Authentication fails in both places, so `AuthenticationException` → `ErrorCode` lives in exactly one
place — `AuthenticationErrorCodes.of(e)` — called by the entry point *and* by every authentication
handler in the advice. A locked account is `A006` whichever path reported it, and no future edit can
drift one path away from the other.

```java
// CustomAuthenticationEntryPoint: the rejected-token attribute is the more specific fact,
// because ExceptionTranslationFilter only ever reports the generic "not authenticated".
Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
if (attribute instanceof ErrorCode errorCode) {
    return errorCode;                              // A004 expired / A005 invalid
}
return AuthenticationErrorCodes.of(authException); // A003 / A006 / A007 / S001 / A001
```

Three decisions that mapping encodes:

- **`UsernameNotFoundException` → `A003`, never `U001`.** `DaoAuthenticationProvider` already hides
  it, but mapping it explicitly means the identical-response guarantee survives a future provider
  that does not.
- **`AuthenticationServiceException` → `S001` (500).** The provider broke — the database is
  unreachable, a collaborator threw. Nothing is wrong with the submitted credentials, so a 401 would
  send the caller off to fix their password while the real fault is server-side.
- **`AccountExpiredException` / `CredentialsExpiredException` → `A001`.** `CustomUserDetails`
  reports both as non-expired because this model has no such states; a code of their own would
  document a rule that does not exist.

The status comes from the `ErrorCode` too, so the entry point answers 403 for `A006`. An
`AuthenticationEntryPoint` returning 403 is unusual, but a code and a status that contradict each
other are worse.

---

## 🧪 curl walkthrough

Copy-pasteable in Git Bash, Linux or macOS (`jq` optional but handy). PowerShell users: prefer
`curl.exe` and single-quote the JSON bodies.

```bash
BASE=http://localhost:8088/api/v1
```

### 1. Signup → 201

```bash
curl -s -X POST $BASE/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","username":"jane_doe","password":"Str0ngPassw0rd"}' | jq
```

```json
{
  "data": { "id": 4, "email": "jane@example.com", "username": "jane_doe", "role": "USER", "enabled": true },
  "status": { "code": 201, "message": "Resource created successfully" }
}
```

### 2. Login → access + refresh token

```bash
LOGIN=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"Str0ngPassw0rd"}')

ACCESS=$(echo "$LOGIN"  | jq -r '.data.accessToken')
REFRESH=$(echo "$LOGIN" | jq -r '.data.refreshToken')
```

The response is tokens only — no profile block:

```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 300
  },
  "status": { "code": 200, "message": "Success" }
}
```

Both values are JWTs. Decode either payload to see the difference — a JWT payload is base64url, not
encryption, so this is also a reminder of what a claim costs:

```bash
echo "$REFRESH" | cut -d. -f2 | base64 -d
# {"iss":"spring-app","sub":"3","jti":"cd565bb9-...","iat":...,"exp":...,"typ":"refresh"}
```

### 3. Authenticated call

```bash
curl -s $BASE/me -H "Authorization: Bearer $ACCESS" | jq
# { "data": { "id": 4, "email": "jane@example.com", "username": "jane_doe", "role": "USER" }, ... }
```

### 4. Refresh (rotates the refresh token)

```bash
REFRESHED=$(curl -s -X POST $BASE/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}")

ACCESS=$(echo "$REFRESHED"  | jq -r '.data.accessToken')
OLD_REFRESH=$REFRESH
REFRESH=$(echo "$REFRESHED" | jq -r '.data.refreshToken')

# the old one is dead already: 401 A008
curl -s -X POST $BASE/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$OLD_REFRESH\"}" | jq
```

### 5. Logout, then try to refresh → 401

```bash
curl -s -X POST $BASE/auth/logout -H "Authorization: Bearer $ACCESS" | jq

curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/auth/refresh \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"
# 401
```

### The checklist, as commands

```bash
# public read, no token
curl -s $BASE/books | jq '.data'

# no token on a write -> 401 JSON (not HTML, not Whitelabel)
curl -s -X POST $BASE/books -H 'Content-Type: application/json' \
  -d '{"title":"t","author":"a"}' | jq
# { "code": "A001", "message": "...", "status": 401, "path": "/api/v1/books" }

# USER on a write -> 403 A002
USER_ACCESS=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"User12345"}' | jq -r '.data.accessToken')
curl -s -X POST $BASE/books -H "Authorization: Bearer $USER_ACCESS" \
  -H 'Content-Type: application/json' -d '{"title":"t","author":"a"}' | jq '.code'   # "A002"

# MANAGER can create, but not delete
MGR=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"manager@example.com","password":"Manager12345"}' | jq -r '.data.accessToken')
BOOK_ID=$(curl -s -X POST $BASE/books -H "Authorization: Bearer $MGR" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Refactoring","author":"Martin Fowler"}' | jq -r '.data.id')
curl -s -X DELETE $BASE/books/$BOOK_ID -H "Authorization: Bearer $MGR" | jq '.code'  # "A002" (403)

# ADMIN can delete -> 204, empty body
ADMIN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Admin12345"}' | jq -r '.data.accessToken')
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $BASE/books/$BOOK_ID \
  -H "Authorization: Bearer $ADMIN"                                                  # 204

# unknown email vs wrong password: byte-identical bodies
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"nobody@example.com","password":"Str0ngPassw0rd"}' > /tmp/a.json
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"WrongPassw0rd"}'   > /tmp/b.json
cmp /tmp/a.json /tmp/b.json && echo "identical"

# a role in the signup body does not create an admin
curl -s -X POST $BASE/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"sneaky@example.com","username":"sneaky","password":"Str0ngPassw0rd","role":"ADMIN"}' \
  | jq '.data.role'                                                                  # "USER"

# one character changed in the JWT -> 401 A005
curl -s $BASE/me -H "Authorization: Bearer ${ACCESS}x" | jq '.code'                  # "A005"
```

An **expired** token returns `A004`, distinct from `A005`. Rather than waiting 5 minutes, that case
is covered by `AuthFlowIntegrationTest#expiredTokenIsA004`, which mints a token with a negative TTL.

---

## 📖 Swagger UI

- UI: <http://localhost:8088/swagger-ui.html>
- Document: <http://localhost:8088/v3/api-docs>

Click **Authorize**, paste the `accessToken` from login (**no** `Bearer` prefix — the scheme adds
it), and protected operations will send the header. The requirement is applied per operation with
`@SecurityRequirement` rather than globally, so the document tells the truth about which endpoints
are public: login and `GET /api/v1/books` are not marked as needing a token, because they do not.

Swagger UI is disabled by default in the `prod` profile — interactive docs publish the full shape of
the API, including which endpoints exist.

---

## 🧪 Testing

```bash
./gradlew test
```

109 tests against in-memory H2 (`src/test/resources/application.yml` shadows the main config, so
tests never touch a real database or a real secret).

| Suite | Covers |
|---|---|
| `JwtTokenProviderTest` (17) | claim round-trip, tampered token, expired vs invalid, foreign signature, foreign issuer, short-secret rejection, refresh-value entropy |
| `AuthFlowIntegrationTest` (23) | signup, duplicate `U002` (incl. case-insensitivity), role-in-body ignored, validation `V001`, byte-identical login failures, working token, `A004`/`A005`, refresh token rejected as access token, rotation, logout revocation, lockout at five, counter reset |
| `BookAuthorizationIntegrationTest` (12) | the full matrix, including that 401s and 403s are JSON with the right code and that `DELETE` as ADMIN is 204 with an empty body |
| `LogHygieneIntegrationTest` (1) | captures everything the app logs at TRACE during a full auth flow and fails if any line contains the password, either token, or a BCrypt hash |
| `ErrorCodeTest` (29) | message rendering, apostrophe survival, the `exception(...)` factory, internal-vs-client message, the templating allowlist, and one case per code asserting no `A0xx` message is templated |
| `AuthenticationErrorCodesTest` (7) | the shared mapping: unknown account indistinguishable from a wrong password, lock/disable codes and statuses, provider failure as 500, unmodelled account states, null tolerated |
| `CustomAuthenticationEntryPointTest` (6) | which code wins (attribute over exception), `A004` vs `A005`, `A006` status 403, no leak of `UsernameNotFoundException`, no timestamp in the body |
| `AuthHelperTest` (5) | the static principal accessors: display name vs login identifier, every field read, and that an anonymous or foreign principal reads as absent instead of throwing |
| `RefreshTokenStoreIntegrationTest` (7) | the storage port: issue/find, unknown jti, single and bulk revoke, expired-not-active, and that the cleanup job removes expired records while keeping live and revoked-but-live ones |
| `SpringAppApplicationTests` (2) | context loads, and the suite really is on H2 (a guard against local override files outranking the test config) |

---

## 🔐 Secret handling

- `jwt.secret` comes from `JWT_SECRET` with **no default**. Startup fails without it, and fails if it
  is shorter than 32 bytes.
- `JWT_SECRET` is absent from this repository: `git log -S 'JWT_SECRET'` finds only configuration
  that *reads* the variable, never a value.
- The test secret in `src/test/resources/application.yml` is a fixed, clearly-labelled non-secret so
  token tests are deterministic. It is not used by any deployment.
- Database credentials come from `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`. The `prod` profile has no
  fallbacks: a deployment that has not been given credentials should fail to start, not fall back to
  something guessable.

### Two things that were removed

The RSA keypair that used to live in `src/main/resources/keys/` — **including `private.pem`** — has
been deleted along with the OAuth2 resource-server setup it fed. So has the committed development
database password.

**Both are still in this repository's git history.** Deleting a file does not unpublish it. Treat
that private key and that password as compromised: the key is no longer used by anything here, and
the password needs rotating. Purging history (`git filter-repo`) is the only way to remove them from
the repository itself, and requires a force-push plus coordination with anyone who has a clone.

---

## 🧹 Log hygiene

No password, hash or token is written to a log line. `LogHygieneIntegrationTest` asserts it; to check
by hand:

```bash
./gradlew bootRun > app.log 2>&1
# ... run the curl walkthrough ...

grep -nE '\$2[aby]\$|eyJ[A-Za-z0-9_-]{10,}|Str0ngPassw0rd' app.log
# no matches: no BCrypt hash, no JWT, no password
```

What that relies on:

- Log lines identify accounts by `userId`, not by email — an id is enough for support and forensics
  without turning the log file into a customer address list.
- `User.toString()` excludes `password`; `RefreshToken.toString()` excludes `token`. A stray
  `log.debug("{}", entity)` cannot leak either.
- The rejected values in a validation failure are logged as field *names* only.
- **`org.hibernate.orm.jdbc.bind` is pinned no lower than INFO in every profile.** At TRACE that
  logger prints every parameter bound to a statement, which on a signup means the BCrypt hash. It
  used to be set to TRACE in `application-dev.yml`; that was the one real leak in this project.

---

## ✅ Submission checklist

| Requirement | Where it is proven |
|---|---|
| `GET /api/v1/books` works with no token | `BookAuthorizationIntegrationTest#listIsPublic` |
| `POST /api/v1/books` without a token → 401 JSON | `#createWithoutTokenIsJsonUnauthorized` (asserts content type too) |
| `POST /api/v1/books` as USER → 403 `A002` | `#createAsUserIsForbidden` |
| `DELETE` as MANAGER → 403; as ADMIN → 204 | `#deleteAsManagerIsForbidden`, `#deleteAsAdminSucceeds` |
| Unknown email and wrong password → byte-identical bodies | `AuthFlowIntegrationTest#failedLoginsAreIndistinguishable` |
| `{"role":"ADMIN"}` in signup does not create an admin | `#roleInBodyIsIgnored` |
| One character changed in a JWT → 401 `A005` | `#tamperedTokenIsA005`, `JwtTokenProviderTest#tamperedTokenIsRejected` |
| Expired token → 401 `A004`, distinct from `A005` | `#expiredTokenIsA004`, `JwtTokenProviderTest#expiredTokenIsRejectedAsExpired` |
| Logout, then refresh with the old token → 401 | `#logoutRevokesRefreshToken` |
| No password, hash or token in any log line | `LogHygieneIntegrationTest`, plus the `grep` above |
| `JWT_SECRET` absent from the repository | `git log -S 'JWT_SECRET'` finds no value |
| Swagger Authorize works end to end | [Swagger UI](#-swagger-ui) |
| README with a curl walkthrough | [curl walkthrough](#-curl-walkthrough) |

---

## 🎯 Stretch goals

### Implemented

**`@EnableJpaAuditing` with `AuditorAware<Long>` from `SecurityContextHolder`.**
`BaseEntity` carries `createdBy`/`modifiedBy` and they populate themselves on every write made in a
request thread. The auditor is the user **id**, not the email: an id is a stable foreign key that
survives a rename, does not copy a customer address into every row of every table, and can be
joined. Writes with no principal (the seeder, a migration, an anonymous signup) leave the column
null, which is the honest answer rather than a fabricated `"system"` user.

### Not implemented, and what they would cost

**Refresh tokens in Redis with a TTL instead of a table.** Not implemented, but the seam for it is:
`RefreshTokenStore` is a port, `JpaRefreshTokenStore` is the implementation behind it, and
`AuthServiceImpl` depends only on the interface — so a `RedisRefreshTokenStore` is a new class and a
bean choice rather than surgery on the authentication flow. The port deliberately returns a record
rather than a JPA entity, which is the return type a Redis store could not honestly produce.

The trade-off, for whoever takes it on: Redis expiry deletes records for you, which removes the
sweep below, and keeps refresh writes off the primary database. Against that, it is a second piece
of infrastructure that must be up before anyone can log in, "who is signed in" stops being a SQL
join, and — the one that actually decides the default here — **revocation stops being durable**.
Revoking is a `DELETE`; under snapshotting or `appendfsync everysec` a crash can lose it, and a lost
revocation brings a logged-out token back to life. Losing an *issue* only forces a re-login; losing
a *revoke* is a security failure. `appendfsync always` fixes it and costs the latency Redis was
chosen for, so the honest high-scale answer is a hybrid: Redis for the lookup, the table for the
durable record of revocations.

**A blacklist so logout invalidates the access token too — and the trade-off against statelessness.**
Right now logout revokes refresh tokens; the access token stays valid for up to 5 minutes. Closing
that window means checking a revocation list on **every** authenticated request, and that is the
whole point of the trade-off: statelessness is precisely the property that no request needs to
consult shared state. Add the blacklist and you have re-introduced a dependency on a store being up
and fast for every call, plus a cache to keep it fast, plus the cache invalidation that follows.
The cheaper 90% of the benefit is a short access TTL (5 minutes here) — most "log me out
everywhere" requirements are satisfied by that plus refresh revocation. Reach for a blacklist when
you genuinely need immediate revocation (a compromised account, an administrative ban), and store
only the token `jti` until its natural expiry so the list stays small and self-cleaning.

**OAuth2 social login (Google) alongside JWT.** A second `AuthenticationProvider` and a callback
endpoint that mints the same access/refresh pair, so nothing downstream changes. The real work is
account linking: what happens when a Google identity arrives with an email that already has a
password account.

**Rate limiting on `/auth/login` (Bucket4j) returning 429.** Lockout protects one account; it does
nothing about one attacker spraying one password across thousands of accounts, and it hands anyone a
denial-of-service against a known email. Per-IP rate limiting is the complement, not a substitute.

**Refresh token in an `HttpOnly; Secure; SameSite=Strict` cookie, with CSRF re-enabled for that
endpoint only.** Keeps the long-lived credential out of reach of JavaScript, which is the right
answer for a browser client. The moment a credential is sent automatically by the browser, CSRF
becomes relevant again — hence re-enabling it for exactly that path, and only that path.

---

## 🚧 What this template does not do

- **No HTTPS enforcement.** Tokens over plain HTTP are readable by anything on the path. Terminate
  TLS in front, and add HSTS.
- **No secret rotation.** One signing key, no key id, no overlap window. Rotating it today
  invalidates every live token.
- **No rate limiting** on login or anywhere else.
- **No monitoring or alerting** on the signals that matter — lockouts, `A005` bursts, refresh reuse.
- **No email verification or password reset.** `enabled` exists; nothing sets it to false.
- **No schema migrations.** `ddl-auto: update` in dev, `validate` in prod, and nothing in between.
- **The cleanup job is not cluster-aware.** Every instance runs its own sweep. Harmless (the delete
  is idempotent) but wasteful; a lock or a leader-election profile is the fix when there is more
  than one instance.
- **No security review.** None of the above has been through one.

---

## 📝 Naming conventions

### Java

| What | How | Example |
|---|---|---|
| Classes | PascalCase | `UserController`, `JwtTokenProvider` |
| Methods | camelCase | `findById()`, `generateAccessToken()` |
| Variables | camelCase | `refreshTokenRepository` |
| Constants | UPPER_SNAKE_CASE | `MAX_FAILED_LOGINS` |
| Packages | lowercase | `com.spring.app.security` |
| Enum constants | UPPER_SNAKE_CASE | `Role.ADMIN`, `ErrorCode.TOKEN_EXPIRED` |

### Entities and columns

- `User` → table `users`; `RefreshToken` → table `refresh_tokens`
- `failedLoginCount` → column `failed_login_count`
- Index names spell out the table and column: `idx_users_email`

```java
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_email", columnList = "email", unique = true))
public class User extends BaseEntity {

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Enumerated(EnumType.STRING)          // never ORDINAL for an authorization column
    @Column(name = "role", nullable = false, length = 20)
    private Role role;
}
```

### DTOs

- Requests: `SignupRequest`, `LoginRequest`, `BookRequest`
- Responses: `UserResponse`, `BookResponse`, `AuthResponse`
- Errors: one `ErrorResponse` for the whole API
- Never expose an entity directly — build the projection field by field. Returning the entity is how
  password hashes and audit columns end up in a JSON response the day someone adds a field.

### Validation

```java
public class SignupRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
             message = "Password must contain at least one letter and one digit")
    private String password;
}
```

Messages are written for the person reading them and never quote the submitted value.

---

**Java:** 17+ · **Spring Boot:** 3.5.4 · **Spring Security:** 6.5 · **JWT:** jjwt 0.12.6 (HS256)
