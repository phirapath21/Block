package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.nostr.SignerType
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

// ---- Your account ----

@Composable
internal fun AccountPage(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val context = LocalContext.current
    val account by vm.account.collectAsState()
    val saved by vm.savedAccounts.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var removing by remember { mutableStateOf<String?>(null) }
    var loggingOut by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { saved.forEach { vm.refreshProfile(it.pubkey) } }

    val acct = account
    if (acct == null) {
        SettingsNote(tr("You're not logged in. Log in with a signer app, a remote signer or your nsec, or create a new account."))
        SettingsLinkRow(tr("Log in or create account")) { navigate(com.example.btcwidget.LoginKey) }
        return
    }
    // Who you are, with the npub to copy.
    val npub = Nip19.npub(acct.pubkey)
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profiles[acct.pubkey], acct.pubkey, size = 52, onClick = { navigate(com.example.btcwidget.ProfileKey(acct.pubkey)) })
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            DisplayName(profiles[acct.pubkey], acct.pubkey, EInk.Value, 18.sp)
            Text(
                npub.take(14) + "…" + npub.takeLast(6), color = EInk.Caption, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Copy npub")) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("npub", npub))
                    android.widget.Toast.makeText(context, tr("npub copied"), android.widget.Toast.LENGTH_SHORT).show()
                }.padding(vertical = 2.dp)
            )
            Text(signerLabel(acct.type), color = EInk.Caption, fontSize = 13.sp)
        }
    }
    SettingsLinkRow(tr("Edit profile")) { navigate(com.example.btcwidget.EditProfileKey) }
    ScheduledRow(vm)

    SettingsDivider()
    SettingsHeader(tr("Accounts on this phone"))
    SettingsNote(tr("Switch between Nostr accounts without logging out."))
    saved.forEach { a ->
        Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Switch")) { vm.switchAccount(a.pubkey) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(profiles[a.pubkey], a.pubkey, size = 40)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(profiles[a.pubkey]?.bestName ?: Profile.shortNpub(a.pubkey), color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(signerLabel(a.type), color = EInk.Caption, fontSize = 13.sp)
            }
            SettingsButton(tr("Switch"), primary = true) { vm.switchAccount(a.pubkey) }
            Spacer(Modifier.size(8.dp))
            SettingsButton(tr("Remove"), destructive = true) { removing = a.pubkey }
        }
    }
    SettingsLinkRow(tr("+ Add account"), chevron = false) { navigate(com.example.btcwidget.LoginKey) }

    if (acct.type == SignerType.LOCAL) {
        SettingsDivider()
        SettingsHeader(tr("Your private key (nsec)"))
        PrivateKey(vm)
    }

    SettingsDivider()
    SettingsLinkRow(tr("Log out"), destructive = true) { loggingOut = true }
    Spacer(Modifier.height(8.dp))

    removing?.let { pk ->
        ConfirmDialog(
            title = tr("Remove this account from the phone?"),
            text = tr("Its key (if stored here), drafts and settings are deleted from this phone. The account itself stays on Nostr. Make sure you have its nsec if you want to use it again."),
            confirmLabel = tr("Remove"),
            onConfirm = { vm.removeSavedAccount(pk); removing = null },
            onDismiss = { removing = null }
        )
    }
    val keyUnsaved by vm.keyBackup.needed.collectAsState()
    var keySheet by remember { mutableStateOf(false) }
    if (keySheet) KeyBackupSheet(vm, onDone = { keySheet = false })
    // The key of an account made here and never saved: logging out loses the account.
    if (loggingOut && acct.type == SignerType.LOCAL && keyUnsaved) BlockDialog(
        onDismiss = { loggingOut = false },
        title = tr("Save your key first"),
        text = tr("You haven't saved this account's key. Logging out deletes it from this phone, and nobody can get the account back without it."),
        confirmLabel = tr("Save key first"),
        onConfirm = { loggingOut = false; keySheet = true },
        dismissLabel = tr("Cancel")
    ) {
        // The way out is there, but small: losing the account should take a deliberate tap.
        Text(
            tr("Log out anyway"), color = EInk.Alert, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button) { loggingOut = false; vm.logout() }
                .padding(vertical = 6.dp)
        )
    }
    else if (loggingOut) ConfirmDialog(
        title = tr("Log out?"),
        text = if (acct.type == SignerType.LOCAL) tr("Logging out deletes the key from this phone. Make sure you saved your nsec.")
        else tr("You can log in again with the same signer any time."),
        confirmLabel = tr("Log out"),
        onConfirm = { loggingOut = false; vm.logout() },
        onDismiss = { loggingOut = false }
    )
}

