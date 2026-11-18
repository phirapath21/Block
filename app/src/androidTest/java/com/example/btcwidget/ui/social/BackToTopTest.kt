package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** "Back to top": hidden near the top, shown further down, and takes you up. */
class BackToTopTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val state = LazyListState()

    private fun show() = composeTestRule.setContent {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(100) { Text("Note $it", Modifier.height(80.dp)) }
            }
            BackToTopButton(state, Modifier.align(Alignment.BottomCenter))
        }
    }

    @Test
    fun hiddenAtTheTop() {
        show()
        composeTestRule.onNodeWithTag("back_to_top").assertDoesNotExist()
    }

    @Test
    fun farDownItShowsAndGoesBackUp() {
        show()
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        composeTestRule.onNodeWithTag("back_to_top").assertExists().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { state.firstVisibleItemIndex == 0 }
        composeTestRule.runOnIdle { assertEquals(0, state.firstVisibleItemIndex) }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTagCount("back_to_top") == 0
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
