package com.example.digitaldelta.domain.routing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflineMapContractTest {
    @Test
    fun `base style cannot request internet resources`() {
        val style = OfflineMapContract.baseStyleJson

        assertThat(style).doesNotContain("http://")
        assertThat(style).doesNotContain("https://")
        assertThat(style).doesNotContain("glyphs")
        assertThat(style).doesNotContain("sprite")
        assertThat(style).contains("offline-background")
    }

    @Test
    fun `mission overlay uses geographic Sylhet coordinates and explicit simulation flags`() {
        val overlay = OfflineMapContract.missionGeoJson(
            vehicle = VehicleType.BOAT,
            failedRoad = true,
            predictedRisk = true,
        )

        assertThat(overlay).contains("91.8687")
        assertThat(overlay).contains("24.8949")
        assertThat(overlay).contains("\"transport\":\"water\"")
        assertThat(overlay).contains("\"failed\":true")
        assertThat(overlay).contains("\"predicted_risk\":true")
        assertThat(overlay).contains("\"simulated\":true")
    }
}
