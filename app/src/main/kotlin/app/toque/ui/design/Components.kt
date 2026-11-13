package app.toque.ui.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.ui.Icon
import app.toque.ui.Ui
import kotlin.math.exp
import kotlin.math.sqrt

/** Fabrique les états focus, sélection et appui sans dépendre du focus blanc par défaut d'Android. */
object Focusable {
    fun states(
        view: View,
        radius: Float,
        normal: Int = T.SURFACE,
        pressed: Int = T.SURFACE3,
        selected: Int? = null,
        focusStroke: Int = T.BRAND,
        hairline: Float = T.Line.HAIR,
    ) {
        val drawable = StateListDrawable()
        if (selected != null) {
            drawable.addState(
                intArrayOf(android.R.attr.state_focused, android.R.attr.state_selected),
                Ui.round(selected, radius, T.SURFACE, T.Line.FOCUS),
            )
            drawable.addState(
                intArrayOf(android.R.attr.state_selected),
                Ui.round(selected, radius, selected, T.Line.SELECTED),
            )
        }
        drawable.addState(intArrayOf(android.R.attr.state_focused), Ui.round(normal, radius, focusStroke, T.Line.FOCUS))
        drawable.addState(intArrayOf(android.R.attr.state_pressed), Ui.round(pressed, radius, T.STROKE, hairline))
        drawable.addState(intArrayOf(), Ui.round(normal, radius, T.STROKE, hairline))
        view.background = drawable
        view.isFocusable = true
    }

    /** Dégradé à trois états : repos, appui, focus. Aucune animation, aucun flou. */
    fun gradientButton(view: View, radius: Float, top: Int, bottom: Int, pressedTop: Int, pressedBottom: Int) {
        fun layer(from: Int, to: Int, stroke: Int?): GradientDrawable =
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(from, to)).apply {
                cornerRadius = Ui.uf(radius)
                if (stroke != null) setStroke(Ui.u(T.Line.FOCUS), stroke)
            }
        val drawable = StateListDrawable()
        drawable.addState(intArrayOf(android.R.attr.state_focused), layer(top, bottom, T.SURFACE))
        drawable.addState(intArrayOf(android.R.attr.state_pressed), layer(pressedTop, pressedBottom, null))
        drawable.addState(intArrayOf(), layer(top, bottom, null))
        view.background = drawable
        view.isFocusable = true
    }
}

/**
 * Ombre de contact, calculée au pixel et mise en cache.
 *
 * Une ombre centrée déborde aussi au-dessus de la forme, ce qui la fait flotter. Ici l'ellipse
 * est centrée sous l'objet et l'alpha est nul au-dessus de sa ligne médiane : la pénombre ne
 * existe que sur la zone basse, comme un objet posé sur le plan de travail.
 */
object ShadowLayer {
    private val cache = HashMap<Long, Bitmap>()

    /**
     * Ombre portée classique, décalée vers le bas mais visible sur tout le pourtour : c'est elle
     * qui marque qu'un bouton est **au-dessus** du cadran qu'il chevauche.
     */
    fun drop(diameterPx: Int, spreadPx: Int, maxAlpha: Int): Bitmap {
        require(diameterPx > 0)
        val key = (diameterPx.toLong() shl 32) or (spreadPx.toLong() shl 8) or maxAlpha.toLong() or (1L shl 62)
        cache[key]?.let { return it }
        val side = diameterPx + 2 * spreadPx
        val pixels = IntArray(side * side)
        val r = diameterPx / 2f
        val c = side / 2f
        val offsetY = r * 0.16f
        val semiX = r * 0.97f
        val semiY = r * 0.92f
        val fade = (spreadPx * 1.6f).coerceAtLeast(1f)
        for (y in 0 until side) {
            val dy = y - c
            for (x in 0 until side) {
                val dx = x - c
                val ex = dx / semiX
                val ey = (dy - offsetY) / semiY
                val e = sqrt(ex * ex + ey * ey)
                val t = if (e <= 0.72f) 0f else e - 0.72f
                val border = minOf(x, side - 1 - x, y, side - 1 - y).toFloat()
                val window = (border / fade).coerceIn(0f, 1f)
                val a = (maxAlpha * exp(-(t * t) * 3.6f) * window).toInt()
                if (a > 0) pixels[y * side + x] = a.coerceIn(0, 255) shl 24
            }
        }
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
        cache[key] = bitmap
        return bitmap
    }

