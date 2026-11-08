package io.github.arthurw31.citations

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Quote(
    val id: String,
    val text: String,
    val author: String,
    val source: String?,
    val theme: String?,
    /** Explication facultative de la citation, et exemple concret. */
    val explanation: String? = null,
    val example: String? = null
)

data class HistoryEntry(
    val id: String,
    val text: String,
    val author: String,
    val source: String?,
    val shownAt: Long
)

data class QuotesInfo(
    val count: Int,
    val fromGithub: Boolean,
    val lastUpdate: Long,
    val lastCheck: Long,
    val lastError: String?
)

sealed class SyncResult {
    data class Updated(val count: Int) : SyncResult()
    data class Unchanged(val count: Int) : SyncResult()
    data class Failed(val reason: String) : SyncResult()
}

object QuoteParser {
    /** Lit le fichier JSON. Les entrées incomplètes ou en double sont ignorées. */
    fun parse(json: String): List<Quote> {
        val array = JSONObject(json).getJSONArray("quotes")
        val out = ArrayList<Quote>(array.length())
        val ids = HashSet<String>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val text = o.str("text")
            val author = o.str("author")
            if (text.isEmpty() || author.isEmpty()) continue
            val id = o.str("id").ifEmpty { "q" + text.hashCode().toUInt().toString(16) }
            if (!ids.add(id)) continue
            out += Quote(
                id, text, author,
                source = o.str("source").ifEmpty { null },
                theme = o.str("theme").ifEmpty { null },
                explanation = o.str("explanation").ifEmpty { null },
                example = o.str("example").ifEmpty { null }
            )
        }
        return out
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key).trim()
}

class QuoteRepository(context: Context) {
    private val ctx = context.applicationContext
    private val p = ctx.getSharedPreferences("quotes", Context.MODE_PRIVATE)
    private val cacheFile = File(ctx.filesDir, "quotes_cache.json")

    /** Liste téléchargée depuis GitHub si disponible, sinon liste intégrée à l'appli. */
    fun quotes(): List<Quote> {
        memo?.let { return it }
        val fromCache = runCatching { if (cacheFile.exists()) QuoteParser.parse(cacheFile.readText()) else null }
            .getOrNull()?.takeIf { it.isNotEmpty() }
        val list = fromCache ?: bundled()
        memo = list
        return list
    }

    private fun bundled(): List<Quote> = runCatching {
        ctx.assets.open("quotes.json").bufferedReader().use { QuoteParser.parse(it.readText()) }
    }.getOrDefault(emptyList())

    fun info(): QuotesInfo = QuotesInfo(
        count = quotes().size,
        fromGithub = cacheFile.exists(),
        lastUpdate = p.getLong("lastUpdate", 0L),
        lastCheck = p.getLong("lastCheck", 0L),
        lastError = p.getString("lastError", null)
    )