private fun signerLabel(type: SignerType): String = when (type) {
    SignerType.AMBER -> tr("Signed by your signer app")
    SignerType.BUNKER -> tr("Signed by your remote signer (bunker)")
    else -> tr("Key encrypted on this device")
}

/** Scheduled posts, opened in place: Post now or Cancel each. */
@Composable
private fun ScheduledRow(vm: SocialViewModel) {
    val posts by vm.scheduledPosts.posts.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.scheduledPosts.refresh() }
    SettingsLinkRow(
        tr("Scheduled posts"),
        summary = tr("Sent at their time if your phone is on and online. Schedule one with 🕒 in the composer."),
        value = if (posts.isEmpty()) tr("None") else posts.size.toString(),
        chevron = posts.isNotEmpty()
    ) { open = !open }
    if (open) posts.forEach { p ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                java.text.SimpleDateFormat("EEE d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(p.at * 1000)) +
                    if (p.at * 1000 < System.currentTimeMillis()) tr(" · waiting to send") else "",
                color = EInk.Caption, fontSize = 13.sp
            )
            Text(p.content, color = EInk.Value, fontSize = 15.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsButton(tr("Post now"), primary = true) { vm.scheduledPosts.postNow(p) }
                SettingsButton(tr("Cancel"), destructive = true) { vm.scheduledPosts.cancel(p.id) }
            }
        }
    }
}

/** Shows the nsec after a warning, with screenshots blocked while it is on screen. */
@Composable
private fun PrivateKey(vm: SocialViewModel) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var nsec by remember { mutableStateOf<String?>(null) }
    val unsaved by vm.keyBackup.needed.collectAsState()
    SettingsNote(tr("Back it up somewhere safe. Anyone with it can post as you, forever; it can't be changed."))
    if (unsaved) SettingsNote(tr("You haven't saved it yet. Show it, then copy it or write it down."))
    val shown = nsec
    if (shown == null) {
        SettingsLinkRow(tr("Show your private key"), chevron = true) { confirm = true }
    } else {
        SecureScreen()
        Text(shown, color = EInk.Value, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(tr("Copy")) { copySecretWithNote(context, shown); vm.keyBackup.saved() }
            if (unsaved) SettingsButton(tr("I saved it")) { vm.keyBackup.saved() }
            SettingsButton(tr("Hide"), primary = true) { nsec = null }
        }
    }
    if (confirm) ConfirmDialog(
        title = tr("Show your private key?"),
        text = tr("Never share it or paste it into a website. Anyone who sees it controls your account, ") +
            tr("and Nostr can't reset it. Make sure no one is looking at your screen."),
        confirmLabel = tr("Show key"),
        onConfirm = { confirm = false; nsec = vm.exportNsec() },
        onDismiss = { confirm = false }
    )
}

/** Copies a secret marked sensitive, so Android 13+ hides it from the clipboard preview. */
private fun copySecretWithNote(context: Context, secret: String) {
    copySecret(context, secret)
    android.widget.Toast.makeText(context, tr("Copied. Paste it somewhere safe, then clear your clipboard."), android.widget.Toast.LENGTH_LONG).show()
}
