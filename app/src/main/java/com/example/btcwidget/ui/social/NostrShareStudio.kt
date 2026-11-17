package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.btcwidget.PriceData
import com.example.btcwidget.PriceRepository
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Article
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Feature #2: Nostr Share Card Studio.
 *
 * Provides a studio-grade image renderer and interactive bottom sheet for sharing Nostr notes
 * and Long-Form Articles as high-resolution branded cards on Instagram/X/TikTok stories or feeds.
 */
enum class ShareCardRatio(val label: String, val width: Int, val height: Int, val aspect: Float) {
    SQUARE("1:1 Square", 1080, 1080, 1f),
    STORY("9:16 Story", 1080, 1920, 9f / 16f)
}

enum class ShareCardTheme(val label: String) {
    CARBON("Carbon Dark"),
    BITCOIN("Bitcoin Orange"),
    EINK("Minimal E-Ink")
}

@Immutable
data class ShareStudioData(
    val authorName: String,
    val authorHandle: String,
    val contentText: String,
    val timestampSeconds: Long,
    val shareUrl: String,
    val blockStamp: BlockStamp? = null,
    val stats: NoteStats? = null,
    val articleTitle: String? = null
)

object NostrShareCardRenderer {
    private const val BITCOIN_ORANGE = 0xFFF7931A.toInt()
    private const val BITCOIN_ORANGE_DARK = 0xFFD47A0E.toInt()
    private const val BG_DARK = 0xFF121417.toInt()
    private const val BG_DARK_CARD = 0xFF1B1E24.toInt()
    private const val BORDER_DARK = 0xFF2D323B.toInt()
    private const val TEXT_WHITE = 0xFFF0F3F6.toInt()
    private const val TEXT_MUTED = 0xFF8B949E.toInt()
    private const val TEXT_DIM = 0xFF656D76.toInt()

    fun render(
        data: ShareStudioData,
        ratio: ShareCardRatio,
        theme: ShareCardTheme,
        showBlockStamp: Boolean,
        showStats: Boolean,
        showQr: Boolean
    ): Bitmap {
        val w = ratio.width
        val h = ratio.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (theme) {
            ShareCardTheme.CARBON -> {
                bgPaint.color = BG_DARK
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            }
            ShareCardTheme.BITCOIN -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(0xFF26190B.toInt(), BG_DARK, BG_DARK),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            }
            ShareCardTheme.EINK -> {
                bgPaint.color = 0xFFF6F8FA.toInt()
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            }
        }

