package com.example.digitaldelta.domain.pod

import com.example.digitaldelta.proto.v1.MissionCustodySnapshot
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BoundedQrCodeTest {
    @Test fun largestSupportedUuidHistoryRemainsVerifiableWithoutCrashingQrEncoding() {
        val snapshot = MissionCustodySnapshot.newBuilder().addAllEventIds((1..128).map { UUID.randomUUID().toString() }.sorted()).build().toByteArray()
        val signer = RsaPssDeliverySigner.generate("test-key")
        val codec = DeliveryOfferCodec()
        val code = codec.createCode(DeliveryOfferDraft("delivery", "mission", "sender", "recipient", sha256(snapshot), ByteArray(16), 100,
            ByteArray(32), false, snapshot), signer)
        assertArrayEquals(snapshot, codec.decodeCode(code).offer.missionSnapshot.toByteArray())
        assertNull(boundedQrMatrix(code))
        assertNotNull(boundedQrMatrix("DIGITALDELTA:small-signed-code"))
    }
}
