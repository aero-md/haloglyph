# Haloglyph

Pack de toys 25×25 pour la **Glyph Matrix du Nothing Phone (3)**, qui tourne
aussi **sur les téléphones qui n'en ont pas** : sur un Phone (3) les toys
s'installent dans Glyph Interface, ailleurs les mêmes moteurs tournent dans un
**hublot** — un widget d'écran d'accueil rond qui émule la matrice au pixel
près. Un seul hublot porte tous les toys (tap pour interagir, double tap pour
changer de toy), jusqu'à trois posés en même temps.

> **État : les cinq toys sont écrits et déclarés au système**, et tournent à
> la fois dans Glyph Interface et dans le hublot.

## Les toys

| Toy | Origine | Ce qu'il fait |
|---|---|---|
| **Lapse** | [glyphlapse](https://github.com/aero-md/glyphlapse) | Compteur temporel : temps écoulé depuis / restant jusqu'à une date. |
| **Dice** | [justadice](https://github.com/aero-md/justadice) | Dé d6/d10/d12/d20, solides 3D suivis en quaternion, secouer pour jeter. |
| **Sono** | [sonoglyph](https://github.com/aero-md/sonoglyph) | Le micro sur la matrice : spectre, VU-mètre, forme d'onde. |
| **Float** | écrit ici | Niveau à bulle à échelle logarithmique, et boussole en second instrument. |
| **G-Forces** | écrit ici | Accéléromètre de bord : une bille pour l'instant, les quatre pics du trajet en second. |

Chacun se change à l'appui long sur le Glyph Button (au tap, dans un hublot).

Les préviews web de chaque toy vivent dans
[GlyphPortal](https://github.com/aero-md/GlyphPortal) → [glyph.suns.red](https://glyph.suns.red).

Le détail produit (pourquoi chaque toy est fait ainsi) et technique (rendu,
performance, permissions) vit dans [`specs/PRODUIT.md`](specs/PRODUIT.md) et
[`specs/TECHNIQUE.md`](specs/TECHNIQUE.md), pas ici.

## L'app

Un dock flottant en bas de l'écran, trois onglets : **Toys** (le pack, une
carte par toy, chacune ouvrant ses réglages), **Cast** (réservé, sans écran
derrière pour l'instant) et **Réglages** (la langue, et sur un Phone (3) les
deux écrans Glyph du système).

## Construire

```
git clone git@github.com:aero-md/haloglyph.git
cd haloglyph
./gradlew :app:assembleDebug
```

Le GlyphMatrixSDK n'est pas versionné : il est téléchargé au premier build.
JDK 17, SDK Android 36, aucune clé API Nothing requise (targetSdk 36).

## Langues

**de · en · es · fr · it**, anglais en repli. Se règle dans l'app, via le
`LocaleManager` du système. Ajouter une langue = poser un
`values-xx/strings.xml`.

## Licence

MIT — voir [LICENSE](LICENSE).
