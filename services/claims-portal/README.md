# Claims Portal

React and TypeScript workspace for human review of synthetic insurance claims. The
portal lists claims from the Spring Boot claims service with status and type filters,
pagination, loading and empty states, and RFC Problem Details errors. It also submits
new synthetic claims through the same service and refreshes the inventory after a
successful response.

## Local development

Use Node.js 24 and pnpm 11. From this directory:

```bash
pnpm install
pnpm dev
```

Open `http://localhost:5173`. The Vite development server proxies browser requests
under `/api` to the claims service at `http://localhost:8080`. This keeps browser code
on a relative URL and avoids enabling broad cross-origin access in Spring Boot.

Start the claims service and its policy-service dependency before loading or submitting
real data. The submission form mirrors the service's required fields and basic limits,
while the backend remains authoritative for policy coverage and duplicate-reference
checks. API failures are shown with safe Problem Details messages, field errors, and
correlation references where available.

## Verification

```bash
pnpm lint
pnpm test
pnpm build
```

Component tests use Vitest, Testing Library, and JSDOM. They cover listing, filtering,
pagination, client-side submission validation, successful creation, and backend field
errors. Tests replace `fetch` with a test double, so they are fast and do not require
the Java services or PostgreSQL.

The production build writes static assets to `dist/`. Container and deployment
integration will be added in Milestone 14.
