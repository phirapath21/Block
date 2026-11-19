package com.example.btcwidget.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.btcwidget.theme.BTCWidgetTheme

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    BTCWidgetTheme {
        MainScreenContent(selectedTab = MainTab.HOME, onTabSelected = {}, onItemClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenSettingsPreview() {
    BTCWidgetTheme {
        MainScreenContent(selectedTab = MainTab.SETTINGS, onTabSelected = {}, onItemClick = {})
    }
}
