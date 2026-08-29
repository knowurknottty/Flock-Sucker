package com.flockyou.ai

import java.io.File
import java.security.MessageDigest

sealed interface ModelArtifactVerification {
    data object Verified : ModelArtifactVerification
    data class Rejected(val reason: String) : ModelArtifactVerification
}

object ModelArtifactVerifier {
    fun verify(file: File, expectedSizeBytes: Long, expectedSha256: String): ModelArtifactVerification {
        if (!file.isFile) return ModelArtifactVerification.Rejected("artifact is not a regular file")
        if (file.length() != expectedSizeBytes) {
            return ModelArtifactVerification.Rejected(
                "size mismatch: expected $expectedSizeBytes bytes, found ${file.length()}"
            )
        }

        val expected = expectedSha256.lowercase()
        if (!expected.matches(Regex("[0-9a-f]{64}"))) {
            return ModelArtifactVerification.Rejected("expected SHA-256 is invalid")
        }
        val actual = sha256(file)
        return if (actual == expected) ModelArtifactVerification.Verified
        else ModelArtifactVerification.Rejected("SHA-256 mismatch: expected $expected, found $actual")
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
