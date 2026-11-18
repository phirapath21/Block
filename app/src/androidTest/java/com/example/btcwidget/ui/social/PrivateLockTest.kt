package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.btcwidget.AppPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** "Only private things": the app opens freely; a private thing asks for the PIN once per session. */
class PrivateLockTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var opened = 0

    @Before
    fun privateThingsOnly() {
        val context = composeTestRule.activity
        AppPrefs.clearAppPin(context)
        AppPin.save(context, "1234")
        AppPrefs.setAppLockScope(context, LockScope.SENSITIVE.id)
        AppPrefs.setAppLockAfterMs(context, LockAfter.NOW.ms)
        composeTestRule.runOnIdle { AppLockState.unlockedThisSession = false; AppLockState.request = null }
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Text("Secret key", Modifier.clickable { AppLock.guard("Show your private key") { opened++ } })
                AppLockGate(activity = null)
            }
        }
    }

    @After
    fun clear() {
        val context = composeTestRule.activity
        AppPrefs.clearAppPin(context)
        AppPrefs.setAppLockScope(context, LockScope.APP.id)
        composeTestRule.runOnIdle { AppLockState.unlockedThisSession = false; AppLockState.request = null; AppLockState.load(context) }
    }

    @Test
    fun theAppOpensButAPrivateThingAsksOnce() {
        // Not locked at start: the app underneath is usable.
        composeTestRule.runOnIdle { assertEquals(false, AppLockState.locked) }
        composeTestRule.onNodeWithText("Secret key").performClick()
        composeTestRule.onNodeWithText("Show your private key").assertExists()
        listOf("1", "2", "3", "4").forEach { composeTestRule.onNodeWithText(it).performClick() }
        composeTestRule.waitUntil(10_000) { opened == 1 }
        // Unlocked for the session: the next one opens at once.
        composeTestRule.onNodeWithText("Secret key").performClick()
        composeTestRule.runOnIdle { assertEquals(2, opened) }
    }

    @Test
    fun cancellingDoesNotOpenIt() {
        composeTestRule.onNodeWithText("Secret key").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.runOnIdle {
            assertEquals(0, opened)
            assertEquals(false, AppLockState.locked)
        }
    }
}
