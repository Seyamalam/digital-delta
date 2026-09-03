"""Generate deterministic synthetic route-decay observations for the fair prototype."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

import numpy as np


SEED = 20260412
FEATURE_NAMES = ("rainfall_mm_per_hour", "elevation_meters", "soil_saturation")


def generate(rows: int, seed: int = SEED) -> tuple[np.ndarray, np.ndarray]:
    if rows < 100:
        raise ValueError("at least 100 rows are required")
    rng = np.random.default_rng(seed)
    rainfall = np.clip(rng.gamma(shape=2.0, scale=21.0, size=rows), 0.0, 140.0)
    elevation = np.clip(rng.normal(loc=13.0, scale=9.0, size=rows), 0.0, 55.0)
    saturation = np.clip(rng.beta(a=2.5, b=1.8, size=rows), 0.0, 1.0)

    # This is a scenario rule, not observed flood ground truth. Noise prevents the
    # trained model from merely restating one exact threshold expression.
    hidden_logit = (
        -4.25
        + rainfall * 0.052
        - elevation * 0.075
        + saturation * 4.1
        + ((rainfall > 72.0) & (saturation > 0.72)) * 0.55
        + rng.normal(0.0, 0.48, size=rows)
    )
    probability = 1.0 / (1.0 + np.exp(-hidden_logit))
    labels = rng.binomial(1, probability).astype(np.int64)
    features = np.column_stack((rainfall, elevation, saturation)).astype(np.float32)
    return features, labels


def write_csv(path: Path, features: np.ndarray, labels: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(("sample_id", *FEATURE_NAMES, "impassable_within_two_hours", "synthetic"))
        for index, (values, label) in enumerate(zip(features, labels, strict=True)):
            writer.writerow(
                (
                    f"syn-{index:05d}",
                    f"{values[0]:.5f}",
                    f"{values[1]:.5f}",
                    f"{values[2]:.7f}",
                    int(label),
                    True,
                )
            )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rows", type=int, default=6000)
    parser.add_argument("--output", type=Path, default=Path("data/synthetic_route_risk.csv"))
    args = parser.parse_args()
    features, labels = generate(args.rows)
    write_csv(args.output, features, labels)
    print(f"wrote {len(labels)} visibly synthetic rows to {args.output}")


if __name__ == "__main__":
    main()