        // Inner Card Box
        val pad = if (ratio == ShareCardRatio.STORY) 72f else 64f
        val topPad = if (ratio == ShareCardRatio.STORY) 180f else 64f
        val cardRect = RectF(pad, topPad, w - pad, h - pad)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (theme) {
                ShareCardTheme.CARBON -> BG_DARK_CARD
                ShareCardTheme.BITCOIN -> 0xFF1E1E24.toInt()
                ShareCardTheme.EINK -> 0xFFFFFFFF.toInt()
            }
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = when (theme) {
                ShareCardTheme.CARBON -> BORDER_DARK
                ShareCardTheme.BITCOIN -> BITCOIN_ORANGE_DARK
                ShareCardTheme.EINK -> 0xFFD0D7DE.toInt()
            }
        }
        canvas.drawRoundRect(cardRect, 44f, 44f, cardPaint)
        canvas.drawRoundRect(cardRect, 44f, 44f, cardBorderPaint)

        // Text & Content colors
        val primaryTextColor = if (theme == ShareCardTheme.EINK) 0xFF1F2328.toInt() else TEXT_WHITE
        val secondaryTextColor = if (theme == ShareCardTheme.EINK) 0xFF656D76.toInt() else TEXT_MUTED

        var cursorY = cardRect.top + 54f
        val contentLeft = cardRect.left + 54f
        val contentRight = cardRect.right - 54f
        val contentWidth = (contentRight - contentLeft).toInt()

        // 2. Header: App branding & optional Block stamp
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (theme == ShareCardTheme.BITCOIN) BITCOIN_ORANGE else primaryTextColor
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("⚡ BLOCK", contentLeft, cursorY, brandPaint)

        if (showBlockStamp && data.blockStamp != null) {
            val stampText = data.blockStamp.compactLabel()
            val stampPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BITCOIN_ORANGE
                textSize = 28f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val stampWidth = stampPaint.measureText(stampText)
            val stampBgRect = RectF(contentRight - stampWidth - 32f, cursorY - 34f, contentRight, cursorY + 12f)
            val stampBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (theme == ShareCardTheme.EINK) 0x1A000000 else 0x28F7931A.toInt()
            }
            canvas.drawRoundRect(stampBgRect, 20f, 20f, stampBgPaint)
            canvas.drawText(stampText, stampBgRect.left + 16f, cursorY - 2f, stampPaint)
        }

        cursorY += 46f
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (theme == ShareCardTheme.EINK) 0xFFEAEEF2.toInt() else BORDER_DARK
            strokeWidth = 2f
        }
        canvas.drawLine(contentLeft, cursorY, contentRight, cursorY, divPaint)

        // 3. Author Row
        cursorY += 60f
        val authorNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextColor
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(data.authorName, contentLeft, cursorY, authorNamePaint)

        cursorY += 38f
        val authorHandlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextColor
            textSize = 28f
        }
        canvas.drawText(data.authorHandle, contentLeft, cursorY, authorHandlePaint)

        // 4. Article Title (if any)
        if (!data.articleTitle.isNullOrBlank()) {
            cursorY += 56f
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (theme == ShareCardTheme.BITCOIN) BITCOIN_ORANGE else primaryTextColor
                textSize = 44f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val titleLayout = StaticLayout.Builder.obtain(data.articleTitle, 0, data.articleTitle.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(3)
                .build()
            canvas.save()
            canvas.translate(contentLeft, cursorY)
            titleLayout.draw(canvas)
            canvas.restore()
            cursorY += titleLayout.height + 24f
        } else {
            cursorY += 44f
        }

        // 5. Post / Article Content Body
        val maxContentLines = if (ratio == ShareCardRatio.STORY) {
            if (data.articleTitle != null) 16 else 22
        } else {
            if (data.articleTitle != null) 7 else 11
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextColor
            textSize = 33f
            typeface = if (data.articleTitle != null) Typeface.SERIF else Typeface.DEFAULT
        }
        val bodyLayout = StaticLayout.Builder.obtain(data.contentText, 0, data.contentText.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxContentLines)
            .setLineSpacing(14f, 1.25f)
            .build()
        canvas.save()
        canvas.translate(contentLeft, cursorY)
        bodyLayout.draw(canvas)
        canvas.restore()

        // 6. Bottom Row: Stats & QR Code
        val qrSize = if (showQr) 140 else 0
        val bottomY = cardRect.bottom - 46f

        if (showQr) {
            val qrColor = if (theme == ShareCardTheme.EINK) android.graphics.Color.BLACK else BITCOIN_ORANGE
            val qrBitmap = generateQrBitmap(data.shareUrl, qrSize, qrColor)
            if (qrBitmap != null) {
                val qrLeft = contentRight - qrSize
                val qrTop = bottomY - qrSize
                canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)
                qrBitmap.recycle()
            }
        }

        // Stats text
        if (showStats && data.stats != null) {
            val statsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryTextColor
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val zapsSats = data.stats.zapMsat / 1000
            val statsText = buildString {
                if (zapsSats > 0) append("⚡ ${"%,d".format(Locale.US, zapsSats)} sats   ")
                if (data.stats.likes > 0) append("♥ ${"%,d".format(Locale.US, data.stats.likes)}   ")
                if (data.stats.reposts > 0) append("⟲ ${"%,d".format(Locale.US, data.stats.reposts)}")
            }.trim()
            if (statsText.isNotEmpty()) {
                canvas.drawText(statsText, contentLeft, bottomY - 14f, statsPaint)
            }
        }

        // App watermarking / link at the very bottom
        val linkText = data.shareUrl.removePrefix("https://").take(42)
        val linkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DIM
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.drawText(linkText, contentLeft, bottomY + 18f, linkPaint)

        return bitmap
    }

    private fun generateQrBitmap(content: String, size: Int, color: Int): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) color else android.graphics.Color.TRANSPARENT)
            }
        }
        bmp
    }.getOrNull()
}

