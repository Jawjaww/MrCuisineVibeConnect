package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.i18n.trKey
import app.toque.core.machine.Accessory
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobState
import app.toque.core.machine.Limits
import app.toque.core.machine.Programs
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Snapshot
import app.toque.core.protocol.Direction
import app.toque.core.protocol.Mcc
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui
import app.toque.ui.design.Dial
import app.toque.ui.design.HoldRoundButton
import app.toque.ui.design.PrimaryButton
import app.toque.ui.design.RoundButton
import app.toque.ui.design.SurfaceCard
import app.toque.ui.design.T
import app.toque.ui.design.WeightBadge

/** Cuisson manuelle : nouvelle présentation, logique moteur et politique de sécurité inchangées. */
class ManualScreen(a: MainActivity, preset: app.toque.core.recipes.StepMachine? = null) : Screen(a) {
    override val title = JobKind.MANUAL.label
    private var seconds = preset?.seconds?.takeIf { it > 0 } ?: 60
    private var tempLevel = preset?.temperatureLevel ?: 0
    private var speed = preset?.speed?.coerceIn(1, 10) ?: 2
    private var reverse = preset?.reverse ?: false
    private var accessory = preset?.accessory ?: Accessory.BLADE
    private var selected = 0

    private lateinit var timeDial: Dial
    private lateinit var tempDial: Dial
    private lateinit var speedDial: Dial
    private lateinit var normalButton: RoundButton
    private lateinit var reverseButton: RoundButton
    private lateinit var bladeButton: RoundButton
    private lateinit var whiskButton: RoundButton
    private lateinit var weightBadge: WeightBadge
    private lateinit var startButton: PrimaryButton
    private lateinit var hint: TextView
    private lateinit var turbo: HoldRoundButton

