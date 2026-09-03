package com.example.digitaldelta.domain.prediction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRiskClassifierTest {
    private val classifier = RouteRiskClassifier(threshold = 0.65)

    @Test
    fun `heavy rain low elevation and saturated soil predict impassability`() {
        val prediction = classifier.predict(
            RouteRiskFeatures(rainfallMmPerHour = 82.0, elevationMeters = 3.0, soilSaturation = 0.92),
        )

        assertTrue(prediction.impassableWithinTwoHours)
        assertTrue(prediction.probability >= 0.65)
        assertTrue(prediction.simulatedInputs)
        assertEquals(RouteRiskRuntime.BASELINE_FALLBACK, prediction.runtime)
    }

    @Test
    fun `light rain high elevation and dry soil remain below threshold`() {
        val prediction = classifier.predict(
            RouteRiskFeatures(rainfallMmPerHour = 8.0, elevationMeters = 28.0, soilSaturation = 0.22),
        )

        assertFalse(prediction.impassableWithinTwoHours)
        assertTrue(prediction.probability < 0.65)
    }

    @Test
    fun `resilient predictor exposes baseline fallback when model fails`() {
        val predictor = ResilientRouteRiskPredictor(
            primary = RouteRiskPredictor { error("model unavailable") },
            fallback = classifier,
        )

        val prediction = predictor.predict(
            RouteRiskFeatures(rainfallMmPerHour = 82.0, elevationMeters = 3.0, soilSaturation = 0.92),
        )

        assertEquals(RouteRiskRuntime.BASELINE_FALLBACK, prediction.runtime)
        assertEquals("baseline-logit-v1", prediction.modelVersion)
        assertTrue(prediction.impassableWithinTwoHours)
    }
}
