# Digital Delta route-risk model card

## Model details

- **Name:** `route-risk-logreg-v1`
- **Type:** standardized logistic regression exported as ONNX, opset 17
- **Runtime:** ONNX Runtime Android 1.23.2, entirely on device
- **Decision threshold:** 0.285, selected on the validation split for F1, then precision
- **Inputs, in order:** rainfall in millimetres per hour, elevation in metres, and soil saturation from 0 to 1
- **Output:** probability that a graph edge becomes impassable within two hours

The fair build visibly labels both environmental inputs and training data as simulated. It uses no sensors, IoT equipment, live weather service, or commercial internet.

## Intended use

The model demonstrates an offline prediction feeding M4 routing. A probability above the threshold adds a risk penalty to an edge. It does **not** mark that edge as a confirmed closure. In the seeded Sylhet scenario, the penalty makes a boat route preferable to the risk-adjusted truck route while leaving both graph paths inspectable.

This model is suitable for a deterministic innovation-fair demonstration and software integration testing. It is not suitable for real evacuation, dispatch, navigation, or public-safety decisions.

## Training and evaluation data

`generate_dataset.py` creates 6,000 deterministic synthetic examples with seed `20260412`. Labels are generated scenario labels, not field observations or ground truth.

- Training: 4,200 rows
- Validation: 900 rows
- Held-out test: 900 rows
- Positive-label rate: 0.381833
- Dataset SHA-256: `7f2ffcc8197474f4ad5a3f3c26f6a9eb4e5c9a2abb35e506ee7efa13a8c1951e`
- Dataset license/provenance: project-generated synthetic data; no third-party dataset

The seed, generator, frozen dependencies, dataset, configuration, metrics, and exported model are checked into version control so the result is reproducible locally.

## Metrics

| Evaluation | Precision | Recall | F1 | Confusion matrix `[[TN, FP], [FN, TP]]` |
|---|---:|---:|---:|---|
| Validation | 0.627706 | 0.845481 | 0.720497 | `[[385, 172], [53, 290]]` |
| Held-out test | 0.612766 | 0.837209 | 0.707617 | `[[374, 182], [56, 288]]` |
| Rule baseline, held-out test | 0.954545 | 0.061047 | 0.114754 | `[[555, 1], [323, 21]]` |

The lower threshold favors recall for the demonstration: it catches more synthetic risk cases at the cost of false alarms. These numbers measure agreement with the synthetic label function only and must not be presented as expected Bangladesh flood accuracy.

The maximum absolute probability difference across the 128-sample export parity check is `8.940696716308594e-08`. Model SHA-256 is `ef2bace1ce45c2441d198775107f56c9cc383fc467ebf1c773bd1f9bea5c92e5`.

## Failure modes and safeguards

- Heavy rainfall, low elevation, and saturation are an intentionally simplified feature set. River flow, drainage, embankments, forecasts, road condition, and observation age are absent.
- Synthetic correlations may not match any real district or monsoon event.
- False positives can cause unnecessary reroutes; false negatives can leave a risky edge under-penalized.
- Missing or corrupt model assets fall back to a visible deterministic baseline state. A native-runtime incompatibility still requires a compatible runtime build; version 1.23.2 is pinned because 1.29.0 raised an illegal-instruction crash on the Android 16 ARM64 emulator used for verification.
- Prediction and confirmed closure remain different route causes. A prediction never creates proof of physical conditions and never transfers custody.
- Production work requires governed field data, provenance, time-aware validation, calibration, district-level evaluation, monitoring, and human operational review.

## Verification

`scripts/verify-local.sh` regenerates and byte-compares the dataset, metrics, config, and model, then runs Android unit tests. Connected verification additionally loads the bundled ONNX asset on an Android emulator, compares high- and low-risk examples, and runs the bilingual proactive-reroute journey.
