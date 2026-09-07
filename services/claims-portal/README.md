# Claims Portal

React and TypeScript workspace for human review of synthetic insurance claims. The
portal currently lists claims from the Spring Boot claims service with status and
type filters, pagination, loading and empty states, and RFC Problem Details errors.

## Local development

Use Node.js 24 and pnpm 11. From this directory:

```bash
pnpm install
pnpm dev
```

Open `http://localhost:5173`. The Vite development server proxies browser requests
under `/api` to the claims service at `http://localhost:8080`. This keeps browser code
on a relative URL and avoids enabling broad cross-origin access in Spring Boot.

Start the claims service before loading real data. If it is unavailable, the portal
shows a retryable service error instead of crashing.

## Verification

```bash
pnpm lint
pnpm test
pnpm build
```

Component tests use Vitest, Testing Library, and JSDOM. They replace `fetch` with a
test double, so tests are fast and do not require the Java service or PostgreSQL.

The production build writes static assets to `dist/`. Container and deployment
integration will be added in Milestone 14.
