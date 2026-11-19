package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.foundation.background
import com.example.btcwidget.ui.main.screenBackground
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ListKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.core.PeopleList
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EInk.Accent, unfocusedBorderColor = EInk.BodyStroke,
    focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Accent
)

/**
 * Lists of people, like X's Lists (NIP-51 kind 30000): the user's own (a page in the side
 * bar, [owner] null) or someone else's (from their profile, with [onBack]).
 */
@Composable
fun ListsScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit, owner: String? = null, onBack: (() -> Unit)? = null) {
    val account by vm.account.collectAsState()
    val mine by vm.lists.collectAsState()
    val me = account?.pubkey
    val own = owner == null || owner == me
    val theirs by produceKept<List<PeopleList>?>(null, owner) { if (!own) value = vm.listsBy(owner) }
    LaunchedEffect(me, own) { if (own && me != null) vm.loadLists() }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().screenBackground()) {
        if (onBack != null) SocialTopBar(tr("Lists"), onBack)
        SocialScaffold(vm, navigate) { _ ->
            val lists = if (own) mine else theirs
            when {
                own && me == null -> {
                    EmptyNote(tr("Log in to make lists of people and read them as feeds."))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PillButton(tr("Log in")) { navigate(LoginKey) } }
                }
                lists == null -> Spinner(Modifier.padding(top = 30.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (own) item(key = "new") {
                        Row(
                            Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("New list")) { creating = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).clip(Radius.Small).background(EInk.Body), contentAlignment = Alignment.Center) {
                                Text("+", color = EInk.Value, fontSize = TextSize.Title)
                            }
                            Text(tr("New list"), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                    }
                    if (lists.isEmpty()) item(key = "empty") {
                        if (own) com.example.btcwidget.ui.main.EmptyState(
                            Glyphs.ListLine, tr("Make your first list"),
                            tr("Lists keep people together, a topic or a circle of friends, and read as a feed of just them. Lists are public on Nostr.")
                        ) else EmptyNote(tr("No lists."))
                    }
                    items(lists, key = { it.address }) { l -> ListRow(l, vm) { navigate(ListKey(l.owner, l.d)) } }
                }
            }
        }
    }
    if (creating) NameDialog("New list", "", onDismiss = { creating = false }) { name ->
        creating = false
        vm.createList(name) { navigate(ListKey(it.owner, it.d)) }
    }
}

