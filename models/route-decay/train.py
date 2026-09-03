"""Train, evaluate, export, and parity-check the tiny route-risk model."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix, f1_score, precision_score, recall_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

from generate_dataset import FEATURE_NAMES, SEED, generate, write_csv


MODEL_VERSION = "route-risk-logreg-v1"


def scores(labels: np.ndarray, predicted: np.ndarray) -> dict[str, object]:
    return {
        "precision": round(float(precision_score(labels, predicted, zero_division=0)), 6),
        "recall": round(float(recall_score(labels, predicted, zero_division=0)), 6),
        "f1": round(float(f1_score(labels, predicted, zero_division=0)), 6),
        "confusion_matrix": confusion_matrix(labels, predicted).tolist(),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rows", type=int, default=6000)
    parser.add_argument("--output-dir", type=Path, default=Path("artifacts"))
    parser.add_argument("--android-assets", type=Path)
    args = parser.parse_args()

    features, labels = generate(args.rows)
    train_x, remainder_x, train_y, remainder_y = train_test_split(
        features,
        labels,
        test_size=0.30,
        random_state=SEED,
        stratify=labels,
    )
    validation_x, test_x, validation_y, test_y = train_test_split(
        remainder_x,
        remainder_y,
        test_size=0.50,
        random_state=SEED + 1,
        stratify=remainder_y,
    )
    model = Pipeline(
        steps=(
            ("scale", StandardScaler()),
            ("classifier", LogisticRegression(max_iter=1000, random_state=SEED)),
        )
    )
    model.fit(train_x, train_y)

    validation_probability = model.predict_proba(validation_x)[:, 1]
    test_probability = model.predict_proba(test_x)[:, 1]
    threshold_candidates = np.linspace(0.25, 0.75, 101)
    decision_threshold = float(
        max(
            threshold_candidates,
            key=lambda threshold: (
                f1_score(validation_y, validation_probability >= threshold),
                precision_score(validation_y, validation_probability >= threshold, zero_division=0),
                threshold,
            ),
        )
    )
    validation_prediction = (validation_probability >= decision_threshold).astype(np.int64)
    test_prediction = (test_probability >= decision_threshold).astype(np.int64)
    baseline_prediction = (
        (test_x[:, 0] >= 60.0) & (test_x[:, 1] <= 12.0) & (test_x[:, 2] >= 0.72)
    ).astype(np.int64)

    onnx_model = convert_sklearn(
        model,
        name=MODEL_VERSION,
        initial_types=[("features", FloatTensorType([None, len(FEATURE_NAMES)]))],
        target_opset=17,
        options={id(model): {"zipmap": False}},
    )
    args.output_dir.mkdir(parents=True, exist_ok=True)
    model_path = args.output_dir / "route_risk_v1.onnx"
    model_path.write_bytes(onnx_model.SerializeToString())

    runtime = ort.InferenceSession(model_path.read_bytes(), providers=["CPUExecutionProvider"])
    runtime_outputs = runtime.run(None, {"features": test_x[:128]})
    onnx_probability = next(
        output[:, 1]
        for output in runtime_outputs
        if isinstance(output, np.ndarray) and output.ndim == 2 and output.shape[1] == 2
    )
    parity_error = float(np.max(np.abs(test_probability[:128] - onnx_probability)))
    if parity_error > 1e-5:
        raise RuntimeError(f"ONNX parity error {parity_error} exceeds 1e-5")

    write_csv(args.output_dir / "synthetic_route_risk.csv", features, labels)
    metrics = {
        "model_version": MODEL_VERSION,
        "dataset": {
            "kind": "deterministic synthetic scenario data; not observed ground truth",
            "seed": SEED,
            "rows": len(labels),
            "positive_rate": round(float(np.mean(labels)), 6),
            "features": list(FEATURE_NAMES),
            "split_rows": {
                "train": len(train_y),
                "validation": len(validation_y),
                "test": len(test_y),
            },
        },
        "threshold": decision_threshold,
        "validation": scores(validation_y, validation_prediction),
        "held_out_test": scores(test_y, test_prediction),
        "non_ml_baseline_test": scores(test_y, baseline_prediction),
        "onnx_parity_max_absolute_error": parity_error,
    }
    (args.output_dir / "metrics.json").write_text(
        json.dumps(metrics, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    config = {
        "model_version": MODEL_VERSION,
        "threshold": decision_threshold,
        "input_name": "features",
        "probability_output_name": "probabilities",
        "feature_order": list(FEATURE_NAMES),
        "simulated_training_data": True,
    }
    (args.output_dir / "model_config.json").write_text(
        json.dumps(config, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    if args.android_assets is not None:
        args.android_assets.mkdir(parents=True, exist_ok=True)
        (args.android_assets / model_path.name).write_bytes(model_path.read_bytes())
        (args.android_assets / "route_risk_v1_config.json").write_text(
            json.dumps(config, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
    print(json.dumps(metrics["held_out_test"], sort_keys=True))
    print(f"ONNX parity max absolute error: {parity_error:.9g}")


if __name__ == "__main__":
    main()
