#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# ///
"""Fail when the new app is slower than the baseline by more than a threshold.

Both inputs are summary files of the shape
  {"startupMs":    {"p50": 480, "p90": 520},
   "openFrameMs":  {"p50": 8.1, "p90": 14.0},
   "scrubFrameMs": {"p50": 8.1, "p90": 14.0}}
Every measure above, with both percentiles, is required on both sides: a gate that passes because
nothing was compared is worse than no gate. `summarize` turns a Macrobenchmark JSON file into
that shape (startup from the fully compiled run, so it compares like with like; the uncompiled
run is kept as extra, uncompared, `startupNoneMs`). The baseline is written by hand from the
phone measurement of the previous launcher.

  perf_budget.py compare BASELINE.json NEW.json [--threshold 0.10]
  perf_budget.py summarize benchmarkData.json > new.json

Exit codes: 0 within budget, 1 over budget, 2 no data or bad input.
"""
import json
import math
import sys

REQUIRED = ("startupMs", "openFrameMs", "scrubFrameMs")
PERCENTILES = ("p50", "p90")

EXIT_OK, EXIT_OVER, EXIT_NO_DATA = 0, 1, 2

# Benchmark test name -> summary key for the timeToInitialDisplay runs.
STARTUP_BENCHMARKS = {
    "coldStartFullCompilation": "startupMs",
    "coldStartNoCompilation": "startupNoneMs",
}
FRAME_BENCHMARKS = {"openAppList": "openFrameMs", "scrubAndFling": "scrubFrameMs"}


def missing(summary: dict) -> list[str]:
    """Required measures absent from a summary, as `key.pct` names."""
    return [
        f"{key}.{pct}"
        for key in REQUIRED
        for pct in PERCENTILES
        if not isinstance(summary.get(key), dict) or summary[key].get(pct) is None
    ]


def compare(baseline: dict, new: dict, threshold: float) -> list[str]:
    """One message per measure where `new` exceeds `baseline` by more than `threshold` (a fraction).

    Raises ValueError when either side lacks a required measure.
    """
    for label, summary in (("baseline", baseline), ("new", new)):
        absent = missing(summary)
        if absent:
            raise ValueError(f"{label} is missing: {', '.join(absent)}")
    failures = []
    for key in REQUIRED:
        for pct in PERCENTILES:
            base, cur = baseline[key][pct], new[key][pct]
            if cur > base * (1 + threshold):
                failures.append(f"{key} {pct}: {cur} vs baseline {base} (limit {base * (1 + threshold):.2f})")
    return failures


def summarize(data: dict) -> dict:
    """Startup percentiles from the run samples; frame percentiles from Macrobenchmark's own."""
    out: dict = {}
    for bench in data.get("benchmarks", []):
        name = bench.get("name")
        if name in STARTUP_BENCHMARKS:
            runs = bench.get("metrics", {}).get("timeToInitialDisplayMs", {}).get("runs")
            if runs:
                out[STARTUP_BENCHMARKS[name]] = {"p50": _percentile(runs, 50), "p90": _percentile(runs, 90)}
        elif name in FRAME_BENCHMARKS:
            frames = bench.get("sampledMetrics", {}).get("frameDurationCpuMs")
            if frames:
                out[FRAME_BENCHMARKS[name]] = {"p50": frames["P50"], "p90": frames["P90"]}
    return out


def _percentile(values: list[float], pct: int) -> float:
    """Nearest-rank percentile."""
    ordered = sorted(values)
    return ordered[max(0, math.ceil(pct / 100 * len(ordered)) - 1)]


def _load(path: str) -> dict:
    with open(path) as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ValueError(f"{path} is not a JSON object")
    return data


def main(argv: list[str]) -> int:
    try:
        if len(argv) >= 3 and argv[1] == "summarize":
            summary = summarize(_load(argv[2]))
            json.dump(summary, sys.stdout, indent=2)
            print()
            if not summary:
                print("no benchmark results found in the input", file=sys.stderr)
                return EXIT_NO_DATA
            return EXIT_OK
        if len(argv) >= 4 and argv[1] == "compare":
            threshold = float(argv[argv.index("--threshold") + 1]) if "--threshold" in argv else 0.10
            failures = compare(_load(argv[2]), _load(argv[3]), threshold)
            for line in failures:
                print("FAIL", line)
            print("over budget" if failures else "within budget")
            return EXIT_OVER if failures else EXIT_OK
    except (OSError, ValueError, KeyError) as error:
        print(f"no data / bad input: {error}", file=sys.stderr)
        return EXIT_NO_DATA
    print(__doc__)
    return EXIT_NO_DATA


if __name__ == "__main__":
    sys.exit(main(sys.argv))
