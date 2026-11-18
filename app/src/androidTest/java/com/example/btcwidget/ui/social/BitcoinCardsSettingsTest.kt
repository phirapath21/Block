package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.btcwidget.AppPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Settings › Display › Bitcoin in posts: the switches and the per-place style apply and stick. */
class BitcoinCardsSettingsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun restore() {
        val c = composeTestRule.activity
        composeTestRule.runOnIdle {
            BitcoinCards.setShowTx(c, true)
            BitcoinCards.setShowBlocks(c, true)
            BitcoinCards.setFeedStyle(c, TxCardStyle.ROW)
            BitcoinCards.setPostStyle(c, TxCardStyle.ROW)
        }
    }

    @Test
    fun choosingFullCardForTimelinesSticks() {
        composeTestRule.setContent { Column { BitcoinCardsSettings() } }
        // The first "Full card" belongs to "In timelines".
        composeTestRule.onAllNodesWithText("Full card")[0].performClick()
        composeTestRule.runOnIdle {
            assertEquals(TxCardStyle.FULL, BitcoinCards.feedStyle)
            assertEquals(TxCardStyle.ROW, BitcoinCards.postStyle)
            assertEquals("full", AppPrefs.btcTxStyleFeed(composeTestRule.activity))
        }
    }

    @Test
    fun blockCardsTurnOff() {
        composeTestRule.setContent { Column { BitcoinCardsSettings() } }
        composeTestRule.onNodeWithText("Block cards").performClick()
        composeTestRule.runOnIdle {
            assertFalse(BitcoinCards.showBlocks)
            assertFalse(AppPrefs.btcBlockCards(composeTestRule.activity))
        }
    }
}
