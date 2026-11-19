package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

/** The unsaved-key dot on Settings' "Your account". */
class KeyBackupUiTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsShowsADotOnYourAccountUntilTheKeyIsSaved() {
        composeTestRule.setContent { SettingsHome(accountName = "Alice", onOpen = {}, needsAttention = setOf(SettingsPage.ACCOUNT)) }
        composeTestRule.onNodeWithContentDescription("Needs your attention", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noDotOnceSaved() {
        composeTestRule.setContent { SettingsHome(accountName = "Alice", onOpen = {}) }
        composeTestRule.onNodeWithContentDescription("Needs your attention", useUnmergedTree = true).assertDoesNotExist()
    }
}
