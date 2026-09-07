package com.inversionlabs.flocksucker.data.repository

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlCipherOpenConfigTest {
    @Test
    fun `factory retains dedicated passphrase copy while source buffer is zeroed`() {
        val original = byteArrayOf(7, 11, 13, 17)
        val expected = original.copyOf()

        val factory = SqlCipherOpenConfig.createFactory(original)

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), original)
        val passwordField = SupportOpenHelperFactory::class.java.getDeclaredField("password").apply { isAccessible = true }
        val retained = passwordField.get(factory) as ByteArray
        assertNotSame(original, retained)
        assertArrayEquals(expected, retained)
        assertFalse(retained.all { it == 0.toByte() })
    }

    @Test
    fun `factory enables WAL and installs a database hook`() {
        val factory = SqlCipherOpenConfig.createFactory(byteArrayOf(1, 2, 3, 4))

        val walField = SupportOpenHelperFactory::class.java.getDeclaredField("enableWriteAheadLogging").apply { isAccessible = true }
        val hookField = SupportOpenHelperFactory::class.java.getDeclaredField("hook").apply { isAccessible = true }
        assertTrue(walField.getBoolean(factory))
        assertTrue(hookField.get(factory) != null)
    }
}
