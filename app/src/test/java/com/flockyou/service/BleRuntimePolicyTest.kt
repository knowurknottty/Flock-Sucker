package com.flockyou.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRuntimePolicyTest {

    @Test
    fun conservativePlan_doesNotInventExtendedControllerCapabilities() {
        val plan = ScanningRuntimePolicy.planBleScan(
            aggressive = false,
            controller = BleControllerCapabilities()
        )

        assertFalse(plan.aggressiveMatching)
        assertFalse(plan.maxAdvertisementMatches)
        assertEquals(500L, plan.reportDelayMs)
        assertFalse(plan.requestExtendedAdvertisements)
        assertEquals(BlePhyRequest.LE_1M, plan.phyRequest)
    }

    @Test
    fun boostPlan_requestsMaximumAperture_onlyWhenControllerEvidenceSupportsIt() {
        val plan = ScanningRuntimePolicy.planBleScan(
            aggressive = true,
            controller = BleControllerCapabilities(
                extendedAdvertising = true,
                le2mPhy = true,
                codedPhy = false
            )
        )

        assertTrue(plan.aggressiveMatching)
        assertTrue(plan.maxAdvertisementMatches)
        assertEquals(0L, plan.reportDelayMs)
        assertTrue(plan.requestExtendedAdvertisements)
        assertEquals(BlePhyRequest.ALL_SUPPORTED, plan.phyRequest)
    }

    @Test
    fun controllerPhyFlags_doNotEscapeLegacyModeWithoutExtendedAdvertising() {
        val plan = ScanningRuntimePolicy.planBleScan(
            aggressive = true,
            controller = BleControllerCapabilities(
                extendedAdvertising = false,
                le2mPhy = true,
                codedPhy = true
            )
        )

        assertFalse(plan.requestExtendedAdvertisements)
        assertEquals(BlePhyRequest.LE_1M, plan.phyRequest)
    }

    @Test
    fun aggressivePlan_fallsBackTo1mWhenControllerHasNoExtendedPhyEvidence() {
        val plan = ScanningRuntimePolicy.planBleScan(
            aggressive = true,
            controller = BleControllerCapabilities(extendedAdvertising = true)
        )

        assertTrue(plan.requestExtendedAdvertisements)
        assertEquals(BlePhyRequest.LE_1M, plan.phyRequest)
    }
}
