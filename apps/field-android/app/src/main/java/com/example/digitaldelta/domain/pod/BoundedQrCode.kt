package com.example.digitaldelta.domain.pod

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/** QR capacity and scan density are separate from validity of a signed offer. */
fun boundedQrMatrix(code: String): BitMatrix? {
    if (code.isBlank() || code.toByteArray(Charsets.UTF_8).size > 1_800) return null
    return runCatching { QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 520, 520) }.getOrNull()
}
