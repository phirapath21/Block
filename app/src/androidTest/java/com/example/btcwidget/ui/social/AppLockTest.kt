package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.btcwidget.AppPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The lock screen: its keypad works, and nothing under it can be tapped. */
class AppLockTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var tappedUnderneath = 0

    @Before
    fun setPin() {
        val context = composeTestRule.activity
        AppPrefs.clearAppPin(context)
        AppPin.save(context, "1234")
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize()) {
                // The app under the lock: a full-screen button that counts taps.
                Box(Modifier.fillMaxSize().clickable { tappedUnderneath++ }) { Text("Secret") }
                AppLockGate(activity = null)
            }
        }
    }

    @After
    fun clearPin() {
        AppPrefs.clearAppPin(composeTestRule.activity)
    }

    @Test
    fun theRightPinUnlocks() {
        listOf("1", "2", "3", "4").forEach { composeTestRule.onNodeWithText(it).performClick() }
        // The PIN is checked off the main thread (PBKDF2), so wait for it.
        composeTestRule.waitUntil(timeoutMillis = 10_000) { !AppLockState.locked }
        assertFalse(AppLockState.locked)
    }

    @Test
    fun tapsDontReachTheAppUnderneath() {
        // A corner of the lock screen, away from its keypad.
        composeTestRule.onRoot().performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(10f, 10f)) }
        composeTestRule.onRoot().performTouchInput { click(bottomRight - androidx.compose.ui.geometry.Offset(10f, 10f)) }
        composeTestRule.runOnIdle { assertEquals(0, tappedUnderneath) }
    }
}
