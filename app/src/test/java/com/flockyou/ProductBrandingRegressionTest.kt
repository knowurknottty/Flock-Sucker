package com.flockyou

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductBrandingRegressionTest {
    @Test
    fun visibleResourceBrandingUsesFlockSucker() {
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("Flock-Sucker"))
        assertFalse("Legacy product branding must not return to visible resources", strings.contains("Flock You"))
    }
}
