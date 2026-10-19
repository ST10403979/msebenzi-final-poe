package com.msebenzi.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.ImageView
import coil.load
import com.msebenzi.app.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Helpers for the blob-storage feature: shrink photos before upload and show stored ones. */
object ImageUtils {

    private const val TAG = "ImageUtils"

    /**
     * Reads the picked image, scales it so its longest side is at most [maxSide] pixels and
     * re-encodes it as JPEG, keeping uploads small (the API accepts up to 3 MB).
     * Returns null if the image could not be read.
     */
    suspend fun readAsCompressedJpeg(context: Context, uri: Uri, maxSide: Int = 1280, quality: Int = 82): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val longest = max(info.size.width, info.size.height)
                        if (longest > maxSide) {
                            val scale = maxSide.toFloat() / longest
                            decoder.setTargetSize(
                                (info.size.width * scale).toInt().coerceAtLeast(1),
                                (info.size.height * scale).toInt().coerceAtLeast(1)
                            )
                        }
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(input, null, bounds)
                        bounds
                    }?.let { bounds ->
                        var sample = 1
                        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                    } ?: return@withContext null
                }
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            } catch (e: Exception) {
                Log.w(TAG, "Could not read image: ${e.message}")
                null
            }
        }

    /** Loads a stored image BLOB from the API into this view (falls back to [fallback] if none/failed). */
    fun ImageView.loadMedia(mediaId: Int?, fallback: Int) {
        if (mediaId == null || mediaId <= 0) {
            setImageResource(fallback)
        } else {
            load(RetrofitClient.mediaUrl(mediaId)) {
                placeholder(fallback)
                error(fallback)
                crossfade(true)
            }
        }
    }
}
