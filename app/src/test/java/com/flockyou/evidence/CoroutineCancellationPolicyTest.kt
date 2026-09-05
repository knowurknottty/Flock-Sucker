package com.flockyou.evidence

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Test

class CoroutineCancellationPolicyTest {
    @Test
    fun `cancellation is rethrown unchanged`() {
        val cancellation = CancellationException("scanner stopped")
        var observed: CancellationException? = null
        try {
            cancellation.rethrowCancellation()
        } catch (error: CancellationException) {
            observed = error
        }
        assertSame(cancellation, observed)
    }

    @Test
    fun `ordinary failures are not rethrown by cancellation policy`() {
        IllegalStateException("radio error").rethrowCancellation()
    }
}
