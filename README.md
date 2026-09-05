# Haloglyph

Un pack de toys 25×25 pour la **Glyph Matrix du Nothing Phone (3)** — qui tourne
aussi **sur les téléphones qui n'en ont pas**.

Sur un Phone (3), les toys s'installent dans Glyph Interface et s'affichent sur
la matrice arrière. Sur n'importe quel autre Android, les mêmes toys tournent
dans des **widgets d'écran d'accueil** qui émulent la matrice au pixel près —
mêmes moteurs, mêmes renderers, même masque de 489 LEDs.

> **État : conception.** Le dépôt contient pour l'instant l'ossature et la
> licence. Le code arrive par migration des toys existants, avec leur historique
> (`git subtree`).

## Les toys

| Toy | Origine | Ce qu'il fait |
|---|---|---|
| **Lapse** | [glyphlapse](https://github.com/aero-md/glyphlapse) | Compteur temporel : temps écoulé depuis / restant jusqu'à une date. 3 lapse, anneau ou sablier. |
| **Slot** | [glyphslot](https://github.com/aero-md/glyphslot) | Machine à sous : spin de 5 s, arrêts en cascade, chorégraphie jackpot 777. |
| **Dice** | [justadice](https://github.com/aero-md/justadice) | Dé d6/d10/d12/d20, solides 3D suivis en quaternion, secouer pour jeter. |
| **Sono — Spectre** | [sonoglyph](https://github.com/aero-md/sonoglyph) | 25 bandes de tiers d'octave, 40 Hz à 16 kHz. |
| **Sono — Aiguille** | [sonoglyph](https://github.com/aero-md/sonoglyph) | VU-mètre pivotant, niveau en dB(A). |

Les préviews web de chaque toy vivent dans
[GlyphPortal](https://github.com/aero-md/GlyphPortal) → [glyph.suns.red](https://glyph.suns.red).

## Langues

**de · en · es · fr · it**, anglais en repli — le même jeu que le reste de
l'écosystème. La traduction va jusqu'à la matrice : les toys écrivent leurs
unités dans la langue de l'app.

## Licence

MIT — voir [LICENSE](LICENSE).
