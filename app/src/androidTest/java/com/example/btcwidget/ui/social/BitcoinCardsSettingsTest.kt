package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.btcwidget.AppPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings › Display › Bitcoin in posts: the one switch applies and sticks. */
class BitcoinCardsSettingsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun restore() {
        val c = composeTestRule.activity
        composeTestRule.runOnIdle { BitcoinCards.setShow(c, true) }
    }

    @Test
    fun switchingOffHidesCardsAndSticks() {
        composeTestRule.runOnIdle { BitcoinCards.setShow(composeTestRule.activity, true) }
        composeTestRule.setContent { Column { BitcoinCardsSettings() } }
        composeTestRule.onNodeWithText("Bitcoin in posts").performClick()
        composeTestRule.runOnIdle {
            assertFalse(BitcoinCards.show)
            assertFalse(AppPrefs.btcCards(composeTestRule.activity))
        }
    }

    @Test
    fun switchingBackOnSticks() {
        composeTestRule.runOnIdle { BitcoinCards.setShow(composeTestRule.activity, false) }
        composeTestRule.setContent { Column { BitcoinCardsSettings() } }
        composeTestRule.onNodeWithText("Bitcoin in posts").performClick()
        composeTestRule.runOnIdle {
            assertTrue(BitcoinCards.show)
            assertTrue(AppPrefs.btcCards(composeTestRule.activity))
        }
    }
}
