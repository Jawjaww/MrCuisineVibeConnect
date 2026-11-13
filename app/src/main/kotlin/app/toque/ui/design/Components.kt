package app.toque.ui.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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

/** Fabrique les états focus, sélection et appui sans dépendre du focus blanc par défaut d'Android. */
object Focusable {
    fun states(
        view: View,
        radius: Float,
        normal: Int = T.SURFACE,
        pressed: Int = T.SURFACE3,
        selected: Int? = null,
        focusStroke: Int = T.BRAND,
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
        drawable.addState(intArrayOf(android.R.attr.state_pressed), Ui.round(pressed, radius, T.STROKE, T.Line.HAIR))
        drawable.addState(intArrayOf(), Ui.round(normal, radius, T.STROKE, T.Line.HAIR))
        view.background = drawable
        view.isFocusable = true
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
    size: Int = 72,
    private val action: () -> Unit = {},
) : LinearLayout(context) {
    private val circle = FrameLayout(context)
    private val icon = Icon(context, iconKind, T.TEXT)
    private val caption: TextView? = if (label.isNotEmpty()) Ui.text(context, label, T.Type.MICRO, T.TEXT2) else null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        circle.addView(icon, Ui.flp(Ui.u(if (size < 64) 22 else 28), Ui.u(if (size < 64) 22 else 28), Gravity.CENTER))
        Focusable.states(circle, size / 2f, selected = T.BRAND)
        circle.contentDescription = label
        circle.setOnClickListener { if (isEnabled) action() }
        addView(circle, LinearLayout.LayoutParams(Ui.u(size), Ui.u(size)))
        caption?.let { addView(it, Ui.lp(Ui.WRAP, Ui.WRAP, t = 4)) }
        isFocusable = false
        isClickable = false
        contentDescription = label
    }

    fun select(selected: Boolean) {
        circle.isSelected = selected
        icon.color = if (selected) T.SURFACE else T.TEXT
        caption?.setTextColor(if (selected) T.BRAND else T.TEXT2)
    }

    fun setButtonEnabled(enabled: Boolean) {
        isEnabled = enabled
        circle.isEnabled = enabled
        alpha = if (enabled) 1f else T.DISABLED_ALPHA
    }

    fun focusView(): View = circle
}

/** Dial natif léger : un View Canvas dessine cadran, graduations, arc et repère de mesure. */
class Dial(context: Context, iconKind: Icon.Kind) : LinearLayout(context) {
    private val face = DialFace(context)
    private val minus = RoundButton(context, Icon.Kind.MINUS, size = 56) { onSelect(); onMinus() }
    private val plus = RoundButton(context, Icon.Kind.PLUS, size = 56) { onSelect(); onPlus() }

    var onSelect: () -> Unit = {}
    var onMinus: () -> Unit = {}
    var onPlus: () -> Unit = {}
    var repeatEnabled: Boolean = true

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val faceBox = FrameLayout(context)
        faceBox.addView(face, Ui.flp(Ui.u(216), Ui.u(216)))
        faceBox.addView(
            Icon(context, iconKind, T.TEXT2),
            Ui.flp(Ui.u(26), Ui.u(26), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.u(29) },
        )
        addView(faceBox, LinearLayout.LayoutParams(Ui.u(216), Ui.u(216)))
        val actions = Ui.horizontal(context, Gravity.CENTER)
        actions.addView(minus, Ui.lp(Ui.u(56), Ui.u(56), r = 10))
        actions.addView(plus, Ui.lp(Ui.u(56), Ui.u(56)))
        addView(actions, Ui.lp(Ui.MATCH, Ui.u(56), t = 4))
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
        private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
        private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
        private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val thinTypeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        private val lightTypeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val bounds = RectF()
        private var value = "0"
        private var unit = ""
        private var legend = ""
        private var progress = 0f
        private var marker: Float? = null

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

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = minOf(width, height).toFloat()
            if (size <= 0f) return
            val cx = width / 2f
            val cy = height / 2f
            val radius = size * 0.455f
            circle.color = T.SURFACE
            circle.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius, circle)

            line.strokeWidth = Ui.uf(if (isFocused) T.Line.FOCUS else if (isSelected) T.Line.SELECTED else T.Line.HAIR)
            line.color = if (isFocused || isSelected) T.BRAND else T.STROKE
            bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(bounds, 135f, 270f, false, line)

            line.strokeWidth = Ui.uf(2.2f)
            line.color = T.SURFACE3
            canvas.drawArc(bounds, 135f, 270f, false, line)
            line.strokeWidth = Ui.uf(3.5f)
            // Le vert botanique est réservé aux arcs et grandes icônes : il ne porte jamais de texte.
            line.color = T.BOTANICAL
            if (progress > 0f) canvas.drawArc(bounds, 135f, 270f * progress, false, line)

            // 46 graduations sur l'arc actif de 270° (pas de 6°).
            line.strokeWidth = Ui.uf(1.5f)
            line.color = T.TEXT3
            for (i in 0..45) {
                val angle = Math.toRadians((135 + i * 6).toDouble())
                val major = i % 5 == 0
                val inner = radius - Ui.uf(if (major) 13f else 8f)
                val outer = radius - Ui.uf(2f)
                canvas.drawLine(
                    cx + (inner * Math.cos(angle)).toFloat(),
                    cy + (inner * Math.sin(angle)).toFloat(),
                    cx + (outer * Math.cos(angle)).toFloat(),
                    cy + (outer * Math.sin(angle)).toFloat(),
                    line,
                )
            }

            marker?.let { fraction ->
                val angle = Math.toRadians((135 + 270 * fraction).toDouble())
                line.strokeWidth = Ui.uf(2f)
                line.color = T.TEXT
                val inner = radius - Ui.uf(18f)
                val outer = radius + Ui.uf(2f)
                canvas.drawLine(
                    cx + (inner * Math.cos(angle)).toFloat(),
                    cy + (inner * Math.sin(angle)).toFloat(),
                    cx + (outer * Math.cos(angle)).toFloat(),
                    cy + (outer * Math.sin(angle)).toFloat(),
                    line,
                )
            }

            valuePaint.color = T.TEXT
            valuePaint.typeface = thinTypeface
            valuePaint.textSize = Ui.uf(T.Type.DIAL_VALUE)
            val available = size * 0.66f
            // Un tiret de consigne absente ne doit pas se lire comme un trait de règle.
            if (value.isEmpty() || value == "—") valuePaint.textSize = Ui.uf(T.Type.DIAL_VALUE * 0.62f)
            while (valuePaint.measureText(value) > available && valuePaint.textSize > Ui.uf(42f)) {
                valuePaint.textSize -= Ui.uf(2f)
            }
            unitPaint.color = T.TEXT2
            unitPaint.typeface = lightTypeface
            unitPaint.textSize = Ui.uf(T.Type.DIAL_UNIT)
            val valueWidth = valuePaint.measureText(value)
            val unitWidth = unitPaint.measureText(unit)
            val gap = if (unit.isEmpty()) 0f else Ui.uf(6f)
            val total = valueWidth + gap + unitWidth
            val start = cx - total / 2f
            val baseline = cy + Ui.uf(20f)
            canvas.drawText(value, start, baseline, valuePaint)
            if (unit.isNotEmpty()) canvas.drawText(unit, start + valueWidth + gap, baseline, unitPaint)

            legendPaint.color = T.TEXT2
            legendPaint.typeface = mediumTypeface
            legendPaint.textSize = Ui.uf(T.Type.DIAL_LEGEND)
            // Deux lignes au plus : la légende porte la nature de la consigne ET la mesure réelle,
            // on ne veut pas perdre la valeur derrière une ellipse.
            var legendY = cy + Ui.uf(50f)
            for (lineText in wrapLegend(legend, size * 0.74f, legendPaint)) {
                canvas.drawText(lineText, cx, legendY, legendPaint)
                legendY += Ui.uf(17f)
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
}

