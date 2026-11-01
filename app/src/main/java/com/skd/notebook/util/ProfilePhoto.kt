package com.skd.notebook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import kotlin.math.min

/**
 * The user's profile picture, chosen with the system photo picker.
 *
 * Privacy / Play policy: the picker gives the app only the one image the user taps, so no storage
 * or media permission is needed. The picture is shrunk to a square and kept in the app's private
 * storage on this device only; it is never uploaded, and it is deleted on sign out.
 */
object ProfilePhoto {
    private const val FILE_NAME = "profile_photo.jpg"
    private const val SIZE_PX = 512

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): Bitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
    }

    fun save(context: Context, bitmap: Bitmap): Boolean = runCatching {
        file(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }.getOrDefault(false)

    fun delete(context: Context) {
        file(context).delete()
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
