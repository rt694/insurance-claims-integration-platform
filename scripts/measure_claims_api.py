#!/usr/bin/env python3
"""Measure a claims API endpoint with bounded concurrent HTTP requests."""

from __future__ import annotations

import argparse
import json
import math
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass


@dataclass(frozen=True)
class RequestResult:
    status: int
    duration_ms: float


def request_once(url: str, token: str | None, timeout_seconds: float) -> RequestResult:
    headers = {"Accept": "application/json", "X-Correlation-ID": "performance-baseline"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    started_at = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        error.read()
        status = error.code
    except urllib.error.URLError:
        status = 0
    return RequestResult(status=status, duration_ms=(time.perf_counter() - started_at) * 1_000)


def percentile(values: list[float], percentage: float) -> float:
    ordered = sorted(values)
    index = max(0, math.ceil(percentage * len(ordered)) - 1)
    return ordered[index]


def measure(
    url: str,
    token: str | None,
    request_count: int,
    concurrency: int,
    timeout_seconds: float,
    warmup_count: int,
) -> dict[str, object]:
    for _ in range(warmup_count):
        request_once(url, token, timeout_seconds)

    started_at = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        results = list(
            executor.map(
                lambda _: request_once(url, token, timeout_seconds), range(request_count)
            )
        )
    elapsed_seconds = time.perf_counter() - started_at
    durations = [result.duration_ms for result in results]
    successes = sum(200 <= result.status < 300 for result in results)
    statuses: dict[str, int] = {}
    for result in results:
        key = str(result.status) if result.status else "connection_error"
        statuses[key] = statuses.get(key, 0) + 1

    return {
        "url": url,
        "requests": request_count,
        "concurrency": concurrency,
        "successes": successes,
        "errors": request_count - successes,
        "error_rate": round((request_count - successes) / request_count, 4),
        "throughput_requests_per_second": round(request_count / elapsed_seconds, 2),
        "latency_ms": {
            "mean": round(statistics.fmean(durations), 2),
            "p50": round(percentile(durations, 0.50), 2),
            "p95": round(percentile(durations, 0.95), 2),
            "p99": round(percentile(durations, 0.99), 2),
            "max": round(max(durations), 2),
        },
        "statuses": statuses,
    }


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError("must be at least 1")
    return parsed


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Measure throughput and latency for a running claims API endpoint."
    )
    parser.add_argument(
        "--url", default="http://localhost:8080/api/v1/claims?page=0&size=20"
    )
    parser.add_argument("--requests", type=positive_int, default=100)
    parser.add_argument("--concurrency", type=positive_int, default=10)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--max-error-rate", type=float, default=0.01)
    parser.add_argument("--max-p95-ms", type=float)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.warmup < 0 or args.timeout <= 0:
        raise SystemExit("--warmup cannot be negative and --timeout must be positive")
    if not 0 <= args.max_error_rate <= 1:
        raise SystemExit("--max-error-rate must be between 0 and 1")

    report = measure(
        url=args.url,
        token=os.getenv("CLAIMS_PERF_TOKEN"),
        request_count=args.requests,
        concurrency=min(args.concurrency, args.requests),
        timeout_seconds=args.timeout,
        warmup_count=args.warmup,
    )
    print(json.dumps(report, indent=2, sort_keys=True))

    failed = report["error_rate"] > args.max_error_rate
    latency = report["latency_ms"]
    if args.max_p95_ms is not None and latency["p95"] > args.max_p95_ms:
        failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