    private var live = false
    private var wasLive = false
    private var pendingEdit = false
    private var timeEdited = false
    private var configuredSeconds = seconds
    private var actualTemp = 0
    private var hasSample = false
    private val pushLive = Runnable { sendAdjust() }

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(24), Ui.u(8), Ui.u(24), Ui.u(16))

        val left = Ui.vertical(c)
        val dials = Ui.horizontal(c, Gravity.TOP)
        timeDial = Dial(c, Icon.Kind.CLOCK)
        tempDial = Dial(c, Icon.Kind.THERMO)
        speedDial = Dial(c, Icon.Kind.SPEED)
        val all = listOf(timeDial, tempDial, speedDial)
        for ((index, dial) in all.withIndex()) {
            dial.onSelect = { selected = index; refresh() }
            dial.onMinus = { change(index, -1) }
            dial.onPlus = { change(index, 1) }
            dials.addView(dial, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply {
                if (index < 2) rightMargin = Ui.u(16)
            })
        }
        left.addView(dials, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))

        val options = Ui.horizontal(c, Gravity.CENTER_VERTICAL)
        normalButton = RoundButton(c, Icon.Kind.SYNC, tr("Normal")) { setReverse(false) }
        reverseButton = RoundButton(c, Icon.Kind.REVERSE, tr("Inverse")) { setReverse(true) }
        bladeButton = RoundButton(c, Icon.Kind.BLADE, tr("Lame")) { setAccessory(Accessory.BLADE) }
        whiskButton = RoundButton(c, Icon.Kind.WHISK, tr("Fouet")) { setAccessory(Accessory.WHISK) }
        val modes = listOf(normalButton, reverseButton, bladeButton, whiskButton)
        for ((index, button) in modes.withIndex()) {
            options.addView(button, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply {
                if (index < modes.lastIndex) rightMargin = Ui.u(8)
            })
        }
        turbo = HoldRoundButton(c, tr("Turbo"), onDown = {
            a.machine.turboDown(accessory) { verdict -> if (!verdict.ok) a.refusal(verdict) }
        }, onUp = { a.machine.turboUp() })
        options.addView(turbo, Ui.lp(Ui.u(88), Ui.u(104)))
        left.addView(options, Ui.lp(Ui.MATCH, Ui.WRAP, t = 12))

        root.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { rightMargin = Ui.u(20) })

        val right = SurfaceCard(c).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.u(14), Ui.u(12), Ui.u(14), Ui.u(14))
        }
        weightBadge = WeightBadge(c)
        right.addView(weightBadge, Ui.lp(Ui.MATCH, Ui.u(120)))
        hint = Ui.text(c, "", T.Type.SMALL, T.WARN).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.05f)
        }
        right.addView(hint, Ui.lp(Ui.MATCH, Ui.WRAP, t = 8))
        startButton = PrimaryButton(c, tr("Démarrer")) { start() }
        right.addView(startButton, Ui.lp(Ui.MATCH, Ui.u(68), t = 10))
        root.addView(right, LinearLayout.LayoutParams(Ui.u(276), Ui.MATCH))
        refresh()
        return root
    }

    private val tempSteps: IntArray get() = intArrayOf(0) + (1..19).toList().toIntArray()

    private fun nextSeconds(s: Int, dir: Int): Int {
        val step = when {
            dir > 0 && s < 60 || dir < 0 && s <= 60 -> 5
            dir > 0 && s < 600 || dir < 0 && s <= 600 -> 30
            else -> 60
        }
        val max = ((if (speed > 6) Limits.MAX_MS_HIGH_SPEED else Limits.MAX_MS_DEFAULT) / 1000).toInt()
        return (s + dir * step).coerceIn(5, max)
    }

    private fun change(card: Int, dir: Int) {
        val snapshot = a.snapshot
        when (card) {
            0 -> seconds = nextSeconds(seconds, dir)
            1 -> {
                val index = tempSteps.indexOf(tempLevel).coerceAtLeast(0)
                tempLevel = tempSteps[(index + dir).coerceIn(0, tempSteps.size - 1)]
                if (tempLevel > 0 && speed > Limits.MAX_SPEED_HEATED) {
                    speed = Limits.MAX_SPEED_HEATED
                    a.toast(tr("En chauffe, vitesse %d maximum", Limits.MAX_SPEED_HEATED))
                }
            }
            2 -> {
                var max = 10
                var why = trKey("Vitesse %d maximum")
                if (tempLevel > 0) { max = Limits.MAX_SPEED_HEATED; why = trKey("En chauffe, vitesse %d maximum") }
                if (reverse && max > Limits.MAX_SPEED_REVERSE) { max = Limits.MAX_SPEED_REVERSE; why = trKey("En sens inverse, vitesse %d maximum") }
                if (accessory.maxSpeed < max) { max = accessory.maxSpeed; why = trKey("Avec le fouet, vitesse %d maximum") }
                val next = (speed + dir).coerceIn(1, 10)
                if (next > max) a.toast(tr(why, max)) else speed = next
            }
        }
        val maxSeconds = ((if (speed > 6) Limits.MAX_MS_HIGH_SPEED else Limits.MAX_MS_DEFAULT) / 1000).toInt()
        if (seconds > maxSeconds) seconds = maxSeconds
        refresh()
        if (live && snapshot.kind?.name == "MANUAL") {
            if (card == 0) timeEdited = true
            pendingEdit = true
            view.removeCallbacks(pushLive)
            view.postDelayed(pushLive, 350)
        }
    }

    private fun setReverse(value: Boolean) {
        if (value && speed > Limits.MAX_SPEED_REVERSE) {
            speed = Limits.MAX_SPEED_REVERSE
            a.toast(tr("En sens inverse, vitesse %d maximum", Limits.MAX_SPEED_REVERSE))
        }
        reverse = value
        refresh()
        if (live) {
            pendingEdit = true
            view.removeCallbacks(pushLive)
            view.postDelayed(pushLive, 200)
        }
    }

    private fun setAccessory(value: Accessory) {
        if (live) return
        accessory = value
        if (speed > value.maxSpeed) {
            speed = value.maxSpeed
            a.toast(tr("%s : vitesse %d maximum", value.label, value.maxSpeed))
        }
        refresh()
    }

    private fun spec(hot: Boolean = false) = Programs.manual(
        speed, tempLevel, if (reverse) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE,
        seconds * 1000L, accessory, hot,
    )

    private fun start() {
        val snapshot = a.snapshot
        if (snapshot.state == JobState.PAUSED && snapshot.kind?.name == "MANUAL") {
            a.machine.resume(snapshot.hotBlendConfirmed) { a.refusal(it) }
            return
        }
        configuredSeconds = seconds
        a.startJob(spec())
    }

    private fun sendAdjust() {
        pendingEdit = false
        val snapshot = a.snapshot
        if (!snapshot.jobActive || snapshot.kind?.name != "MANUAL") return
        // La durée n'est renvoyée que si elle a été modifiée : changer la vitesse ne relance pas le minuteur.
        val total: Long? = if (timeEdited) snapshot.elapsedMs + seconds * 1000L else null
        timeEdited = false
        a.machine.adjust(speed, tempLevel, reverse, total, snapshot.hotBlendConfirmed) { verdict ->
            if (!verdict.ok && verdict.has(SafetyPolicy.HOT_BLEND)) {
                a.confirm(
                    tr("Contenu chaud"),
                    tr("Le bol est à %d °C. Passer à la vitesse %d ?", a.snapshot.actualTempC, speed),
                    tr("Confirmer"),
                    icon = Icon.Kind.THERMO,
                ) {
                    a.machine.adjust(speed, tempLevel, reverse, total, true) { a.refusal(it) }
                }
            } else a.refusal(verdict)
        }
    }

    private fun refresh() {
        val timeLegend = if (live) tr("restant") else if (speed > 6) "max 15 min" else "max 99 min"
        timeDial.set(Ui.clock(seconds * 1000L), "", timeLegend, (seconds / 5940f).coerceIn(0f, 1f))

        val target = if (tempLevel > 0) Mcc.celsiusForLevel(tempLevel) else 0
        val measured = if (hasSample) tr("Mesurée : %d °C", actualTemp) else tr("Mesure indisponible")
        tempDial.set(
            if (target > 0) target.toString() else "—",
            "°C",
            if (target > 0) tr("Consigne · %s", measured) else measured,
            if (target > 0) (target / 130f).coerceIn(0f, 1f) else 0f,
            if (hasSample) (actualTemp / 130f).coerceIn(0f, 1f) else null,
        )

        val speedLegend = when (speed) {
            1 -> tr("Mijoter")
            2, 3 -> tr("Mélanger")
            in 4..6 -> tr("Hacher")
            else -> tr("Mixer")
        } + if (reverse) " · " + tr("inverse") else ""
        speedDial.set(speed.toString(), "", speedLegend, speed / 10f,
            if (a.snapshot.speedLevel > 0) (a.snapshot.speedLevel / 10f).coerceIn(0f, 1f) else null)

        timeDial.select(selected == 0)
        tempDial.select(selected == 1)
        speedDial.select(selected == 2)
        normalButton.select(!reverse)
        reverseButton.select(reverse)
        bladeButton.select(accessory == Accessory.BLADE)
        whiskButton.select(accessory == Accessory.WHISK)
        bladeButton.setButtonEnabled(!live)
        whiskButton.setButtonEnabled(!live)

        val warnings = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        hint.text = warnings.firstOrNull()?.message
            ?: if (a.snapshot.hot && speed >= 4) tr("Contenu chaud : confirmation demandée") else ""
        startButton.setEnabledState(warnings.isEmpty())
    }

    override fun onSnapshot(s: Snapshot) {
        hasSample = s.hasSample
        actualTemp = if (s.hasSample) s.actualTempC else 0
        tempDial.set(
            if (tempLevel > 0) Mcc.celsiusForLevel(tempLevel).toString() else "—",
            "°C",
            if (s.hasSample) tr("Mesurée : %d °C", s.actualTempC) else tr("Mesure indisponible"),
            if (tempLevel > 0) (Mcc.celsiusForLevel(tempLevel) / 130f).coerceIn(0f, 1f) else 0f,
            if (s.hasSample) (s.actualTempC / 130f).coerceIn(0f, 1f) else null,
        )
        val speedLegend = when (speed) {
            1 -> tr("Mijoter")
            2, 3 -> tr("Mélanger")
            in 4..6 -> tr("Hacher")
            else -> tr("Mixer")
        } + if (reverse) " · " + tr("inverse") else ""
        speedDial.set(speed.toString(), "", speedLegend, speed / 10f,
            if (s.speedLevel > 0) (s.speedLevel / 10f).coerceIn(0f, 1f) else null)
        weightBadge.set(s.weightG, s.hasSample)

        val manualJob = s.kind?.name == "MANUAL" &&
            (s.state == JobState.RUNNING || s.state == JobState.STARTING || s.state == JobState.PAUSED)
        live = manualJob && s.state != JobState.PAUSED
        timeDial.repeatEnabled = true
        tempDial.repeatEnabled = !live
        speedDial.repeatEnabled = !live

        if (live && !pendingEdit) {
            val remaining = ((s.remainingMs + 999) / 1000).toInt().coerceAtLeast(1)
            if (remaining != seconds) {
                seconds = remaining
                timeDial.set(Ui.clock(seconds * 1000L), "", tr("restant"), (seconds / 5940f).coerceIn(0f, 1f))
            }
        }
        if (wasLive && !live && !manualJob) {
            seconds = configuredSeconds
            refresh()
        }
        wasLive = live

        val busy = s.busy
        startButton.visibility = if (busy && s.state != JobState.PAUSED) View.GONE else View.VISIBLE
        startButton.setLabel(if (s.state == JobState.PAUSED && manualJob) tr("Reprendre") else tr("Démarrer"))
        turbo.setButtonEnabled(!busy || s.turboActive)
        if (live) hint.text = tr("Réglages appliqués en direct")
        else if (!busy) refreshHintOnly()
    }

    private fun refreshHintOnly() {
        val warnings = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        hint.text = warnings.firstOrNull()?.message
            ?: if (a.snapshot.hot && speed >= 4) tr("Contenu chaud : confirmation demandée") else ""
    }

    override fun onKnob(delta: Int): Boolean {
        change(selected, delta)
        return true
    }

    override fun onKnobPress(): Boolean {
        val snapshot = a.snapshot
        when {
            snapshot.state == JobState.RUNNING -> a.machine.pause { a.refusal(it) }
            else -> start()
        }
        return true
    }
}
