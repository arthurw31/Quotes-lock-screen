package io.github.arthurw31.citations

import android.content.Context
import android.graphics.Typeface

enum class TextAlignChoice(val label: String) {
    LEFT("Gauche"), CENTER("Centre"), RIGHT("Droite")
}

enum class FontChoice(val label: String) {
    SERIF("Classique"),
    SERIF_ITALIC("Italique"),
    SANS_LIGHT("Moderne fin"),
    SANS("Moderne");

    fun typeface(): Typeface = when (this) {
        SERIF -> Typeface.create("serif", Typeface.NORMAL)
        SERIF_ITALIC -> Typeface.create("serif", Typeface.ITALIC)
        SANS_LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        SANS -> Typeface.create("sans-serif", Typeface.NORMAL)
    }
}

/** argb null signifie : couleur choisie automatiquement selon le fond. */
enum class ColorChoice(val label: String, val argb: Int?) {
    AUTO("Auto", null),
    WHITE("Blanc", 0xFFFFFFFF.toInt()),
    CREAM("Crème", 0xFFF3EBDD.toInt()),
    GOLD("Doré", 0xFFE6CC97.toInt()),
    BLACK("Noir", 0xFF161616.toInt())
}

enum class Frequency(val label: String, val hours: Int) {
    DAILY("Chaque jour", 24),
    TWICE("2 fois par jour", 12),
    FOUR("4 fois par jour", 6)
}

/** Réglages d'apparence. Les valeurs numériques vont de 0 à 1. */
data class Appearance(
    val position: Float = 0.68f,
    /** Centre horizontal du texte (0 = bord gauche, 1 = bord droit). */
    val hPosition: Float = 0.5f,
    val size: Float = 0.45f,
    val width: Float = 0.5f,
    val align: TextAlignChoice = TextAlignChoice.CENTER,
    val font: FontChoice = FontChoice.SERIF,
    val color: ColorChoice = ColorChoice.AUTO,
    val scrim: Float = 0.3f,
    val shadow: Boolean = true,
    val showAuthor: Boolean = true
)

class SettingsStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun appearance(): Appearance {
        val d = Appearance()
        return Appearance(
            position = p.getFloat("position", d.position),
            hPosition = p.getFloat("hPosition", d.hPosition),
            size = p.getFloat("size", d.size),
            width = p.getFloat("width", d.width),
            align = enumOr(p.getString("align", null), d.align),
            font = enumOr(p.getString("font", null), d.font),
            color = enumOr(p.getString("color", null), d.color),
            scrim = p.getFloat("scrim", d.scrim),
            shadow = p.getBoolean("shadow", d.shadow),
            showAuthor = p.getBoolean("showAuthor", d.showAuthor)
        )
    }

    fun saveAppearance(a: Appearance) {
        p.edit()
            .putFloat("position", a.position)
            .putFloat("hPosition", a.hPosition)
            .putFloat("size", a.size)
            .putFloat("width", a.width)
            .putString("align", a.align.name)
            .putString("font", a.font.name)
            .putString("color", a.color.name)
            .putFloat("scrim", a.scrim)
            .putBoolean("shadow", a.shadow)
            .putBoolean("showAuthor", a.showAuthor)
            .apply()
    }

    var frequency: Frequency
        get() = enumOr(p.getString("frequency", null), Frequency.DAILY)
        set(v) = p.edit().putString("frequency", v.name).apply()

    var quotesUrl: String
        get() = p.getString("quotesUrl", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_QUOTES_URL
        set(v) = p.edit().putString("quotesUrl", v.trim()).apply()

    var lastApplied: Long
        get() = p.getLong("lastApplied", 0L)
        set(v) = p.edit().putLong("lastApplied", v).apply()

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    companion object {
        /** HEAD désigne la branche par défaut du dépôt, quelle qu'elle soit. */
        const val DEFAULT_QUOTES_URL =
            "https://raw.githubusercontent.com/arthurw31/Quotes-lock-screen/HEAD/citations/quotes.json"
    }
}
