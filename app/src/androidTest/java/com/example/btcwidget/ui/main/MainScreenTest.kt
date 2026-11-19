package com.example.btcwidget.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData
import org.junit.Rule
import org.junit.Test

/** UI tests for [MainScreenContent] with fixed data (no network, no prefs). */
class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sample = PriceData(
        btcUsd = 74_455.0, btcThb = 2_415_000.0, moscowTime = 1343, mstrUsd = 412.6,
        mstrBtc = 0.005574, blockHeight = 925_000, mstrBtcHeld = 640_031.0,
        btcCirculation = 19_912_345.0, timestamp = 0L
    )

    private fun setContent() {
        composeTestRule.setContent {
            var tab by remember { mutableStateOf(MainTab.HOME) }
            MainScreenContent(
                selectedTab = tab,
                onTabSelected = { tab = it },
                onItemClick = {},
                settingsContent = { com.example.btcwidget.ui.social.SettingsHome(accountName = null, onOpen = { openedSettings = it }) }
            )
        }
    }

    private var openedSettings: com.example.btcwidget.ui.social.SettingsPage? = null

    @Test
    fun bottomBar_hasFourTabsAndNewPostInTheMiddle() {
        setContent()
        listOf("Home", "Search", "New post", "Notifications", "Messages", "Open menu").forEach {
            composeTestRule.onNodeWithContentDescription(it).assertExists()
        }
        composeTestRule.onNodeWithTag("compose_button").assertExists()
        // The wallet is in your menu now, not the bar.
        composeTestRule.onNodeWithContentDescription("Wallet").assertDoesNotExist()
    }

    @Test
    fun menu_fromYourAvatar_opensTheWallet() {
        setContent()
        openFromSideBar("Wallet")
        composeTestRule.onNodeWithText("Wallet").assertExists()
    }

    @Test
    fun landscape_tabsMoveToARailOnTheLeft() {
        composeTestRule.setContent {
            // A phone on its side: wider than tall.
            val wide = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply {
                screenWidthDp = 840; screenHeightDp = 380; orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides wide) {
                MainScreenContent(selectedTab = MainTab.HOME, onTabSelected = {}, onItemClick = {})
            }
        }
        composeTestRule.onNodeWithTag("side_rail").assertExists()
        listOf("Home", "Search", "New post", "Notifications", "Messages", "Open menu").forEach {
            composeTestRule.onNodeWithContentDescription(it).assertExists()
        }
    }

    @Test
    fun topBarAction_showsOnlyOnItsPage() {
        var tapped = false
        composeTestRule.setContent {
            var tab by remember { mutableStateOf(MainTab.HOME) }
            val newChat: @androidx.compose.runtime.Composable () -> Unit = {
                androidx.compose.material3.Text("New chat", modifier = androidx.compose.ui.Modifier.clickable { tapped = true })
            }
            MainScreenContent(
                selectedTab = tab, onTabSelected = { tab = it }, onItemClick = {},
                topBarActions = mapOf(MainTab.MESSAGES to newChat)
            )
        }
        composeTestRule.onNodeWithText("New chat").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Messages").performClick()
        composeTestRule.onNodeWithText("New chat").performClick()
        composeTestRule.runOnIdle { assert(tapped) }
    }

    @Test
    fun portrait_hasNoRail() {
        setContent()
        composeTestRule.onNodeWithTag("side_rail").assertDoesNotExist()
    }

    @Test
    fun notifications_openFromTheBottomBar() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Notifications").performClick()
        // The top bar shows Search now, not the page's name: the tab itself says it's open.
        composeTestRule.onNodeWithContentDescription("Notifications").assertIsSelected()
    }

    private val sats = "SATS 1USD 1343. Sats per 1 dollar"

    /**
     * Scrolls the Widgets page (its one lazy grid) until a node matching [target] is composed:
     * panels below the how-to and stack cards aren't until scrolled to.
     */
    private fun scrollWidgetsTo(target: androidx.compose.ui.test.SemanticsMatcher) {
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToNode(target)
    }

    /** Opens your menu (a sheet from your avatar) and picks [page]. */
    private fun openFromSideBar(page: String) {
        composeTestRule.onNodeWithContentDescription("Open menu").performClick()
        composeTestRule.onNodeWithText(page).performClick()
    }

    @Test
    fun settings_openFromSideBar() {
        setContent()
        openFromSideBar("Settings")
        // The categories, each opening its own page.
        composeTestRule.onNodeWithText("Your account").assertExists()
        // The last categories are below the fold on a small screen.
        composeTestRule.onNodeWithText("Bitcoin and widgets").performScrollTo().performClick()
        org.junit.Assert.assertEquals(com.example.btcwidget.ui.social.SettingsPage.BITCOIN, openedSettings)
    }

    @Test
    fun settings_searchFindsASettingAndItsPage() {
        setContent()
        openFromSideBar("Settings")
        composeTestRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("currency")
        composeTestRule.onNodeWithText("Currency").performClick()
        org.junit.Assert.assertEquals(com.example.btcwidget.ui.social.SettingsPage.BITCOIN, openedSettings)
    }

    @Test
    fun settings_proofOfWorkIsUnderAdvancedAndFoundBySearch() {
        setContent()
        openFromSideBar("Settings")
        // Not a category of its own: it opens from Advanced.
        composeTestRule.onNodeWithText("Proof of work").assertDoesNotExist()
        composeTestRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("proof")
        // Each hit shows its page's name under it: the first hit is the setting itself.
        composeTestRule.onAllNodesWithText("Proof of work")[0].performClick()
        org.junit.Assert.assertEquals(com.example.btcwidget.ui.social.SettingsPage.POW, openedSettings)
    }

    /** The Widgets page (Settings › Bitcoin and widgets › Widgets) with fixed data. */
    private fun setWidgets(states: List<Boolean> = List(BlockclockMetrics.MODE_COUNT) { true }, onToggle: (Int, Boolean) -> Unit = { _, _ -> }) {
        composeTestRule.setContent {
            var collapsed by remember { mutableStateOf(emptySet<com.example.btcwidget.MetricCategory>()) }
            WidgetsScreen(
                data = sample, currencySetting = "USD", modeStates = states, onModeToggle = onToggle,
                order = BlockclockMetrics.MODE_KEYS, onOrderChange = {},
                collapsedCategories = collapsed,
                onToggleCategory = { c -> collapsed = if (c in collapsed) collapsed - c else collapsed + c },
                stackSats = 0, onSetUpStack = {}
            )
        }
    }

    @Test
    fun widgets_offMetricsStayVisibleWithTheirSwitch() {
        val states = androidx.compose.runtime.mutableStateListOf(*Array(BlockclockMetrics.MODE_COUNT) { it != 1 }) // SATS off
        setWidgets(states) { index, on -> states[index] = on }
        // The switched-off SATS panel is still shown, with its switch off.
        scrollWidgetsTo(hasContentDescription(sats))
        composeTestRule.onNodeWithContentDescription(sats).assertExists()
        scrollWidgetsTo(hasText("Off widget"))
        composeTestRule.onNodeWithText("Off widget").assertExists().performClick()
        composeTestRule.onNodeWithText("Off widget").assertDoesNotExist()
    }

    @Test
    fun widgets_countMetricsOnTheWidget() {
        setWidgets()
        // STACK needs holdings, so all but one of the modes are available.
        val available = BlockclockMetrics.MODE_COUNT - 1
        composeTestRule.onNodeWithText("$available of $available", substring = true).assertExists()
    }

    @Test
    fun widgets_sectionsCollapse() {
        setWidgets()
        scrollWidgetsTo(hasContentDescription(sats))
        composeTestRule.onNodeWithContentDescription(sats).assertExists()
        scrollWidgetsTo(hasText("Market"))
        composeTestRule.onNodeWithText("Market").performClick()
        composeTestRule.onNodeWithContentDescription(sats).assertDoesNotExist()
    }
}
