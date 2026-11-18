package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.AppPrefs
import com.example.btcwidget.SettingsKey
import com.example.btcwidget.ui.main.EInk

/** Bump when there's something new to tell people about. */
internal const val WHATS_NEW_VERSION = 1

private class NewThing(val icon: () -> ImageVector, val title: String, val text: String, val page: String?)

private val NEW_THINGS = listOf(
    NewThing({ Glyphs.Sparkle }, "Themes", "Ember, Gilded and Kyriaki, from the Zeus wallet. Settings › Display.", "DISPLAY"),
    NewThing({ Glyphs.PhotoLine }, "Photo albums", "Posts with several photos show as one tidy grid; tap any to swipe through them all.", null),
    NewThing({ Glyphs.BlockCube }, "Bitcoin in posts", "Transactions and blocks in posts get a card with live confirmations; tap for the details.", "DISPLAY"),
    NewThing({ Glyphs.Pickaxe }, "Proof of work", "Mine your posts so spam filters trust them (NIP-13).", "POW"),
)

/**
 * "What's new" once per [WHATS_NEW_VERSION]: a sheet listing what's arrived, each one opening its
 * settings. Closing it, or opening one, counts as seen.
 */
@Composable
fun WhatsNewSheet(navigate: (NavKey) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(AppPrefs.whatsNewSeen(context) < WHATS_NEW_VERSION) }
    if (!open) return
    fun done() { AppPrefs.setWhatsNewSeen(context, WHATS_NEW_VERSION); open = false }
    BlockSheet(onDismiss = ::done) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(tr("What's new in Block"), color = EInk.Value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(tr("A few things that arrived recently."), color = EInk.Caption, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            NEW_THINGS.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(if (t.page != null) Modifier.clickable(role = Role.Button) { done(); navigate(SettingsKey(t.page)) } else Modifier)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(t.icon(), contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(tr(t.title), color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(tr(t.text), color = EInk.Label, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    if (t.page != null) Icon(Glyphs.Chevron, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(18.dp))
                }
            }
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(EInk.Accent)
                    .clickable(role = Role.Button, onClick = ::done),
                contentAlignment = Alignment.Center
            ) { Text(tr("Got it"), color = EInk.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
