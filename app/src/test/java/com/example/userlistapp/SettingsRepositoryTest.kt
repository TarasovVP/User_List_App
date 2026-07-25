package com.example.userlistapp

import com.example.userlistapp.data.preferences.SettingsRepositoryImpl
import com.example.userlistapp.data.preferences.createPreferencesDataStore
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SettingsRepositoryTest {
    private val dataStoreFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        dataStoreFiles.forEach(File::delete)
        dataStoreFiles.clear()
    }

    @Test
    fun `a missing preferences file exposes the documented defaults`() = runTest {
        assertEquals(AppSettings(), repository().settings.first())
    }

    @Test
    fun `a corrupted preferences file falls back to defaults and stays writable`() = runTest {
        val file = newDataStoreFile()
        file.writeText("not a preferences protobuf")
        val repository = repository(file)

        assertEquals(AppSettings(), repository.settings.first())

        repository.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.settings.first().themeMode)
    }

    private fun TestScope.repository(file: File = newDataStoreFile()) =
        SettingsRepositoryImpl(createPreferencesDataStore(scope = backgroundScope) { file })

    private fun newDataStoreFile(): File {
        val file = File.createTempFile("settings-", ".preferences_pb")
        file.delete()
        dataStoreFiles += file
        return file
    }
}
