import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO

from perf_budget import EXIT_NO_DATA, EXIT_OK, EXIT_OVER, compare, main, summarize


def full(startup=500, open_=(8.0, 12.0), scrub=(8.0, 12.0)):
    return {
        "startupMs": {"p50": startup, "p90": startup + 100},
        "openFrameMs": {"p50": open_[0], "p90": open_[1]},
        "scrubFrameMs": {"p50": scrub[0], "p90": scrub[1]},
    }


def run_main(baseline, new):
    paths = []
    for data in (baseline, new):
        f = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
        json.dump(data, f)
        f.close()
        paths.append(f.name)
    try:
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            return main(["perf_budget.py", "compare", *paths])
    finally:
        for p in paths:
            os.unlink(p)


class CompareTest(unittest.TestCase):
    def test_within_threshold_passes(self):
        self.assertEqual(compare(full(), full(startup=540, scrub=(8.5, 12.5)), 0.10), [])

    def test_slower_startup_fails(self):
        self.assertEqual(len(compare(full(), full(startup=560), 0.10)), 1)

    def test_slower_p90_frame_fails(self):
        failures = compare(full(), full(scrub=(8.0, 14.0)), 0.10)
        self.assertEqual(len(failures), 1)
        self.assertIn("scrubFrameMs p90", failures[0])

    def test_missing_key_raises(self):
        new = full()
        del new["openFrameMs"]
        with self.assertRaises(ValueError):
            compare(full(), new, 0.10)

    def test_missing_percentile_raises(self):
        new = full()
        del new["startupMs"]["p90"]
        with self.assertRaises(ValueError):
            compare(full(), new, 0.10)


class ExitCodeTest(unittest.TestCase):
    def test_within_budget_exits_zero(self):
        self.assertEqual(run_main(full(), full()), EXIT_OK)

    def test_over_budget_exits_one(self):
        self.assertEqual(run_main(full(), full(startup=900)), EXIT_OVER)

    def test_empty_summary_exits_two(self):
        self.assertEqual(run_main(full(), {}), EXIT_NO_DATA)
        self.assertEqual(run_main({}, full()), EXIT_NO_DATA)

    def test_missing_keys_exit_two(self):
        new = full()
        del new["scrubFrameMs"]
        self.assertEqual(run_main(full(), new), EXIT_NO_DATA)

    def test_unreadable_input_exits_two(self):
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            self.assertEqual(main(["perf_budget.py", "compare", "/no/such", "/no/such"]), EXIT_NO_DATA)


class SummarizeTest(unittest.TestCase):
    def test_splits_startup_by_mode_and_frames_by_benchmark(self):
        data = {"benchmarks": [
            {"name": "coldStartNoCompilation", "metrics": {"timeToInitialDisplayMs": {"runs": [900, 1000]}}},
            {"name": "coldStartFullCompilation", "metrics": {"timeToInitialDisplayMs": {"runs": [400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300]}}},
            {"name": "openAppList", "sampledMetrics": {"frameDurationCpuMs": {"P50": 7.0, "P90": 11.0}}},
            {"name": "scrubAndFling", "sampledMetrics": {"frameDurationCpuMs": {"P50": 6.0, "P90": 10.0}}},
        ]}
        self.assertEqual(summarize(data), {
            "startupNoneMs": {"p50": 900, "p90": 1000},
            "startupMs": {"p50": 800, "p90": 1200},
            "openFrameMs": {"p50": 7.0, "p90": 11.0},
            "scrubFrameMs": {"p50": 6.0, "p90": 10.0},
        })

    def test_empty_results_summarize_to_nothing(self):
        self.assertEqual(summarize({}), {})

    def test_summarize_of_nothing_exits_two(self):
        f = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
        json.dump({"benchmarks": []}, f)
        f.close()
        try:
            with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
                self.assertEqual(main(["perf_budget.py", "summarize", f.name]), EXIT_NO_DATA)
        finally:
            os.unlink(f.name)


if __name__ == "__main__":
    unittest.main()
