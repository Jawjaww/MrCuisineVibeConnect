package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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
import app.toque.ui.design.MiniCircle
import app.toque.ui.design.RoundButton
import app.toque.ui.screens.ScaleScreen
import app.toque.ui.design.ToggleRoundButton
import app.toque.ui.design.T

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
    private lateinit var directionToggle: ToggleRoundButton
    private lateinit var accessoryToggle: ToggleRoundButton
    private lateinit var startButton: PrimaryButton
    private lateinit var hint: TextView
    private lateinit var turbo: HoldRoundButton
    private lateinit var weightMini: MiniCircle
    private lateinit var tempMini: MiniCircle
    private var dialSize = Dial.SIZE

    private var live = false
    private var wasLive = false
    private var pendingEdit = false
    private var timeEdited = false
    private var configuredSeconds = seconds
    private var actualTemp = 0
    private var hasSample = false
    private val pushLive = Runnable { sendAdjust() }

    override fun build(): View {
        // Quatre étages : mini-cadrans 140 en haut, cadrans, puis les commandes SUR LES FLANCS
        // des cadrans mais nettement sous la ligne des − / +. Les ancrages passent par la gravité :
        // la fenêtre réelle est plus étroite que l'écran quand il y a une encoche.
        val button = 96
        val gap = 6
        // Plafonds mesurés : 365 par la hauteur (mini-cadrans + − / + + commandes),
        // 421 par la largeur. On retient 300, le plus grand qui laisse Démarrer dans son angle.
        dialSize = 300
        val groupWidth = 3 * dialSize + 2 * gap
        val period = (groupWidth - button) / 3

        val dash = FrameLayout(c)
        dash.setPadding(Ui.u(16), Ui.u(8), Ui.u(16), Ui.u(8))

        // --- Coins hauts : poids à gauche, température à droite.
        weightMini = MiniCircle(c, Icon.Kind.SCALE, showBar = true, size = 140)
        dash.addView(weightMini, FrameLayout.LayoutParams(Ui.u(140), Ui.u(140), Gravity.TOP or Gravity.START))
        tempMini = MiniCircle(c, Icon.Kind.THERMO, size = 140)
        dash.addView(
            tempMini,
            FrameLayout.LayoutParams(Ui.u(140), Ui.u(140), Gravity.TOP or Gravity.END).apply {
                rightMargin = Ui.u(16)
            },
        )

        // --- Grille des cadrans et des commandes, d'une seule pièce pour que les colonnes
        // restent alignées : une commande dans chaque creux, sous la ligne des − / +.
        val grid = FrameLayout(c)
        timeDial = Dial(c, Icon.Kind.CLOCK, size = dialSize)
        speedDial = Dial(c, Icon.Kind.SPEED, Icon.Kind.SPEED, size = dialSize)
        tempDial = Dial(c, Icon.Kind.THERMO, size = dialSize)
        val all = listOf(timeDial, speedDial, tempDial)
        for ((index, dial) in all.withIndex()) {
            dial.onSelect = { selected = index; refresh() }
            dial.onMinus = { change(index, -1) }
            dial.onPlus = { change(index, 1) }
            grid.addView(
                dial,
                FrameLayout.LayoutParams(Ui.u(dialSize), Ui.WRAP).apply {
                    leftMargin = Ui.u(index * (dialSize + gap))
                },
            )
        }

        directionToggle = ToggleRoundButton(c) { setReverse(!reverse) }
        directionToggle.configure(tr("Inverse"), Icon.Kind.REVERSE, tr("Sens normal"), Icon.Kind.SYNC)
        accessoryToggle = ToggleRoundButton(c) {
            setAccessory(if (accessory == Accessory.BLADE) Accessory.WHISK else Accessory.BLADE)
        }
        accessoryToggle.configure(tr("Fouet"), Icon.Kind.WHISK, tr("Lame"), Icon.Kind.BLADE)
        turbo = HoldRoundButton(c, tr("Turbo"), onDown = {
            a.machine.turboDown(accessory) { verdict -> if (!verdict.ok) a.refusal(verdict) }
        }, onUp = { a.machine.turboUp() })
        val weigh = RoundButton(c, Icon.Kind.SCALE, tr("Peser")) { a.push(ScaleScreen(a)) }

        val controlsTop = dialSize + 37 + 4
        val controls = listOf(weigh, directionToggle, accessoryToggle, turbo)
        for ((index, control) in controls.withIndex()) {
            grid.addView(
                control,
                FrameLayout.LayoutParams(Ui.u(button), Ui.WRAP).apply {
                    leftMargin = Ui.u(index * period)
                    topMargin = Ui.u(controlsTop)
                },
            )
        }
        dash.addView(
            grid,
            // Décalée à gauche : le bouton Démarrer occupe l'angle bas-droit et ne doit
            // recouvrir aucune commande.
            FrameLayout.LayoutParams(Ui.u(groupWidth), Ui.WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = Ui.u(0)
            },
        )

        // --- Démarrer, dans l'angle bas-droit.
        startButton = PrimaryButton(c, tr("Démarrer")) { start() }
        dash.addView(
            startButton,
            FrameLayout.LayoutParams(Ui.u(190), Ui.u(56), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = Ui.u(16)
                bottomMargin = Ui.u(6)
            },
        )

        // Message d'aide dans la bande haute, entre les deux mini-cadrans.
        hint = Ui.text(c, "", T.Type.SMALL, T.WARN).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.05f)
            visibility = View.GONE
        }
        dash.addView(
            hint,
            FrameLayout.LayoutParams(Ui.MATCH, Ui.u(90), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                leftMargin = Ui.u(150)
                rightMargin = Ui.u(150)
                topMargin = Ui.u(24)
            },
        )

        refresh()
        return dash
    }

    /** Position absolue dans la grille du tableau de bord, en unités 1024×600. */
    private fun at(x: Int, y: Int, w: Int, h: Int, gravity: Int = Gravity.TOP or Gravity.START) =
        FrameLayout.LayoutParams(Ui.u(w), Ui.u(h), gravity).apply {
            leftMargin = Ui.u(x)
            topMargin = Ui.u(y)
        }

    private val tempSteps: IntArray get() = intArrayOf(0) + (1..19).toList().toIntArray()

    private companion object {
        const val DIAL_TIME = 0
        const val DIAL_SPEED = 1
        const val DIAL_TEMP = 2
    }

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
            DIAL_TIME -> seconds = nextSeconds(seconds, dir)
            DIAL_TEMP -> {
                val index = tempSteps.indexOf(tempLevel).coerceAtLeast(0)
                tempLevel = tempSteps[(index + dir).coerceIn(0, tempSteps.size - 1)]
                if (tempLevel > 0 && speed > Limits.MAX_SPEED_HEATED) {
                    speed = Limits.MAX_SPEED_HEATED
                    a.toast(tr("En chauffe, vitesse %d maximum", Limits.MAX_SPEED_HEATED))
                }
            }
            DIAL_SPEED -> {
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
            if (card == DIAL_TIME) timeEdited = true
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

    /**
     * Vocabulaire d'origine du projet : il décrivait déjà la nature de la consigne
     * (« Tiédir », « Cuisson », « Ébullition », « Sans chauffe »). On le réutilise au lieu
     * de le remplacer, et on accole la mesure réelle.
     */
    private fun tempQuality(): String {
        if (tempLevel == 0) return tr("Sans chauffe")
        val t = Mcc.celsiusForLevel(tempLevel)
        return when {
            t >= 100 -> tr("Ébullition")
            t >= 60 -> tr("Cuisson")
            else -> tr("Tiédir")
        }
    }

    private fun tempValue(): String = if (tempLevel > 0) Mcc.celsiusForLevel(tempLevel).toString() else "0"

    private fun tempLegend(): String {
        val measured = if (hasSample) tr("Mesurée : %d °C", actualTemp) else tr("Mesure indisponible")
        return tempQuality() + " · " + measured
    }

    private fun tempProgress(): Float =
        if (tempLevel > 0) (Mcc.celsiusForLevel(tempLevel) / 130f).coerceIn(0f, 1f) else 0f

    private fun measuredMarker(): Float? =
        if (hasSample) (actualTemp / 130f).coerceIn(0f, 1f) else null

    private fun speedLegend(): String = when (speed) {
        1 -> tr("Mijoter")
        2, 3 -> tr("Mélanger")
        in 4..6 -> tr("Hacher")
        else -> tr("Mixer")
    } + if (reverse) " · " + tr("inverse") else ""

    private fun refresh() {
        val timeLegend = if (live) tr("restant") else if (speed > 6) "max 15 min" else "max 99 min"
        timeDial.set(Ui.clock(seconds * 1000L), "", timeLegend, (seconds / 5940f).coerceIn(0f, 1f))

        tempDial.set(tempValue(), "°C", tempLegend(), tempProgress(), measuredMarker())
        speedDial.set(speed.toString(), "", speedLegend(), speed / 10f,
            if (a.snapshot.speedLevel > 0) (a.snapshot.speedLevel / 10f).coerceIn(0f, 1f) else null)

        timeDial.select(selected == DIAL_TIME)
        speedDial.select(selected == DIAL_SPEED)
        tempDial.select(selected == DIAL_TEMP)
        directionToggle.set(reverse)
        accessoryToggle.set(accessory == Accessory.WHISK)
        accessoryToggle.setButtonEnabled(!live)

        val warnings = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        setHint(warnings.firstOrNull()?.message
            ?: if (a.snapshot.hot && speed >= 4) tr("Contenu chaud : confirmation demandée") else "")
        startButton.setEnabledState(warnings.isEmpty())
    }

    override fun onSnapshot(s: Snapshot) {
        hasSample = s.hasSample
        actualTemp = if (s.hasSample) s.actualTempC else 0
        tempDial.set(tempValue(), "°C", tempLegend(), tempProgress(), measuredMarker())
        speedDial.set(speed.toString(), "", speedLegend(), speed / 10f,
            if (s.speedLevel > 0) (s.speedLevel / 10f).coerceIn(0f, 1f) else null)

        tempMini.set(if (s.hasSample) "${s.actualTempC} °C" else "—", s.hasSample)
        weightMini.set(if (s.hasSample) "${s.weightG} g" else "—", s.hasSample)
        weightMini.contentDescription =
            if (s.hasSample) tr("Balance : %d g", s.weightG) else tr("Base non connectée")

        val manualJob = s.kind?.name == "MANUAL" &&
            (s.state == JobState.RUNNING || s.state == JobState.STARTING || s.state == JobState.PAUSED)
        live = manualJob && s.state != JobState.PAUSED
        timeDial.repeatEnabled = true
        tempDial.repeatEnabled = !live
        speedDial.repeatEnabled = !live
        accessoryToggle.setButtonEnabled(!live)

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
        // La barre machine a son propre bouton de reprise : Démarrer laisse la place entière
        // à la zone basse dès qu'un programme est actif.
        startButton.visibility = if (s.jobActive || s.turboActive || s.hasFault) View.GONE else View.VISIBLE
        startButton.setLabel(if (s.state == JobState.PAUSED && manualJob) tr("Reprendre") else tr("Démarrer"))
        turbo.setButtonEnabled(!busy || s.turboActive)
        if (live) setHint(tr("Réglages appliqués en direct"))
        else if (!busy) refreshHintOnly()
    }

    private fun refreshHintOnly() {
        val warnings = SafetyPolicy.checkJob(spec(), if (a.snapshot.hasSample) a.snapshot.actualTempC else null)
            .filter { it.code != SafetyPolicy.HOT_BLEND }
        setHint(warnings.firstOrNull()?.message
            ?: if (a.snapshot.hot && speed >= 4) tr("Contenu chaud : confirmation demandée") else "")
    }

    /** La ligne d'aide ne réserve de la place que lorsqu'elle a quelque chose à dire. */
    private fun setHint(text: String) {
        hint.text = text
        hint.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
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
