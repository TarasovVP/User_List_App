package com.example.userlistapp.data.local

import java.io.File
import android.util.Log
import java.io.IOException
import java.io.InputStream

class LocalAvatarStorage(
    private val directory: File,
    private val openInputStream: (String) -> InputStream?,
) {
    fun import(sourceUri: String): String {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(CREATE_DIRECTORY_ERROR)
        }
        val target = File.createTempFile(AVATAR_FILE_PREFIX, AVATAR_FILE_SUFFIX, directory)
        try {
            val input = openInputStream(sourceUri)
                ?: throw IOException(OPEN_IMAGE_ERROR)
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
        if (uri.scheme != FILE_URI_SCHEME) return
        val file = runCatching { File(uri).canonicalFile }.getOrNull() ?: return
        if (file.parentFile == directory.canonicalFile && file.exists() && !file.delete()) {
            Log.w(TAG, DELETE_AVATAR_ERROR_PREFIX + file.name)
        }
    }

    companion object {
        const val DIRECTORY_NAME = "account_avatars"
        private const val TAG = "LocalAvatarStorage"
        private const val AVATAR_FILE_PREFIX = "avatar-"
        private const val AVATAR_FILE_SUFFIX = ".image"
        private const val FILE_URI_SCHEME = "file"
        private const val CREATE_DIRECTORY_ERROR = "Could not create the avatar directory"
        private const val OPEN_IMAGE_ERROR = "Could not open the selected image"
        private const val DELETE_AVATAR_ERROR_PREFIX = "Could not delete local avatar: "
    }
}