/** Contrôle principal tactile, à forte hiérarchie et avec focus explicite. */
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
        minimumHeight = Ui.u(68)
        icon.color = T.SURFACE
        addView(icon, Ui.lp(Ui.u(26), Ui.u(26), r = 10))
        addView(title)
        Focusable.states(this, T.Radius.PILL, T.BRAND, T.BRAND_PRESSED, focusStroke = T.SURFACE)
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
    private val circle = FrameLayout(context)
    private val icon = Icon(context, Icon.Kind.BOLT, T.BRAND)
    private val caption = Ui.text(context, label, T.Type.MICRO, T.TEXT2, Ui.MEDIUM)
    private var held = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        circle.addView(icon, Ui.flp(Ui.u(28), Ui.u(28), Gravity.CENTER))
        Focusable.states(circle, 36f, selected = T.BRAND)
        circle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!held && isEnabled) {
                        held = true
                        view.isPressed = true
                        circle.background = Ui.round(T.WARN, 36f, T.WARN, T.Line.HAIR)
                        icon.color = T.TEXT
                        onDown()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (held) {
                        held = false
                        view.isPressed = false
                        icon.color = T.BRAND
                        Focusable.states(circle, 36f, selected = T.BRAND)
                        onUp()
                    }
                }
            }
            true
        }
        circle.setOnClickListener { /* Turbo est volontairement une action maintenue. */ }
        addView(circle, LinearLayout.LayoutParams(Ui.u(72), Ui.u(72)))
        addView(caption, Ui.lp(Ui.WRAP, Ui.WRAP, t = 4))
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
    private val value = Ui.text(context, "—", T.Type.H1, T.TEXT, Ui.LIGHT)
    private val status = Ui.text(context, tr("Base non connectée"), T.Type.CAPTION, T.TEXT2)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = Ui.round(T.SURFACE, T.Radius.CARD, T.STROKE, T.Line.HAIR)
        setPadding(Ui.u(12), Ui.u(10), Ui.u(12), Ui.u(10))
        addView(icon, Ui.lp(Ui.u(28), Ui.u(28), b = 2))
        addView(value)
        addView(status, Ui.lp(Ui.WRAP, Ui.WRAP, t = 2))
    }

    fun set(weightG: Int, online: Boolean) {
        value.text = if (online) "$weightG g" else "—"
        status.text = if (online) tr("Balance") else tr("Base non connectée")
        // La phrase d'origine est réutilisée comme description lue par les lecteurs d'écran.
        contentDescription = if (online) tr("Balance : %d g", weightG) else tr("Base non connectée")
        value.setTextColor(if (online) T.TEXT else T.TEXT2)
        icon.color = if (online) T.BRAND else T.TEXT2
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
