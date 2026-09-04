package com.example.digitaldelta.ui.scanner

enum class QrScanPurpose(val requiredPrefix: String) {
    ADMIN_TRUST("DIGITALDELTA:TRUST:"),
    RECIPIENT_CREDENTIAL("DIGITALDELTA:CREDENTIAL:"),
    DELIVERY_HANDOFF("DIGITALDELTA:POD:"),
}

enum class QrPayloadRejection {
    EMPTY,
    WRONG_PURPOSE,
}

sealed interface QrPayloadResult {
    data class Accepted(val value: String) : QrPayloadResult
    data class Rejected(val reason: QrPayloadRejection) : QrPayloadResult
}

object QrPayloadGate {
    fun accept(rawValue: String?, purpose: QrScanPurpose): QrPayloadResult {
        val normalized = rawValue?.trim().orEmpty()
        if (normalized.isEmpty()) return QrPayloadResult.Rejected(QrPayloadRejection.EMPTY)
        if (!normalized.startsWith(purpose.requiredPrefix)) {
            return QrPayloadResult.Rejected(QrPayloadRejection.WRONG_PURPOSE)
        }
        return QrPayloadResult.Accepted(normalized)
    }
}
