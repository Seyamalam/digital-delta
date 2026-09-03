package com.example.digitaldelta.domain.prediction

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnnxRouteRiskPredictorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun bundledOnnxModelRunsOfflineAndSeparatesHighAndLowRisk() {
        val predictor = AssetOnnxRouteRiskPredictor(context)

        val high = predictor.predict(RouteRiskFeatures(82.0, 3.0, 0.92))
        val low = predictor.predict(RouteRiskFeatures(8.0, 28.0, 0.22))

        assertTrue(high.impassableWithinTwoHours)
        assertFalse(low.impassableWithinTwoHours)
        assertTrue(high.probability > low.probability)
        assertTrue(high.modelVersion == "route-risk-logreg-v1")
        assertTrue(high.runtime == RouteRiskRuntime.ONNX)
        assertTrue(high.simulatedInputs)
    }
}
