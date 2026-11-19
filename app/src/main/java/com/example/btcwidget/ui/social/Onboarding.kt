package com.example.btcwidget.ui.social

import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.btcwidget.ui.main.screenBackground
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.AmberSigner
import com.example.btcwidget.nostr.core.HomeFeed
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

// ---- The first screen: a welcome, not a manual ----

/**
 * The first screen for someone not logged in: a friendly hello, one button to join, and one for
 * people who already have a Nostr account (the ways to log in are in a sheet). No jargon here:
 * "Nostr" is one small line at the bottom.
 */
@Composable
fun LandingScreen(
    vm: SocialViewModel,
    onCreate: () -> Unit,
    onNsec: () -> Unit,
    onBunker: () -> Unit,
    onLoggedIn: () -> Unit,
    onBrowse: () -> Unit,
    onOpenRules: () -> Unit
) {
    var signIn by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().screenBackground().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BlockLogo(Modifier.size(40.dp))
        WelcomeArt(Modifier.fillMaxWidth().height(250.dp).padding(top = 8.dp))
        Text(
            tr("Your people.") + "\n" + tr("Your conversations."),
            color = EInk.Value, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold
        )
        Text(
            tr("Share what's on your mind and follow people you like. No ads, and nobody can take your account away."),
            color = EInk.Label, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp)
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(28.dp))
        BigButton(tr("Create account"), filled = true, onClick = onCreate)
        Spacer(Modifier.height(10.dp))
        BigButton(tr("I already have an account"), filled = false) { signIn = true }
        Text(
            tr("Look around first"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).clip(RoundedCornerShape(50))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onBrowse).padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Text(
            tr("Built on Nostr, an open network. By signing up you agree to the Community rules."),
            color = EInk.Caption, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Community rules"), onClick = onOpenRules).padding(vertical = 6.dp)
        )
    }
    if (signIn) SignInSheet(
        vm, onDismiss = { signIn = false },
        onNsec = { signIn = false; onNsec() }, onBunker = { signIn = false; onBunker() }, onLoggedIn = onLoggedIn
    )
}

/**
 * Faces and chat bubbles drifting gently: what Block is for, at a glance. Plain shapes and
 * emoji only (no photos, nothing to load), so it's there at once and works offline.
 */
@Composable
private fun WelcomeArt(modifier: Modifier = Modifier) {
    val drift = androidx.compose.animation.core.rememberInfiniteTransition(label = "welcome_drift")
    val t by drift.animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(6_000, easing = androidx.compose.animation.core.LinearEasing)
        ), label = "t"
    )
    // Each piece bobs a few dp on its own phase.
    fun bob(phase: Float) = (kotlin.math.sin((t + phase) * 2 * Math.PI) * 4).toFloat().dp
    BoxWithConstraints(modifier) {
        val w = maxWidth
        Face("🙂", listOf(Color(0xFFFFB199), Color(0xFFFF4D6D)), 70.dp, Modifier.offset(w * 0.04f, 30.dp + bob(0f)))
        Face("😎", listOf(Color(0xFFA1C4FD), Color(0xFFC2E9FB)), 54.dp, Modifier.offset(w * 0.78f, 12.dp + bob(0.3f)))
        Face("🌿", listOf(Color(0xFFD4FC79), Color(0xFF96E6A1)), 60.dp, Modifier.offset(w * 0.74f, 168.dp + bob(0.55f)))
        Face("🎧", listOf(Color(0xFFFBC2EB), Color(0xFFA6C1EE)), 46.dp, Modifier.offset(w * 0.08f, 180.dp + bob(0.8f)))
        Bubble(tr("Morning! Coffee first ☕"), mine = false, Modifier.offset(w * 0.24f, 44.dp + bob(0.15f)))
        Bubble(tr("Just joined, hi everyone 👋"), mine = true, Modifier.offset(w * 0.28f, 108.dp + bob(0.4f)))
        Bubble(tr("Welcome! 🎉"), mine = false, Modifier.offset(w * 0.27f, 190.dp + bob(0.65f)))
        Text("❤️", fontSize = 18.sp, modifier = Modifier.offset(w * 0.86f, 96.dp + bob(0.9f)))
        Text("✨", fontSize = 18.sp, modifier = Modifier.offset(0.dp, 132.dp + bob(0.2f)))
    }
}

@Composable
private fun Face(emoji: String, colors: List<Color>, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(
        modifier.size(size).shadow(10.dp, CircleShape).clip(CircleShape).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = (size.value * 0.38f).sp) }
}

