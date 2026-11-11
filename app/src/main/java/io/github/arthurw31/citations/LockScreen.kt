package io.github.arthurw31.citations

import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.hardware.display.DisplayManager
import android.net.Uri
import android.util.Size
import android.view.Display
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Ta photo de fond, copiée dans l'appli (aucune permission d'accès aux fichiers nécessaire). */
object Photo {
    private const val MAX_SIDE = 3200

    fun file(ctx: Context) = File(ctx.filesDir, "photo.jpg")

    fun exists(ctx: Context) = file(ctx).exists()

    fun import(ctx: Context, uri: Uri): Boolean = runCatching {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val f = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }
        val tmp = File(ctx.filesDir, "photo.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()
        tmp.renameTo(file(ctx))
    }.getOrDefault(false)

    fun load(ctx: Context, sampleSize: Int = 1): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }
}

object Screen {
    /** Résolution réelle de l'écran, en portrait. */
    fun size(ctx: Context): Size {
        val display = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        var w = min(mode.physicalWidth, mode.physicalHeight)
        var h = max(mode.physicalWidth, mode.physicalHeight)
        if (w > 1440) {
            h = h * 1440 / w
            w = 1440
        }
        return Size(w, h)
    }
}

sealed class ApplyResult {
    data object Done : ApplyResult()
    data object NoPhoto : ApplyResult()
    data object NoQuote : ApplyResult()
    data class Failed(val reason: String) : ApplyResult()
}

/** Toute la logique : choisir la citation et l'installer sur l'écran de verrouillage. */
object Engine {

    /** Dessine la citation actuelle sur la photo et l'installe sur l'écran de verrouillage uniquement. */
    fun apply(ctx: Context): ApplyResult {
        val repo = QuoteRepository(ctx)
        val quote = repo.current() ?: repo.next(Schedule.current(SettingsStore(ctx)).second.word) ?: return ApplyResult.NoQuote
        val photo = Photo.load(ctx) ?: return ApplyResult.NoPhoto
        return try {
            val size = Screen.size(ctx)
            val settings = SettingsStore(ctx)
            val image = QuoteRenderer.render(photo, quote, settings.appearance(), size.width, size.height)
            photo.recycle()
            val wm = WallpaperManager.getInstance(ctx)
            if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) {
                return ApplyResult.Failed("le téléphone n'autorise pas le changement de fond d'écran")
            }
            // FLAG_LOCK : seul l'écran de verrouillage change, l'écran d'accueil garde son fond.
            wm.setBitmap(image, null, true, WallpaperManager.FLAG_LOCK)
            image.recycle()
            settings.lastApplied = System.currentTimeMillis()
            ApplyResult.Done
        } catch (e: Exception) {
            ApplyResult.Failed(e.message ?: "erreur inconnue")
        }
    }

    /** Passe à l'élément suivant (même type que l'actuel) puis l'installe. */
    fun next(ctx: Context): ApplyResult {
        QuoteRepository(ctx).next(manualType(ctx)) ?: return ApplyResult.NoQuote
        markCurrentSlot(ctx)
        return apply(ctx)
    }

    /** Type choisi pour le bouton « Suivante » : on garde le type affiché, sauf s'il est réglé à zéro par jour. */
    fun manualType(ctx: Context): Boolean {
        val settings = SettingsStore(ctx)
        val current = QuoteRepository(ctx).current()
        return when {
            current == null -> Schedule.current(settings).second.word
            current.isWord && settings.wordsPerDay == 0 -> false
            !current.isWord && settings.quotesPerDay == 0 && settings.wordsPerDay > 0 -> true
            else -> current.isWord
        }
    }

    /** Appelé régulièrement en arrière-plan : change l'élément quand on entre dans un nouveau créneau. */
    fun tick(ctx: Context, force: Boolean = false) {
        val repo = QuoteRepository(ctx)
        val (key, slot) = Schedule.current(SettingsStore(ctx))
        if (!force && repo.slotKey == key) return
        repo.next(slot.word) ?: return
        repo.slotKey = key
        if (Photo.exists(ctx)) apply(ctx)
    }

    /** Évite un changement immédiat quand on vient de choisir un élément à la main. */
    fun markCurrentSlot(ctx: Context) {
        QuoteRepository(ctx).slotKey = Schedule.current(SettingsStore(ctx)).first
    }
}

/**
 * Programme de la journée : les changements sont répartis à intervalles réguliers
 * entre 7 h et 22 h. Les citations sont placées en premier (le matin) puis espacées.
 */
object Schedule {
    private const val START_MIN = 7 * 60
    private const val END_MIN = 22 * 60

    data class Slot(val minuteOfDay: Int, val word: Boolean)

    fun slots(quotesPerDay: Int, wordsPerDay: Int): List<Slot> {
        val q = if (quotesPerDay + wordsPerDay == 0) 1 else quotesPerDay
        val n = q + wordsPerDay
        val quotePositions = (0 until q).map { k -> k * n / q }.toSet()
        return (0 until n).map { i ->
            val minute = if (n == 1) START_MIN else START_MIN + i * (END_MIN - START_MIN) / (n - 1)
            Slot(minute, i !in quotePositions)
        }
    }

    /** Clé du créneau en cours (date + numéro) et créneau correspondant. Avant 7 h, c'est le dernier créneau de la veille. */
    fun current(settings: SettingsStore, now: Calendar = Calendar.getInstance()): Pair<String, Slot> {
        val q = settings.quotesPerDay
        val w = settings.wordsPerDay
        val list = slots(q, w)
        val day = now.clone() as Calendar
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val index = if (minute < list.first().minuteOfDay) {
            day.add(Calendar.DAY_OF_YEAR, -1)
            list.lastIndex
        } else list.indexOfLast { it.minuteOfDay <= minute }
        val date = "%04d%02d%02d".format(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH))
        return "$date:$index:$q:$w" to list[index]
    }

    /** Texte lisible du programme, par exemple « 7 h citation, 10 h mot ». */
    fun describe(quotesPerDay: Int, wordsPerDay: Int): String =
        slots(quotesPerDay, wordsPerDay).joinToString(", ") {
            val h = it.minuteOfDay / 60
            val m = it.minuteOfDay % 60
            (if (m == 0) "$h h" else "$h h %02d".format(m)) + if (it.word) " mot" else " citation"
        }
}

class RotateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { Engine.tick(applicationContext) }
        return Result.success()
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return when (QuoteRepository(ctx).sync(SettingsStore(ctx).quotesUrl)) {
            is SyncResult.Failed -> if (runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}

object Scheduler {
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        // Vérification légère toutes les heures : lit une date, et ne fait rien si la période n'est pas finie.
        wm.enqueueUniquePeriodicWork(
            "rotation", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RotateWorker>(1, TimeUnit.HOURS).build()
        )
        // Mise à jour de la liste depuis GitHub, seulement quand le réseau est disponible.
        wm.enqueueUniquePeriodicWork(
            "sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }
}

class CitationsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Scheduler.schedule(this)
    }
}
