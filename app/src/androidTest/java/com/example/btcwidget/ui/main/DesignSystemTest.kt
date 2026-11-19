package com.example.btcwidget.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The design system's shared pieces: buttons click (or don't), chips say whether they're on. */
class DesignSystemTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun buttonsClickUnlessDisabled() {
        var taps = 0
        composeTestRule.setContent {
            Column {
                BlockButton("Post", kind = ButtonKind.Accent) { taps++ }
                BlockButton("Later", kind = ButtonKind.Outline, enabled = false) { taps += 10 }
            }
        }
        composeTestRule.onNodeWithText("Post").performClick()
        composeTestRule.onNodeWithText("Later").assertIsNotEnabled()
        composeTestRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun choiceChipsShowTheirState() {
        composeTestRule.setContent {
            var on by remember { mutableStateOf(false) }
            BlockChip("Music", selected = on, filter = false) { on = !on }
        }
        composeTestRule.onNodeWithText("Music").assertIsNotSelected().performClick()
        // On: filled, with a drawn tick (no emoji).
        composeTestRule.onNodeWithText("Music").assertIsSelected()
    }

    @Test
    fun capsuleWithAnIconIsAButtonThatShowsItsChoice() {
        var picked by mutableStateOf(false)
        composeTestRule.setContent {
            BlockChip(
                "3 days", selected = picked, filter = false, tick = false, icon = com.example.btcwidget.ui.social.Glyphs.Calendar,
                role = androidx.compose.ui.semantics.Role.Button
            ) { picked = true }
        }
        composeTestRule.onNodeWithText("3 days").performClick()
        composeTestRule.onNodeWithText("3 days").assertIsSelected()
    }

    @Test
    fun emptyStateOffersItsAction() {
        var asked = false
        composeTestRule.setContent {
            EmptyState(com.example.btcwidget.ui.social.Glyphs.Bell, "Nothing yet", "When people reply, it shows up here.", action = "Find people") { asked = true }
        }
        composeTestRule.onNodeWithText("Nothing yet").assertExists()
        composeTestRule.onNodeWithText("Find people").performClick()
        composeTestRule.runOnIdle { assertEquals(true, asked) }
    }
}
