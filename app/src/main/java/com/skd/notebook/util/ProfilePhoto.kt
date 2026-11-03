package com.skd.notebook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.skd.notebook.data.remote.FirebaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/**
 * The user's profile picture, chosen with the system photo picker.
 *
 * Privacy / Play policy: the picker gives the app only the one image the user taps, so no storage
 * or media permission is needed. The picture is shrunk to a small square and saved in the signed-in
 * user's own Firestore area, so it comes back after logging out and in, or on another device. A copy
 * in the app's private storage keeps it quick to show; that copy is deleted on sign out.
 */
object ProfilePhoto {
    private const val FILE_NAME = "profile_photo.jpg"
    private const val PREFS_NAME = "notebook_prefs"
    private const val KEY_IN_CLOUD = "profile_photo_in_cloud"
    private const val SIZE_PX = 512
    private const val MAX_BYTES = 400_000 // far below Firestore's 1 MiB document limit

    // Uploads outlive the screen that started them (Firestore also keeps them queued while offline).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cloud by lazy { FirebaseService() }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): Bitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
    }

    /** Keeps the picture on this device and uploads it to the account. */
    fun save(context: Context, bitmap: Bitmap): Boolean {
        val jpeg = toJpeg(bitmap)
        val saved = runCatching { file(context).writeBytes(jpeg) }.isSuccess
        if (saved) {
            prefs(context).edit().putBoolean(KEY_IN_CLOUD, true).apply()
            scope.launch { runCatching { cloud.saveProfilePhoto(jpeg) } }
        }
        return saved
    }

    /** The user removed their picture: gone from this device and from the account. */
    fun remove(context: Context) {
        delete(context)
        scope.launch { runCatching { cloud.deleteProfilePhoto() } }
    }

    /** Clears this device's copy only (sign out); the account's copy stays for the next login. */
    fun delete(context: Context) {
        file(context).delete()
        prefs(context).edit().remove(KEY_IN_CLOUD).apply()
    }

    /**
     * Brings the account's picture onto this device after login. Returns it when one was fetched, or null
     * when there is none (or no connection). A picture set before pictures were stored in the account is
     * uploaded once, so it isn't lost either.
     */
    suspend fun restoreFromCloud(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val jpeg = cloud.getProfilePhoto()
            if (jpeg != null) {
                file(context).writeBytes(jpeg)
                prefs(context).edit().putBoolean(KEY_IN_CLOUD, true).apply()
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            } else {
                val local = file(context)
                if (local.exists() && !prefs(context).getBoolean(KEY_IN_CLOUD, false)) {
                    cloud.saveProfilePhoto(local.readBytes())
                    prefs(context).edit().putBoolean(KEY_IN_CLOUD, true).apply()
                }
                null
            }
        }.getOrNull()
    }

    /** JPEG bytes, lowering the quality only if a picture is unusually large. */
    private fun toJpeg(bitmap: Bitmap): ByteArray {
        var quality = 90
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_BYTES || quality <= 40) return out.toByteArray()
            quality -= 10
        }
    }

    /**
     * Reads the picked image, honours its camera rotation, crops the middle square and scales it to
     * at most [SIZE_PX]. Returns null if the image can't be read.
     */
    fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val side = min(raw.width, raw.height)
        val matrix = Matrix().apply { if (rotation != 0f) postRotate(rotation) }
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side, matrix, true)
        if (square.width > SIZE_PX) Bitmap.createScaledBitmap(square, SIZE_PX, SIZE_PX, true) else square
    }.getOrNull()
}
