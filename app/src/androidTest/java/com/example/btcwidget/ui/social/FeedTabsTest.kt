package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.btcwidget.nostr.core.HomeFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Home feeds as tabs: switching, and the buttons at the end. */
class FeedTabsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingATabSelectsThatFeed() {
        var picked: HomeFeed? = null
        var managed = false
        composeTestRule.setContent {
            FeedTabBar(
                feeds = HomeFeed.DEFAULTS + HomeFeed.Hashtag("siamstr"),
                current = HomeFeed.DEFAULTS.first(),
                onSelect = { picked = it }, onManage = { managed = true }
            )
        }
        // Collapsed by default: only the currently selected feed is shown.
        composeTestRule.onNodeWithText("Latest").assertExists()
        composeTestRule.onNodeWithText("With replies").assertDoesNotExist()

        // Tap the pill to slide out the feed menu.
        composeTestRule.onNodeWithText("Latest").performClick()
        composeTestRule.onNodeWithText("With replies").assertExists()
        composeTestRule.onNodeWithText("With replies").performClick()
        composeTestRule.runOnIdle { assertEquals(HomeFeed.Following(true), picked) }

        // Menu auto-collapses after selecting; tap the pill again to access Manage feeds.
        composeTestRule.onNodeWithText("Latest").performClick()
        composeTestRule.onNodeWithContentDescription("Manage feeds").performClick()
        composeTestRule.runOnIdle { assertTrue(managed) }
    }
}
