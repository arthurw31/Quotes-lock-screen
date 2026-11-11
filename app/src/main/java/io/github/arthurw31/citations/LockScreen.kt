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
import java.util.TimeZone
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
        val quote = repo.current() ?: repo.next(SettingsStore(ctx).contentMode) ?: return ApplyResult.NoQuote
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

    /** Passe à la citation suivante puis l'installe. */
    fun next(ctx: Context): ApplyResult {
        QuoteRepository(ctx).next(SettingsStore(ctx).contentMode) ?: return ApplyResult.NoQuote
        markCurrentSlot(ctx)
        return apply(ctx)
    }

    /** Appelé régulièrement en arrière-plan : change la citation seulement quand la période est écoulée. */
    fun tick(ctx: Context) {
        if (!Photo.exists(ctx)) return
        val repo = QuoteRepository(ctx)
        val settings = SettingsStore(ctx)
        val key = slotKey(settings.frequency)
        if (repo.slotKey == key) return
        repo.next(settings.contentMode) ?: return
        repo.slotKey = key
        apply(ctx)
    }

    /** Évite un changement immédiat quand on vient de choisir la citation à la main ou de changer la fréquence. */
    fun markCurrentSlot(ctx: Context) {
        QuoteRepository(ctx).slotKey = slotKey(SettingsStore(ctx).frequency)
    }

    /** Les périodes commencent à minuit, heure locale (minuit et midi pour 2 fois par jour, etc.). */
    private fun slotKey(f: Frequency): String {
        val now = System.currentTimeMillis()
        val local = now + TimeZone.getDefault().getOffset(now)
        return "${f.hours}:${local / (f.hours * 3_600_000L)}"
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
