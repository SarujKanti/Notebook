package com.skd.notebook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import com.skd.notebook.data.remote.FirebaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * A photo placed in a note: its [id], [pos] (the character of the note's text it sits in front of, always the
 * start of a line) and [heightDp] (0 = the standard height).
 */
data class NoteImage(val id: String, val pos: Int = 0, val heightDp: Int = 0)

/**
 * Photos attached to notes, chosen with the system photo picker or taken with the phone's camera app
 * (neither needs a storage or camera permission).
 *
 * Each photo is shrunk to a small JPEG and given an id; a note keeps the list of its photo ids
 * ([NoteEntity.imageIds]). The JPEG is saved in the app's private storage for quick display and in the
 * signed-in user's own Firestore area, so photos follow the account to another device or after logging
 * out and in. The on-device copies are cleared on sign out and fetched again when needed.
 */
object NoteImages {
    private const val DIR = "note_images"
    private const val MAX_SIDE = 1600
    private const val MAX_BYTES = 700_000 // well below Firestore's 1 MiB document limit

    // Uploads and deletions outlive the screen that started them (Firestore also queues them while offline).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cloud by lazy { FirebaseService() }

    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /**
     * What a note keeps for its photos, as one text: entries joined with commas, each "id:pos:height".
     * An entry that is just an id (as saved before photos could be placed) sits at the top, at automatic height.
     */
    fun parse(csv: String): List<NoteImage> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
        val parts = entry.split(':')
        NoteImage(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2)?.toIntOrNull() ?: 0)
    }

    fun format(images: List<NoteImage>): String = images.joinToString(",") { "${it.id}:${it.pos}:${it.heightDp}" }

    fun ids(csv: String): List<String> = parse(csv).map { it.id }

    private fun dir(context: Context) = File(context.filesDir, DIR).also { it.mkdirs() }

    private fun file(context: Context, id: String) = File(dir(context), "$id.jpg")

    /** Shrinks the picked or captured photo, saves it, starts uploading it, and returns its new id (null if it can't be read). */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(context, uri) ?: return@withContext null
        val jpeg = toJpeg(bitmap)
        val id = UUID.randomUUID().toString()
        val saved = runCatching { file(context, id).writeBytes(jpeg) }.isSuccess
        if (!saved) return@withContext null
        scope.launch { runCatching { cloud.saveNoteImage(id, jpeg) } }
        id
    }

    /** Removes photos from this device and from the account (they were taken off a note, or the note is gone). */
    fun delete(context: Context, ids: List<String>) {
        if (ids.isEmpty()) return
        ids.forEach { file(context, it).delete() }
        memory.evictAll()
        scope.launch { ids.forEach { runCatching { cloud.deleteNoteImage(it) } } }
    }

    /** Clears this device's copies only (sign out); the account's copies stay for the next login. */
    fun clearLocal(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        memory.evictAll()
    }

    /** The photo as a bitmap no larger than [maxPx] on its long side; fetched from the account if this device lacks it. */
    suspend fun load(context: Context, id: String, maxPx: Int): Bitmap? {
        val key = "$id@$maxPx"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val local = file(context, id)
            if (!local.exists()) {
                val jpeg = runCatching { cloud.getNoteImage(id) }.getOrNull() ?: return@withContext null
                runCatching { local.writeBytes(jpeg) }
            }
            decodeFile(local, maxPx)?.also { memory.put(key, it) }
        }
    }

    /** What [load] already has in memory, so a list can show it at once instead of flickering. */
    fun cached(id: String, maxPx: Int): Bitmap? = memory.get("$id@$maxPx")

    /** Same as [load], for code that is not a coroutine: [onLoaded] runs on the main thread. */
    fun loadAsync(context: Context, id: String, maxPx: Int, onLoaded: (Bitmap?) -> Unit) {
        val app = context.applicationContext
        uiScope.launch { onLoaded(load(app, id, maxPx)) }
    }

    private fun decodeFile(file: File, maxPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, maxPx) })
    }.getOrNull()

    private fun sampleFor(bounds: BitmapFactory.Options, maxPx: Int): Int {
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return sample
    }

    /** Reads the image, honours its camera rotation, and scales it so its long side is at most [MAX_SIDE]. */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, MAX_SIDE) }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = MAX_SIDE.toFloat() / max(raw.width, raw.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }.getOrNull()

    /** JPEG bytes, lowering the quality only if a photo is unusually large. */
    private fun toJpeg(bitmap: Bitmap): ByteArray {
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_BYTES || quality <= 40) return out.toByteArray()
            quality -= 10
        }
    }
}
