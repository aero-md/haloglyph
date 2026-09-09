# Haloglyph

Un pack de toys 25×25 pour la **Glyph Matrix du Nothing Phone (3)** — qui tourne
aussi **sur les téléphones qui n'en ont pas**.

Sur un Phone (3), les toys s'installent dans Glyph Interface et s'affichent sur
la matrice arrière. Sur n'importe quel autre Android, les mêmes toys tournent
dans des **widgets d'écran d'accueil** qui émulent la matrice au pixel près —
mêmes moteurs, mêmes renderers, même masque de 489 LEDs.

> **État : deux toys sur quatre, tous les deux déclarés au système.** La
> plomberie est en place — géométrie de la matrice, les trois sinks, le socle des
> services Glyph et des widgets, le hub. **Lapse** et **Dice** sont écrits, et
> leurs deux surfaces système sont ouvertes : ils apparaissent dans Glyph
> Interface et dans le sélecteur de widgets. Le hub ne l'écrit nulle part en dur,
> il interroge le `PackageManager`. Sono suit.

## Les toys

| Toy | Origine | Ce qu'il fait |
|---|---|---|
| **Lapse** | [glyphlapse](https://github.com/aero-md/glyphlapse) | Compteur temporel : temps écoulé depuis / restant jusqu'à une date. 3 lapse, anneau ou sablier. |
| **Dice** | [justadice](https://github.com/aero-md/justadice) | Dé d6/d10/d12/d20, solides 3D suivis en quaternion, secouer pour jeter. |
| **Sono — Spectre** | [sonoglyph](https://github.com/aero-md/sonoglyph) | 25 bandes de tiers d'octave, 40 Hz à 16 kHz. |
| **Sono — Aiguille** | [sonoglyph](https://github.com/aero-md/sonoglyph) | VU-mètre pivotant, niveau en dB(A). |

Quatre toys, pas cinq : **Slot est abandonné**. Une machine à sous déclenche un
PEGI 18 qui contaminerait tout le pack, et maintenir une seconde app Play pour un
toy sans réglages coûte plus cher que ce qu'elle rapporte.
[glyphslot](https://github.com/aero-md/glyphslot) reste un dépôt à part.

Les préviews web de chaque toy vivent dans
[GlyphPortal](https://github.com/aero-md/GlyphPortal) → [glyph.suns.red](https://glyph.suns.red).

## L'app

Le hub liste les toys et **mesure** ce qu'il affiche : la matrice est sondée par
une vraie connexion, « dans Glyph Interface » est une requête au
`PackageManager`, le nombre de widgets vient de l'`AppWidgetManager`. Un toy
ouvre son écran de réglages, qui vit dans le module du toy — Lapse en a un, Dice
n'en a pas parce qu'il n'a rien à régler.

Au-dessus des toys, un étage de réglages d'application : ce qui a la même valeur
pour tout le pack. La langue y est, et pour l'instant elle y est seule.

## Construire

```
git clone git@github.com:aero-md/haloglyph.git
cd haloglyph
./gradlew :app:assembleDebug
```

Le GlyphMatrixSDK n'est pas versionné : il est téléchargé au premier build.
JDK 17, SDK Android 36, aucune clé API Nothing requise (targetSdk 36).

## Langues

**de · en · es · fr · it**, anglais en repli — le même jeu que le reste de
l'écosystème. La traduction va jusqu'à la matrice : les toys écrivent leurs
unités dans la langue de l'app.

Le choix se fait dans les réglages de l'application, et passe par le
`LocaleManager` du système — donc il apparaît aussi dans Paramètres →
Applications → Langue, et il reconfigure les services de toy sans les redémarrer.
Ajouter une langue = poser un `values-xx/strings.xml` : le sélecteur la propose,
`generateLocaleConfig` s'occupe du reste.

## Licence

MIT — voir [LICENSE](LICENSE).
