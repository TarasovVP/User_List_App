package com.example.userlistapp.data.local

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream

class LocalAvatarStorage(
    private val directory: File,
    private val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
    private val openInputStream: (String) -> InputStream?,
) {
    init {
        require(maxFileSizeBytes > 0)
    }

    fun import(sourceUri: String): String {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(CREATE_DIRECTORY_ERROR)
        }
        val target = File.createTempFile(AVATAR_FILE_PREFIX, AVATAR_FILE_SUFFIX, directory)
        try {
            val input = openInputStream(sourceUri)
                ?: throw IOException(OPEN_IMAGE_ERROR)
            input.use { source ->
                target.outputStream().use { destination ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES)
                    var copiedBytes = 0L
                    while (true) {
                        val readBytes = source.read(buffer)
                        if (readBytes < 0) break
                        if (copiedBytes + readBytes > maxFileSizeBytes) {
                            throw IOException(IMAGE_TOO_LARGE_ERROR)
                        }
                        destination.write(buffer, 0, readBytes)
                        copiedBytes += readBytes
                    }
                }
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
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
        private const val TAG = "LocalAvatarStorage"
        private const val COPY_BUFFER_SIZE_BYTES = 8 * 1024
        private const val AVATAR_FILE_PREFIX = "avatar-"
        private const val AVATAR_FILE_SUFFIX = ".image"
        private const val FILE_URI_SCHEME = "file"
        private const val CREATE_DIRECTORY_ERROR = "Could not create the avatar directory"
        private const val OPEN_IMAGE_ERROR = "Could not open the selected image"
        private const val IMAGE_TOO_LARGE_ERROR = "Selected image exceeds the size limit"
        private const val DELETE_AVATAR_ERROR_PREFIX = "Could not delete local avatar: "
    }
}
