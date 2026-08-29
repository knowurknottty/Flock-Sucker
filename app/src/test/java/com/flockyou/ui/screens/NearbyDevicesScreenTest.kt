package com.flockyou.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyDevicesScreenTest {
    @Test
    fun `nearby tab count stays exact through 99`() {
        assertEquals("0", compactNearbyCount(0))
        assertEquals("1", compactNearbyCount(1))
        assertEquals("99", compactNearbyCount(99))
    }

    @Test
    fun `nearby tab count caps triple digits`() {
        assertEquals("99+", compactNearbyCount(100))
        assertEquals("99+", compactNearbyCount(1247))
    }

    @Test
    fun `nearby tab count is defensive for impossible negatives`() {
        assertEquals("0", compactNearbyCount(-1))
    }
}
