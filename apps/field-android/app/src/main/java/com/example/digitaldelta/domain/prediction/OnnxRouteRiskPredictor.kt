package com.example.digitaldelta.domain.prediction

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import org.json.JSONObject

class AssetOnnxRouteRiskPredictor(
    context: Context,
) : RouteRiskPredictor {
    private val applicationContext = context.applicationContext
    private val config by lazy {
        JSONObject(
            applicationContext.assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() },
        )
    }
    private val environment by lazy(OrtEnvironment::getEnvironment)
    private val session by lazy {
        val model = applicationContext.assets.open(MODEL_ASSET).use { it.readBytes() }
        OrtSession.SessionOptions().use { options ->
            // One three-feature row does not benefit from ORT's default per-core thread pools.
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            options.setMemoryPatternOptimization(false)
            options.setCPUArenaAllocator(false)
            environment.createSession(model, options)
        }
    }

    override fun predict(features: RouteRiskFeatures): RouteRiskPrediction {
        check(config.getBoolean("simulated_training_data")) {
            "route-risk model metadata must label synthetic training data"
        }
        val values = floatArrayOf(
            features.rainfallMmPerHour.toFloat(),
            features.elevationMeters.toFloat(),
            features.soilSaturation.toFloat(),
        )
        val probability = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(values),
            longArrayOf(1, values.size.toLong()),
        ).use { tensor ->
            session.run(mapOf(config.getString("input_name") to tensor)).use { output ->
                val probabilities = output.get(config.getString("probability_output_name"))
                    .orElseThrow { IllegalStateException("probability output is missing") }
                    .value as? Array<*>
                    ?: error("probability output has the wrong rank")
                val row = probabilities.singleOrNull() as? FloatArray
                    ?: error("probability output must contain one float row")
                check(row.size == 2) { "probability output must contain two classes" }
                row[1].toDouble()
            }
        }
        val threshold = config.getDouble("threshold")
        return RouteRiskPrediction(
            probability = probability,
            impassableWithinTwoHours = probability >= threshold,
            threshold = threshold,
            modelVersion = config.getString("model_version"),
            simulatedInputs = features.simulated,
            runtime = RouteRiskRuntime.ONNX,
        )
    }

    companion object {
        private const val MODEL_ASSET = "route_risk_v1.onnx"
        private const val CONFIG_ASSET = "route_risk_v1_config.json"
    }
}

class ResilientRouteRiskPredictor(
    private val primary: RouteRiskPredictor,
    private val fallback: RouteRiskPredictor = RouteRiskClassifier(threshold = 0.65),
) : RouteRiskPredictor {
    override fun predict(features: RouteRiskFeatures): RouteRiskPrediction =
        runCatching { primary.predict(features) }.getOrElse { fallback.predict(features) }
}
