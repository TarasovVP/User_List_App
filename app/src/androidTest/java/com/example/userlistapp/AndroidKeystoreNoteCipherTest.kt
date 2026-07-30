package com.example.userlistapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.userlistapp.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreNoteCipherTest {
    @Test
    fun androidKeystoreMasterKeyProtectsUsableTinkKeyset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cipher = AppModule.noteCipher(context)

        val payload = cipher.encrypt(USER_ID, PLAINTEXT)

        assertFalse(payload.contains(PLAINTEXT))
        assertEquals(PLAINTEXT, cipher.decrypt(USER_ID, payload).plaintext)
    }

    private companion object {
        const val USER_ID = 7
        const val PLAINTEXT = "keystore integration note"
    }
}
