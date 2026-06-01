import time
import unittest
from pathlib import Path

from fluxdsl.parser import parse


class TestBenchmark(unittest.TestCase):

    def test_benchmark_sample_files(self):
        samples_dir = Path(__file__).resolve().parent.parent.parent.parent / "samples"
        if not samples_dir.is_dir():
            self.skipTest("samples/ directory not found")

        fx_files = sorted(samples_dir.rglob("*.fx"))
        if not fx_files:
            self.skipTest("No .fx files found in samples/")

        warmup = 3
        iterations = 10

        results = []

        for file in fx_files:
            text = file.read_text()
            for _ in range(warmup):
                parse(text)

            total = 0.0
            for _ in range(iterations):
                start = time.perf_counter()
                parse(text)
                end = time.perf_counter()
                total += (end - start) * 1000

            avg = total / iterations
            results.append((file.relative_to(samples_dir.parent), avg))

        print(f"\nBenchmark results ({iterations} iterations, warmup={warmup}):")
        print("-" * 60)
        for path, avg in results:
            print(f"  {str(path):<45} {avg:8.3f} ms")
        print()
