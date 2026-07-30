package com.example.userlistapp.data.local

import com.google.crypto.tink.Aead
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

interface NoteCipher {
    fun encrypt(userId: Int, plaintext: String): String
    fun decrypt(userId: Int, payload: String): DecryptedNote
}

data class DecryptedNote(
    val plaintext: String,
    val requiresMigration: Boolean,
)

class TinkNoteCipher(private val aead: Aead) : NoteCipher {
    override fun encrypt(userId: Int, plaintext: String): String {
        val ciphertext = aead.encrypt(plaintext.toByteArray(UTF_8), associatedData(userId))
        return FORMAT_PREFIX + Base64.getEncoder().encodeToString(ciphertext)
    }

    override fun decrypt(userId: Int, payload: String): DecryptedNote {
        if (!payload.startsWith(FORMAT_PREFIX)) {
            return DecryptedNote(payload, requiresMigration = true)
        }
        val ciphertext = Base64.getDecoder().decode(payload.removePrefix(FORMAT_PREFIX))
        val plaintext = aead.decrypt(ciphertext, associatedData(userId)).toString(UTF_8)
        return DecryptedNote(plaintext, requiresMigration = false)
    }

    private fun associatedData(userId: Int) =
        "$ASSOCIATED_DATA_PREFIX$userId:$FORMAT_VERSION".toByteArray(UTF_8)

    companion object {
        const val FORMAT_VERSION = 1
        const val FORMAT_PREFIX = "enc:v$FORMAT_VERSION:"
        private const val ASSOCIATED_DATA_PREFIX = "user-note:"
    }
}
