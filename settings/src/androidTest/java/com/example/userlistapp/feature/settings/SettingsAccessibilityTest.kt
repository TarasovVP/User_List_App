package com.example.userlistapp.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.platform.app.InstrumentationRegistry
import com.example.userlistapp.R
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.ui.theme.UserListTheme
import org.junit.Rule
import org.junit.Test

class SettingsAccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsExposesHeadingsAndPassesAutomatedAccessibilityChecks() {
        compose.setContent {
            UserListTheme(ThemeMode.LIGHT) {
                SettingsScreen(
                    state = SettingsUiState(),
                    onBack = {},
                    onTheme = {},
                    onSync = {},
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.settings))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(context.getString(R.string.theme))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(context.getString(R.string.background_sync))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

        compose.enableAccessibilityChecks()
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
