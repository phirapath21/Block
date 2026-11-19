package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.nostr.Search
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.screenBackground
import kotlinx.coroutines.launch

/** Start a conversation: pick someone you follow, or enter an npub, nprofile or name@domain. */
@Composable
internal fun NewMessageDialog(
    vm: SocialViewModel,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onNewGroup: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val following by vm.following.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(following) { vm.loadProfiles(following.take(200)) }

    val q = input.trim().lowercase()
    val suggestedFollows = remember(q, following, profiles) {
        following.filter { pk ->
            val p = profiles[pk]
            if (q.isEmpty()) p != null
            else p?.let { listOfNotNull(it.name, it.displayName, it.nip05).any { s -> s.lowercase().contains(q) } } == true
        }.sortedBy { profiles[it]?.bestName?.lowercase() ?: "~" }.take(20)
    }

    BlockDialog(
        onDismiss = onDismiss,
        title = tr("New message"),
        confirmLabel = if (busy) tr("Looking…") else tr("Open chat"),
        confirmEnabled = !busy && input.isNotBlank(),
        onConfirm = {
            busy = true
            scope.launch {
                val target = vm.resolve(input)
                busy = false
                if (target is Search.Direct.ProfileTarget) onOpen(target.pubkey)
                else error = tr("Couldn't find that person. Try their npub.")
            }
        }
    ) {
        // Quick action: New group chat
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radius.Large)
                .screenBackground()
                .border(0.5.dp, EInk.BodyStroke, Radius.Large)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onNewGroup)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(EInk.Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Glyphs.Group, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(tr("New group chat"), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                Text(tr("Up to {0} people · NIP-17 encrypted", MAX_GROUP), color = EInk.Caption, fontSize = TextSize.Caption)
            }
            Icon(Glyphs.Chevron, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(12.dp))

        SettingsTextField(
            value = input,
            onValueChange = { input = it; error = null },
            placeholder = tr("Search follows, npub, or name@domain"),
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(
                error!!,
                color = EInk.Alert,
                fontSize = TextSize.Caption,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (suggestedFollows.isNotEmpty()) {
            Text(
                tr("People you follow"),
                color = EInk.Caption,
                fontSize = TextSize.Micro,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
            )
            Column {
                suggestedFollows.forEach { pk ->
                    val p = profiles[pk]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radius.Small)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { onOpen(pk) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(p, pk, size = 36)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p?.bestName ?: Profile.shortNpub(pk),
                                    color = EInk.Value,
                                    fontSize = TextSize.Body,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isVerified(p, pk)) {
                                    VerifiedBadge(14.dp, Modifier.padding(start = 4.dp))
                                }
                            }
                            Text(
                                handleText(p, pk),
                                color = EInk.Caption,
                                fontSize = TextSize.Caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Glyphs.Chat, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Pick people you follow (or paste npubs) for a group chat. */
@Composable
internal fun NewGroupDialog(vm: SocialViewModel, onDismiss: () -> Unit, onStart: (List<String>) -> Unit) {
    val following by vm.following.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(following) { vm.loadProfiles(following.take(300)) }
    val q = query.trim().lowercase()
    val pasted = remember(q) { (Nip19.decode(q) as? Nip19.Pubkey)?.hex }
    val matches = remember(q, following, profiles) {
        following.filter { pk ->
            q.isEmpty() || profiles[pk]?.let { p -> listOfNotNull(p.name, p.displayName, p.nip05).any { it.lowercase().contains(q) } } == true
        }.sortedBy { profiles[it]?.bestName?.lowercase() ?: "~" }.take(40)
    }
    BlockDialog(
        onDismiss = onDismiss,
        title = tr("New group chat"),
        confirmLabel = if (picked.size >= 2) tr("Start with {0} people", picked.size) else tr("Pick at least 2 people"),
        confirmEnabled = picked.size >= 2,
        onConfirm = { onStart(picked) }
    ) {
        SettingsTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = tr("Search follows or paste an npub"),
            modifier = Modifier.fillMaxWidth()
        )
        if (picked.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                picked.forEach { pk ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(Radius.Full)
                            .background(EInk.Accent.copy(alpha = 0.16f))
                            .border(0.5.dp, EInk.Accent, Radius.Full)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { picked = picked - pk }
                            .padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp)
                    ) {
                        Avatar(profiles[pk], pk, size = 20)
                        Text(
                            profiles[pk]?.bestName ?: Profile.shortNpub(pk),
                            color = EInk.Value,
                            fontSize = TextSize.Caption,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Icon(Glyphs.CloseLine, contentDescription = tr("Remove"), tint = EInk.Accent, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        // A plain column (at most 40 rows): the dialog itself scrolls.
        Column(Modifier.padding(top = 8.dp)) {
            (listOfNotNull(pasted) + matches).distinct().forEach { pk ->
                val on = pk in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.Small)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { picked = if (on) picked - pk else (picked + pk).take(MAX_GROUP) }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(profiles[pk], pk, size = 34)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            profiles[pk]?.bestName ?: Profile.shortNpub(pk),
                            color = EInk.Value,
                            fontSize = TextSize.Body,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            handleText(profiles[pk], pk),
                            color = EInk.Caption,
                            fontSize = TextSize.Caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (on) EInk.Accent else EInk.Track),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Icon(Glyphs.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Text(
            tr("Everyone in the group sees each other and every message. Up to {0} people.", MAX_GROUP),
            color = EInk.Caption,
            fontSize = TextSize.Caption,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Each message is wrapped once per member, so groups stay small. */
private const val MAX_GROUP = 10
