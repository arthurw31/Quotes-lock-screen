package io.github.arthurw31.citations.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.arthurw31.citations.Appearance
import io.github.arthurw31.citations.ApplyResult
import io.github.arthurw31.citations.Engine
import io.github.arthurw31.citations.Schedule
import io.github.arthurw31.citations.HistoryEntry
import io.github.arthurw31.citations.Photo
import io.github.arthurw31.citations.Quote
import io.github.arthurw31.citations.QuoteRenderer
import io.github.arthurw31.citations.QuoteRepository
import io.github.arthurw31.citations.QuotesInfo
import io.github.arthurw31.citations.Screen
import io.github.arthurw31.citations.SettingsStore
import io.github.arthurw31.citations.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val appearance: Appearance = Appearance(),
    val quotesPerDay: Int = 2,
    val wordsPerDay: Int = 4,
    val hasPhoto: Boolean = false,
    val current: Quote? = null,
    val info: QuotesInfo? = null,
    val history: List<HistoryEntry> = emptyList(),
    /** Toutes les citations de la liste, pour retrouver l'explication d'une entrée de l'historique. */
    val quotesById: Map<String, Quote> = emptyMap(),
    val ratings: Map<String, Int> = emptyMap(),
    val quotesUrl: String = "",
    val batteryUnrestricted: Boolean = false,
    val lastApplied: Long = 0L,
    val busy: Boolean = false,
    val screenRatio: Float = 0.45f
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val settings = SettingsStore(ctx)
    private val repo = QuoteRepository(ctx)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    @Volatile private var previewPhoto: Bitmap? = null

    // Les demandes d'aperçu sont fusionnées : pendant un glissement, on dessine toujours
    // l'état le plus récent sans interrompre le dessin en cours (rendu fluide).
    private val previewRequests = MutableStateFlow(0)

    init {
        startPreviewLoop()
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.current() == null) {
                repo.next(Schedule.current(settings).second.word)
                Engine.markCurrentSlot(ctx)
            }
            refreshNow()
            requestPreview()
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshNow() }
    }

    private fun refreshNow() {
        val size = Screen.size(ctx)
        val pm = ctx.getSystemService(PowerManager::class.java)
        _ui.update {
            it.copy(
                appearance = settings.appearance(),
                quotesPerDay = settings.quotesPerDay,
                wordsPerDay = settings.wordsPerDay,
                hasPhoto = Photo.exists(ctx),
                current = repo.current(),
                info = repo.info(),
                history = repo.history(),
                quotesById = repo.quotes().associateBy { it.id },
                ratings = repo.ratings(),
                quotesUrl = settings.quotesUrl,
                batteryUnrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName),
                lastApplied = settings.lastApplied,
                screenRatio = size.width.toFloat() / size.height
            )
        }
    }

    fun updateAppearance(a: Appearance) {
        settings.saveAppearance(a)
        _ui.update { it.copy(appearance = a) }
        requestPreview()
    }

    private fun requestPreview() {
        previewRequests.update { it + 1 }
    }

    private fun startPreviewLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            previewRequests.collect {
                val state = _ui.value
                val quote = state.current ?: return@collect
                val size = Screen.size(ctx)
                val w = 600
                val h = w * size.height / size.width
                val photo = previewPhoto ?: (Photo.load(ctx, 4) ?: QuoteRenderer.placeholder(w, h)).also { previewPhoto = it }
                _preview.value = QuoteRenderer.render(photo, quote, state.appearance, w, h)
            }
        }
    }

    /** Déplacement au doigt : dx et dy en fraction de la taille de l'aperçu. */
    fun moveQuote(dx: Float, dy: Float) {
        val a = _ui.value.appearance
        _ui.update {
            it.copy(appearance = a.copy(
                hPosition = (a.hPosition + dx).coerceIn(0f, 1f),
                position = (a.position + dy).coerceIn(0.08f, 0.94f)
            ))
        }
        requestPreview()
    }

    /** Enregistre la position à la fin du glissement. */
    fun endMove() {
        settings.saveAppearance(_ui.value.appearance)
    }

    fun pickPhoto(uri: Uri) = work {
        if (Photo.import(ctx, uri)) {
            previewPhoto = null
            refreshNow()
            requestPreview()
            say("Photo enregistrée. Règle l'apparence, puis appuie sur Appliquer.")
        } else {
            say("Impossible de lire cette image.")
        }
    }

    fun applyNow() = work {
        report(Engine.apply(ctx))
    }

    fun nextQuote() = work {
        if (Photo.exists(ctx)) {
            report(Engine.next(ctx))
        } else {
            repo.next(Engine.manualType(ctx))
            Engine.markCurrentSlot(ctx)
        }
        refreshNow()
        requestPreview()
    }

    fun rate(id: String, value: Int) = work {
        val old = repo.ratings()[id] ?: 0
        repo.setRating(id, if (old == value) 0 else value)
        refreshNow()
    }

    fun sync() = work {
        when (val r = repo.sync(settings.quotesUrl)) {
            is SyncResult.Updated -> say("Liste mise à jour : ${r.count} citations.")
            is SyncResult.Unchanged -> say("Déjà à jour (${r.count} citations).")
            is SyncResult.Failed -> say("Mise à jour impossible : ${r.reason}.")
        }
        refreshNow()
    }

    /** Change le nombre de citations et de mots par jour, et affiche tout de suite l'élément du créneau en cours. */
    fun setPerDay(quotes: Int, words: Int) = work {
        settings.quotesPerDay = quotes
        settings.wordsPerDay = words
        Engine.tick(ctx, force = true)
        refreshNow()
        requestPreview()
    }

    fun setUrl(url: String) = work {
        settings.quotesUrl = url
        refreshNow()
        say("Adresse enregistrée.")
    }

    fun resetUrl() = setUrl(SettingsStore.DEFAULT_QUOTES_URL)

    suspend fun exportText(): String = withContext(Dispatchers.IO) { repo.exportFeedback() }

    private fun report(r: ApplyResult) {
        when (r) {
            ApplyResult.Done -> say("C'est fait. Verrouille ton téléphone pour voir le résultat.")
            ApplyResult.NoPhoto -> say("Choisis d'abord ta photo de fond.")
            ApplyResult.NoQuote -> say("Aucune citation disponible.")
            is ApplyResult.Failed -> say("Échec : ${r.reason}.")
        }
    }

    private fun say(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                refreshNow()
                _ui.update { it.copy(busy = false) }
            }
        }
    }
}
