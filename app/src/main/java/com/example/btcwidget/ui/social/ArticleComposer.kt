package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import android.content.Context
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.btcwidget.ui.main.screenBackground
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.core.Article
import com.example.btcwidget.nostr.core.Markdown
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Phase 3: Full-screen In-App Markdown Article Composer & Drafts (`kind: 30023`).
 * Supports Write / Preview modes, Cover Image upload, Markdown formatting toolbar, and auto-saved local drafts.
 */
@Composable
internal fun ArticleComposerDialog(
    vm: SocialViewModel,
    initialArticle: Article? = null,
    onDismiss: () -> Unit,
    onPublished: (Article) -> Unit = {}
) {
    val context = LocalContext.current
    val account by vm.account.collectAsState()
    // The article being written is kept with the account's other drafts: per account, out of
    // Android backup, and deleted on logout.
    val draftKey = remember(account?.pubkey) { "${account?.pubkey ?: ""}/article" }

    val initialDraftJson = remember(initialArticle, draftKey) {
        if (initialArticle != null) null
        else {
            // A draft from before drafts were per account: it goes to whoever opens the composer next.
            val legacy = context.getSharedPreferences("article_draft_prefs", Context.MODE_PRIVATE)
            legacy.getString("current_draft", null)?.let { old ->
                if (com.example.btcwidget.nostr.Drafts.get(context, draftKey) == null) com.example.btcwidget.nostr.Drafts.put(context, draftKey, old)
                legacy.edit().clear().apply()
            }
            com.example.btcwidget.nostr.Drafts.get(context, draftKey)?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
    }

    var title by rememberSaveable {
        mutableStateOf(initialArticle?.title ?: initialDraftJson?.optString("title").orEmpty())
    }
    var summary by rememberSaveable {
        mutableStateOf(initialArticle?.summary ?: initialDraftJson?.optString("summary").orEmpty())
    }
    var coverImage by rememberSaveable {
        mutableStateOf(initialArticle?.image ?: initialDraftJson?.optString("image").orEmpty())
    }
    var tagsInput by rememberSaveable {
        mutableStateOf(
            initialArticle?.event?.tags
                ?.filter { it.size >= 2 && it[0] == "t" }
                ?.joinToString(", ") { it[1] }
                ?: initialDraftJson?.optString("tags").orEmpty()
        )
    }
    var content by rememberSaveable {
        mutableStateOf(initialArticle?.content ?: initialDraftJson?.optString("content").orEmpty())
    }
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var uploadingCover by remember { mutableStateOf(false) }
    var uploadingInline by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadingCover = true
            vm.uploadImage(uri) { att ->
                uploadingCover = false
                if (att != null) coverImage = att.url
            }
        }
    }

    val inlineImagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadingInline = true
            vm.uploadImage(uri) { att ->
                uploadingInline = false
                if (att != null) {
                    val snippet = "\n![](${att.url})\n"
                    content = (content + snippet).trimStart('\n')
                }
            }
        }
    }

    fun saveDraft(showToast: Boolean) {
        if (initialArticle != null) return
        val obj = JSONObject()
            .put("title", title)
            .put("summary", summary)
            .put("image", coverImage)
            .put("tags", tagsInput)
            .put("content", content)
        com.example.btcwidget.nostr.Drafts.put(context, draftKey, obj.toString())
        if (showToast) vm.say(tr("Draft saved"))
    }

    Dialog(
        onDismissRequest = {
            if (!publishing) {
                if (title.isNotBlank() || content.isNotBlank()) saveDraft(showToast = false)
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = EInk.Background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Top Bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !publishing) {
                                if (title.isNotBlank() || content.isNotBlank()) saveDraft(showToast = false)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Glyphs.Close, contentDescription = tr("Close"), tint = EInk.Value, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        if (initialArticle != null) tr("Edit article") else tr("Write article"),
                        color = EInk.Value,
                        fontSize = TextSize.Subtitle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    if (initialArticle == null) {
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .clip(Radius.Full)
                                .background(EInk.Body)
                                .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { saveDraft(showToast = true) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(tr("Save draft"), color = EInk.Value, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold)
                        }
                    }
                    val canPublish = !publishing && title.isNotBlank() && content.isNotBlank() && account != null
                    Box(
                        Modifier
                            .clip(Radius.Full)
                            .background(if (canPublish) EInk.Accent else EInk.Body)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = canPublish) {
                                val me = account?.pubkey ?: return@clickable
                                publishing = true
                                val hashtags = tagsInput.split(',', ' ')
                                    .map { it.trim().removePrefix("#") }
                                    .filter { it.isNotBlank() }
                                val unsigned = Article.create(
                                    pubkey = me,
                                    title = title,
                                    content = content,
                                    summary = summary.ifBlank { null },
                                    image = coverImage.ifBlank { null },
                                    tags = hashtags,
                                    identifier = initialArticle?.identifier
                                )
                                vm.publishArticle(unsigned) { published ->
                                    publishing = false
                                    if (published != null) {
                                        if (initialArticle == null) {
                                            com.example.btcwidget.nostr.Drafts.remove(context, draftKey)
                                        }
                                        onPublished(published)
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (publishing) tr("Publishing…") else tr("Publish"),
                            color = if (canPublish) Color.Black else EInk.Caption,
                            fontSize = TextSize.Caption,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

                // Write / Preview Mode Toggle + Markdown Formatting Toolbar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(EInk.Body)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReaderChip(
                            label = tr("Write"),
                            selected = !previewMode,
                            fontFamily = FontFamily.SansSerif,
                            onClick = { previewMode = false }
                        )
                        ReaderChip(
                            label = tr("Preview"),
                            selected = previewMode,
                            fontFamily = FontFamily.SansSerif,
                            onClick = { previewMode = true }
                        )
                    }
                    if (!previewMode) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MarkdownToolButton("H1") { content += (if (content.endsWith("\n") || content.isEmpty()) "" else "\n") + "# " }
                            MarkdownToolButton("H2") { content += (if (content.endsWith("\n") || content.isEmpty()) "" else "\n") + "## " }
                            MarkdownToolButton("B") { content += "**bold**" }
                            MarkdownToolButton("I") { content += "*italic*" }
                            MarkdownToolButton("“”") { content += (if (content.endsWith("\n") || content.isEmpty()) "" else "\n") + "> " }
                            MarkdownToolButton("<>") { content += "\n```\ncode\n```\n" }
                            MarkdownToolButton("🔗") { content += "[title](https://)" }
                            MarkdownToolButton(if (uploadingInline) "…" else "🖼️") {
                                if (!uploadingInline) {
                                    inlineImagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

                if (previewMode) {
                    val previewBlocks = remember(content) { Markdown.parse(content) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp)
                    ) {
                        if (coverImage.isNotBlank()) {
                            AsyncImage(
                                model = coverImage,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            )
                        }
                        Text(
                            title.ifBlank { tr("Untitled article") },
                            color = EInk.Value,
                            fontSize = TextSize.Title,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        if (summary.isNotBlank()) {
                            Text(
                                summary,
                                color = EInk.Label,
                                fontSize = TextSize.Body,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 8.dp))
                        previewBlocks.forEach { b ->
                            MarkdownBlock(b, actions = null, navigate = {})
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(160) },
                            label = { Text(tr("Article title")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it.take(320) },
                            label = { Text(tr("Short summary / subtitle (optional)")) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = coverImage,
                                onValueChange = { coverImage = it.trim() },
                                label = { Text(tr("Cover image URL (optional)")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier
                                    .clip(Radius.Small)
                                    .background(EInk.Body)
                                    .border(0.5.dp, EInk.BodyStroke, Radius.Small)
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !uploadingCover) {
                                        coverPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    if (uploadingCover) tr("Uploading…") else tr("Upload"),
                                    color = EInk.Accent,
                                    fontSize = TextSize.Caption,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        OutlinedTextField(
                            value = tagsInput,
                            onValueChange = { tagsInput = it.take(160) },
                            label = { Text(tr("Topics (comma-separated, e.g. bitcoin, nostr)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text(tr("Article body (Markdown supported)")) },
                            placeholder = { Text(tr("# Heading\n\nWrite your story in Markdown…")) },
                            minLines = 14,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownToolButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(Radius.Small)
            .screenBackground()
            .border(0.5.dp, EInk.BodyStroke, Radius.Small)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = EInk.Value, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold)
    }
}
