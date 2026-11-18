package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every button in the app's shared building blocks has something for TalkBack to say (its
 * text or a description) and says it's a button: the regression guard for the accessibility
 * pass. Add new shared controls here.
 */
class AccessibilityTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sharedControlsAreNamedButtons() {
        composeTestRule.setContent {
            Column {
                Avatar(null, "a".repeat(64), size = 40, onClick = {})
                ComposeToolButton(Glyphs.PhotoLine, "Add photo or video") {}
                ComposeStatusChip(Glyphs.Warning, "Sensitive", onClear = {})
                PillButton("Follow") {}
                ChoicePill("Following", selected = true) {}
                SettingsRadioRow("Block's browser", selected = true) {}
                SettingsSwitchRow("Web of trust", checked = false) {}
                SettingsButton("Measure again") {}
                MiningPanel(SocialViewModel.PublishActivity.Mining(16, 1_000, 50_000.0, 500), onSkip = {}, onCancel = {})
            }
        }
        val nodes = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("found the controls", nodes.size >= 9)
        nodes.forEach { node ->
            val c = node.config
            val text = c.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
            val desc = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            assertTrue("a control with no name: $c", (text + desc).isNotBlank())
            assertTrue("a control with no role: $c", c.getOrNull(SemanticsProperties.Role) != null)
        }
    }
}
