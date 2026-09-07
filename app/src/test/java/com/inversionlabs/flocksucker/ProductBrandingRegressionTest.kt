package com.inversionlabs.flocksucker

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
            File("src/main/java/com/inversionlabs/flocksucker/data/export/DetectionExportSerializer.kt"),
            File("src/main/java/com/inversionlabs/flocksucker/ui/screens/MainScreen.kt")
        )

        visibleSources.forEach { file ->
            val text = file.readText()
            assertFalse(
                "Legacy visible product branding remains in ${file.path}",
                legacyVisibleBrand.containsMatchIn(text)
            )
        }

        val buildGradle = File("build.gradle.kts").readText()
        assertTrue(buildGradle.contains("namespace = \"com.inversionlabs.flocksucker\""))
        assertTrue(buildGradle.contains("applicationId = \"com.inversionlabs.flocksucker\""))
        assertFalse(buildGradle.contains("com.flocksucker"))

        val strings = visibleSources.first().readText()
        assertTrue(strings.contains("Flock-Sucker"))
        assertFalse(strings.contains("app_title_flock"))
        assertFalse(strings.contains("app_title_you"))

        val mainScreen = visibleSources.last().readText()
        assertTrue(mainScreen.contains("R.string.app_title_full"))
        assertFalse(mainScreen.contains("R.string.app_title_flock"))
        assertFalse(mainScreen.contains("R.string.app_title_you"))
    }
}
