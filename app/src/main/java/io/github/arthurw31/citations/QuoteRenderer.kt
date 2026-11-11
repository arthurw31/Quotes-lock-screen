package io.github.arthurw31.citations

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Dessine la citation par-dessus la photo. Toutes les mesures sont proportionnelles
 * à la largeur de l'image, donc l'aperçu dans l'appli est fidèle au résultat final.
 */
object QuoteRenderer {

    private const val LIGHT_TEXT = 0xFFFAF8F4.toInt()
    private const val DARK_TEXT = 0xFF1C1B1A.toInt()

    /** Un bloc de texte : plusieurs lignes empilées (citation puis auteur, ou mot, traduction, définition). */
    private class Line(val layout: StaticLayout, val alpha: Int, val gapBefore: Float)

    private class Block(val lines: List<Line>, val textSize: Float) {
        val height: Float get() = lines.sumOf { (it.gapBefore + it.layout.height).toDouble() }.toFloat()
    }

    fun render(photo: Bitmap, quote: Quote, a: Appearance, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawCover(canvas, photo, w, h)

        val margin = w * lerp(0.16f, 0.06f, a.width)
        val textWidth = (w - 2 * margin).toInt()

        // Si le texte est très long, on réduit la taille pour qu'il reste discret.
        var size = w * lerp(0.032f, 0.072f, a.size)
        var block = layout(quote, a, size, textWidth)
        while (block.height > h * 0.40f && size > w * 0.022f) {
            size *= 0.92f
            block = layout(quote, a, size, textWidth)
        }

        val center = h * a.position.coerceIn(0.08f, 0.94f)
        val top = (center - block.height / 2f).coerceIn(h * 0.04f, max(h * 0.04f, h * 0.96f - block.height))

        // Position horizontale choisie au doigt, sans sortir de l'écran.
        val edge = w * 0.03f
        val left = (w * a.hPosition - textWidth / 2f).coerceIn(edge, max(edge, w - edge - textWidth))

        val backgroundLum = averageLuminance(out, left, top, left + textWidth, top + block.height)
        val textColor = a.color.argb ?: if (backgroundLum > 0.58f) DARK_TEXT else LIGHT_TEXT
        val lightText = luminance(textColor) > 0.5f

        if (a.scrim > 0.01f) drawScrim(canvas, w, top, block.height, block.textSize, lightText, a.scrim)
        if (a.halo > 0.01f) drawHalo(canvas, block, left, top, lightText, a.halo)

        val s = block.textSize
        for (line in block.lines) {
            val paint = line.layout.paint
            paint.color = (textColor and 0x00FFFFFF) or (line.alpha shl 24)
            if (a.shadow) {
                if (lightText) paint.setShadowLayer(s * 0.22f, 0f, s * 0.05f, 0x80000000.toInt())
                else paint.setShadowLayer(s * 0.25f, 0f, 0f, 0x66FFFFFF)
            }
        }

        canvas.save()
        canvas.translate(left, top)
        for (line in block.lines) {
            canvas.translate(0f, line.gapBefore)
            line.layout.draw(canvas)
            canvas.translate(0f, line.layout.height.toFloat())
        }
        canvas.restore()
        return out
    }

