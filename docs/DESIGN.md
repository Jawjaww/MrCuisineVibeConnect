# Design system Toque — Ivoire & Vert botanique

## Périmètre

- Palette, typographie, espacements, rayons et traits centralisés.
- Composants natifs Kotlin/Canvas, sans Compose ni nouvelle dépendance.
- Refonte de Cuisson manuelle ; les autres écrans héritent des tokens via `Ui`.
- Barre supérieure et barre machine recolorées via la façade `Ui`.
- Décor photo optionnel, pré-composé dans un bitmap RGB_565 de 1024×600.
- Aucune modification de la logique moteur, du protocole, de `SafetyPolicy` ou des règles de commande.

## Tokens

La palette de référence est définie dans `core/.../design/Palette.kt` et exposée à l'application par `ui/design/Tokens.kt`.

| Token | Valeur | Usage |
|---|---|---|
| BG | #F8F1EA | fond ivoire |
| SURFACE | #FBF6F1 | cartes et cadrans |
| SURFACE2 | #F3EADF | champs et surfaces secondaires |
| SURFACE3 | #EAE0D3 | appui et pistes |
| STROKE | #E3D9CB | filet décoratif |
| STROKE_STRONG | #8A7C66 | bordure de champ |
| TEXT | #252F2C | texte principal |
| TEXT2 | #566056 | texte secondaire |
| TEXT3 | #5E665C | mentions |
| BRAND | #37562E | action, sélection, focus |
| BRAND_PRESSED | #2C4525 | appui |
| BOTANICAL | #7A962B | décor / arcs uniquement |
| WASH_BRAND | #E7EDDD | fond d'icône sélectionnée |
| OK / WARN / DANGER / COOL | #2E6B3E / #8A5A00 / #A8322A / #1F5F7A | états sémantiques |

BOTANICAL n'est pas une couleur de texte. BRAND est utilisé pour les éléments interactifs et les libellés nécessitant un contraste fort. Les couleurs d'état de sécurité restent sémantiques et ne sont pas remplacées par la couleur de marque.

## Typographie et géométrie

Polices système Android uniquement. Grille de référence : 1024×600, mise à l'échelle par `Ui.u()`.

- Tailles : display 120, H1 40, H2 28, H3 24, H4 20, body 18, small 16, micro 15, caption 13.
- Espacements : 4, 8, 12, 16, 20, 24, 32, 40, 56.
- Rayons : pilule 999, carte 28, petite carte 20, badge 14.
- Traits : 1 px décoratif, 2 px sélection, 3 px focus.

## Composants

- `SurfaceCard` : surface opaque, filet fin, aucune ombre dynamique.
- `Dial` : cercle 216, arc 270°, 46 graduations espacées de 6°, valeur 64, légende 15. Canvas invalide uniquement si les valeurs changent.
- `RoundButton` : cercle 72, icône 28, libellé 15 sous le cercle.
- `PrimaryButton` : hauteur 68, pilule BRAND, focus ivoire.
- `HoldRoundButton` : action Turbo maintenue, relâchement transmis à la commande existante.
- `WeightBadge` : valeur de poids et état de connexion.
- `Focusable.states` : fond et anneau focus/selection explicites, sans highlight blanc système.

## Cuisson manuelle

Géométrie horizontale exacte dans la grille 1024 : marges 24 + zone cadrans 680 + gouttière 20 + panneau droit 276 + marge 24 = 1024.

Les trois cadrans Durée, Température et Vitesse gardent leurs actions −/+ et leur sélection par molette. Le cadran température distingue la consigne (valeur centrale) de la mesure réelle (légende et repère). Le cadran vitesse garde la consigne et représente la vitesse mesurée par un repère. Le panneau droit garde le poids mesuré, l'avertissement de sécurité et l'action Démarrer/Reprendre. Normal, Inverse, Lame, Fouet et Turbo restent disponibles ; Turbo conserve son comportement d'appui maintenu.

Le contrôle de sécurité continue d'utiliser `SafetyPolicy.checkJob`, `a.startJob`, `machine.adjust`, `machine.resume` et les callbacks existants. Aucun changement au contrôleur machine.

## Focus et navigation

Les vues interactives reçoivent un anneau de focus BRAND de 3 unités ; sur fond BRAND, le focus est ivoire. Sur API 26+, le focus highlight Android blanc est désactivé après vérification de version. Sur API 23, les anneaux personnalisés fonctionnent sans appeler l'API 26.

Les flèches D-pad sont laissées au système quand un contrôle a le focus. Les codes de molette explicitement configurés continuent d'être consommés par le gestionnaire de molette ; les évènements AXIS_SCROLL restent pris en charge par `dispatchGenericMotionEvent`. Ce comportement doit être validé sur le robot : le matériel peut émettre des codes clavier plutôt que des évènements de molette.

## Budget graphique

- Pas de blur, glassmorphism, ombres dynamiques ni animation décorative.
- Icônes dessinées sur Canvas, aucune ressource PNG d'icône.
- Cadrans Canvas natifs ; pas d'images par valeur.
- Décor facultatif : un bitmap final RGB_565 1024×600 (1 228 800 octets, environ 1,17 Mio).
- Décodage des sources décoratives séquentiel et downsample ; images sources limitées à 60 Ko chacune, 180 Ko au total.
- En l'absence d'assets, repli sur un fond ivoire uni sans bitmap.
- Les mesures CPU/RAM/FPS et les captures 1024×600 / 1920×1080 restent à faire sur appareils.

## Contrastes et tests

`Palette.contrast` implémente le calcul WCAG. `DesignTest` vérifie texte ≥4,5:1, focus/selection ≥3:1, absence de couleurs littérales Kotlin hors `ui/design`, et budget des assets décoratifs.

Le test d'internationalisation existant `I18nTest` reste la source de vérité pour les clés `tr()`. Toute nouvelle chaîne doit exister dans `en.json`.

## Limites de cette livraison

Les trois sources photographiques CC0 sont sélectionnées et documentées dans ASSETS-CREDITS.md. Les binaires WebP optimisés ne sont pas encore intégrés ; le rendu se replie donc sur l'ivoire uni. Les captures visuelles, le D-pad réel, le bouton rotatif, les mesures thermiques et la balance doivent être testés sur le robot et sur la TV.
