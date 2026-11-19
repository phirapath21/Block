package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.foundation.background
import com.example.btcwidget.ui.main.screenBackground
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.nostr.RelayStatus
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay

private val Green get() = EInk.Success
private val Orange get() = EInk.Accent
private val Red get() = EInk.Alert

/**
 * How each relay connection is doing, refreshed every second: your relays first, then the
 * ones opened to read people who post elsewhere (outbox). Tap a failing relay to retry now.
 */
@Composable
fun RelaysScreen(vm: SocialViewModel, onBack: () -> Unit) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var info by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().screenBackground()) {
        SocialTopBar(tr("Relays"), onBack)
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            listOf("STATUS", "MY RELAYS", "SEARCH", "BLOCKED").forEachIndexed { i, t ->
                com.example.btcwidget.ui.main.BlockChip(text = t, selected = tab == i, onClick = { tab = i })
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        when (tab) {
            0 -> RelayStatusTab(vm, onInfo = { info = it })
            1 -> MyRelaysTab(vm, onInfo = { info = it })
            2 -> RelaySetTab(vm, blocked = false, onInfo = { info = it })
            else -> RelaySetTab(vm, blocked = true, onInfo = { info = it })
        }
    }
    info?.let { RelayInfoSheet(vm, it, onDismiss = { info = null }) }
}

/** Live connection state of every relay, refreshed every second. */
@Composable
private fun RelayStatusTab(vm: SocialViewModel, onInfo: (String) -> Unit) {
    var status by remember { mutableStateOf(vm.relayStatus()) }
    var showOthers by remember { mutableStateOf(false) }
    var checkup by remember { mutableStateOf(false) }
    if (checkup) RelayCheckupSheet(vm, onDismiss = { checkup = false })
    LaunchedEffect(Unit) {
        while (true) { status = vm.relayStatus(); delay(1_000) }
    }
    val mine = status.filter { it.primary }
    val others = status.filter { !it.primary }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "summary") {
                val up = mine.count { it.state == RelayStatus.State.CONNECTED }
                Column(Modifier.padding(16.dp)) {
                    Text(tr("{0} of {1} connected", up, mine.size), color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            mine.isEmpty() -> tr("No relays set.")
                            up == 0 -> tr("Block can't reach any of your relays. Check your connection.")
                            up < mine.size -> tr("Some relays are down; the rest keep things working.")
                            else -> tr("All your relays are working.")
                        },
                        color = EInk.Caption, fontSize = TextSize.Body, modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(tr("Tap a relay to see what it says about itself."), color = EInk.Caption, fontSize = TextSize.Caption, modifier = Modifier.padding(top = 2.dp))
                    PillButton(tr("Check and tidy relays"), filled = up < mine.size, modifier = Modifier.padding(top = 12.dp)) { checkup = true }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            }
            items(mine, key = { "m" + it.url }) { RelayRow(it, onInfo = { onInfo(it.url) }) { vm.retryRelay(it.url) } }
            if (others.isNotEmpty()) item(key = "others") {
                Row(
                    Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { showOthers = !showOthers }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Other relays ({0}/{1})", others.count { it.state == RelayStatus.State.CONNECTED }, others.size), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                        Text(tr("Opened to read people who post on their own relays"), color = EInk.Caption, fontSize = TextSize.Caption)
                    }
                    androidx.compose.material3.Icon(if (showOthers) Glyphs.ArrowUp else Glyphs.ArrowDown, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            }
            if (showOthers) items(others, key = { "o" + it.url }) { RelayRow(it, onInfo = { onInfo(it.url) }) { vm.retryRelay(it.url) } }
        }
    }
}