    fun contact(diameterPx: Int, spreadPx: Int, maxAlpha: Int): Bitmap {
        require(diameterPx > 0)
        val key = (diameterPx.toLong() shl 32) or (spreadPx.toLong() shl 8) or maxAlpha.toLong()
        cache[key]?.let { return it }
        val side = diameterPx + 2 * spreadPx
        val pixels = IntArray(side * side)
        val r = diameterPx / 2f
        val c = side / 2f
        val ellipseY = r * 0.46f
        val semiY = r * 0.46f
        val semiX = r * 0.95f
        val fade = (spreadPx * 1.6f).coerceAtLeast(1f)
        for (y in 0 until side) {
            val dy = y - c
            // Rien au-dessus de la ligne médiane : l'ombre ne remonte jamais.
            if (dy < r * 0.08f) continue
            for (x in 0 until side) {
                val dx = x - c
                val ex = dx / semiX
                val ey = (dy - ellipseY) / semiY
                val e = sqrt(ex * ex + ey * ey)
                val t = if (e <= 0.55f) 0f else e - 0.55f
                // Fondu de bord : la pénombre atteint zéro avant la limite du bitmap, sinon elle
                // se coupe net et dessine un carré.
                val border = minOf(x, side - 1 - x, y, side - 1 - y).toFloat()
                val window = (border / fade).coerceIn(0f, 1f)
                val a = (maxAlpha * exp(-(t * t) * 3.2f) * window).toInt()
                if (a > 0) pixels[y * side + x] = a.coerceIn(0, 255) shl 24
            }
        }
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
        cache[key] = bitmap
        return bitmap
    }
}

/**
 * Fond de bouton : pénombre + disque, dessinés dans les limites de la vue.
 *
 * Android rogne le dessin d'une vue à ses propres limites : une ombre dessinée hors du view est
 * coupée net sur un carré. La vue réserve donc le débord, et ce Drawable dessine l'ombre, la face
 * et l'anneau de focus à partir de son état.
 */
class DiscBackground(
    private val spread: Int,
    private val alpha: Int,
    private val cast: Boolean = false,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var state = intArrayOf()

    override fun isStateful(): Boolean = true

    override fun onStateChange(newState: IntArray): Boolean {
        state = newState
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val side = bounds.width()
        if (side <= 0) return
        val spreadPx = Ui.u(spread)
        val disc = (side - 2 * spreadPx).coerceAtLeast(1)
        val radius = disc / 2f
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val pressed = state.contains(android.R.attr.state_pressed)
        val focused = state.contains(android.R.attr.state_focused)
        val selected = state.contains(android.R.attr.state_selected)

        val shadow = if (cast) ShadowLayer.drop(disc, spreadPx, alpha) else ShadowLayer.contact(disc, spreadPx, alpha)
        // Aucun décalage au placement : le bitmap occupe exactement la vue, et le moindre
        // décalage ferait rogner son bas par la limite de la vue — d'où la ligne franche.
        canvas.drawBitmap(shadow, cx - shadow.width / 2f, cy - shadow.height / 2f, null)

        fill.color = when {
            selected -> T.BRAND
            pressed -> T.SURFACE2
            else -> T.BUTTON_FACE
        }
        canvas.drawCircle(cx, cy, radius, fill)
        if (focused) {
            ring.color = if (selected) T.SURFACE else T.BRAND
            ring.strokeWidth = Ui.uf(T.Line.FOCUS)
            canvas.drawCircle(cx, cy, radius - ring.strokeWidth / 2f, ring)
        }
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Disque de bouton : la vue réserve le débord nécessaire à la pénombre. */
class SoftCircle(context: Context, spread: Int = 5, alpha: Int = 46, cast: Boolean = false) : FrameLayout(context) {
    init {
        isClickable = true
        isFocusable = true
        background = DiscBackground(spread, alpha, cast)
    }
}

/** Surface claire réutilisable, sans élévation ni ombre dynamique. */
class SurfaceCard(context: Context, radius: Float = T.Radius.CARD) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        background = Ui.round(T.SURFACE, radius, T.STROKE, T.Line.HAIR)
        setPadding(Ui.u(T.Space.S20), Ui.u(T.Space.S16), Ui.u(T.Space.S20), Ui.u(T.Space.S16))
    }
}

