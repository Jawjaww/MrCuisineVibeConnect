package app.toque.ui.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity

/**
 * Pré-compose le décor une seule fois. Les photos optionnelles restent dans les marges à faible
 * opacité. En leur absence, le repli est un fond ivoire uni sans allocation bitmap.
 */
object Decor {
    private val files = listOf("basil.webp", "tomatoes.webp", "pepper.webp")
    @Volatile private var cached: Bitmap? = null
    @Volatile private var attempted = false

    fun background(context: Context): Drawable {
        val bitmap = cached ?: synchronized(this) {
            if (!attempted) {
                cached = compose(context.applicationContext)
                attempted = true
            }
            cached
        }
        return if (bitmap == null) ColorDrawable(T.BG)
        else BitmapDrawable(context.resources, bitmap).apply { gravity = Gravity.FILL }
    }

    private fun compose(context: Context): Bitmap? {
        val output = Bitmap.createBitmap(1024, 600, Bitmap.Config.RGB_565)
        val canvas = Canvas(output)
        canvas.drawColor(T.BG)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 41 }
        val destinations = listOf(
            RectF(-28f, -24f, 245f, 204f),
            RectF(815f, -28f, 1052f, 184f),
            RectF(835f, 430f, 1048f, 620f),
        )
        var drawn = false
        for (index in files.indices) {
            val image = try {
                context.assets.open("decor/${files[index]}").use { stream ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, bounds)
                    val sample = maxOf(1, maxOf(bounds.outWidth / 320, bounds.outHeight / 240))
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    context.assets.open("decor/${files[index]}").use { BitmapFactory.decodeStream(it, null, options) }
                }
            } catch (_: Exception) { null }
            if (image != null) {
                canvas.drawBitmap(image, null, destinations[index], paint)
                image.recycle()
                drawn = true
            }
        }
        if (!drawn) {
            output.recycle()
            return null
        }
        return output
    }
}
}
