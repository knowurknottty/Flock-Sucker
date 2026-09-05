package com.flockyou.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityLinkTest {
    @Test
    fun `identity link preserves resolver proof without rewriting source evidence`() {
        val link = IdentityLink(
            id = "link-1",
            sourceDetectionId = "incoming-1",
            candidateDetectionId = "existing-1",
            sourceObservationId = "observation-1",
            timestamp = 1_788_599_000_000L,
            decision = IdentityLinkDecision.POSSIBLY_RELATED,
            ruleId = "WEAK_SIMILARITY_ONLY",
            score = 0.35f,
            evidenceJson = "[\"manufacturer=Vendor\"]",
            rejectedAlternativesJson = "[\"weak similarity is not canonical identity evidence\"]",
            resolverVersion = 1
        )

        assertEquals(IdentityLinkDecision.POSSIBLY_RELATED, link.decision)
        assertEquals("observation-1", link.sourceObservationId)
        assertTrue(link.rejectedAlternativesJson!!.contains("not canonical"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `identity link rejects invalid scores`() {
        IdentityLink(
            id = "link-2",
            sourceDetectionId = "incoming-2",
            candidateDetectionId = "existing-2",
            timestamp = 1L,
            decision = IdentityLinkDecision.MATCH,
            ruleId = "EXACT_STABLE_ADDRESS",
            score = 1.1f,
            resolverVersion = 1
        )
    }
}
