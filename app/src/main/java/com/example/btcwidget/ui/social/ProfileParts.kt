package com.example.btcwidget.ui.social

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.core.Content
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

// ---- Pieces of the profile screen ----

/** A round, outlined icon button, as X puts beside Follow (more, message, zap). */
@Composable
internal fun CircleIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, EInk.BodyStroke, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = EInk.Value, modifier = Modifier.size(19.dp)) }
}

/** Puts [text] on the clipboard and says so. */
internal fun copyToClipboard(context: android.content.Context, label: String, text: String, toast: String) {
    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    android.widget.Toast.makeText(context, toast, android.widget.Toast.LENGTH_SHORT).show()
}

/**
 * Opens Block Wallet to pay [pubkey] in one tap, and only Block Wallet: never BlueWallet or
 * another app that also handles `bitcoin:`. Tries, in order:
 * 1. `blockwallet://pay?…` (Block Wallet's own scheme);
 * 2. a BIP-21 `bitcoin:` link sent explicitly to whichever installed app is labelled Block Wallet;
 * 3. Block Wallet's launcher with the details as extras.
 * Without Block Wallet the payment link is copied. Used by the Store checkout.
 *
 * [amountSats], [memo] (what's bought) and [reference] (the listing's naddr) are optional;
 * [address] overrides the on-chain address found in the profile.
 */
internal fun openBlockWalletPay(
    context: android.content.Context,
    pubkey: String,
    profile: Profile?,
    amountSats: Long? = null,
    memo: String? = null,
    reference: String? = null,
    address: String? = null
) {
    val npub = com.example.btcwidget.nostr.core.Nip19.npub(pubkey)
    val name = profile?.bestName ?: Profile.shortNpub(pubkey)
    val lud16 = profile?.lud16?.trim().orEmpty()
    val btcAddress = address?.trim()?.takeIf { it.isNotEmpty() } ?: profile?.about?.let { about ->
        Regex("""\b(bc1[ac-hj-np-z02-9]{25,87}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\b""").find(about)?.value
    }.orEmpty()
    val blockUri = android.net.Uri.Builder()
        .scheme("blockwallet")
        .authority("pay")
        .appendQueryParameter("pubkey", pubkey)
        .appendQueryParameter("npub", npub)
        .appendQueryParameter("name", name)
        .apply {
            if (btcAddress.isNotEmpty()) appendQueryParameter("address", btcAddress)
            if (lud16.isNotEmpty()) appendQueryParameter("lud16", lud16)
            if (amountSats != null && amountSats > 0) appendQueryParameter("amount", amountSats.toString())
            memo?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("memo", it) }
            reference?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("ref", it) }
        }
        .build()
    val pm = context.packageManager
    val newTask = android.content.Intent.FLAG_ACTIVITY_NEW_TASK

    // 1. Block Wallet's own scheme, only if something actually handles it.
    val blockIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, blockUri).addFlags(newTask)
    if (blockIntent.resolveActivity(pm) != null && runCatching { context.startActivity(blockIntent) }.isSuccess) return

    // 2. BIP-21, addressed to the app named Block Wallet (so BlueWallet never catches it).
    val blockPackage = findBlockWalletPackage(context)
    if (blockPackage != null) {
        val btcAmount = amountSats?.takeIf { it > 0 }?.let { java.math.BigDecimal(it).movePointLeft(8).stripTrailingZeros().toPlainString() }
        val query = buildList {
            btcAmount?.let { add("amount=$it") }
            add("label=" + android.net.Uri.encode(name))
            memo?.takeIf { it.isNotBlank() }?.let { add("message=" + android.net.Uri.encode(it)) }
            if (lud16.isNotEmpty()) add("lightning=" + android.net.Uri.encode(lud16))
            add("nostr=" + android.net.Uri.encode(npub))
        }.joinToString("&")
        val bip21 = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("bitcoin:$btcAddress?$query"))
            .setPackage(blockPackage).addFlags(newTask)
        if (bip21.resolveActivity(pm) != null && runCatching { context.startActivity(bip21) }.isSuccess) return
        // 3. Just open Block Wallet with the details.
        pm.getLaunchIntentForPackage(blockPackage)?.apply {
            addFlags(newTask)
            data = blockUri
            putExtra("pubkey", pubkey); putExtra("npub", npub); putExtra("name", name)
            if (btcAddress.isNotEmpty()) putExtra("address", btcAddress)
            if (lud16.isNotEmpty()) putExtra("lud16", lud16)
            amountSats?.let { putExtra("amount", it) }
            memo?.let { putExtra("memo", it) }
        }?.let { if (runCatching { context.startActivity(it) }.isSuccess) return }
    }

    copyToClipboard(context, "Block Wallet payment URI", blockUri.toString(), tr("Block Wallet not installed yet — payment link copied"))
}

