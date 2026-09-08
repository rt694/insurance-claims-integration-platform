# Claims Portal

React and TypeScript workspace for human review of synthetic insurance claims. The
portal lists claims from the Spring Boot claims service with status and type filters,
pagination, loading and empty states, and RFC Problem Details errors. It also submits
new synthetic claims through the same service and refreshes the inventory after a
successful response. Reviewers can open an authoritative claim detail view and see
generated reviewer-assistance summaries when asynchronous processing completes.
The detail workspace also lets a human reviewer choose an allowed lifecycle transition
and inspect the claim's chronological status history.

The portal is an OpenID Connect public client. Signed-out users are sent to the local
Keycloak realm, and successful sign-in returns through Authorization Code with PKCE.
The resulting access token is attached as a bearer token to each claims API request.
The UI shows only actions granted to the current `AGENT`, `REVIEWER`, or `ADMIN` role;
the backend independently enforces the same rules.

## Local development

Use Node.js 24 and pnpm 11. From this directory:

```bash
pnpm install
pnpm dev
```

Before starting the portal, copy the repository root `.env.example` to `.env`, replace
all password placeholders, and start Keycloak from the repository root:

```bash
docker compose up --detach --wait keycloak
```

The realm import creates three synthetic portal identities. Use the username and
password from one of these pairs in the root `.env` file:

| Credential variables | Portal capability |
| --- | --- |
| `KEYCLOAK_AGENT_USERNAME` / `KEYCLOAK_AGENT_PASSWORD` | Read and submit claims |
| `KEYCLOAK_REVIEWER_USERNAME` / `KEYCLOAK_REVIEWER_PASSWORD` | Read and transition claims |
| `KEYCLOAK_ADMIN_USER_USERNAME` / `KEYCLOAK_ADMIN_USER_PASSWORD` | All claim operations |

`KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD` are only for Keycloak's
administration console at `http://localhost:8090`; they are not portal credentials.

Open `http://localhost:5173` (or `http://127.0.0.1:5173`). Both loopback forms are
registered as local Keycloak redirects. The Vite development server proxies browser requests
under `/api` to the claims service at `http://localhost:8080`. This keeps browser code
on a relative URL and avoids enabling broad cross-origin access in Spring Boot.

The checked-in OIDC defaults target the local realm. To use a different environment,
copy this directory's `.env.example` to `.env.local` and set `VITE_OIDC_AUTHORITY` and
`VITE_OIDC_CLIENT_ID`. Vite values are visible to browser code, so they must contain
only public OIDC configuration—never passwords, tokens, or client secrets.

The token-bearing OIDC user is stored in browser session storage. Closing the tab ends
that browser session; signing out also redirects through Keycloak so both the portal
and identity-provider sessions are cleared.

Start the claims service and its policy-service dependency before loading or submitting
real data. The submission form mirrors the service's required fields and basic limits,
while the backend remains authoritative for policy coverage and duplicate-reference
checks. API failures are shown with safe Problem Details messages, field errors, and
correlation references where available.

Use **Review** on an inventory row to load the complete claim and its generated summary.
A summary-specific `404` means asynchronous processing is still underway, so the portal
shows a pending state with a summary-only refresh action. Other detail and summary
failures remain retryable without discarding the claims inventory.

Status controls show only transitions allowed from the claim's current state. A
successful `PATCH /api/v1/claims/{id}/status` updates both the detail panel and inventory
row, then reloads the append-only history. The backend still validates every transition;
conflicts are presented without changing the browser's current claim state.

## Verification

```bash
pnpm lint
pnpm test
pnpm build
```

Component tests use Vitest, Testing Library, and JSDOM. They cover sign-in state, role
mapping, bearer-token attachment, listing, filtering,
pagination, client-side submission validation, successful creation, backend field
errors, reviewer detail loading, and pending-summary refresh. Tests replace `fetch` with
a test double. They also cover successful and rejected lifecycle transitions plus
history refresh, so tests remain fast without requiring the Java services or PostgreSQL.

The production build writes static assets to `dist/`. Container and deployment
integration will be added in Milestone 14.
