package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Tip sheet: drawn amounts, and the gold button sends what's picked. */
class TipSheetTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pickingAnAmountTipsIt() {
        var sent: Long? = null
        composeTestRule.setContent { ZapDialog(recipientName = "Mali", inApp = true, onDismiss = {}, onZap = { sats, _ -> sent = sats }) }
        composeTestRule.onNodeWithText("Tip Mali").assertExists()
        // 100 is picked to start with; pick the coffee (21) instead.
        composeTestRule.onNodeWithText("Tip 100 sats").assertExists()
        composeTestRule.onNodeWithContentDescription("21 sats").performClick()
        composeTestRule.onNodeWithText("Tip 21 sats").performClick()
        composeTestRule.runOnIdle { assertEquals(21L, sent) }
    }
}
