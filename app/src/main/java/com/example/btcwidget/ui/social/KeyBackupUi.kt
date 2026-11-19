package com.example.btcwidget.ui.social

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.nostr.KeyBackup
import com.example.btcwidget.nostr.NostrAccount
import com.example.btcwidget.nostr.SignerType
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the key of the current account still needs saving ([needed]: Settings' dot and the
 * log-out warning) and whether to remind about it on Home now ([remind]: not after "Later" for
 * [KeyBackup.SNOOZE_MS]). Only for accounts created in Block, whose key is on this phone.
 */
class KeyBackupState(private val app: Application, private val account: StateFlow<NostrAccount?>, scope: CoroutineScope) {
    // Bumped when a flag changes, so both flows read the prefs again.
    private val changes = MutableStateFlow(0)

    val needed: StateFlow<Boolean> = combine(account, changes) { a, _ ->
        a != null && a.type == SignerType.LOCAL && KeyBackup.needed(app, a.pubkey)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val remind: StateFlow<Boolean> = combine(account, changes) { a, _ ->
        a != null && KeyBackup.shouldRemind(
            needed = KeyBackup.needed(app, a.pubkey), keyOnPhone = a.type == SignerType.LOCAL,
            snoozedUntil = KeyBackup.snoozedUntil(app, a.pubkey), now = System.currentTimeMillis()
        )
    }.stateIn(scope, SharingStarted.Eagerly, false)

    internal fun needed(pubkey: String) { KeyBackup.markNeeded(app, pubkey); changes.value++ }
    internal fun saved(pubkey: String) { KeyBackup.markSaved(app, pubkey); changes.value++ }

    /** The user saved (copied, or said so) the current account's key. */
    fun saved() { account.value?.pubkey?.let(::saved) }

    /** "Later": ask again in a few days. */
    fun later() { account.value?.pubkey?.let { KeyBackup.snooze(app, it); changes.value++ } }
}

/** What saving the key means, in plain words, for the banner, the sheet and the new-account screen. */
internal fun keyBackupExplainer(): String =
    tr("Your key is your account's password, but nobody can reset it: if this phone is lost and the key isn't saved somewhere, the account is gone for good.") +
        " " + tr("Save it in a password manager, or write it down and keep it somewhere safe. Never share it.")

/** Home's reminder, under the feed tabs, until the key is saved (or for a few days after "Later"). */
@Composable
internal fun KeyBackupBanner(vm: SocialViewModel) {
    val remind by vm.keyBackup.remind.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    AnimatedVisibility(visible = remind, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(EInk.Accent.copy(alpha = 0.10f))
                .border(1.dp, EInk.Accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp)
                .testTag("key_backup_banner"),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Accent, modifier = Modifier.padding(top = 2.dp).size(22.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(tr("Save your key"), color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr("It's the only way back into your account. Nobody can reset it for you."),
                    color = EInk.Label, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp)
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr("Save now")) { sheet = true }
                    PillButton(tr("Later"), filled = false) { vm.keyBackup.later() }
                }
            }
        }
    }
    if (sheet) KeyBackupSheet(vm, onDone = { sheet = false })
}

/**
 * Shows the key behind one tap (screenshots blocked meanwhile), with Copy and "I saved it":
 * either marks it saved. Used by Home's reminder and the log-out warning.
 */
@Composable
internal fun KeyBackupSheet(vm: SocialViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var nsec by remember { mutableStateOf<String?>(null) }
    BlockSheet(onDismiss = onDone) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(22.dp))
                }
                Text(tr("Save your key"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
            }
            Text(keyBackupExplainer(), color = EInk.Label, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))
            val shown = nsec
            if (shown == null) {
                PillButton(tr("Show my key")) { nsec = vm.exportNsec() }
                Text(tr("Make sure no one is looking at your screen."), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                SecureScreen()
                Text(
                    shown, color = EInk.Value, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(EInk.Body).padding(12.dp)
                )
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr("Copy"), filled = false) {
                        copySecret(context, shown)
                        vm.keyBackup.saved()
                        android.widget.Toast.makeText(context, tr("Copied. Paste it somewhere safe, then clear your clipboard."), android.widget.Toast.LENGTH_LONG).show()
                    }
                    PillButton(tr("I saved it")) { vm.keyBackup.saved(); onDone() }
                }
            }
        }
    }
}
