package com.flockyou

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductBrandingRegressionTest {
    private val legacyVisibleBrand = Regex("flock[ -]you", RegexOption.IGNORE_CASE)

    @Test
    fun `visible and exported branding is permanently Flock-Sucker`() {
        val visibleSources = listOf(
            File("src/main/res/values/strings.xml"),
            File("src/main/java/com/flockyou/data/export/DetectionExportSerializer.kt")
        )

        visibleSources.forEach { file ->
            val text = file.readText()
            assertFalse(
                "Legacy visible product branding remains in ${file.path}",
                legacyVisibleBrand.containsMatchIn(text)
            )
        }

        val strings = visibleSources.first().readText()
        assertTrue(strings.contains("Flock-Sucker"))
    }
}
