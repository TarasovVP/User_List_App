package com.example.userlistapp

import com.example.userlistapp.data.local.LocalAvatarStorage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.io.path.createTempDirectory

class LocalAvatarStorageTest {
    @Test
    fun `import stores a file within the configured size limit`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val directory = createTempDirectory("avatar-storage-").toFile()
        val storage = LocalAvatarStorage(
            directory = directory,
            openInputStream = { ByteArrayInputStream(bytes) },
            maxFileSizeBytes = bytes.size.toLong(),
        )

        val importedUri = storage.import("content://avatar")

        assertArrayEquals(bytes, java.io.File(java.net.URI(importedUri)).readBytes())
    }

    @Test
    fun `oversized import fails and removes the partial file`() {
        val directory = createTempDirectory("avatar-storage-").toFile()
        val storage = LocalAvatarStorage(
            directory = directory,
            openInputStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)) },
            maxFileSizeBytes = 4,
        )

        assertThrows(IOException::class.java) {
            storage.import("content://oversized-avatar")
        }
        assertEquals(emptyList<java.io.File>(), directory.listFiles().orEmpty().toList())
    }
}