/** Bouton circulaire icône + libellé discret. Un libellé vide produit un bouton compact. */
class RoundButton(
    context: Context,
    iconKind: Icon.Kind,
    label: String = "",
    size: Int = 67,
    private val castShadow: Boolean = false,
    private val shadowAlpha: Int? = null,
    private val action: () -> Unit = {},
) : LinearLayout(context) {
    private val spread = if (size < 64) 5 else 9

    /** Taille réellement occupée : le disque plus la place de sa pénombre. */
    val outerSize: Int = size + 2 * spread
    private val circle = SoftCircle(context, spread = spread, alpha = shadowAlpha ?: if (size < 64) 46 else 58, cast = castShadow)
    private val icon = Icon(context, iconKind, T.BRAND)
    private val caption: TextView? = if (label.isNotEmpty()) Ui.text(context, label, T.Type.CAPTION, T.TEXT, Ui.MEDIUM) else null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        circle.addView(icon, Ui.flp(Ui.u(if (size < 64) 22 else 30), Ui.u(if (size < 64) 22 else 30), Gravity.CENTER))
        circle.contentDescription = label
        circle.setOnClickListener { if (isEnabled) action() }
        addView(circle, LinearLayout.LayoutParams(Ui.u(outerSize), Ui.u(outerSize)))
        caption?.let { addView(it, Ui.lp(Ui.WRAP, Ui.WRAP, t = 1)) }
        isFocusable = false
        isClickable = false
        contentDescription = label
    }

    fun select(selected: Boolean) {
        circle.isSelected = selected
        icon.color = if (selected) T.SURFACE else T.BRAND
        invalidate()
        caption?.setTextColor(if (selected) T.BRAND else T.TEXT2)
    }

    fun setButtonEnabled(enabled: Boolean) {
        isEnabled = enabled
        circle.isEnabled = enabled
        alpha = if (enabled) 1f else T.DISABLED_ALPHA
    }

    fun focusView(): View = circle
}

/**
 * Bascule unique : le libellé et l'icône décrivent **l'état courant**, un appui passe à l'autre.
 * Remplace la paire de boutons qui affichait les deux choix à la fois.
 */
class ToggleRoundButton(
    context: Context,
    size: Int = 67,
    private val action: () -> Unit = {},
) : LinearLayout(context) {
    private val spread = 9
    private val circle = SoftCircle(context, spread = spread, alpha = 58)
    private val icon = Icon(context, Icon.Kind.SYNC, T.BRAND)
    private val caption = Ui.text(context, "", T.Type.CAPTION, T.TEXT, Ui.MEDIUM)
    private var on = false
    private var onKind = Icon.Kind.SYNC
    private var offKind = Icon.Kind.REVERSE
    private var onLabel = ""
    private var offLabel = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        circle.addView(icon, Ui.flp(Ui.u(30), Ui.u(30), Gravity.CENTER))
        circle.setOnClickListener {
            if (isEnabled) {
                on = !on
                action()
                apply()
            }
        }
        addView(circle, LinearLayout.LayoutParams(Ui.u(size + 2 * spread), Ui.u(size + 2 * spread)))
        addView(caption, Ui.lp(Ui.WRAP, Ui.WRAP, t = 1))
        isFocusable = false
        isClickable = false
    }

    /** Libellés et icônes des deux états ; `on` désigne l'état alternatif. */
    fun configure(onLabel: String, onKind: Icon.Kind, offLabel: String, offKind: Icon.Kind) {
        this.onLabel = onLabel
        this.onKind = onKind
        this.offLabel = offLabel
        this.offKind = offKind
        apply()
    }

    /** Aligne l'affichage sur l'état réel du modèle (sans déclencher l'action). */
    fun set(active: Boolean) {
        if (on == active) return
        on = active
        apply()
    }

    fun setButtonEnabled(enabled: Boolean) {
        isEnabled = enabled
        circle.isEnabled = enabled
        alpha = if (enabled) 1f else T.DISABLED_ALPHA
    }

    fun focusView(): View = circle

    private fun apply() {
        icon.set(if (on) onKind else offKind)
        caption.text = if (on) onLabel else offLabel
        circle.isSelected = on
        icon.color = if (on) T.SURFACE else T.BRAND
        caption.setTextColor(if (on) T.BRAND else T.TEXT2)
        circle.contentDescription = caption.text
    }
}

