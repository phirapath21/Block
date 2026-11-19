package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Proof of work on screen: the badge on notes and the mining card's way out. */
class PowUiTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun badgeShowsOnlyRealWork() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Row {
                PowBadge(4)
                PowBadge(21)
            }
        }
        composeTestRule.onAllNodesWithContentDescription("Proof of work: 4 bits").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Proof of work: 21 bits").assertExists()
    }

    @Test
    fun miningCardSkipsOrCancels() {
        var skipped = 0
        var cancelled = 0
        composeTestRule.setContent {
            MiningPanel(
                SocialViewModel.PublishActivity.Mining(difficulty = 20, hashes = 500_000, hashesPerSecond = 250_000.0, elapsedMs = 2_000),
                onSkip = { skipped++ },
                onCancel = { cancelled++ }
            )
        }
        composeTestRule.onNodeWithText("20 bits", substring = true).assertExists()
        composeTestRule.onNodeWithText("Post without it").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, skipped)
            assertEquals(1, cancelled)
        }
    }

    @Test
    fun postSheetPicksALevelForThisPost() {
        var picked = -1
        var dismissed = 0
        composeTestRule.setContent {
            PowPostSheet(selected = 0, default = 0, rate = 0.0, onPick = { picked = it }, onDismiss = { dismissed++ })
        }
        composeTestRule.onNodeWithText("Proof of work for this post").assertExists()
        composeTestRule.onNodeWithText("16 bits").performClick()
        composeTestRule.runOnIdle {
            assertEquals(16, picked)
            assertEquals(1, dismissed)
        }
    }
}
