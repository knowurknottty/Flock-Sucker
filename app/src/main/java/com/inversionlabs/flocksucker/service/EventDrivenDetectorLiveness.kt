package com.inversionlabs.flocksucker.service

data class EventDrivenDetectorLiveness(
    val monitoring: Boolean,
    val listenerRegistered: Boolean
) {
    val isOperational: Boolean
        get() = monitoring && listenerRegistered
}
