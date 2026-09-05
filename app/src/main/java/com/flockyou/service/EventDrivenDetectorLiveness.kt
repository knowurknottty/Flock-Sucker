package com.flockyou.service

data class EventDrivenDetectorLiveness(
    val monitoring: Boolean,
    val listenerRegistered: Boolean
) {
    val isOperational: Boolean
        get() = monitoring && listenerRegistered
}
