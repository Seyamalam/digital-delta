package com.example.digitaldelta.ui.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class QrPayloadGateTest {
    @Test
    fun `accepts only the prefix for the active field workflow`() {
        val trust = QrPayloadGate.accept("  DIGITALDELTA:TRUST:abc  ", QrScanPurpose.ADMIN_TRUST)
        val credential = QrPayloadGate.accept("DIGITALDELTA:CREDENTIAL:def", QrScanPurpose.RECIPIENT_CREDENTIAL)
        val handoff = QrPayloadGate.accept("DIGITALDELTA:POD:ghi", QrScanPurpose.DELIVERY_HANDOFF)
        val revocation = QrPayloadGate.accept(
            "DIGITALDELTA:REVOCATION:jkl",
            QrScanPurpose.CREDENTIAL_REVOCATION,
        )

        assertThat(trust).isEqualTo(QrPayloadResult.Accepted("DIGITALDELTA:TRUST:abc"))
        assertThat(credential).isEqualTo(QrPayloadResult.Accepted("DIGITALDELTA:CREDENTIAL:def"))
        assertThat(handoff).isEqualTo(QrPayloadResult.Accepted("DIGITALDELTA:POD:ghi"))
        assertThat(revocation).isEqualTo(QrPayloadResult.Accepted("DIGITALDELTA:REVOCATION:jkl"))
    }

    @Test
    fun `rejects an empty or wrong-purpose QR without forwarding it`() {
        assertThat(QrPayloadGate.accept("", QrScanPurpose.ADMIN_TRUST))
            .isEqualTo(QrPayloadResult.Rejected(QrPayloadRejection.EMPTY))
        assertThat(QrPayloadGate.accept("DIGITALDELTA:POD:abc", QrScanPurpose.RECIPIENT_CREDENTIAL))
            .isEqualTo(QrPayloadResult.Rejected(QrPayloadRejection.WRONG_PURPOSE))
    }
}
