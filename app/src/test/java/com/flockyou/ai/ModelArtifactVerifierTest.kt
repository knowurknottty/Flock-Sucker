package com.flockyou.ai

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArtifactVerifierTest {
    @Test
    fun `accepts exact size and sha256`() {
        val file = tempFile("known model bytes")
        val hash = sha256(file)

        val result = ModelArtifactVerifier.verify(file, file.length(), hash)

        assertEquals(ModelArtifactVerification.Verified, result)
    }

    @Test
    fun `rejects size mismatch before promotion`() {
        val file = tempFile("known model bytes")
        val result = ModelArtifactVerifier.verify(file, file.length() + 1, sha256(file))
        assertTrue(result is ModelArtifactVerification.Rejected)
    }

    @Test
    fun `rejects sha256 mismatch before promotion`() {
        val file = tempFile("known model bytes")
        val result = ModelArtifactVerifier.verify(file, file.length(), "0".repeat(64))
        assertTrue(result is ModelArtifactVerification.Rejected)
    }

    private fun tempFile(contents: String): File =
        kotlin.io.path.createTempFile("model-artifact", ".bin").toFile().apply { writeText(contents) }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
