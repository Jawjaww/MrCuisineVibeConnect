package app.toque.ui.design

import android.graphics.Typeface
import app.toque.core.design.Palette

/** Façade des tokens visuels Toque. Dimensions exprimées dans la grille 1024×600. */
object T {
    val BG = Palette.BG
    val SURFACE = Palette.SURFACE
    val SURFACE2 = Palette.SURFACE2
    val SURFACE3 = Palette.SURFACE3
    val STROKE = Palette.STROKE
    val STROKE_STRONG = Palette.STROKE_STRONG
    val TEXT = Palette.TEXT
    val TEXT2 = Palette.TEXT2
    val TEXT3 = Palette.TEXT3
    val BRAND = Palette.BRAND
    val BRAND_PRESSED = Palette.BRAND_PRESSED
    val BOTANICAL = Palette.BOTANICAL
    val ARC_TRACK = Palette.ARC_TRACK
    val TICK = Palette.TICK
    val START_TOP = Palette.START_TOP
    val START_BOTTOM = Palette.START_BOTTOM
    val START_TOP_PRESSED = Palette.START_TOP_PRESSED
    val START_BOTTOM_PRESSED = Palette.START_BOTTOM_PRESSED
    val WASH_BRAND = Palette.WASH_BRAND
    val OK = Palette.OK
    val WARN = Palette.WARN
    val DANGER = Palette.DANGER
    val COOL = Palette.COOL
    val DANGER_WASH = Palette.DANGER_WASH
    val WARN_WASH = Palette.WARN_WASH
    val DISABLED_BG = Palette.DISABLED_BG
    val DISABLED_TEXT = Palette.DISABLED_TEXT
    val BAR = Palette.BAR
    val RIPPLE = Palette.RIPPLE
    val SCRIM = Palette.SCRIM
    val RECIPE_PLACEHOLDERS = Palette.RECIPE_PLACEHOLDERS

    object Type {
        const val DISPLAY = 120f
        const val H1 = 40f
        const val H2 = 28f
        const val H3 = 28f
        const val H4 = 20f
        const val BODY = 18f
        const val SMALL = 16f
        const val MICRO = 15f
        const val CAPTION = 13f
        const val DIAL_VALUE = 60f
        const val DIAL_UNIT = 22f
        const val DIAL_LEGEND = 15f
    }

    object Space {
        const val S4 = 4f
        const val S8 = 8f
        const val S12 = 12f
        const val S16 = 16f
        const val S20 = 20f
        const val S24 = 24f
        const val S32 = 32f
        const val S40 = 40f
        const val S56 = 56f
    }

    object Radius {
        const val PILL = 999f
        const val CARD = 28f
        const val CARD_SM = 20f
        const val BADGE = 14f
    }

    object Line {
        const val HAIR = 1f
        const val SELECTED = 2f
        const val FOCUS = 2.5f
    }

    /** Un seul traitement du désactivé pour tout le système. */
    const val DISABLED_ALPHA = 0.38f

    enum class TypefaceRole { DISPLAY, LIGHT, REGULAR, MEDIUM, BOLD }

    fun typeface(role: TypefaceRole): Typeface = when (role) {
        TypefaceRole.DISPLAY, TypefaceRole.LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        TypefaceRole.REGULAR -> Typeface.create("sans-serif", Typeface.NORMAL)
        TypefaceRole.MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        TypefaceRole.BOLD -> Typeface.create("sans-serif", Typeface.BOLD)
    }
}
