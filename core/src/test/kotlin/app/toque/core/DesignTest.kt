package app.toque.core

import app.toque.core.design.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Garde-fous du thème clair : contraste, couleurs littérales et budget des ressources décoratives. */
class DesignTest {
    @Test fun textContrastIsAtLeastWcagAA() {
        val surfaces = listOf(Palette.BG, Palette.SURFACE, Palette.SURFACE2, Palette.SURFACE3)
        val textColors = listOf(Palette.TEXT, Palette.TEXT2, Palette.TEXT3)
        for (foreground in textColors) {
            for (background in surfaces) {
                assertTrue(
                    "Contraste texte insuffisant : " + foreground.toString(16) + " sur " + background.toString(16),
                    Palette.contrast(foreground, background) >= 4.5,
                )
            }
        }
    }

    @Test fun semanticStatusTextContrastIsAtLeastWcagAA() {
        val pairs = listOf(
            Palette.OK to Palette.BG,
            Palette.WARN to Palette.WARN_WASH,
            Palette.DANGER to Palette.DANGER_WASH,
            Palette.COOL to Palette.BG,
        )
        for ((foreground, background) in pairs) {
            assertTrue("Contraste d'état sémantique insuffisant", Palette.contrast(foreground, background) >= 4.5)
        }
    }

    @Test fun focusAndSelectionContrastIsAtLeastThreeToOne() {
        val focusPairs = listOf(
            Palette.BRAND to Palette.BG,
            Palette.BRAND to Palette.SURFACE,
            Palette.BRAND to Palette.SURFACE2,
            Palette.BRAND to Palette.SURFACE3,
            Palette.SURFACE to Palette.BRAND,
            Palette.STROKE_STRONG to Palette.SURFACE3,
        )
        for ((foreground, background) in focusPairs) {
            assertTrue("Contraste d'état insuffisant", Palette.contrast(foreground, background) >= 3.0)
        }
    }

    @Test fun noLiteralColorsOutsideDesignPackage() {
        val root = File("../app/src/main/kotlin")
        assertTrue("Dossier app introuvable : " + root.absolutePath, root.isDirectory)
        val color = Regex("""(?:0x[0-9A-Fa-f]{6,8}|#[0-9A-Fa-f]{6,8})""")
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.contains("/ui/design/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (color.containsMatchIn(line)) file.relativeTo(root).toString() + ":" + (index + 1) + ": " + line.trim() else null
                }
            }.toList()
        assertEquals("Couleurs littérales hors ui/design :\n" + violations.joinToString("\n"), 0, violations.size)
    }

    @Test fun decorativeAssetBudgetAndCredits() {
        val decor = File("../app/src/main/assets/decor")
        val credits = File("../docs/ASSETS-CREDITS.md")
        // Le fond se replie volontairement sur l'ivoire tant que les miniatures ne sont pas livrées.
        if (!decor.exists()) return
        assertTrue("docs/ASSETS-CREDITS.md manquant", credits.isFile)
        val images = decor.listFiles()!!.filter { it.isFile && it.extension.lowercase() in setOf("webp", "jpg", "jpeg", "png") }
        val bytes = images.sumOf { it.length() }
        assertTrue("Budget décor dépassé : " + bytes + " octets", bytes <= 180_000L)
        val documentation = credits.readText()
        for (image in images) assertTrue("Crédit manquant pour " + image.name, documentation.contains(image.name))
    }
}
