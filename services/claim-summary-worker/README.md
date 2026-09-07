# Claim Summary Worker

Python/FastAPI worker for safely receiving synthetic `claim.submitted.v1` events and,
in the next story, generating structured reviewer-assistance summaries.

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
- `http://localhost:8083/docs`

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

Consumption remains disabled by default until the provider-and-result-publisher story
supplies the real handler. This prevents the foundation from acknowledging a valid
claim without producing its summary. When consumption is enabled, the RabbitMQ
password is required through
`CLAIM_SUMMARY_WORKER_RABBITMQ_PASSWORD`; it is never stored in this repository.
Readiness then reports `UP` only while that injected consumer is connected and active.