/** Cadran : disque ombré avec graduations intérieures, arc de progression et valeur centrale. */
class Dial(context: Context, private val topIcon: Icon.Kind, private val bottomIcon: Icon.Kind? = null, val size: Int = SIZE) : LinearLayout(context) {
    private val face = DialFace(context)
    private val minus = RoundButton(context, Icon.Kind.MINUS, size = 55, castShadow = true, shadowAlpha = 22) { onSelect(); onMinus() }
    private val plus = RoundButton(context, Icon.Kind.PLUS, size = 55, castShadow = true, shadowAlpha = 22) { onSelect(); onPlus() }

    var onSelect: () -> Unit = {}
    var onMinus: () -> Unit = {}
    var onPlus: () -> Unit = {}
    var repeatEnabled: Boolean = true

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        face.hasBottomIcon = bottomIcon != null
        val box = FrameLayout(context)
        box.addView(face, Ui.flp(Ui.u(size), Ui.u(size)))
        box.addView(
            Icon(context, topIcon, T.TEXT),
            Ui.flp(Ui.u(34), Ui.u(34), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.u(30) },
        )
        if (bottomIcon != null) {
            box.addView(
                Icon(context, bottomIcon, T.TEXT),
                Ui.flp(Ui.u(34), Ui.u(34), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = Ui.u(30) },
            )
        }
        addView(box, LinearLayout.LayoutParams(Ui.u(size), Ui.u(size)))
        val actions = Ui.horizontal(context, Gravity.CENTER)
        actions.addView(minus, Ui.lp(Ui.u(minus.outerSize), Ui.u(minus.outerSize), r = 8))
        actions.addView(plus, Ui.lp(Ui.u(plus.outerSize), Ui.u(plus.outerSize)))
        // Les boutons sont posés sur le bas du disque, comme sur la maquette.
        addView(actions, Ui.lp(Ui.MATCH, Ui.u(minus.outerSize), t = -44))
        face.isFocusable = true
        face.isClickable = true
        face.setOnClickListener { onSelect() }
        face.setOnFocusChangeListener { _, focused -> if (focused) onSelect(); face.invalidate() }
        minus.focusView().setOnFocusChangeListener { _, focused -> if (focused) onSelect() }
        plus.focusView().setOnFocusChangeListener { _, focused -> if (focused) onSelect() }
        repeatOnHold(minus.focusView()) { if (repeatEnabled) onMinus() }
        repeatOnHold(plus.focusView()) { if (repeatEnabled) onPlus() }
    }

    fun set(value: String, unit: String = "", legend: String = "", progress: Float = 0f, marker: Float? = null) {
        face.update(value, unit, legend, progress, marker)
    }

    fun select(selected: Boolean) {
        face.isSelected = selected
        face.invalidate()
    }

    fun focusControl(): View = face

    private fun repeatOnHold(view: View, action: () -> Unit) {
        var count = 0
        val repeat = object : Runnable {
            override fun run() {
                if (!view.isPressed || !repeatEnabled || count >= 40) return
                count++
                action()
                view.postDelayed(this, 110)
            }
        }
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    count = 0
                    target.removeCallbacks(repeat)
                    target.postDelayed(repeat, 450)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.removeCallbacks(repeat)
            }
            false
        }
    }

    private class DialFace(context: Context) : View(context) {
        var hasBottomIcon = false

        private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
        private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
        private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val regularTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
        private val bounds = RectF()
        private var value = "0"
        private var unit = ""
        private var legend = ""
        private var progress = 0f
        private var marker: Float? = null
        private var staticLayer: Bitmap? = null
        private var layerSize = 0

        fun update(value: String, unit: String, legend: String, progress: Float, marker: Float?) {
            if (this.value == value && this.unit == unit && this.legend == legend &&
                this.progress == progress && this.marker == marker) return
            this.value = value
            this.unit = unit
            this.legend = legend
            this.progress = progress.coerceIn(0f, 1f)
            this.marker = marker?.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            staticLayer?.recycle()
            staticLayer = null
            layerSize = w
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = minOf(width, height).toFloat()
            if (size <= 0f) return
            val cx = width / 2f
            val cy = height / 2f
            // Le disque occupe 87 % de la vue : le reste est la place de la pénombre.
            // Le disque laisse 15 % de la vue à la pénombre : assez pour qu'elle s'éteigne
            // avant le bord de la vue, sinon elle est coupée net sur une ligne horizontale.
            val radius = size * 0.425f
            val arcRadius = radius - Ui.uf(3f)

            // Pénombre dessinée hors du bitmap de couche, sinon elle est rognée au bord du view.
            val shadowBitmap = ShadowLayer.contact((2 * radius).toInt(), Ui.u(12), 64)
            canvas.drawBitmap(
                shadowBitmap,
                cx - shadowBitmap.width / 2f,
                cy + Ui.uf(4f) - shadowBitmap.height / 2f,
                null,
            )
            // Couche figée : disque et graduations. Redessinée uniquement si la taille change.
            val layer = staticLayer ?: buildLayer(size, cx, cy, radius, arcRadius).also { staticLayer = it }
            canvas.drawBitmap(layer, 0f, 0f, null)

            // Piste et progression à l'extérieur du disque blanc : le bord du cadran reste net.
            val ringRadius = radius + Ui.uf(1f)
            bounds.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
            arcPaint.strokeWidth = Ui.uf(3f)
            // Le cadran actif se signale par un anneau vert pâle, sans ajouter de trait d'état.
            arcPaint.color = if (isFocused || isSelected) withAlpha(T.BOTANICAL, 0x6A) else T.ARC_TRACK
            canvas.drawArc(bounds, 135f, 270f, false, arcPaint)
            arcPaint.color = T.BOTANICAL
            if (progress > 0f) canvas.drawArc(bounds, 135f, 270f * progress, false, arcPaint)

            marker?.let { fraction ->
                val angle = Math.toRadians((135 + 270 * fraction).toDouble())
                arcPaint.strokeWidth = Ui.uf(2f)
                arcPaint.color = T.TEXT
                val inner = radius - Ui.uf(20f)
                val outer = radius - Ui.uf(4f)
                canvas.drawLine(
                    cx + (inner * Math.cos(angle)).toFloat(),
                    cy + (inner * Math.sin(angle)).toFloat(),
                    cx + (outer * Math.cos(angle)).toFloat(),
                    cy + (outer * Math.sin(angle)).toFloat(),
                    arcPaint,
                )
            }

            drawValue(canvas, cx, cy, size)
            drawLegend(canvas, cx, cy, size)
        }

        private fun buildLayer(size: Float, cx: Float, cy: Float, radius: Float, arcRadius: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            discPaint.color = T.BUTTON_FACE
            discPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius, discPaint)

            tickPaint.strokeWidth = Ui.uf(1.2f)
            tickPaint.color = T.TICK
            for (i in 0..44) {
                val angle = Math.toRadians((135 + i * 6).toDouble())
                val inner = radius - Ui.uf(if (i % 5 == 0) 22f else 15f)
                val outer = radius - Ui.uf(8f)
                canvas.drawLine(
                    cx + (inner * Math.cos(angle)).toFloat(),
                    cy + (inner * Math.sin(angle)).toFloat(),
                    cx + (outer * Math.cos(angle)).toFloat(),
                    cy + (outer * Math.sin(angle)).toFloat(),
                    tickPaint,
                )
            }
            return bitmap
        }

        private fun drawValue(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            valuePaint.color = T.TEXT
            valuePaint.typeface = mediumTypeface
            valuePaint.textSize = Ui.uf(T.Type.DIAL_VALUE)
            if (value.isEmpty() || value == "—") valuePaint.textSize = Ui.uf(T.Type.DIAL_VALUE * 0.7f)
            val available = size * 0.62f
            while (valuePaint.measureText(value) > available && valuePaint.textSize > Ui.uf(36f)) {
                valuePaint.textSize -= Ui.uf(2f)
            }
            unitPaint.color = T.TEXT2
            unitPaint.typeface = regularTypeface
            unitPaint.textSize = Ui.uf(T.Type.DIAL_UNIT)
            val valueWidth = valuePaint.measureText(value)
            val unitWidth = unitPaint.measureText(unit)
            val gap = if (unit.isEmpty()) 0f else Ui.uf(5f)
            val start = cx - (valueWidth + gap + unitWidth) / 2f
            val baseline = cy + Ui.uf(14f)
            canvas.drawText(value, start, baseline, valuePaint)
            if (unit.isNotEmpty()) canvas.drawText(unit, start + valueWidth + gap, baseline, unitPaint)
        }

        private fun drawLegend(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            if (legend.isEmpty()) return
            legendPaint.color = T.TEXT2
            legendPaint.typeface = mediumTypeface
            legendPaint.textSize = Ui.uf(T.Type.DIAL_LEGEND)
            val maxWidth = size * 0.66f
            val lines = if (hasBottomIcon) listOf(fit(legend, maxWidth, legendPaint)) else wrapLegend(legend, maxWidth, legendPaint)
            var y = cy + Ui.uf(if (hasBottomIcon) 42f else 46f)
            for (lineText in lines) {
                canvas.drawText(lineText, cx, y, legendPaint)
                y += Ui.uf(17f)
            }
        }

        private fun wrapLegend(text: String, maxWidth: Float, paint: Paint): List<String> {
            if (paint.measureText(text) <= maxWidth) return listOf(text)
            val separator = " · "
            val cut = text.indexOf(separator)
            if (cut > 0) {
                return listOf(
                    fit(text.substring(0, cut), maxWidth, paint),
                    fit(text.substring(cut + separator.length), maxWidth, paint),
                ).filter { it.isNotEmpty() }
            }
            return listOf(fit(text, maxWidth, paint))
        }

        private fun fit(text: String, maxWidth: Float, paint: Paint): String {
            if (paint.measureText(text) <= maxWidth) return text
            var shortened = text
            while (shortened.length > 1 && paint.measureText(shortened + "…") > maxWidth) {
                shortened = shortened.dropLast(1)
            }
            return shortened + "…"
        }
    }

    companion object {
        /** Taille de repli du cadran, en unités de la grille 1024×600. */
        const val SIZE = 258

        private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
    }
}

