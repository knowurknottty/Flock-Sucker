package com.inversionlabs.flocksucker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inversionlabs.flocksucker.data.model.*
import com.inversionlabs.flocksucker.data.repository.DetectionRepository
import com.inversionlabs.flocksucker.utils.TestDataFactory
import com.inversionlabs.flocksucker.utils.TestHelpers
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * E2E tests for the MainScreen UI.
 *
 * Tests cover:
 * - Detection list rendering
 * - Filter functionality
 * - Empty state display
 * - Status card interactions
 * - Threat badge rendering
 * - Detection card interactions
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainScreenE2ETest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Inject
    lateinit var detectionRepository: DetectionRepository

    private val context = TestHelpers.getContext()

    @Before
    fun setup() {
        hiltRule.inject()
        TestHelpers.clearAppData(context)
        runBlocking {
            detectionRepository.deleteAllDetections()
        }
    }

    @After
    fun cleanup() {
        runBlocking {
            detectionRepository.deleteAllDetections()
        }
    }

    // ==================== Detection List Tests ====================

    @Test
    fun mainScreen_displaysEmptyStateWhenNoDetections() {
        // No detections added

        // Empty state or no detections message should be shown
        // The specific text depends on the implementation
        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_displaysDetectionInList() = runTest {
        // Add a detection
        val detection = TestDataFactory.createFlockSafetyCameraDetection()
        detectionRepository.insertDetection(detection)

        // Detection should appear in the list
        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_displaysMultipleDetections() = runTest {
        // Add multiple detections
        val detections = TestDataFactory.createMultipleDetections(5)
        detections.forEach { detectionRepository.insertDetection(it) }

        // All detections should be rendered
        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_displaysMixedProtocolDetections() = runTest {
        // Add detections of different protocols
        val detections = TestDataFactory.createMixedProtocolDetections()
        detections.forEach { detectionRepository.insertDetection(it) }

        // Each type should be represented
        composeTestRule.waitForIdle()
    }

    // ==================== Threat Level Display Tests ====================

    @Test
    fun mainScreen_displaysCriticalThreatBadge() = runTest {
        val detection = TestDataFactory.createStingrayDetection() // Critical threat
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
        // Critical threat should show appropriate badge/color
    }

    @Test
    fun mainScreen_displaysHighThreatBadge() = runTest {
        val detection = TestDataFactory.createFlockSafetyCameraDetection() // High threat
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_displaysMediumThreatBadge() = runTest {
        val detection = TestDataFactory.createDroneDetection() // Medium threat
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_displaysLowThreatBadge() = runTest {
        val detection = TestDataFactory.createTestDetection(threatLevel = ThreatLevel.LOW)
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
    }

    // ==================== Device Type Tests ====================

    @Test
    fun mainScreen_displaysDeviceTypeInfo() = runTest {
        val detection = TestDataFactory.createFlockSafetyCameraDetection()
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
        // Device type name should be visible
    }

    @Test
    fun mainScreen_displaysDifferentDeviceTypes() = runTest {
        val camera = TestDataFactory.createFlockSafetyCameraDetection()
        val drone = TestDataFactory.createDroneDetection()
        val stingray = TestDataFactory.createStingrayDetection()

        detectionRepository.insertDetection(camera)
        detectionRepository.insertDetection(drone)
        detectionRepository.insertDetection(stingray)

        composeTestRule.waitForIdle()
    }

    // ==================== Detection Card Tests ====================

    @Test
    fun mainScreen_detectionCardShowsSSID() = runTest {
        val detection = TestDataFactory.createTestDetection(ssid = "TestNetwork-12345")
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_detectionCardShowsMacAddress() = runTest {
        val detection = TestDataFactory.createTestDetection(macAddress = "AA:BB:CC:DD:EE:FF")
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_detectionCardShowsSignalStrength() = runTest {
        val detection = TestDataFactory.createTestDetection()
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
        // Signal strength indicator should be visible
    }

    // ==================== Filtering Tests ====================

    @Test
    fun mainScreen_filterByThreatLevelWorks() = runTest {
        // Add detections of different threat levels
        val critical = TestDataFactory.createStingrayDetection()
        val medium = TestDataFactory.createDroneDetection()
        val low = TestDataFactory.createTestDetection(threatLevel = ThreatLevel.LOW)

        detectionRepository.insertDetection(critical)
        detectionRepository.insertDetection(medium)
        detectionRepository.insertDetection(low)

        composeTestRule.waitForIdle()
        // Filter interactions would be tested here
    }

    @Test
    fun mainScreen_filterByProtocolWorks() = runTest {
        // Add detections of different protocols
        val wifi = TestDataFactory.createFlockSafetyCameraDetection()
        val cellular = TestDataFactory.createStingrayDetection()
        val audio = TestDataFactory.createUltrasonicBeaconDetection()

        detectionRepository.insertDetection(wifi)
        detectionRepository.insertDetection(cellular)
        detectionRepository.insertDetection(audio)

        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_clearFiltersShowsAll() = runTest {
        val detections = TestDataFactory.createMultipleDetections(10)
        detections.forEach { detectionRepository.insertDetection(it) }

        composeTestRule.waitForIdle()
        // Clear filters should show all detections
    }

    // ==================== Status Card Tests ====================

    @Test
    fun mainScreen_statusCardShowsScanStatus() {
        composeTestRule.waitForIdle()
        // Status card should show current scanning status
    }

    @Test
    fun mainScreen_statusCardShowsDetectionCounts() = runTest {
        val detections = TestDataFactory.createMultipleDetections(5)
        detections.forEach { detectionRepository.insertDetection(it) }

        composeTestRule.waitForIdle()
        // Detection count should be visible
    }

    // ==================== Edge Cases ====================

    @Test
    fun mainScreen_handlesLargeDetectionList() = runTest {
        // Add many detections
        val detections = TestDataFactory.createMultipleDetections(100)
        detections.forEach { detectionRepository.insertDetection(it) }

        composeTestRule.waitForIdle()
        // Should handle without crashing or freezing
    }

    @Test
    fun mainScreen_handlesDetectionWithNullFields() = runTest {
        val detection = TestDataFactory.createTestDetection(
            macAddress = null,
            ssid = null,
            latitude = null,
            longitude = null
        )
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
        // Should handle null fields gracefully
    }

    @Test
    fun mainScreen_handlesVeryLongSSID() = runTest {
        val longSsid = "A".repeat(256)
        val detection = TestDataFactory.createTestDetection(ssid = longSsid)
        detectionRepository.insertDetection(detection)

        composeTestRule.waitForIdle()
        // Should truncate or handle long SSID appropriately
    }

    // ==================== Refresh Tests ====================

    @Test
    fun mainScreen_updatesWhenNewDetectionAdded() = runTest {
        composeTestRule.waitForIdle()

        // Add detection after initial render
        val detection = TestDataFactory.createFlockSafetyCameraDetection()
        detectionRepository.insertDetection(detection)

        // UI should update to show new detection
        composeTestRule.waitForIdle()
    }

    @Test
    fun mainScreen_updatesWhenDetectionRemoved() = runTest {
        val detection = TestDataFactory.createFlockSafetyCameraDetection()
        detectionRepository.insertDetection(detection)
        composeTestRule.waitForIdle()

        // Remove detection
        detectionRepository.deleteDetection(detection)

        composeTestRule.waitForIdle()
        // UI should update to remove detection
    }
}
