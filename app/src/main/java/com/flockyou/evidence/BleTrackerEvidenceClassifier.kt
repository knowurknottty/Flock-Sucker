package com.flockyou.evidence

import com.flockyou.data.model.DeviceType

enum class BleEvidenceKind {
    NONE,
    APPLE_FIND_MY_NETWORK,
    APPLE_PROXIMITY_PAIRING,
    APPLE_VENDOR_ADVERTISEMENT,
    SAMSUNG_SMARTTAG_SERVICE,
    SAMSUNG_OFFLINE_FINDING,
    SAMSUNG_VENDOR_ADVERTISEMENT,
    TILE_SERVICE
}

data class BleTrackerEvidence(
    val kind: BleEvidenceKind,
    val displayName: String,
    val trackerEvidence: Boolean,
    val exactProductFamily: Boolean = false,
    val suggestedDeviceType: DeviceType? = null,
    val reasons: List<String> = emptyList()
)

object BleTrackerEvidenceClassifier {
    private const val APPLE_COMPANY_ID = 0x004C
    private const val SAMSUNG_COMPANY_ID = 0x0075
    private val APPLE_POPUP_FRAME_TYPES = setOf(
        "07", "0F", "10", "05", "0C", "0D", "0E", "13", "14",
        "01", "06", "09", "0A", "0B", "11"
    )

    fun isApplePopupEligible(manufacturerData: String): Boolean {
        if (manufacturerData.length < 2) return false
        return manufacturerData.take(2).uppercase() in APPLE_POPUP_FRAME_TYPES
    }

    fun classify(
        manufacturerData: Map<Int, String>,
        serviceUuids: Collection<String>
    ): BleTrackerEvidence {
        val normalizedServices = serviceUuids.map { normalizeService(it) }.toSet()

        if (normalizedServices.any { it.startsWith("7DFC9000") }) {
            return BleTrackerEvidence(
                kind = BleEvidenceKind.APPLE_FIND_MY_NETWORK,
                displayName = "Apple Find My network device",
                trackerEvidence = true,
                reasons = listOf("Advertises Apple Find My network service UUID 7DFC9000")
            )
        }

        if ("FD5A" in normalizedServices) {
            return BleTrackerEvidence(
                kind = BleEvidenceKind.SAMSUNG_SMARTTAG_SERVICE,
                displayName = "Samsung SmartTag protocol device",
                trackerEvidence = true,
                exactProductFamily = true,
                suggestedDeviceType = DeviceType.SAMSUNG_SMARTTAG,
                reasons = listOf("Advertises Samsung-assigned SmartTag service UUID FD5A")
            )
        }

        if ("FD69" in normalizedServices) {
            return BleTrackerEvidence(
                kind = BleEvidenceKind.SAMSUNG_OFFLINE_FINDING,
                displayName = "Samsung Offline Finding device",
                trackerEvidence = true,
                reasons = listOf("Advertises Samsung Offline Finding service UUID FD69")
            )
        }
        val apple = manufacturerData[APPLE_COMPANY_ID]?.uppercase().orEmpty()
        if (apple.startsWith("12")) {
            return BleTrackerEvidence(
                kind = BleEvidenceKind.APPLE_FIND_MY_NETWORK,
                displayName = "Apple Find My network device",
                trackerEvidence = true,
                reasons = listOf("Apple manufacturer frame type 0x12 is Find My/offline finding traffic")
            )
        }

        if (apple.startsWith("07")) {
            return BleTrackerEvidence(
                kind = BleEvidenceKind.APPLE_PROXIMITY_PAIRING,
                displayName = "Apple proximity-pairing accessory",
                trackerEvidence = false,
                reasons = listOf("Apple manufacturer frame type 0x07 is proximity-pairing traffic")
            )
        }

        if (apple.isNotEmpty()) {
            return BleTrackerEvidence(
                kind = BleEvidenceKind.APPLE_VENDOR_ADVERTISEMENT,
                displayName = "Apple BLE device",
                trackerEvidence = false,
                reasons = listOf("Apple company ID present without a tracker-specific frame")
            )
        }
        if (manufacturerData.containsKey(SAMSUNG_COMPANY_ID)) {
            return BleTrackerEvidence(
                kind = BleEvidenceKind.SAMSUNG_VENDOR_ADVERTISEMENT,
                displayName = "Samsung BLE device",
                trackerEvidence = false,
                reasons = listOf("Samsung company ID 0x0075 alone does not identify a SmartTag")
            )
        }

        if (normalizedServices.any { it.startsWith("FEED") }) {
            return BleTrackerEvidence(
                kind = BleEvidenceKind.TILE_SERVICE,
                displayName = "Tile tracker protocol device",
                trackerEvidence = true,
                exactProductFamily = true,
                suggestedDeviceType = DeviceType.TILE_TRACKER,
                reasons = listOf("Advertises Tile tracker service family FEED")
            )
        }

        return BleTrackerEvidence(
            kind = BleEvidenceKind.NONE,
            displayName = "Unclassified BLE device",
            trackerEvidence = false
        )
    }

    private fun normalizeService(uuid: String): String {
        val upper = uuid.uppercase()
        val compact = upper.replace("-", "")
        return when {
            compact.length == 4 -> compact
            compact.startsWith("0000") && compact.length >= 8 -> compact.substring(4, 8)
            else -> upper
        }
    }
}
