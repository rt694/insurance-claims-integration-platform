# Claim Summary Worker

Python/FastAPI foundation for generating structured reviewer-assistance summaries from
synthetic `claim.submitted.v1` events.

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

Start the operational API:

```bash
~/.local/bin/uv run uvicorn claim_summary_worker.main:app --app-dir src --reload --port 8083
```

Then inspect:

- `http://localhost:8083/health/live`
- `http://localhost:8083/health/ready`
- `http://localhost:8083/docs`

At this foundation stage, readiness means configuration and the fixed safety policy
loaded successfully. The RabbitMQ story will extend readiness to include the worker's
broker connection and consumer state.
