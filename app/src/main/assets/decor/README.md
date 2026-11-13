# Décor photographique Toque

Trois miniatures WebP : `basil.webp`, `tomatoes.webp`, `pepper.webp`. Elles sont composées une
seule fois dans un bitmap 1024×600 (RGB_565, ≈ 1,17 Mio) par `Decor.kt`, hors du thread principal,
puis appliquées comme fond. En leur absence, le repli est un fond ivoire uni sans allocation.

Origine, licence CC0 et transformation : voir `docs/ASSETS-CREDITS.md`.
Budget contrôlé par les tests : 60 Ko par fichier, 180 Ko au total.
