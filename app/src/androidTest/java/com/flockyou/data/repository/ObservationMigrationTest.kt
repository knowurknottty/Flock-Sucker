package com.flockyou.data.repository

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FlockYouDatabase::class.java
    )

    @Test
    fun `migration 11 to 12 creates empty observation ledger and preserves prior tables`() {
        val databaseName = "observation-migration-test"
        helper.createDatabase(databaseName, 11).use { oldDb ->
            oldDb.execSQL("INSERT INTO sightings (id, detectionId, timestamp, sequence, protocol, sourceScanner, detectorHealthGeneration, disposition) VALUES ('s1', 'd1', 1000, 1, 'BLUETOOTH_LE', 'BLE', 0, 'new_device')")
        }

        helper.runMigrationsAndValidate(
            databaseName,
            12,
            true,
            FlockYouDatabase.MIGRATION_11_12
        ).use { migratedDb ->
            migratedDb.query("SELECT COUNT(*) FROM observations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migratedDb.query("SELECT COUNT(*) FROM sightings WHERE id = 's1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }
}