/**
 * Interactive Share Studio Bottom Sheet.
 */
@Composable
fun NostrShareStudioSheet(
    data: ShareStudioData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ratio by remember { mutableStateOf(ShareCardRatio.SQUARE) }
    var cardTheme by remember { mutableStateOf(ShareCardTheme.CARBON) }
    var showBlockStamp by remember { mutableStateOf(data.blockStamp != null) }
    var showStats by remember { mutableStateOf(data.stats != null) }
    var showQr by remember { mutableStateOf(true) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }

    // Re-render preview on parameter changes
    LaunchedEffect(ratio, cardTheme, showBlockStamp, showStats, showQr) {
        isRendering = true
        withContext(Dispatchers.Default) {
            val bmp = NostrShareCardRenderer.render(data, ratio, cardTheme, showBlockStamp, showStats, showQr)
            previewBitmap = bmp
        }
        isRendering = false
    }

    fun shareImageFile() {
        val bmp = previewBitmap ?: return
        scope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "nostr_card.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, data.shareUrl)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, tr("Share Note Card"))
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    fun saveToGallery() {
        val bmp = previewBitmap ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val filename = "Block_Nostr_${System.currentTimeMillis()}.png"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Block")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Block").apply { mkdirs() }
                    val file = File(dir, filename)
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, tr("Saved to Gallery"), Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, tr("Couldn't save image"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareLinkOnly() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, data.shareUrl)
        val chooser = Intent.createChooser(send, tr("Share Link"))
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    BlockSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(tr("Share Card Studio"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Glyphs.Close, contentDescription = tr("Close"), tint = EInk.Caption, modifier = Modifier.size(18.dp))
                }
            }

            // Interactive Preview Box
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EInk.Body)
                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = tr("Preview"),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                if (isRendering) {
                    CircularProgressIndicator(color = Color(0xFFF7931A), modifier = Modifier.size(32.dp))
                }
            }

            // Controls Section 1: Aspect Ratio
            Text(tr("Aspect Ratio"), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareCardRatio.values().forEach { r ->
                    ChoicePill(tr(r.label), selected = ratio == r) { ratio = r }
                }
            }

            // Controls Section 2: Card Style
            Text(tr("Card Style"), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareCardTheme.values().forEach { t ->
                    ChoicePill(tr(t.label), selected = cardTheme == t) { cardTheme = t }
                }
            }

            // Controls Section 3: Toggles
            Text(tr("Elements"), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (data.blockStamp != null) {
                    ChoicePill(tr("Block Stamp"), selected = showBlockStamp) { showBlockStamp = !showBlockStamp }
                }
                if (data.stats != null) {
                    ChoicePill(tr("Stats"), selected = showStats) { showStats = !showStats }
                }
                ChoicePill(tr("QR Code"), selected = showQr) { showQr = !showQr }
            }

            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 16.dp))

            // Action Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF7931A))
                        .clickable(onClick = ::shareImageFile)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tr("Share Image Card"), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(EInk.Body)
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                        .clickable(onClick = ::saveToGallery)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tr("Save"), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(EInk.Body)
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                        .clickable(onClick = ::shareLinkOnly)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tr("Link"), color = EInk.Caption, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