    /** Télécharge la liste. En cas d'échec, la liste déjà enregistrée reste utilisée. */
    suspend fun sync(url: String): SyncResult = withContext(Dispatchers.IO) {
        val result = try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                val etag = p.getString("etag", null)
                if (etag != null && cacheFile.exists() && p.getString("etagUrl", null) == url) {
                    setRequestProperty("If-None-Match", etag)
                }
            }
            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> SyncResult.Unchanged(quotes().size)
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val parsed = runCatching { QuoteParser.parse(body) }.getOrNull()
                        if (parsed.isNullOrEmpty()) {
                            SyncResult.Failed("le fichier en ligne est vide ou mal formé, l'ancienne liste est conservée")
                        } else {
                            val tmp = File(ctx.filesDir, "quotes_cache.tmp")
                            tmp.writeText(body)
                            tmp.renameTo(cacheFile)
                            memo = parsed
                            p.edit()
                                .putString("etag", conn.getHeaderField("ETag"))
                                .putString("etagUrl", url)
                                .putLong("lastUpdate", System.currentTimeMillis())
                                .apply()
                            SyncResult.Updated(parsed.size)
                        }
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> SyncResult.Failed("fichier introuvable (erreur 404), vérifie l'adresse")
                    else -> SyncResult.Failed("erreur du serveur ($code)")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            SyncResult.Failed("pas de connexion")
        }
        p.edit()
            .putLong("lastCheck", System.currentTimeMillis())
            .putString("lastError", (result as? SyncResult.Failed)?.reason)
            .apply()
        result
    }

    fun current(): Quote? {
        val id = p.getString("currentId", null) ?: return null
        quotes().firstOrNull { it.id == id }?.let { return it }
        // La citation n'est plus dans la liste : on garde celle de l'historique.
        return history().firstOrNull { it.id == id }?.let { Quote(it.id, it.text, it.author, it.source, null) }
    }

    /** Choisit une nouvelle citation : jamais la même, pas de répétition avant d'avoir tout vu, sans celles que tu n'aimes pas. */
    @Synchronized
    fun next(): Quote? {
        val all = quotes()
        if (all.isEmpty()) return null
        val ratings = ratings()
        val currentId = p.getString("currentId", null)
        var shown = p.getStringSet("shown", emptySet())!!.toMutableSet()
        val allowed = all.filter { ratings[it.id] != -1 && it.id != currentId }
        var pool = allowed.filter { it.id !in shown }
        if (pool.isEmpty()) {
            shown = mutableSetOf()
            pool = allowed
        }
        if (pool.isEmpty()) pool = all.filter { it.id != currentId }.ifEmpty { all }
        val q = pool.random()
        shown += q.id
        p.edit().putStringSet("shown", shown).putString("currentId", q.id).apply()
        addHistory(q)
        return q
    }

    // Historique

    fun history(): List<HistoryEntry> {
        val raw = p.getString("history", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                HistoryEntry(
                    o.getString("id"), o.getString("text"), o.getString("author"),
                    if (o.isNull("source")) null else o.optString("source"), o.getLong("at")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun addHistory(q: Quote) {
        val list = listOf(HistoryEntry(q.id, q.text, q.author, q.source, System.currentTimeMillis())) +
            history().take(MAX_HISTORY - 1)
        val a = JSONArray()
        list.forEach { e ->
            a.put(JSONObject().put("id", e.id).put("text", e.text).put("author", e.author)
                .put("source", e.source ?: JSONObject.NULL).put("at", e.shownAt))
        }
        p.edit().putString("history", a.toString()).apply()
    }

    // Avis : 1 = j'aime, -1 = je n'aime pas

    fun ratings(): Map<String, Int> {
        val raw = p.getString("ratings", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    fun setRating(id: String, value: Int) {
        val o = JSONObject(ratings())
        if (value == 0) o.remove(id) else o.put(id, value)
        p.edit().putString("ratings", o.toString()).apply()
    }

    // Changement planifié

    var slotKey: String?
        get() = p.getString("slotKey", null)
        set(v) = p.edit().putString("slotKey", v).apply()

    /** Texte prêt à coller dans la conversation avec Claude. */
    fun exportFeedback(): String {
        val ratings = ratings()
        val known = LinkedHashMap<String, Pair<String, String>>()
        history().forEach { known.putIfAbsent(it.id, it.text to it.author) }
        quotes().forEach { known.putIfAbsent(it.id, it.text to it.author) }
        val seenIds = history().map { it.id }.toSet()

        fun line(id: String): String {
            val (text, author) = known[id] ?: return "- ($id)"
            return "- « $text » ($author)"
        }

        val liked = ratings.filterValues { it > 0 }.keys
        val disliked = ratings.filterValues { it < 0 }.keys
        val neutral = seenIds.filter { it !in ratings }
        val date = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).format(Date())

        return buildString {
            appendLine("Mes retours sur les citations de mon écran de verrouillage (export du $date).")
            appendLine("Liste actuelle : ${quotes().size} citations.")
            appendLine()
            appendLine("J'ai aimé (${liked.size}) :")
            if (liked.isEmpty()) appendLine("- aucune pour l'instant")
            liked.forEach { appendLine(line(it)) }
            appendLine()
            appendLine("Je n'ai pas aimé (${disliked.size}) :")
            if (disliked.isEmpty()) appendLine("- aucune pour l'instant")
            disliked.forEach { appendLine(line(it)) }
            appendLine()
            appendLine("Vues sans avis (${neutral.size}) :")
            if (neutral.isEmpty()) appendLine("- aucune")
            neutral.forEach { appendLine(line(it)) }
        }.trimEnd()
    }

    companion object {
        private const val MAX_HISTORY = 300

        /** Partagé entre l'écran et les tâches de fond. */
        @Volatile private var memo: List<Quote>? = null
    }
}