@Composable
private fun RelayRow(s: RelayStatus, onInfo: () -> Unit, onRetry: () -> Unit) {
    val (color, label) = when (s.state) {
        RelayStatus.State.CONNECTED -> Green to "Connected" + (s.connectMs?.let { " · ${it} ms" } ?: "")
        RelayStatus.State.CONNECTING -> Orange to "Connecting…"
        RelayStatus.State.RETRYING -> Orange to "Retrying (${s.failures} failed)"
        RelayStatus.State.FAILED -> Red to if (s.failures > 0) "Not reachable (${s.failures} tries)" else "Not reachable"
    }
    val canRetry = s.state == RelayStatus.State.RETRYING || (s.state == RelayStatus.State.FAILED && s.failures > 0)
    Row(
        Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("About this relay"), onClick = onInfo).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(s.url.removePrefix("wss://").removeSuffix("/"), color = EInk.Value, fontSize = TextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                label + (if (s.events > 0) " · ${s.events} messages" else ""),
                color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1
            )
            if (s.state != RelayStatus.State.CONNECTED) s.lastError?.let {
                Text(it, color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canRetry) Text(
            tr("Retry"), color = EInk.Value, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(Radius.Full).background(EInk.Track).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onRetry).padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/**
 * Check and tidy relays: tests a fresh connection to each of the user's relays, shows which
 * work (and how fast), which are slow and which can't be reached, with the unreachable ones
 * ticked for removal. Nothing changes until "Remove" is tapped; at least two relays stay, and
 * if none answer (the phone is offline) nothing is offered. Below, relays most of the people
 * the user follows write to, tested the same way, each with Add.
 */
@Composable
internal fun RelayCheckupSheet(vm: SocialViewModel, onDismiss: () -> Unit) {
    val mineUrls = remember { vm.myRelayUrls() }
    var report by remember { mutableStateOf<com.example.btcwidget.nostr.core.RelayCheckup.Report?>(null) }
    var ticked by remember { mutableStateOf(emptySet<String>()) }
    var removed by remember { mutableStateOf(emptySet<String>()) }
    var suggestions by remember { mutableStateOf<List<Pair<com.example.btcwidget.nostr.core.RelayCheckup.Probe, Int>>?>(null) }
    var added by remember { mutableStateOf(emptySet<String>()) }
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) {
        val r = com.example.btcwidget.nostr.core.RelayCheckup.report(vm.probeRelays(mineUrls))
        report = r; ticked = r.suggestedRemovals
        if (r.offline) { suggestions = emptyList(); return@LaunchedEffect }
        val counts = vm.suggestedRelays()
        val probes = vm.probeRelays(counts.map { it.first }).associateBy { it.url }
        // Only ones that answered, the most used first, then the fastest.
        suggestions = counts.mapNotNull { (url, n) -> probes[url]?.takeIf { it.connectMs != null }?.let { it to n } }
            .sortedWith(compareBy({ -it.second }, { it.first.connectMs ?: Long.MAX_VALUE })).take(5)
    }
    BlockSheet(onDismiss) {
        Text(tr("Check your relays"), color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        val r = report
        if (r == null) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(color = SettingsAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text(tr("Testing {0} relays…", mineUrls.size), color = EInk.Caption, fontSize = TextSize.Body, modifier = Modifier.padding(start = 12.dp))
            }
            return@BlockSheet
        }
        if (r.offline) {
            Text(tr("None of your relays answered. Check your internet connection and try again: nothing was changed."),
                color = Red, fontSize = TextSize.Body, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        } else {
            val ok = r.probes.count { it.health != com.example.btcwidget.nostr.core.RelayCheckup.Health.DOWN }
            Text(tr("{0} of {1} answered. Tick the ones to remove.", ok, r.probes.size), color = EInk.Caption, fontSize = TextSize.Body,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp))
        }
        r.probes.forEach { p ->
            val gone = p.url in removed
            val (color, detail) = when (p.health) {
                com.example.btcwidget.nostr.core.RelayCheckup.Health.OK -> Green to tr("{0} ms", p.connectMs ?: 0)
                com.example.btcwidget.nostr.core.RelayCheckup.Health.SLOW -> Orange to tr("Slow · {0} ms", p.connectMs ?: 0)
                com.example.btcwidget.nostr.core.RelayCheckup.Health.DOWN -> Red to (p.error?.let { tr("Can't connect · {0}", it.take(60)) } ?: tr("Can't connect"))
            }
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !gone && !r.offline && p.health != com.example.btcwidget.nostr.core.RelayCheckup.Health.OK) {
                        haptics.tick(); ticked = if (p.url in ticked) ticked - p.url else ticked + p.url
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (gone) EInk.Track else color))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(p.url.removePrefix("wss://"), color = if (gone) EInk.Caption else EInk.Value, fontSize = TextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (gone) tr("Removed") else detail, color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!gone && !r.offline && p.health != com.example.btcwidget.nostr.core.RelayCheckup.Health.OK) androidx.compose.material3.Checkbox(
                    checked = p.url in ticked, onCheckedChange = { ticked = if (it) ticked + p.url else ticked - p.url },
                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = SettingsAccent, checkmarkColor = Color.Black, uncheckedColor = EInk.Caption)
                )
            }
        }
        val toRemove = ticked - removed
        val remaining = mineUrls.filter { it !in removed }
        if (!r.offline && toRemove.isNotEmpty()) {
            val allowed = com.example.btcwidget.nostr.core.RelayCheckup.canRemove(remaining, toRemove)
            if (!allowed) Text(tr("Keep at least two relays."), color = EInk.Caption, fontSize = TextSize.Caption, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            PillButton(
                tr("Remove {0}", toRemove.size), filled = allowed,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
            ) {
                if (allowed) { haptics.confirm(); vm.removeRelays(toRemove); removed = removed + toRemove; ticked = emptySet() }
            }
        }
        // Suggestions: what the people you follow use, that answered just now.
        if (!r.offline) {
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 10.dp))
            Text(tr("Suggested relays"), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
            Text(tr("Where most of the people you follow post. Adding one helps their posts reach you."), color = EInk.Caption, fontSize = TextSize.Caption,
                lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
            val list = suggestions
            when {
                list == null -> Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(color = SettingsAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(tr("Finding relays…"), color = EInk.Caption, fontSize = TextSize.Body, modifier = Modifier.padding(start = 12.dp))
                }
                list.isEmpty() -> Text(tr("No suggestions right now."), color = EInk.Caption, fontSize = TextSize.Body, modifier = Modifier.padding(20.dp))
                else -> list.forEach { (p, n) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if ((p.connectMs ?: 0) > com.example.btcwidget.nostr.core.RelayCheckup.SLOW_MS) Orange else Green))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.url.removePrefix("wss://"), color = EInk.Value, fontSize = TextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tr("Used by {0} you follow · {1} ms", n, p.connectMs ?: 0), color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1)
                        }
                        if (p.url in added) Text(tr("Added"), color = EInk.Caption, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold)
                        else PillButton(tr("Add"), filled = false) { haptics.tick(); vm.addRelayToMine(p.url); added = added + p.url }
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
    }
}