@Composable
private fun Bubble(text: String, mine: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Text(
        text, color = if (mine) EInk.OnAccent else EInk.Value, fontSize = 13.sp, maxLines = 1,
        modifier = modifier.shadow(8.dp, shape).clip(shape)
            .background(if (mine) EInk.Accent else EInk.Body)
            .then(if (mine) Modifier else Modifier.border(1.dp, EInk.BodyStroke, shape))
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

/** "I already have an account": the ways in, each in plain words, the safest first. */
@Composable
private fun SignInSheet(vm: SocialViewModel, onDismiss: () -> Unit, onNsec: () -> Unit, onBunker: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val hasSigner = remember { AmberSigner.isInstalled(context) }
    BlockSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(tr("Welcome back"), color = EInk.Value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("Use the Nostr account you already have, from any app."),
                color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            SignInOption(
                Glyphs.Shield, tr("Signer app"), badge = tr("Safest"),
                body = if (hasSigner) tr("Amber keeps your key; Block just asks it to sign.")
                    else tr("Amber keeps your key; Block just asks it to sign. Tap to get Amber.")
            ) {
                if (hasSigner) { onDismiss(); vm.loginWithAmber(onLoggedIn) } else uriHandler.openUri("https://github.com/greenart7c3/Amber")
            }
            SignInOption(Glyphs.Lock, tr("Paste your key"), body = tr("The private key that starts with nsec1… Stored encrypted on this phone only."), onClick = onNsec)
            SignInOption(Glyphs.Server, tr("Remote signer"), body = tr("A bunker:// link from nsec.app or similar."), divider = false, onClick = onBunker)
        }
    }
}

@Composable
private fun SignInOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String,
    badge: String? = null, divider: Boolean = true, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = title, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Body), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (badge != null) Text(
                    badge, color = EInk.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(EInk.Accent).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(body, color = EInk.Label, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
    if (divider) androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** A full-width pill: [filled] in the text colour (white on dark, black on light), as on X; otherwise outlined. */
@Composable
private fun BigButton(text: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(if (filled) EInk.Value.copy(alpha = if (enabled) 1f else 0.4f) else Color.Transparent, shape)
            .then(if (filled) Modifier else Modifier.border(1.dp, EInk.BodyStroke, shape))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) EInk.Background else EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- Signing up: two short steps ----

/**
 * Signing up: "What should we call you?" (creates the account; nothing to copy or write down
 * yet: Home asks to save the key from tomorrow), then interests and people to follow on one
 * page. The second step can be skipped.
 */
@Composable
fun OnboardingScreen(vm: SocialViewModel, onFinish: () -> Unit) {
    val account by vm.account.collectAsState()
    var step by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    // Back: to the name step; from there, once the account exists, on into Block.
    androidx.activity.compose.BackHandler(enabled = step > 0 || account != null) { if (step > 0) step-- else onFinish() }
    Column(Modifier.fillMaxSize().screenBackground().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(64.dp))
            StepDots(count = 2, current = step, modifier = Modifier.weight(1f))
            Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                if (step == 1) Text(
                    tr("Skip"), color = EInk.Label, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button) { finish(vm, name, emptySet(), onFinish) }.padding(10.dp)
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                0 -> NameStep(vm, name, onName = { name = it }) { step = 1 }
                else -> InterestsStep(vm) { topics -> finish(vm, name, topics, onFinish) }
            }
        }
    }
}

/** Into Block: each topic becomes a Home feed, and Home says hello once. */
private fun finish(vm: SocialViewModel, name: String, topics: Set<String>, onFinish: () -> Unit) {
    topics.forEach { vm.addFeed(HomeFeed.Hashtag(it), select = false) }
    vm.welcome.value = name.trim().ifEmpty { tr("friend") }
    onFinish()
}

/** Where you are: a dot per step, the current one a short bar. */
@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = tr("Step {0} of {1}", current + 1, count) }, horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val on = i == current
            val width by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 7.dp, label = "dot")
            Box(Modifier.padding(horizontal = 3.dp).height(7.dp).width(width).clip(RoundedCornerShape(50)).background(if (on) EInk.Value else EInk.Track))
        }
    }
}

