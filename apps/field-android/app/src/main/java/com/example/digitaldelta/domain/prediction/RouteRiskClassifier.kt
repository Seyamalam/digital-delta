package com.example.digitaldelta.domain.prediction

import kotlin.math.exp

data class RouteRiskFeatures(
    val rainfallMmPerHour: Double,
    val elevationMeters: Double,
    val soilSaturation: Double,
    val simulated: Boolean = true,
) {
    init {
        require(rainfallMmPerHour >= 0.0)
        require(elevationMeters >= -10.0)
        require(soilSaturation in 0.0..1.0)
    }
}

data class RouteRiskPrediction(
    val probability: Double,
    val impassableWithinTwoHours: Boolean,
    val threshold: Double,
    val modelVersion: String,
    val simulatedInputs: Boolean,
)

/**
 * A deterministic, on-device logistic baseline used until the versioned ONNX model is bundled.
 * It never turns a prediction into a confirmed closure; callers may only apply a route penalty.
 */
class RouteRiskClassifier(
    private val threshold: Double,
) {
    init {
        require(threshold in 0.0..1.0)
    }

    fun predict(features: RouteRiskFeatures): RouteRiskPrediction {
        val logit = -3.0 +
            (features.rainfallMmPerHour * 0.04) -
            (features.elevationMeters * 0.06) +
            (features.soilSaturation * 3.2)
        val probability = 1.0 / (1.0 + exp(-logit))
        return RouteRiskPrediction(
            probability = probability,
            impassableWithinTwoHours = probability >= threshold,
            threshold = threshold,
            modelVersion = "baseline-logit-v1",
            simulatedInputs = features.simulated,
        )
    }
}
