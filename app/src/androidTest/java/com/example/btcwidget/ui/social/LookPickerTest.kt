package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.btcwidget.AppPrefs
import com.example.btcwidget.theme.AppLook
import com.example.btcwidget.ui.main.EInk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Settings › Display › Theme: picking a look applies it and remembers it. */
class LookPickerTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun backToBlock() {
        AppPrefs.setAppLook(composeTestRule.activity, AppLook.BLOCK)
        composeTestRule.runOnIdle { EInk.look = AppLook.BLOCK; EInk.lookProgress = 1f }
    }

    @Test
    fun tappingEmberAppliesAndSavesIt() {
        composeTestRule.setContent { com.example.btcwidget.theme.BTCWidgetTheme { LookPicker() } }
        composeTestRule.onNodeWithContentDescription("Ember", substring = true).performClick()
        composeTestRule.onNodeWithContentDescription("Ember", substring = true).assertIsSelected()
        composeTestRule.runOnIdle {
            assertEquals(AppLook.EMBER, EInk.look)
            assertEquals(AppLook.EMBER, AppPrefs.appLook(composeTestRule.activity))
        }
        // The fade finishes on the Ember colours.
        composeTestRule.waitUntil(5_000) { EInk.lookProgress >= 1f }
        composeTestRule.runOnIdle { assertEquals(AppLook.EMBER.colors.accent, EInk.current.accent) }
    }
}