@Composable
private fun NameStep(vm: SocialViewModel, name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    val account by vm.account.collectAsState()
    // The photo is picked now and uploaded once the account exists (uploads are signed).
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    var creating by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(tr("What should we call you?"), color = EInk.Value, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
            Text(
                tr("This is how you'll appear to people. You can change it any time."),
                color = EInk.Label, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp)
            )
            Box(
                Modifier.padding(top = 28.dp).size(96.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(EInk.Body)
                    .border(1.5.dp, EInk.BodyStroke, CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Add photo")) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                val p = photo
                if (p != null) AsyncImage(p, tr("Profile picture"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Glyphs.PhotoLine, contentDescription = null, tint = EInk.Label, modifier = Modifier.size(24.dp))
                    Text(tr("Add photo"), color = EInk.Label, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { onName(it.take(50)) }, label = { Text(tr("Name")) }, singleLine = true,
                colors = fieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 22.dp)
            )
            Text(tr("Optional, but it helps friends find you."), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Box(Modifier.padding(vertical = 18.dp)) {
            BigButton(if (creating) tr("Just a moment…") else tr("Continue"), filled = true, enabled = !creating) {
                creating = true
                val fresh = account == null
                // A new account (no key to show: Home reminds later); or the one just made elsewhere.
                if (fresh && vm.createAccount(name) == null) { creating = false; return@BigButton }
                if (!fresh && name.isNotBlank()) vm.saveProfile(name, "", null) {}
                // The photo goes up in the background; the profile picks it up when it's there.
                photo?.let { uri -> vm.uploadImage(uri) { a -> if (a != null) vm.saveProfile(name, "", a.url) {} } }
                creating = false
                onNext()
            }
        }
    }
}

/** Topics to start with (a Home feed each) and the people posting about them; general ones first. */
private val TOPICS = listOf(
    "news" to "News", "music" to "Music", "food" to "Food", "sports" to "Sports", "travel" to "Travel",
    "tech" to "Tech", "art" to "Art", "photography" to "Photography", "memes" to "Memes", "gaming" to "Gaming",
    "movies" to "Movies", "books" to "Books", "science" to "Science", "pets" to "Pets", "fitness" to "Fitness",
    "thailand" to "Thailand", "bitcoin" to "Bitcoin"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsStep(vm: SocialViewModel, onDone: (Set<String>) -> Unit) {
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(emptyList<String>()) }
    val following by vm.following.collectAsState()
    // Who to follow follows the picks (a moment after the last tap); with none, well-known people.
    val people by produceState<List<Profile>?>(null, selected) {
        value = null
        kotlinx.coroutines.delay(if (selected.isEmpty()) 0 else 600)
        value = if (selected.isEmpty()) vm.starterPeople() else vm.topicPeople(selected)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(tr("What are you into?"), color = EInk.Value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
            Text(tr("Pick a few and we'll fill your Home with them."), color = EInk.Label, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TOPICS.forEach { (tag, label) ->
                    val on = tag in selected
                    TopicChip(tr(label), on) { selected = if (on) selected - tag else selected + tag }
                }
            }
            val list = people
            val notYet = list.orEmpty().filter { it.pubkey !in following }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("People you might like"), color = EInk.Value, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (notYet.size > 1) Text(
                    tr("Follow all"), color = EInk.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    // One update of the follow list, not one per person.
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.followAll(notYet.map { it.pubkey }) }.padding(8.dp)
                )
            }
            when {
                list == null -> repeat(3) { PersonSkeleton() }
                list.isEmpty() -> Text(tr("Couldn't find anyone right now. You can follow people later from Search."), color = EInk.Caption, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                else -> list.take(12).forEach { p ->
                    val on = p.pubkey in following
                    PersonRow(p, isFollowing = on, onClick = { vm.follow(p.pubkey, !on) }) { FollowPill(on) { vm.follow(p.pubkey, !on) } }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.padding(vertical = 18.dp)) { BigButton(tr("Let's go"), filled = true) { onDone(selected.toSet()) } }
    }
}

@Composable
private fun TopicChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        (if (on) "✓ " else "") + label, color = if (on) EInk.Background else EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(shape).background(if (on) EInk.Value else Color.Transparent)
            .border(1.dp, if (on) EInk.Value else EInk.BodyStroke, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Checkbox) { onClick() }
            .semantics { selected = on }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun FollowPill(following: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        if (following) tr("Following") else tr("Follow"), color = if (following) EInk.Value else EInk.Background, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(shape).background(if (following) Color.Transparent else EInk.Value)
            .border(1.dp, if (following) EInk.BodyStroke else EInk.Value, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp)
    )
}

@Composable
private fun PersonSkeleton() {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(EInk.Track))
        Column(Modifier.padding(start = 12.dp)) {
            Box(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(EInk.Track))
            Box(Modifier.padding(top = 6.dp).width(180.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(EInk.Track))
        }
    }
}

/** Home, right after signing up: hello, and what to do first. Once, until closed. */
@Composable
internal fun FirstHomeWelcome(vm: SocialViewModel) {
    val name by vm.welcome.collectAsState()
    androidx.compose.animation.AnimatedVisibility(visible = name != null) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp))
                .background(EInk.Body).border(1.dp, EInk.BodyStroke, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("👋", fontSize = 26.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(tr("Welcome, {0}!", name.orEmpty()), color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr("Your Home fills with posts from people you follow. Say hi with your first post."),
                    color = EInk.Label, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                Glyphs.CloseLine, contentDescription = tr("Close"), tint = EInk.Caption,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.welcome.value = null }.padding(6.dp)
            )
        }
    }
}

/**
 * Block's logo, as on its app icon: a cube in three shades of bitcoin orange on a dark tile.
 */
@Composable
fun BlockLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(22)).background(Color(0xFF1F2024))) {
        // The icon's 108-unit grid, its visible part (10..98) scaled to this size.
        val k = size.minDimension / 88f
        fun p(x: Float, y: Float) = androidx.compose.ui.geometry.Offset((x - 10f) * k, (y - 10f) * k)
        fun face(color: Color, vararg pts: Pair<Float, Float>) {
            val path = androidx.compose.ui.graphics.Path().apply {
                pts.forEachIndexed { i, (x, y) -> val o = p(x, y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                close()
            }
            drawPath(path, color)
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * k, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        face(Color(0xFFFFB347), 54f to 27f, 78f to 40.5f, 54f to 54f, 30f to 40.5f)
        face(Color(0xFFF7931A), 30f to 40.5f, 54f to 54f, 54f to 81f, 30f to 67.5f)
        face(Color(0xFFC2680A), 54f to 54f, 78f to 40.5f, 78f to 67.5f, 54f to 81f)
    }
}

