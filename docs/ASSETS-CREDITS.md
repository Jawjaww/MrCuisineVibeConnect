# Crédits des images décoratives

Sources vérifiées le 13 novembre 2026 via l'API Wikimedia Commons. Les trois photographies sont
publiées sous **CC0 1.0 Universal Public Domain Dedication** par leurs auteurs respectifs : aucune
attribution n'est exigée, elle est conservée ici par correction. Aucune image ne provient de Lidl.

| Fichier livré | Source d'origine | Auteur | Licence | Poids |
|---|---|---|---|---|
| `basil.webp` | [Sweet basil leaves.jpg](https://commons.wikimedia.org/wiki/File:Sweet_basil_leaves.jpg) | TheTechnician27 | CC0 1.0 | 52,8 Ko |
| `tomatoes.webp` | [Fresh Tomatoes and pepper.jpg](https://commons.wikimedia.org/wiki/File:Fresh_Tomatoes_and_pepper.jpg) | Eunice Ameh | CC0 1.0 | 32,5 Ko |
| `pepper.webp` | [Tellicherry Black Peppercorns.jpg](https://commons.wikimedia.org/wiki/File:Tellicherry_Black_Peppercorns.jpg) | Misterneedlemouse | CC0 1.0 | 53,5 Ko |

Total embarqué : **138,8 Ko** (budget fixé à 60 Ko par fichier et 180 Ko au total, contrôlé par
`DesignTest.decorativeAssetBudgetAndCredits`).

## Transformation appliquée

Recadrage centré au format exact de la zone d'affichage définie dans `Decor.kt` (pour éviter toute
déformation), redimensionnement à 560 px de large, conversion WebP qualité 82, méthode 6.

| Fichier | Zone d'affichage (unités 1024×600) | Format |
|---|---|---|
| `basil.webp` | `-28,-24 → 245,204` | 560×468 |
| `tomatoes.webp` | `815,-28 → 1052,184` | 560×501 |
| `pepper.webp` | `835,430 → 1048,620` | 560×500 |

Les photographies sont composées **une seule fois** dans un bitmap RGB_565 de 1024×600, à 16 %
d'opacité, uniquement dans les marges. Elles ne passent jamais sous un texte ni sous une commande.
