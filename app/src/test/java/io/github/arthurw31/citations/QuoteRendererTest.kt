package io.github.arthurw31.citations

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class QuoteRendererTest {

    private val quote = Quote(
        "test", "Au milieu de l'hiver, j'apprenais enfin qu'il y avait en moi un été invincible.",
        "Albert Camus", "L'Été", null
    )

    private fun photo(light: Boolean): Bitmap {
        val w = 640
        val h = 1428
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val colors = if (light) intArrayOf(0xFFBFD9EE.toInt(), 0xFFF4E9D8.toInt(), 0xFFE9D2B0.toInt())
        else intArrayOf(0xFF0E1B2C.toInt(), 0xFF2E4A62.toInt(), 0xFF1B2A1E.toInt())
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        })
        val hill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (light) 0xFF9DB58A.toInt() else 0xFF0B140D.toInt() }
        c.drawCircle(w * 0.2f, h * 1.05f, h * 0.35f, hill)
        c.drawCircle(w * 0.9f, h * 1.1f, h * 0.4f, hill)
        return bmp
    }

    @Test
    fun rendersAtRequestedSize() {
        val out = QuoteRenderer.render(photo(false), quote, Appearance(), 1280, 2856)
        assertEquals(1280, out.width)
        assertEquals(2856, out.height)
    }

    @Test
    fun parsesBundledList() {
        val text = File("../citations/quotes.json").readText()
        val list = QuoteParser.parse(text)
        assertTrue(list.size >= 30)
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertTrue(list.all { it.explanation != null && it.example != null })
    }

    @Test
    fun frenchTypography() {
        assertEquals("a ; b : c ?", QuoteRenderer.frenchTypography("a ; b : c ?"))
    }

    @Test
    fun exportSamples() {
        val dir = System.getProperty("renderOut") ?: return
        File(dir).mkdirs()
        val cases = listOf(
            "sombre_defaut" to (false to Appearance()),
            "clair_auto" to (true to Appearance()),
            "sombre_italique_gauche" to (false to Appearance(font = FontChoice.SERIF_ITALIC, align = TextAlignChoice.LEFT, position = 0.55f, scrim = 0f)),
            "clair_ombrage_moyen" to (true to Appearance(color = ColorChoice.WHITE, scrim = 0f, halo = 0.45f)),
            "clair_ombrage_fort" to (true to Appearance(color = ColorChoice.WHITE, scrim = 0f, halo = 0.9f, hPosition = 0.4f)),
            "clair_moderne_grand" to (true to Appearance(font = FontChoice.SANS_LIGHT, size = 0.8f, color = ColorChoice.BLACK, scrim = 0.6f))
        )
        for ((name, c) in cases) {
            val out = QuoteRenderer.render(photo(c.first), quote, c.second, 640, 1428)
            File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
