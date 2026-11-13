# Crédits des images décoratives

Sources vérifiées le 13 novembre 2026 via l'API Wikimedia Commons. Les trois photographies sont
publiées sous **CC0 1.0 Universal Public Domain Dedication** par leurs auteurs respectifs : aucune
attribution n'est exigée, elle est conservée ici par correction. Aucune image ne provient de Lidl.

| Fichier livré | Source d'origine | Auteur | Licence | Poids |
|---|---|---|---|---|
| `basil.webp` | [Sweet basil leaves.jpg](https://commons.wikimedia.org/wiki/File:Sweet_basil_leaves.jpg) | TheTechnician27 | CC0 1.0 | 10,8 Ko |
| `tomatoes.webp` | [Fresh Tomatoes and pepper.jpg](https://commons.wikimedia.org/wiki/File:Fresh_Tomatoes_and_pepper.jpg) | Eunice Ameh | CC0 1.0 | 9,0 Ko |
| `pepper.webp` | [Tellicherry Black Peppercorns.jpg](https://commons.wikimedia.org/wiki/File:Tellicherry_Black_Peppercorns.jpg) | Misterneedlemouse | CC0 1.0 | 11,6 Ko |

Total embarqué : **31,4 Ko** (budget fixé à 60 Ko par fichier et 180 Ko au total, contrôlé par
`DesignTest.decorativeAssetBudgetAndCredits`).

## Transformation appliquée

1. recadrage centré au format exact de la zone d'affichage définie dans `Decor.kt` (aucune déformation) ;
2. redimensionnement à 480 px de large ;
3. **flou gaussien de rayon 14 appliqué à la conversion**, pas à l'exécution : le robot ne calcule
   jamais de flou, le bokeh est figé dans le fichier ;
3bis. **fondu alpha elliptique cuit dans le fichier** (alpha 0 exactement sur les bords) : plus
   aucun masque n'est appliqué au rendu, donc aucun raccord rectangulaire ne peut apparaître ;
4. conversion WebP, méthode 6.

| Fichier | Zone d'affichage (unités 1024×600) | Format | Rendu |
|---|---|---|---|
| `basil.webp` | `-30,300 → 330,628` | 480×438 | bas gauche |
| `tomatoes.webp` | `740,-40 → 1080,215` | 480×360 | haut droite |
| `pepper.webp` | `830,350 → 1070,610` | 480×520 | bas droite |

## Composition

Les photographies sont dessinées à **65 % d'opacité**, avec leur fondu déjà présent dans le fichier,
puis recouvertes d'un **voile ivoire en dégradé radial** : opaque au centre (zone des cadrans, des
valeurs et des libellés), transparent en périphérie. Aucune image ne passe sous une information sans
ce voile.
