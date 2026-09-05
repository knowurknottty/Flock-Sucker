package com.flockyou.evidence

import kotlinx.coroutines.CancellationException

/** Preserve structured-concurrency cancellation across broad error boundaries. */
fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}