/** The installed app that calls itself Block Wallet (by its `bitcoin:` handler or launcher label). */
private fun findBlockWalletPackage(context: android.content.Context): String? {
    val pm = context.packageManager
    fun isBlockWallet(info: android.content.pm.ResolveInfo): Boolean {
        val pkg = info.activityInfo?.packageName ?: return false
        if (pkg == context.packageName) return false
        val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault("") + " " +
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("")
        return label.contains("block wallet", ignoreCase = true) || label.contains("blockwallet", ignoreCase = true) ||
            pkg.contains("blockwallet", ignoreCase = true)
    }
    val bitcoinHandlers = runCatching {
        pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("bitcoin:")), 0)
    }.getOrDefault(emptyList())
    bitcoinHandlers.firstOrNull(::isBlockWallet)?.let { return it.activityInfo.packageName }
    val launchers = runCatching {
        pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
    }.getOrDefault(emptyList())
    return launchers.firstOrNull(::isBlockWallet)?.activityInfo?.packageName
}

/** The small copy button at the end of a profile row. */
@Composable
private fun CopyButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Glyphs.CopyLine, contentDescription = label, tint = EInk.Caption, modifier = Modifier.size(16.dp)) }
}

/**
 * A detail under the bio (website, Lightning address, npub): an icon, the text, and a copy
 * button. Tapping the text does [onClick] (open the site, zap); the button copies it.
 */
@Composable
internal fun ProfileInfoRow(
    icon: ImageVector?,
    text: String,
    copyText: String,
    copiedToast: String,
    color: Color = EInk.Label,
    fontSize: Int = 14,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (icon != null) Icon(icon, contentDescription = null, tint = EInk.Caption, modifier = Modifier.padding(end = 6.dp).size(16.dp))
        Text(
            text, color = color, fontSize = fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(4.dp))
                .clickable { if (onClick != null) onClick() else copyToClipboard(context, copyText, copyText, copiedToast) }
                .padding(vertical = 4.dp)
        )
        CopyButton(tr("Copy")) { copyToClipboard(context, copyText, copyText, copiedToast) }
    }
}

/** The bio, with its links, #hashtags and @mentions tappable, as in a post. */
@Composable
internal fun ProfileBio(about: String, actions: NoteActions, modifier: Modifier = Modifier) {
    val segments = remember(about) { Content.parse(about.trim()) }
    val linkStyle = TextLinkStyles(SpanStyle(color = SettingsAccent))
    val text: AnnotatedString = buildAnnotatedString {
        for (seg in segments) when (seg) {
            is Content.Segment.Text -> append(seg.text)
            is Content.Segment.Link, is Content.Segment.Image, is Content.Segment.Video -> {
                val url = when (seg) { is Content.Segment.Link -> seg.url; is Content.Segment.Image -> seg.url; else -> (seg as Content.Segment.Video).url }
                withLink(LinkAnnotation.Clickable("url", linkStyle) { actions.openLink(url, null) }) {
                    append(url.removePrefix("https://").removePrefix("http://").removeSuffix("/").let { if (it.length > 40) it.take(38) + "…" else it })
                }
            }
            is Content.Segment.Hashtag -> withLink(LinkAnnotation.Clickable("tag", linkStyle) { actions.openHashtag(seg.tag) }) { append("#" + seg.tag) }
            is Content.Segment.ProfileRef -> withLink(LinkAnnotation.Clickable("p", linkStyle) { actions.openProfile(seg.pubkey) }) {
                append("@" + (actions.profileOf(seg.pubkey)?.bestName ?: Profile.shortNpub(seg.pubkey)))
            }
            is Content.Segment.NoteRef -> append(seg.raw)
            is Content.Segment.AddressRef -> append(seg.raw)
            is Content.Segment.Emoji -> append(":" + seg.code + ":")
        }
    }
    Text(text, color = EInk.Value, fontSize = 15.sp, lineHeight = 20.sp, modifier = modifier)
}

