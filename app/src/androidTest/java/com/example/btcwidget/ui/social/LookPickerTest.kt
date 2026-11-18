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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Settings › Display › Theme: picking a look applies it and remembers it. The fade between
 * looks is switched off here: the test checks the choice, not the animation, and doesn't make
 * the emulator redraw every screen for every frame of it.
 */
class LookPickerTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun noFade() {
        EInk.fadeLooks = false
        AppPrefs.setAppLook(composeTestRule.activity, AppLook.BLOCK)
        composeTestRule.runOnIdle { EInk.look = AppLook.BLOCK }
    }

    @After
    fun backToBlock() {
        AppPrefs.setAppLook(composeTestRule.activity, AppLook.BLOCK)
        composeTestRule.runOnIdle { EInk.look = AppLook.BLOCK; EInk.lookProgress = 1f }
        EInk.fadeLooks = true
    }

    @Test
    fun tappingEmberAppliesAndSavesIt() {
        composeTestRule.setContent { LookPicker() }
        composeTestRule.onNodeWithContentDescription("Ember", substring = true).performClick()
        composeTestRule.onNodeWithContentDescription("Ember", substring = true).assertIsSelected()
        composeTestRule.runOnIdle {
            assertEquals(AppLook.EMBER, EInk.look)
            assertEquals(AppLook.EMBER, AppPrefs.appLook(composeTestRule.activity))
            // Applied at once: the colours on screen are Ember's.
            assertEquals(1f, EInk.lookProgress, 0f)
            assertEquals(AppLook.EMBER.colors.accent, EInk.current.accent)
        }
    }
}
