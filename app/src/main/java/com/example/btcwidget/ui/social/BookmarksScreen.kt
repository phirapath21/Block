package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ArticleKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/** Saved notes (NIP-51 public bookmarks) & Offline Reading List (Saved Long-Form Articles). */
@Composable
fun BookmarksScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val ids by vm.bookmarks.collectAsState()
    val savedArticles by vm.savedArticles.collectAsState()
    val stats by vm.stats.collectAsState()
    val items by produceKept<List<FeedItem>?>(null, ids) { value = vm.bookmarkedNotes() }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = Notes, 1 = Reading List (Articles)
    var composingArticle by remember { mutableStateOf(false) }

    if (composingArticle) {
        ArticleComposerDialog(
            vm = vm,
            onDismiss = { composingArticle = false },
            onPublished = { published ->
                composingArticle = false
                navigate(ArticleKey(published.naddr()))
            }
        )
    }

    SocialScaffold(vm, navigate) { actions ->
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // The tabs take what "Write article" leaves, scrolling if counts make them wide,
                // so the button keeps its size however many bookmarks there are.
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChoicePill(
                        label = if (ids.isEmpty()) tr("Notes") else tr("Notes ({0})", ids.size),
                        selected = tab == 0
                    ) { tab = 0 }
                    ChoicePill(
                        label = if (savedArticles.isEmpty()) tr("Reading List") else tr("Reading List ({0})", savedArticles.size),
                        selected = tab == 1
                    ) { tab = 1 }
                }
                if (account != null) {
                    ChoicePill(
                        label = tr("Write article"),
                        selected = false,
                        icon = Glyphs.Quote
                    ) { composingArticle = true }
                }
            }

            if (tab == 0) {
                when {
                    account == null -> {
                        EmptyNote(tr("Log in to save notes for later."))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PillButton(tr("Log in")) { navigate(LoginKey) }
                        }
                    }
                    ids.isEmpty() -> com.example.btcwidget.ui.main.EmptyState(
                        Glyphs.Bookmark, tr("Save posts for later"),
                        tr("Tap the bookmark on any post to find it here again."),
                        action = tr("Explore posts"), onAction = { com.example.btcwidget.OpenTabRequest.tab.value = "SEARCH" }
                    )
                    items == null -> Spinner(Modifier.padding(top = 30.dp))
                    else -> NoteList(items.orEmpty(), stats, actions)
                }
            } else {
                if (savedArticles.isEmpty()) {
                    com.example.btcwidget.ui.main.EmptyState(Glyphs.ListLine, tr("Your reading list is empty"), tr("Tap the bookmark inside any article to save it for reading offline."))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(savedArticles, key = { it.address }) { a ->
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Avatar(actions.profileOf(a.pubkey), a.pubkey, size = 20, onClick = { actions.openProfile(a.pubkey) })
                                    Text(
                                        actions.profileOf(a.pubkey)?.bestName ?: Profile.shortNpub(a.pubkey),
                                        color = EInk.Label,
                                        fontSize = TextSize.Caption,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f).padding(start = 6.dp)
                                    )
                                    Text(
                                        tr("Remove"),
                                        color = EInk.Caption,
                                        fontSize = TextSize.Caption,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.toggleSavedArticle(a) }.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                ArticleRow(
                                    article = a,
                                    onClick = { actions.openArticle(Nip19.Address(a.event.kind, a.pubkey, a.identifier)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
