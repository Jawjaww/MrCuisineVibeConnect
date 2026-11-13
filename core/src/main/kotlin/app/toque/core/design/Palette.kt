package app.toque.core.design

/** Palette sémantique Ivoire & Vert botanique, testable sans Android. */
object Palette {
    val BG = 0xFFF8F1EA.toInt()
    val SURFACE = 0xFFFBF6F1.toInt()
    val SURFACE2 = 0xFFF3EADF.toInt()
    val SURFACE3 = 0xFFEAE0D3.toInt()
    val STROKE = 0xFFE3D9CB.toInt()
    val STROKE_STRONG = 0xFF8A7C66.toInt()
    val TEXT = 0xFF252F2C.toInt()
    val TEXT2 = 0xFF566056.toInt()
    val TEXT3 = 0xFF5A6359.toInt()
    val BRAND = 0xFF37562E.toInt()
    val BRAND_PRESSED = 0xFF2C4525.toInt()
    val BOTANICAL = 0xFF7A962B.toInt()
    val WASH_BRAND = 0xFFE7EDDD.toInt()
    val OK = 0xFF2E6B3E.toInt()
    val WARN = 0xFF8A5A00.toInt()
    val DANGER = 0xFFA8322A.toInt()
    val COOL = 0xFF1F5F7A.toInt()
    val DANGER_WASH = 0xFFFBE9E7.toInt()
    val WARN_WASH = 0xFFFBF0DC.toInt()
    val DISABLED_BG = 0xFFEDE5D8.toInt()
    val DISABLED_TEXT = 0xFFA79C88.toInt()
    val RIPPLE = 0x14000000
    val SCRIM = 0xCC000000.toInt()
    val RECIPE_PLACEHOLDERS = intArrayOf(
        0xFFE2E8D8.toInt(), 0xFFF0E2D2.toInt(), 0xFFE8DDD0.toInt(),
        0xFFDDE5E0.toInt(), 0xFFF0E9D9.toInt(), 0xFFE4E0D2.toInt(),
        0xFFE1E7D6.toInt(), 0xFFEEDFD5.toInt(),
    )

    /** Ratio WCAG 2.x entre deux couleurs opaques. */
    fun contrast(a: Int, b: Int): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92
            else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
