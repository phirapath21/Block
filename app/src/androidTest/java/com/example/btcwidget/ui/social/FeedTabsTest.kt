package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.btcwidget.nostr.core.HomeFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Home's feeds as tabs at the top, as on X: all in view, one tap to switch, Manage at the end. */
class FeedTabsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyFeedIsATabAndOneTapSwitches() {
        var managed = false
        composeTestRule.setContent {
            var current by remember { mutableStateOf(HomeFeed.DEFAULTS.first()) }
            FeedTabBar(
                feeds = HomeFeed.DEFAULTS + HomeFeed.Hashtag("siamstr"),
                current = current,
                onSelect = { current = it }, onManage = { managed = true }
            )
        }
        // All feeds show at once, the open one marked selected.
        composeTestRule.onNodeWithTag("feed_tabs").assertExists()
        composeTestRule.onNodeWithText("Latest").assertIsSelected()
        composeTestRule.onNodeWithText("With replies").assertIsNotSelected()
        composeTestRule.onNodeWithText("#siamstr").assertExists()

        // One tap switches.
        composeTestRule.onNodeWithText("With replies").performClick()
        composeTestRule.onNodeWithText("With replies").assertIsSelected()
        composeTestRule.onNodeWithText("Latest").assertIsNotSelected()

        composeTestRule.onNodeWithContentDescription("Manage feeds").performClick()
        composeTestRule.runOnIdle { assertTrue(managed) }
    }

    @Test
    fun tappingTheOpenTabReportsIt() {
        var picked: HomeFeed? = null
        composeTestRule.setContent {
            FeedTabBar(feeds = HomeFeed.DEFAULTS, current = HomeFeed.DEFAULTS.first(), onSelect = { picked = it }, onManage = {})
        }
        // Home turns this into "back to the top", as on X.
        composeTestRule.onNodeWithText("Latest").performClick()
        composeTestRule.runOnIdle { assertEquals(HomeFeed.DEFAULTS.first(), picked) }
    }

    @Test
    fun tabsFoldAwayWithTheBars() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalBarsVisible provides false) {
                FeedTabBar(feeds = HomeFeed.DEFAULTS, current = HomeFeed.DEFAULTS.first(), onSelect = {}, onManage = {})
            }
        }
        composeTestRule.onNodeWithTag("feed_tabs").assertDoesNotExist()
    }
}
