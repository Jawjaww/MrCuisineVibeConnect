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
        val decoded = files.mapNotNull { name ->
            try {
                context.assets.open("decor/$name").use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
        if (decoded.isEmpty()) return null

        val output = Bitmap.createBitmap(1024, 600, Bitmap.Config.RGB_565)
        val canvas = Canvas(output)
        canvas.drawColor(T.BG)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 41 }
        val destinations = listOf(
            RectF(-28f, -24f, 245f, 204f),
            RectF(815f, -28f, 1052f, 184f),
            RectF(835f, 430f, 1048f, 620f),
        )
        for (index in decoded.indices) {
            val image = decoded[index]
            canvas.drawBitmap(image, null, destinations[index.coerceAtMost(destinations.lastIndex)], paint)
            image.recycle()
        }
        return output
    }
}
