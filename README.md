# Haloglyph

Un pack de toys 25×25 pour la **Glyph Matrix du Nothing Phone (3)** — qui tourne
aussi **sur les téléphones qui n'en ont pas**.

Sur un Phone (3), les toys s'installent dans Glyph Interface et s'affichent sur
la matrice arrière. Sur n'importe quel autre Android, les mêmes toys tournent
dans des **widgets d'écran d'accueil** qui émulent la matrice au pixel près —
mêmes moteurs, mêmes renderers, même masque de 489 LEDs.

> **État : les trois toys sont écrits et déclarés au système.** La plomberie est
> en place — géométrie de la matrice, les trois sinks, le socle des services
> Glyph et des widgets, le hub. **Lapse**, **Dice** et **Sono** apparaissent dans
> Glyph Interface, et les deux premiers dans le sélecteur de widgets. Le hub ne
> l'écrit nulle part en dur, il interroge le `PackageManager`.

## Les toys

| Toy | Origine | Ce qu'il fait |
|---|---|---|
| **Lapse** | [glyphlapse](https://github.com/aero-md/glyphlapse) | Compteur temporel : temps écoulé depuis / restant jusqu'à une date. Jusqu'à 5 lapse, anneau ou sablier. |
| **Dice** | [justadice](https://github.com/aero-md/justadice) | Dé d6/d10/d12/d20, solides 3D suivis en quaternion, secouer pour jeter. |
| **Sono** | [sonoglyph](https://github.com/aero-md/sonoglyph) | Le micro sur la matrice, en trois modes : spectre 25 bandes, VU-mètre à aiguille en dB(A), spectrogramme sur 5 s. |

Chacun se change à l'**appui long** sur le Glyph Button : le lapse suivant, le
solide suivant, le mode suivant.

Trois toys, pas cinq. **Slot est abandonné** — une machine à sous déclenche un
PEGI 18 qui contaminerait tout le pack, et maintenir une seconde app Play pour un
toy sans réglages coûte plus cher que ce qu'elle rapporte ;
[glyphslot](https://github.com/aero-md/glyphslot) reste un dépôt à part. Et Sono
n'en fait qu'un là où Sonoglyph en exposait deux : « Spectre » et « Aiguille »
ouvraient le même micro et faisaient tourner la même FFT pour ne différer que par
le dernier étage du rendu.

### Sono et le micro

C'est le seul toy du pack qui demande une permission, et le seul sans widget —
un widget qui écoute le micro en permanence est indéfendable.

L'autorisation est demandée **à la première ouverture de l'app**, une fois. Un
Glyph Toy est un service lié par Glyph Interface : il n'a pas d'écran, donc il ne
peut pas poser la question lui-même. Refusée, la ligne de Sono s'affiche
atténuée dans le hub, avec ce qui manque et où le corriger — la ligne reste
cliquable, et son écran de réglages redemande l'autorisation ou mène à la page
des autorisations de l'app quand Android ne veut plus rien afficher.

Le son est analysé au vol et n'est jamais enregistré ni transmis. **Les aperçus
de l'app n'ouvrent pas le micro** : ils jouent le vrai renderer sur une scène
simulée, pour que la pastille micro d'Android ne s'allume pas pour une vignette.

Les préviews web de chaque toy vivent dans
[GlyphPortal](https://github.com/aero-md/GlyphPortal) → [glyph.suns.red](https://glyph.suns.red).

## L'app

Le hub liste les toys et **mesure** ce qu'il affiche : la matrice est sondée par
une vraie connexion, « dans Glyph Interface » est une requête au
`PackageManager`, le nombre de widgets vient de l'`AppWidgetManager`. Un toy
ouvre son écran de réglages, qui vit dans le module du toy — Lapse et Sono en ont
un, Dice n'en a pas parce qu'il n'a rien à régler.

Il mène aussi aux deux écrans Glyph du système : la liste des toys actifs et le
gestionnaire, où on active et réordonne. Nothing ne publie d'action documentée
pour ni l'un ni l'autre, alors `GlyphSettings` énumère ce que
`com.nothing.thirdparty` expose vraiment et ne montre que les boutons qui
ouvriront quelque chose.

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