@Composable
private fun ListRow(list: PeopleList, vm: SocialViewModel, onClick: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The first few faces, overlapping.
        Box(Modifier.width(44.dp + ((list.members.size.coerceIn(1, 3) - 1) * 14).dp)) {
            if (list.members.isEmpty()) Box(Modifier.size(44.dp).clip(Radius.Small).background(EInk.Body), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Glyphs.Menu, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(22.dp))
            }
            list.members.take(3).forEachIndexed { i, pk ->
                Box(Modifier.padding(start = (i * 14).dp)) { Avatar(profiles[pk], pk, size = 44) }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(list.title, color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                peopleCount(list.members.size) + (list.description?.let { " · $it" } ?: ""),
                color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun peopleCount(n: Int) = if (n == 1) "1 person" else "$n people"

/** One list: its posts (a feed of just these people) or its people, with editing for your own. */
@Composable
fun ListScreen(vm: SocialViewModel, owner: String, d: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val account by vm.account.collectAsState()
    val mine by vm.lists.collectAsState()
    val stats by vm.stats.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val isOwn = owner == account?.pubkey
    // Your own list follows your edits; someone else's is fetched once.
    val fetched by produceKept<Any?>(Unit, owner, d) { value = vm.peopleList(owner, d) }
    val list = (if (isOwn) mine?.firstOrNull { it.d == d } else null) ?: fetched as? PeopleList
    val feedKey = list?.members?.sorted()?.hashCode()
    val posts by produceKept<List<FeedItem>?>(null, feedKey) {
        value = null
        if (list != null) value = vm.listFeed(list)
    }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().screenBackground()) {
        SocialTopBar(list?.title ?: tr("List"), onBack)
        SocialScaffold(vm, navigate) { actions ->
            when {
                list == null && fetched == Unit -> Spinner(Modifier.padding(top = 30.dp))
                list == null -> EmptyNote(tr("This list couldn't be found on your relays."))
                else -> {
                    val header: @Composable () -> Unit = {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(list.title, color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
                            list.description?.let { Text(it, color = EInk.Label, fontSize = TextSize.Body, modifier = Modifier.padding(top = 4.dp)) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { navigate(ProfileKey(list.owner)) }) {
                                Avatar(profiles[list.owner], list.owner, size = 20)
                                Text(
                                    (profiles[list.owner]?.bestName ?: Profile.shortNpub(list.owner)) + " · " + peopleCount(list.members.size),
                                    color = EInk.Caption, fontSize = TextSize.Caption, modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                                PillButton(tr("Show on Home")) { vm.showListOnHome(list); onBack() }
                                if (isOwn) {
                                    PillButton(tr("Edit"), filled = false) { editing = true }
                                    PillButton(tr("Delete"), filled = false) { confirmDelete = true }
                                }
                            }
                        }
                        FeedTabs(listOf(tr("Posts"), tr("People")), tab) { tab = it }
                    }
                    if (tab == 0) {
                        val items = posts
                        NoteList(items.orEmpty(), stats, actions, header = {
                            header()
                            when {
                                list.members.isEmpty() -> EmptyNote(if (isOwn) tr("No one here yet. Add people from the People tab or from their profile.") else tr("No one in this list."))
                                items == null -> Spinner()
                                items.isEmpty() -> EmptyNote(tr("No recent posts from these people."))
                            }
                        })
                    } else LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "header") { header() }
                        if (isOwn) item(key = "add") {
                            Text(
                                tr("+ Add people"), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { adding = true }.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                        }
                        items(list.members, key = { it }) { pk ->
                            MemberRow(pk, profiles[pk], pk in following, onOpen = { navigate(ProfileKey(pk)) }, onRemove = if (isOwn) ({ vm.setInList(list, pk, false) }) else null)
                        }
                    }
                }
            }
        }
    }
    if (list != null && editing) EditListDialog(list, onDismiss = { editing = false }) { title, about ->
        editing = false
        vm.renameList(list, title, about)
    }
    if (list != null && confirmDelete) ConfirmDialog(
        title = tr("Delete “{0}”?", list.title),
        text = tr("The list is removed from your relays and your Home feeds. The people in it aren't told."),
        confirmLabel = tr("Delete"),
        onConfirm = { confirmDelete = false; vm.deleteList(list); onBack() },
        onDismiss = { confirmDelete = false }
    )
    if (list != null && adding) AddPeopleSheet(vm, list, onClose = { adding = false })
}

@Composable
private fun MemberRow(pubkey: String, profile: Profile?, isFollowing: Boolean, onOpen: () -> Unit, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profile, pubkey, size = 44)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            DisplayName(profile, pubkey, EInk.Value, 15.sp)
            Text(handleText(profile, pubkey) + if (isFollowing) tr(" · Following") else "", color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) Text(
            tr("Remove"), color = EInk.Value, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(Radius.Full).background(EInk.Track).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onRemove).padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Search people (as you type) and add them to [list]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPeopleSheet(vm: SocialViewModel, list: PeopleList, onClose: () -> Unit) {
    val mine by vm.lists.collectAsState()
    val current = mine?.firstOrNull { it.d == list.d } ?: list
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = vm.searchPeople(q)
        searching = false
    }
    BlockSheet(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(tr("Add to {0}", current.title), color = EInk.Value, fontSize = TextSize.Subtitle, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(tr("Search people")) }, colors = fieldColors,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            if (searching) Spinner()
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                results.forEach { p ->
                    val inList = p.pubkey in current.members
                    PersonRow(p, isFollowing = false, onClick = { vm.setInList(current, p.pubkey, !inList) }) {
                        PillButton(
                            text = if (inList) tr("Added") else tr("Add"),
                            filled = !inList,
                            onClick = { vm.setInList(current, p.pubkey, !inList) }
                        )
                    }
                }
            }
        }
    }
}

/** From a profile: tick the lists this person is in, or start a new one with them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(vm: SocialViewModel, pubkey: String, onClose: () -> Unit) {
    val lists by vm.lists.collectAsState()
    LaunchedEffect(Unit) { vm.loadLists() }
    var creating by remember { mutableStateOf(false) }
    val name = vm.profiles.collectAsState().value[pubkey]?.bestName ?: Profile.shortNpub(pubkey)
    BlockSheet(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(tr("Lists with {0}", name), color = EInk.Value, fontSize = TextSize.Subtitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            when (val l = lists) {
                null -> Spinner()
                else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    l.forEach { list ->
                        val inList = pubkey in list.members
                        Row(
                            Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.setInList(list, pubkey, !inList) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(list.title, color = EInk.Value, fontSize = TextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(peopleCount(list.members.size), color = EInk.Caption, fontSize = TextSize.Caption)
                            }
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(if (inList) EInk.Accent else EInk.Track),
                                contentAlignment = Alignment.Center
                            ) { if (inList) androidx.compose.material3.Icon(Glyphs.Check, contentDescription = null, tint = EInk.OnAccent, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            Text(
                tr("+ New list with {0}", name), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { creating = true }.padding(horizontal = 16.dp, vertical = 14.dp)
            )
            Text(tr("Lists are public on Nostr: anyone can see who's in them."), color = EInk.Caption, fontSize = TextSize.Caption, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    if (creating) NameDialog("New list", "", onDismiss = { creating = false }) { title ->
        creating = false
        vm.createList(title, firstMember = pubkey)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    BlockDialog(onDismiss = onDismiss, title = title, confirmLabel = tr("Create"), confirmEnabled = name.isNotBlank(), onConfirm = { onDone(name) }) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, placeholder = { Text(tr("Name, e.g. Bitcoin devs")) }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EditListDialog(list: PeopleList, onDismiss: () -> Unit, onDone: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(list.title) }
    var about by remember { mutableStateOf(list.description.orEmpty()) }
    BlockDialog(onDismiss = onDismiss, title = tr("Edit list"), confirmLabel = tr("Save"), confirmEnabled = name.isNotBlank(), onConfirm = { onDone(name, about) }) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, label = { Text(tr("Name")) }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(value = about, onValueChange = { about = it.take(280) }, label = { Text(tr("Description (optional)")) }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
    }
}