/** The "Pinned" label over a pinned note, with a pin icon as on X. */
@Composable
internal fun PinnedLabel() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 34.dp, top = 10.dp)) {
        Icon(Glyphs.Pin, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(14.dp))
        Text(tr("Pinned"), color = EInk.Caption, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
    }
}

// ---- Skeleton while a profile loads ----

/** A gentle fade in and out for the grey shapes standing in for what's loading. */
@Composable
internal fun skeletonAlpha(): Float {
    val pulse by rememberInfiniteTransition(label = "skeleton")
        .animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "pulse")
    return pulse
}

@Composable
private fun Bone(width: Dp?, height: Dp, alpha: Float, shape: Shape = RoundedCornerShape(6.dp), modifier: Modifier = Modifier) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(EInk.Track)
    )
}

/** The profile header before anything has loaded: banner, picture, name and bio as grey shapes. */
@Composable
internal fun ProfileHeaderSkeleton(topInset: Boolean) {
    val a = skeletonAlpha()
    Column(Modifier.fillMaxWidth()) {
        Box {
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = a }.background(EInk.Track)) {
                if (topInset) Spacer(Modifier.statusBarsPadding())
                Spacer(Modifier.fillMaxWidth().aspectRatio(3f))
            }
            Box(
                Modifier.padding(start = 14.dp).align(Alignment.BottomStart).offset(y = 36.dp)
                    .border(3.dp, EInk.Background, CircleShape).padding(3.dp)
            ) { Bone(70.dp, 70.dp, a, CircleShape) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 14.dp), horizontalArrangement = Arrangement.End) {
            Bone(104.dp, 36.dp, a, RoundedCornerShape(50))
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Spacer(Modifier.height(4.dp))
            Bone(150.dp, 20.dp, a)
            Bone(100.dp, 13.dp, a)
            Spacer(Modifier.height(2.dp))
            Bone(null, 13.dp, a)
            Bone(240.dp, 13.dp, a)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { Bone(90.dp, 13.dp, a); Bone(80.dp, 13.dp, a) }
        }
    }
}

/** Grey stand-ins for notes while the first ones load. */
@Composable
internal fun NotesSkeleton(rows: Int = 4) {
    val a = skeletonAlpha()
    Column {
        repeat(rows) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Bone(40.dp, 40.dp, a, CircleShape)
                Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bone(if (i % 2 == 0) 140.dp else 110.dp, 13.dp, a)
                    Bone(null, 12.dp, a)
                    Bone(if (i % 2 == 0) 200.dp else 160.dp, 12.dp, a)
                }
            }
        }
    }
}

// ---- The bar over the banner ----

/**
 * The bar at the top of a profile, as on X: a round back button floats over the banner; once
 * the banner scrolls away, the bar fills in (with the banner, dimmed) and shows the name and
 * how many posts. [collapse] goes 0..1 as the banner leaves; [showTitle] once the name has.
 */
@Composable
internal fun ProfileTopBar(
    profile: Profile?,
    pubkey: String,
    postsLine: String?,
    collapse: Float,
    showTitle: Boolean,
    onBack: () -> Unit
) {
    val banner = profile?.banner
    val onBanner = banner != null
    Box(Modifier.fillMaxWidth()) {
        // What fills in behind the bar as the banner scrolls away.
        Box(Modifier.matchParentSize().graphicsLayer { alpha = collapse }) {
            if (banner != null) {
                AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            } else Box(Modifier.fillMaxSize().background(EInk.Background))
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (onBanner) 0.45f else 0.45f * (1f - collapse)))
                    .clickable(onClickLabel = tr("Back"), onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Glyphs.Back, contentDescription = tr("Back"),
                    tint = if (onBanner || collapse < 0.5f) Color.White else EInk.Value,
                    modifier = Modifier.size(20.dp)
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showTitle,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                modifier = Modifier.padding(start = 14.dp).weight(1f)
            ) {
                Column {
                    Text(
                        profile?.bestName ?: Profile.shortNpub(pubkey),
                        color = if (onBanner) Color.White else EInk.Value, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (postsLine != null) Text(
                        postsLine, color = if (onBanner) Color.White.copy(alpha = 0.8f) else EInk.Caption, fontSize = 13.sp, maxLines = 1
                    )
                }
            }
        }
        if (!onBanner && collapse >= 1f) androidx.compose.material3.HorizontalDivider(
            thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
