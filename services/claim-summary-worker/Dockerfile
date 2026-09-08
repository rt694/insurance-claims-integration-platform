FROM ghcr.io/astral-sh/uv:0.12.10 AS uv
FROM python:3.12.14-slim-bookworm

COPY --from=uv /uv /uvx /bin/
RUN useradd --create-home --uid 10001 app

WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    PATH="/app/.venv/bin:$PATH"

COPY pyproject.toml uv.lock README.md ./
RUN uv sync --locked --no-dev --no-install-project

COPY src src
RUN uv sync --locked --no-dev --no-editable

USER app
EXPOSE 8083
ENTRYPOINT ["uvicorn", "claim_summary_worker.main:app", "--host", "0.0.0.0", "--port", "8083"]
