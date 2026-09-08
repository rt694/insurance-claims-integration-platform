# Claim Summary Worker

Python/FastAPI worker for safely receiving synthetic `claim.submitted.v1` events,
generating deterministic local reviewer assistance, and publishing versioned
`claim.summary.completed.v1` results.

The worker must never approve, deny, price, or determine coverage for a claim. Claim
descriptions are untrusted data and cannot modify the worker's fixed safety policy.

## Local commands

From this directory:

```bash
~/.local/bin/uv sync
~/.local/bin/uv run ruff check .
~/.local/bin/uv run ruff format --check .
~/.local/bin/uv run mypy
~/.local/bin/uv run pytest
```

The pytest suite includes a RabbitMQ 4.3.5 Testcontainer, so Docker Desktop must be
running. The container uses a randomly generated test password and is removed after
the test.

Start the operational API:

```bash
~/.local/bin/uv run uvicorn claim_summary_worker.main:app --app-dir src --reload --port 8083
```

Then inspect:

- `http://localhost:8083/health/live`
- `http://localhost:8083/health/ready`
- `http://localhost:8083/metrics`
- `http://localhost:8083/docs`

The Prometheus endpoint reports aggregate success/error counts and a processing-time
histogram. It deliberately does not use claim IDs, correlation IDs, descriptions, or
provider error text as labels. Those values would create an unbounded number of time
series and could expose data that does not belong in monitoring telemetry.
The readiness endpoint returns HTTP `503` with `{"status":"DOWN"}` when an enabled
broker runtime is not connected, allowing an orchestrator to stop routing work to an
instance that cannot process it.

## RabbitMQ delivery safety

`RabbitMqConsumer` passively verifies the queues and exchanges declared by the claims
service. It uses a recovery-capable connection, publisher confirms, mandatory routing,
manual acknowledgements, and a configurable prefetch count.

- A valid event is acknowledged only after its injected `ClaimEventHandler` succeeds.
- Invalid contracts are confirmed into `claim.summary.requests.dlq.v1` before the
  original delivery is acknowledged.
- Processing failures are confirmed into `claim.summary.requests.retry.v1`, delayed
  for five seconds, and tried at most three times before dead-lettering.
- If retry or dead-letter publication cannot be confirmed, the original delivery is
  requeued instead of being lost.

## Mock summary provider and result publication

The default `MockSummaryProvider` is deterministic: it proves the provider interface
and strict output contract without making a network call. It summarizes structured
facts, recommends only a human review queue, reports that supporting documentation is
not present in the event, and flags obvious instruction-like text without repeating
or obeying it. It never approves, denies, prices, or determines coverage.

The processor rejects a provider result whose claim ID differs from the consumed
claim. It derives a stable completed-event ID from the source event ID, so RabbitMQ
redelivery produces the same downstream identity and the claims-service inbox can
recognize the duplicate. The result publisher sends persistent JSON to
`claims.events` with routing key `claim.summary.completed.v1`, mandatory routing, and
publisher confirms.

Consumption remains disabled by default. To run the complete local pipeline after
the claims service has declared the RabbitMQ topology, load your ignored `.env` and
explicitly enable the worker:

```bash
cd services/claim-summary-worker
set -a
source ../../.env
set +a
export CLAIM_SUMMARY_WORKER_RABBITMQ_ENABLED=true
export CLAIM_SUMMARY_WORKER_RABBITMQ_USERNAME="$RABBITMQ_USERNAME"
export CLAIM_SUMMARY_WORKER_RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD"
~/.local/bin/uv run uvicorn claim_summary_worker.main:app --app-dir src --port 8083
```

The password comes only from your local environment. When enabled, readiness reports
`UP` only when both the result publisher and request consumer are connected. Submit a
claim through the existing API walkthrough, then retrieve its generated result from
`GET /api/v1/claims/{claimId}/summary`.

## Optional OpenAI provider

The mock provider remains the default and requires no external account. To exercise a
real model, set the provider explicitly and supply the API key through the standard
`OPENAI_API_KEY` environment variable:

```bash
export CLAIM_SUMMARY_WORKER_SUMMARY_PROVIDER=openai
export OPENAI_API_KEY="replace-with-your-api-key"
```

The current default model is `gpt-6-astra`. Override it without changing code when an
environment needs a different compatible model:

```bash
export CLAIM_SUMMARY_WORKER_OPENAI_MODEL=gpt-6-astra
```

The OpenAI adapter uses the Responses API and converts `ClaimSummaryOutput` into a
strict response schema. It sends the fixed safety policy as model instructions and
the allowlisted claim fields as separate untrusted JSON data. Requests are not stored,
are limited to 1,000 output tokens, time out after 30 seconds, and receive at most two
SDK retries by default. These controls can be tuned with
`CLAIM_SUMMARY_WORKER_OPENAI_MAX_OUTPUT_TOKENS`,
`CLAIM_SUMMARY_WORKER_OPENAI_TIMEOUT_SECONDS`, and
`CLAIM_SUMMARY_WORKER_OPENAI_MAX_RETRIES`.

An absent key fails configuration immediately when `openai` is selected. API failures,
refusals, incomplete generations, and missing parsed output become sanitized processing
failures, allowing the existing RabbitMQ retry and dead-letter policy to handle them.
The key and raw upstream error details are never included in those errors.