/** Contrôle principal tactile : dégradé statique, libellé large, focus explicite. */
class PrimaryButton(
    context: Context,
    label: String,
    private val action: () -> Unit,
) : LinearLayout(context) {
    private val icon = Icon(context, Icon.Kind.PLAY, T.SURFACE)
    private val title = Ui.text(context, label, T.Type.H3, T.SURFACE, Ui.MEDIUM)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(Ui.u(T.Space.S24), 0, Ui.u(T.Space.S24), 0)
        minimumHeight = Ui.u(86)
        addView(icon, Ui.lp(Ui.u(30), Ui.u(30), r = 12))
        addView(title)
        Focusable.gradientButton(
            this, T.Radius.PILL, T.START_TOP, T.START_BOTTOM, T.START_TOP_PRESSED, T.START_BOTTOM_PRESSED,
        )
        isClickable = true
        setOnClickListener { if (isEnabled) action() }
    }

    fun setLabel(label: String) { title.text = label }

    fun setEnabledState(enabled: Boolean) {
        isEnabled = enabled
        alpha = if (enabled) 1f else T.DISABLED_ALPHA
    }
}

/** Turbo : icône et libellé circulaires, avec sémantique d'appui maintenu préservée. */
class HoldRoundButton(
    context: Context,
    label: String,
    private val onDown: () -> Unit,
    private val onUp: () -> Unit,
) : LinearLayout(context) {
    private val spread = 9
    private val circle = SoftCircle(context, spread = spread, alpha = 58)
    private val icon = Icon(context, Icon.Kind.BOLT, T.BRAND)
    private val caption = Ui.text(context, label, T.Type.CAPTION, T.TEXT, Ui.MEDIUM)
    private var held = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        circle.addView(icon, Ui.flp(Ui.u(30), Ui.u(30), Gravity.CENTER))
        circle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!held && isEnabled) {
                        held = true
                        view.isPressed = true
                        circle.background = Ui.round(T.WARN, 31f, T.WARN, T.Line.HAIR)
                        icon.color = T.TEXT
                        onDown()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (held) {
                        held = false
                        view.isPressed = false
                        icon.color = T.BRAND
                        onUp()
                    }
                }
            }
            true
        }
        circle.setOnClickListener { /* Turbo est volontairement une action maintenue. */ }
        addView(circle, LinearLayout.LayoutParams(Ui.u(67 + 2 * spread), Ui.u(67 + 2 * spread)))
        addView(caption, Ui.lp(Ui.WRAP, Ui.WRAP, t = 1))
        isFocusable = false
    }

    fun setButtonEnabled(enabled: Boolean) {
        circle.isEnabled = enabled
        alpha = if (enabled) 1f else T.DISABLED_ALPHA
        if (!enabled && held) {
            held = false
            onUp()
        }
    }
}

