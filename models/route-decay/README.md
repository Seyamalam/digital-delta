# Route-decay model

This directory contains the deterministic, offline training and export pipeline for Digital Delta's M7 route-risk demonstration. It uses synthetic scenario data only. The generated scores do not estimate real flood performance.

## Reproduce the checked artifacts

```bash
cd models/route-decay
uv run --frozen train.py --android-assets ../../apps/field-android/app/src/main/assets
```

The script generates 6,000 seeded examples, keeps training, validation, and held-out test sets separate, selects its operating threshold on validation data, exports an ONNX model, and verifies scikit-learn/ONNX probability parity. The repository-wide local gate retrains to a temporary directory and byte-compares every checked artifact.

See [MODEL_CARD.md](MODEL_CARD.md) for intended use, metrics, limitations, and safety boundaries.
