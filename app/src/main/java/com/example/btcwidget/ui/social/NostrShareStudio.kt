package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Article
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Share cards: a note or article drawn as a picture (author, words, link QR code), offered
 * next to the plain link by [NoteShareSheet].
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
        canvas.drawText("BLOCK", contentLeft, cursorY, brandPaint)

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
                // Words, not symbols: the card is drawn, and the app uses no emoji.
                val parts = buildList {
                    if (zapsSats > 0) add("${"%,d".format(Locale.US, zapsSats)} sats tipped")
                    if (data.stats.likes > 0) add("${"%,d".format(Locale.US, data.stats.likes)} likes")
                    if (data.stats.reposts > 0) add("${"%,d".format(Locale.US, data.stats.reposts)} reposts")
                }
                append(parts.joinToString(" · "))
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
 * Share, for notes and articles: one sheet with the link (what Share always sent), or the
 * same as a picture card with the link attached, drawn on the phone.
 */
@Composable
fun NoteShareSheet(data: ShareStudioData, onDismiss: () -> Unit) {
    // The sheet is gone by the time an action runs, so the work can't hang off its scope.
    val app = LocalContext.current.applicationContext
    ActionSheet(
        onDismiss = onDismiss,
        actions = listOf(
            SheetAction(Glyphs.Link, tr("Share link")) { shareCardLink(app, data.shareUrl) },
            SheetAction(Glyphs.PhotoLine, tr("Share as image")) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    val file = withContext(Dispatchers.Default) {
                        runCatching {
                            val bmp = NostrShareCardRenderer.render(
                                data, ShareCardRatio.SQUARE, ShareCardTheme.CARBON,
                                showBlockStamp = data.blockStamp != null, showStats = data.stats != null, showQr = true
                            )
                            val dir = File(app.cacheDir, "shared").apply { mkdirs() }
                            File(dir, "nostr_card.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        }.getOrNull()
                    }
                    if (file == null) { Toast.makeText(app, tr("Couldn't make the image"), Toast.LENGTH_SHORT).show(); return@launch }
                    val uri = FileProvider.getUriForFile(app, "${app.packageName}.share", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, data.shareUrl)
                        clipData = ClipData.newRawUri(null, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    app.startActivity(Intent.createChooser(intent, tr("Share as image")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        )
    )
}

private fun shareCardLink(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, tr("Share link")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