/** Balance actuelle, indépendante des commandes de cuisson. */
class WeightBadge(context: Context) : LinearLayout(context) {
    private val icon = Icon(context, Icon.Kind.SCALE, T.TEXT2)
    private val value = Ui.text(context, "—", T.Type.H1, T.TEXT, Ui.MEDIUM)
    private val unit = Ui.text(context, "g", T.Type.SMALL, T.TEXT2, Ui.REGULAR)
    private val status = Ui.text(context, tr("Base non connectée"), T.Type.CAPTION, T.TEXT2)
    private val underline = View(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = Ui.round(T.SURFACE, T.Radius.CARD, T.STROKE, T.Line.HAIR)
        setPadding(Ui.u(12), Ui.u(12), Ui.u(12), Ui.u(12))
        addView(icon, Ui.lp(Ui.u(30), Ui.u(30)))
        val row = Ui.horizontal(context, Gravity.CENTER)
        row.addView(value)
        row.addView(unit, Ui.lp(Ui.WRAP, Ui.WRAP, l = 4, b = 6))
        addView(row, Ui.lp(Ui.WRAP, Ui.WRAP, t = 2))
        underline.setBackgroundColor(T.BOTANICAL)
        addView(underline, Ui.lp(Ui.u(72), Ui.u(3), t = 4))
        addView(status, Ui.lp(Ui.WRAP, Ui.WRAP, t = 4))
    }

