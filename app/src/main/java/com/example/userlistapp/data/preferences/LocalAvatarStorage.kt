package com.example.userlistapp.data.preferences

import java.io.File
import java.io.IOException
import java.io.InputStream

class LocalAvatarStorage(
    private val directory: File,
    private val openInputStream: (String) -> InputStream?,
) {
    fun import(sourceUri: String): String {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the avatar directory")
        }
        val target = File.createTempFile("avatar-", ".image", directory)
        try {
            val input = openInputStream(sourceUri)
                ?: throw IOException("Could not open the selected image")
            input.use { source ->
                target.outputStream().use(source::copyTo)
            }
            return target.toURI().toString()
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun delete(value: String) {
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return
        if (uri.scheme != "file") return
        val file = runCatching { File(uri).canonicalFile }.getOrNull() ?: return
        if (file.parentFile == directory.canonicalFile) file.delete()
    }

    companion object {
        const val DIRECTORY_NAME = "account_avatars"
    }
}
