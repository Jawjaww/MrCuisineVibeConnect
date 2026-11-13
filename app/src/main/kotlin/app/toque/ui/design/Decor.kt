package app.toque.ui.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import java.util.concurrent.Executors

/**
 * Pré-compose le décor une seule fois, hors du thread principal. Les photos restent dans les
 * marges à faible opacité ; en leur absence le repli est un fond ivoire uni, sans allocation.
 * Le robot (MT6580, 1 Go) ne doit pas payer ce coût au démarrage : l'ivoire s'affiche tout de
 * suite et la composition remplace le fond quand elle est prête.
 */
object Decor {
    private val files = listOf("basil.webp", "tomatoes.webp", "pepper.webp")
    @Volatile private var cached: Bitmap? = null
    @Volatile private var started = false
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "toque-decor").apply { priority = Thread.MIN_PRIORITY }
    }

    /** Pose le fond immédiatement, puis le remplace si les photos finissent de composer. */
    fun install(root: View) {
        root.background = drawable(root.context)
        if (cached != null || started) return
        started = true
        val app = root.context.applicationContext
        worker.execute {
            val bitmap = compose(app) ?: return@execute
            cached = bitmap
            root.post {
                root.background = BitmapDrawable(root.resources, bitmap).apply { gravity = Gravity.FILL }
            }
        }
    }

    private fun drawable(context: Context): Drawable {
        val bitmap = cached ?: return ColorDrawable(T.BG)
        return BitmapDrawable(context.resources, bitmap).apply { gravity = Gravity.FILL }
    }

    private fun compose(context: Context): Bitmap? {
        val output = Bitmap.createBitmap(1024, 600, Bitmap.Config.RGB_565)
        val canvas = Canvas(output)
        canvas.drawColor(T.BG)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 200 }
        // Basilic en bas à gauche, tomates en haut à droite, poivre en bas à droite.
        val destinations = listOf(
            RectF(-30f, 300f, 330f, 628f),
            RectF(740f, -40f, 1080f, 215f),
            RectF(830f, 350f, 1070f, 610f),
        )
        var drawn = false
        for (index in files.indices) {
            val image = try {
                context.assets.open("decor/${files[index]}").use { stream ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 240) sample *= 2
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    context.assets.open("decor/${files[index]}").use { BitmapFactory.decodeStream(it, null, options) }
                }
            } catch (_: Exception) {
                null
            }
            if (image != null) {
                drawPhoto(canvas, image, destinations[index], paint)
                image.recycle()
                drawn = true
            }
        }
        if (!drawn) {
            output.recycle()
            return null
        }
        // Voile ivoire : les photos restent en périphérie, la zone des cadrans et des textes
        // demeure parfaitement lisible sur le robot comme sur une TV.
        val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                512f, 300f, 650f,
                intArrayOf(withAlpha(T.BG, 250), withAlpha(T.BG, 214), withAlpha(T.BG, 104), withAlpha(T.BG, 26)),
                floatArrayOf(0f, 0.38f, 0.74f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, 1024f, 600f, veil)
        return output
    }

    /**
     * Dessine une photo avec un masque radial : les bords du rectangle disparaissent, seule la
     * matière floue reste visible. Composition unique, hors thread principal.
     */
    private fun drawPhoto(canvas: Canvas, image: Bitmap, dest: RectF, paint: Paint) {
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                dest.centerX(), dest.centerY(), maxOf(dest.width(), dest.height()) * 0.62f,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val layer = canvas.saveLayer(dest, null)
        canvas.drawBitmap(image, null, dest, paint)
        canvas.drawRect(dest, mask)
        canvas.restoreToCount(layer)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}