    fun set(weightG: Int, online: Boolean) {
        value.text = if (online) "$weightG" else "—"
        unit.text = if (online) "g" else ""
        status.text = if (online) tr("Balance") else tr("Base non connectée")
        // La phrase d'origine est réutilisée comme description lue par les lecteurs d'écran.
        contentDescription = if (online) tr("Balance : %d g", weightG) else tr("Base non connectée")
        value.setTextColor(if (online) T.TEXT else T.TEXT2)
        icon.color = if (online) T.BRAND else T.TEXT2
        underline.visibility = if (online) View.VISIBLE else View.INVISIBLE
    }
}

/** Valeurs courantes (température mesurée, poids) réunies au-dessus du bouton principal. */
class ReadoutCard(context: Context) : LinearLayout(context) {
    private val temp = Ui.text(context, "—", T.Type.H2, T.TEXT, Ui.MEDIUM)
    private val weight = Ui.text(context, "—", T.Type.H2, T.TEXT, Ui.MEDIUM)
    private val underline = View(context).apply { setBackgroundColor(T.BOTANICAL) }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = Ui.round(T.BUTTON_FACE, T.Radius.CARD, T.STROKE, T.Line.HAIR)
        setPadding(Ui.u(16), Ui.u(16), Ui.u(16), Ui.u(16))
        // Les deux valeurs se répartissent sur la hauteur de la carte.
        addView(row(Icon.Kind.THERMO, temp), Ui.lp(Ui.MATCH, 0, weight = 1f))
        addView(View(context).apply { setBackgroundColor(T.STROKE) }, Ui.lp(Ui.MATCH, Ui.u(1)))
        addView(row(Icon.Kind.SCALE, weight), Ui.lp(Ui.MATCH, 0, weight = 1f))
        addView(underline, Ui.lp(Ui.u(80), Ui.u(4), t = 6))
    }

    private fun row(icon: Icon.Kind, value: TextView): LinearLayout {
        val line = Ui.horizontal(context, Gravity.CENTER_VERTICAL)
        line.addView(Icon(context, icon, T.TEXT2), Ui.lp(Ui.u(30), Ui.u(30), r = 12))
        line.addView(value)
        return line
    }

    fun set(tempC: Int?, weightG: Int?) {
        temp.text = if (tempC != null) "$tempC °C" else "—"
        temp.setTextColor(if (tempC != null) T.TEXT else T.TEXT3)
        weight.text = if (weightG != null) "$weightG g" else "—"
        weight.setTextColor(if (weightG != null) T.TEXT else T.TEXT3)
        underline.visibility = if (weightG != null) View.VISIBLE else View.INVISIBLE
    }
}