    /** Fond neutre utilisé dans l'aperçu tant qu'aucune photo n'est choisie. */
    fun placeholder(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                0xFF3A4A5C.toInt(), 0xFF1A2129.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    private fun layout(quote: Quote, a: Appearance, size: Float, width: Int): Block {
        val alignment = when (a.align) {
            TextAlignChoice.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignChoice.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignChoice.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val serif = a.font == FontChoice.SERIF || a.font == FontChoice.SERIF_ITALIC
        fun paint(sizePx: Float, face: Typeface, locale: Locale, spacing: Float) =
            TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = sizePx
                typeface = face
                textLocale = locale
                letterSpacing = spacing
            }

        if (quote.isWord) {
            // Carte « mot anglais » : le mot, sa traduction, puis la définition courte.
            val lines = ArrayList<Line>()
            val wordPaint = paint(size * 1.25f, a.font.typeface(), Locale.ENGLISH, if (serif) 0.01f else 0f)
            lines += Line(build(quote.text, wordPaint, width, alignment, 0f), 0xFF, 0f)
            val trPaint = paint(size * 0.72f, Typeface.create("serif", Typeface.ITALIC), Locale.FRENCH, 0f)
            lines += Line(build(frenchTypography(quote.author), trPaint, width, alignment, 0f), 0xE6, size * 0.35f)
            quote.definition?.let {
                val defPaint = paint(size * 0.5f, Typeface.create("sans-serif", Typeface.NORMAL), Locale.FRENCH, 0.02f)
                lines += Line(build(frenchTypography(it), defPaint, width, alignment, size * 0.08f), 0xC8, size * 0.55f)
            }
            return Block(lines, size)
        }

        val quotePaint = paint(size, a.font.typeface(), Locale.FRENCH, if (serif) 0.005f else 0f)
        val text = withGuillemets(frenchTypography(quote.text))
        val lines = arrayListOf(Line(build(text, quotePaint, width, alignment, size * 0.14f), 0xFF, 0f))
        if (a.showAuthor) {
            val authorPaint = paint(size * 0.5f, Typeface.create("sans-serif-medium", Typeface.NORMAL), Locale.FRENCH, 0.14f)
            lines += Line(build(quote.author.uppercase(Locale.FRENCH), authorPaint, width, alignment, 0f), 0xD9, size * 0.8f)
        }
        return Block(lines, size)
    }

    private fun build(text: String, paint: TextPaint, width: Int, alignment: Layout.Alignment, extra: Float) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
            .setAlignment(alignment)
            .setLineSpacing(extra, 1f)
            .setIncludePad(false)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    /** Espaces insécables avant ; : ! ? et apostrophe typographique, comme en typographie française. */
    fun frenchTypography(s: String): String = s
        .replace(Regex("\\s+([;!?])"), " $1")
        .replace(Regex("\\s+:"), " :")
        .replace('\'', '’')

    private fun withGuillemets(s: String): String {
        val t = s.trim()
        return if (t.startsWith("«")) t else "« $t »"
    }

    private fun drawCover(canvas: Canvas, photo: Bitmap, w: Int, h: Int) {
        val scale = max(w.toFloat() / photo.width, h.toFloat() / photo.height)
        val dw = photo.width * scale
        val dh = photo.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        canvas.drawBitmap(photo, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawScrim(canvas: Canvas, w: Int, top: Float, height: Float, textSize: Float, lightText: Boolean, strength: Float) {
        val pad = textSize * 2.4f
        val y0 = top - pad
        val y1 = top + height + pad
        val rgb = if (lightText) 0x000000 else 0xFFFFFF
        val alpha = (strength.coerceIn(0f, 1f) * (if (lightText) 0.6f else 0.65f) * 255).toInt()
        val solid = (alpha shl 24) or rgb
        val clear = rgb
        val paint = Paint().apply {
            shader = LinearGradient(0f, y0, 0f, y1,
                intArrayOf(clear, solid, solid, clear), floatArrayOf(0f, 0.32f, 0.68f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, y0, w.toFloat(), y1, paint)
    }

    /**
     * Ombrage doux qui épouse le bloc de texte : un rectangle arrondi flouté,
     * sombre derrière un texte clair (ou clair derrière un texte foncé).
     */
    private fun drawHalo(canvas: Canvas, block: Block, left: Float, top: Float, lightText: Boolean, strength: Float) {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        fun scan(layout: StaticLayout) {
            for (i in 0 until layout.lineCount) {
                minX = min(minX, layout.getLineLeft(i))
                maxX = max(maxX, layout.getLineRight(i))
            }
        }
        block.lines.forEach { scan(it.layout) }
        if (minX > maxX) return

        val s = block.textSize
        val padX = s * 1.1f
        val padY = s * 0.9f
        val rect = RectF(left + minX - padX, top - padY, left + maxX + padX, top + block.height + padY)
        val t = strength.coerceIn(0f, 1f)
        val alpha = (t * (if (lightText) 0.78f else 0.82f) * 255).toInt()
        val rgb = if (lightText) 0x000000 else 0xFFFFFF
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (alpha shl 24) or rgb
            maskFilter = BlurMaskFilter(s * 1.8f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(rect, s * 1.5f, s * 1.5f, paint)
    }

    /** Luminosité moyenne (0 = noir, 1 = blanc) de la zone sous le texte. */
    private fun averageLuminance(bmp: Bitmap, l: Float, t: Float, r: Float, b: Float): Float {
        val x0 = l.toInt().coerceIn(0, bmp.width - 1)
        val x1 = r.toInt().coerceIn(x0 + 1, bmp.width)
        val y0 = t.toInt().coerceIn(0, bmp.height - 1)
        val y1 = b.toInt().coerceIn(y0 + 1, bmp.height)
        val steps = 24
        var sum = 0f
        var n = 0
        for (i in 0 until steps) for (j in 0 until steps) {
            val x = min(x1 - 1, x0 + (x1 - x0) * i / steps)
            val y = min(y1 - 1, y0 + (y1 - y0) * j / steps)
            sum += luminance(bmp.getPixel(x, y))
            n++
        }
        return if (n == 0) 0f else sum / n
    }

    private fun luminance(c: Int): Float =
        (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
}
