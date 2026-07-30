package com.example.userlistapp

import com.example.userlistapp.data.local.TinkNoteCipher
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64

class NoteCipherTest {
    private lateinit var cipher: TinkNoteCipher

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        cipher = TinkNoteCipher(aead)
    }

    @Test
    fun `encrypts and decrypts a versioned payload without plaintext`() {
        val payload = cipher.encrypt(USER_ID, PLAINTEXT)

        assertTrue(payload.startsWith(TinkNoteCipher.FORMAT_PREFIX))
        assertFalse(payload.contains(PLAINTEXT))
        assertEquals(PLAINTEXT, cipher.decrypt(USER_ID, payload).plaintext)
        assertFalse(cipher.decrypt(USER_ID, payload).requiresMigration)
    }

    @Test
    fun `ciphertext is bound to user id through associated data`() {
        val payload = cipher.encrypt(USER_ID, PLAINTEXT)

        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(USER_ID + 1, payload)
        }
    }

    @Test
    fun `tampering is detected`() {
        val payload = cipher.encrypt(USER_ID, PLAINTEXT)
        val encoded = payload.removePrefix(TinkNoteCipher.FORMAT_PREFIX)
        val bytes = Base64.getDecoder().decode(encoded)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val tampered = TinkNoteCipher.FORMAT_PREFIX + Base64.getEncoder().encodeToString(bytes)

        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(USER_ID, tampered)
        }
    }

    @Test
    fun `plaintext is identified for migration without modification`() {
        val decrypted = cipher.decrypt(USER_ID, PLAINTEXT)

        assertEquals(PLAINTEXT, decrypted.plaintext)
        assertTrue(decrypted.requiresMigration)
    }

    private companion object {
        const val USER_ID = 42
        const val PLAINTEXT = "private meeting notes"
    }
}