/** Raccourci du bandeau bas : icône et libellé, action simple ou maintenue. */
class StripAction(
    context: Context,
    iconKind: Icon.Kind,
    label: String,
    private val onClick: (() -> Unit)? = null,
    private val onDown: (() -> Unit)? = null,
    private val onUp: (() -> Unit)? = null,
) : LinearLayout(context) {
    private val icon = Icon(context, iconKind, T.BRAND)
    private val caption = Ui.text(context, label, T.Type.CAPTION, T.TEXT, Ui.MEDIUM)
    private var held = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(Ui.u(8), Ui.u(6), Ui.u(8), Ui.u(6))
        addView(icon, Ui.lp(Ui.u(30), Ui.u(30)))
        addView(caption, Ui.lp(Ui.WRAP, Ui.WRAP, t = 4))
        isClickable = true
        isFocusable = true
        background = Ui.pressable(Ui.round(0, T.Radius.CARD_SM), T.Radius.CARD_SM)
        if (onDown != null || onUp != null) {
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!held) {
                            held = true
                            view.isPressed = true
                            onDown?.invoke()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (held) {
                            held = false
                            view.isPressed = false
                            onUp?.invoke()
                        }
                    }
                }
                true
            }
        } else {
            setOnClickListener { onClick?.invoke() }
        }
    }
}

/** Filet de séparation discret, sans ombre. */
class SectionDivider(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = T.STROKE
        paint.strokeWidth = Ui.uf(T.Line.HAIR)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, paint)
    }
}

/** Puce réutilisable pour les écrans dont la mise en page est conservée. */
object Chip {
    fun create(context: Context, label: String, icon: Icon.Kind? = null): LinearLayout =
        Ui.chip(context, label, icon, T.TEXT2, T.SURFACE2, T.Type.SMALL)
}
