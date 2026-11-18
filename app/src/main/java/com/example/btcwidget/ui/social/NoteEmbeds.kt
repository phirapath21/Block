package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.core.Content
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk

/** A linked long-form article: cover, title and summary, boxed. */
@Composable
internal fun ArticleCard(address: com.example.btcwidget.nostr.core.Nip19.Address, actions: NoteActions) {
    val article by produceState<com.example.btcwidget.nostr.core.Article?>(null, address) { value = actions.resolveArticle(address) }
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.openArticle(address) }
    ) {
        val a = article
        a?.image?.let { url ->
            coil3.compose.AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
        }
        Column(Modifier.padding(10.dp)) {
            Text(tr("ARTICLE"), color = EInk.Caption, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(a?.title ?: tr("Loading article…"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            a?.summary?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

/** A quoted note, boxed, as in a quote post. */
@Composable
internal fun QuotedNote(ref: Content.Segment.NoteRef, quoting: Event, actions: NoteActions) {
    // null: still looking; then the note, or nothing when no relay has it.
    val lookup by produceState<Result<Event?>?>(null, ref.id) { value = runCatching { actions.resolveQuote(ref, quoting) } }
    val quoted = lookup?.getOrNull()
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = quoted != null) { quoted?.let(actions.openNote) }
            .padding(10.dp)
    ) {
        val q = quoted
        if (q == null) {
            Text(
                if (lookup == null) tr("Loading quoted note…") else tr("This note couldn't be found on your relays or its author's."),
                color = EInk.Caption, fontSize = 13.sp
            )
            // Not found anywhere Block asked: njump.me looks across more relays, in the browser.
            if (lookup != null) {
                val hints = remember(ref.id, quoting.id) { com.example.btcwidget.nostr.core.QuoteHints.of(quoting, ref.id, ref.relays, ref.author) }
                Text(
                    tr("Look for it on njump.me ›"),
                    color = SettingsAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        actions.openLink("https://njump.me/" + com.example.btcwidget.nostr.core.Nip19.nevent(ref.id, hints.relays.take(2), hints.author), quoting)
                    }
                )
            }
        } else {
            val author = actions.profileOf(q.pubkey)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(author, q.pubkey, size = 24, onClick = { actions.openProfile(q.pubkey) })
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { AuthorLine(q, author, actions, small = true) }
            }
            NoteBody(q, actions, fontSize = 14, depth = 1)
        }
    }
}
