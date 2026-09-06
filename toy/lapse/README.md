<p align="center">
  <img src="glyphlapse-icon.svg" alt="GlyphLapse" width="180">
</p>

# GlyphLapse

Glyph Toy « compteur temporel » pour la Glyph Matrix du Nothing Phone (3) :
temps écoulé **depuis** une date de référence, ou restant **jusqu'à** elle
(direction déduite automatiquement). Jusqu'à **3 lapse** configurables
(chacun sa date, son format, son animation), appui long sur le Glyph Button
pour passer au lapse actif suivant. Configurable via l'app (date/heure,
format d'affichage, secondes en anneau ou sablier).

**[▶ Préview interactive](https://glyph.suns.red/glyphlapse/)** — le toy jouable
dans le navigateur : mêmes polices, mêmes règles, même sablier, rendu à
l'échelle réelle sur une photo du dos du Phone (3). Elle vit avec celles des
autres Glyph Toys dans
[glyph-portal](https://github.com/aero-md/GlyphPortal), sur un noyau commun.

Specs : [SPECS.md](SPECS.md) · préview : [SPECS-PREVIEW.md](SPECS-PREVIEW.md) ·
plan : [PLAN.md](PLAN.md).

| Détail + anneau | Sablier | Arrivée J-0 |
|:---:|:---:|:---:|
| ![Détail, anneau des secondes](preview/glyphlapse-detail.svg) | ![Sablier qui se remplit](preview/glyphlapse-sablier.svg) | ![Compte à rebours et arrivée](preview/glyphlapse-arrivee.svg) |

*Animations SVG générées depuis le moteur réel
([`preview/`](preview/)) — mêmes constantes, mêmes easings que le toy.*

## L'app de réglages

<p align="center">
  <img src="docs/screenshot-settings.png" alt="App de réglages GlyphLapse" width="300">
</p>

Date/heure de référence, **animation** des secondes (anneau ou sablier) et
**style d'affichage** — avec une préview live 25×25 partageant moteur et
renderer avec le toy.

## Formats

| Format | Affichage |
|--------|-----------|
| Dense *(défaut)* | Granularité complète, 2 unités par ligne si besoin — 3×5 partout |
| Compact | Les 2 unités les plus significatives en 5×7 |
| Cycle | Une unité plein écran, défilement vertical toutes les 2 s |
| Jours | Total de jours (« J-42 » en compte à rebours) |

Les secondes vivent sur l'**anneau** périphérique (horaire en depuis,
anti-horaire en jusqu'à) ou en **sablier** d'arrière-plan (le disque se
remplit / se vide sur la minute, surface en cône, texte lisible par-dessus).
Compte à rebours atteint → animation d'arrivée (~4 s) puis bascule en depuis.

## Build

- Android Studio (ou `./gradlew assembleDebug`), JDK 17, minSdk 34.
- Sans Android Studio : installer les
  [commandline-tools](https://developer.android.com/studio#command-line-tools-only)
  (`sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"`,
  puis `sdkmanager --licenses`) et indiquer le SDK via un `local.properties`
  à la racine (`sdk.dir=C:\\chemin\\vers\\le\\sdk`) ou `ANDROID_HOME`.
- `GlyphMatrixSDK.aar` est **téléchargé automatiquement au premier build**
  (tâche `downloadGlyphSdk`, hook sur `preBuild`) dans `app/libs/` — non versionné.

## Test sur appareil

1. Installer sur un Phone (3) : `./gradlew installDebug`.
2. Activer le toy : **Settings → Glyph Interface → Glyph Toys → Glyph Lapse**.
3. **Android 16+ (`targetSdk 36`) : aucune clé API requise.** Sur un OS plus
   ancien, activer le mode debug Glyph (clé `NothingKey=test`, 48 h) :
   `adb shell settings put global nt_glyph_interface_debug_enable 1`
4. Configurer via l'app **Glyph Lapse** (icône launcher présente, contrairement
   à GlyphSlot) : date de référence, format, mode secondes — le toy se met à
   jour immédiatement (listener SharedPreferences).
5. Diagnostic en cas de crash : `adb logcat -b crash -d`.

## Architecture

```
engine/  TimeBreakdown.kt, LapseEngine.kt  logique pure (java.time), testable JUnit
render/  Fonts.kt, LapseRenderer.kt        IntArray(625) par frame, sans SDK
toy/     LapseToyService.kt,
         GlyphMatrixService.kt             seul module dépendant du GlyphMatrixSDK
Config.kt                                  SharedPreferences partagées app ↔ toy
MainActivity.kt                            configuration Compose + préview live
```

Boucle de rendu : 1 tick/s **aligné sur la seconde** au repos, 30 fps pendant
les animations (changement de lapse, format Cycle, arrivée). AOD : `EVENT_AOD`
chaque minute → rendu statique sans anneau/sablier.

Multi-lapse : jusqu'à 3 lapse indépendants (date/format/animation propres),
persistés par index dans les mêmes SharedPreferences ; `active_lapse` désigne
celui rendu sur la Glyph. Les dates favorites sont communes à tous les lapse.

## Interaction

| Event | Action |
|-------|--------|
| Appui court | Système : cycle entre les toys |
| Appui long (« change ») | Lapse actif suivant (slide horizontal + tick haptique) |
| Compte à rebours à 0 | Animation d'arrivée + pattern haptique, bascule en depuis |
| `onUnbind` | Stop boucle + extinction matrice |

## Release

Tag annoté `vX.Y.Z` → le workflow [release.yml](.github/workflows/release.yml)
build, signe (secrets keystore) et publie `GlyphLapse-X.Y.Z.apk` en asset,
avec le message du tag comme release note. CI sur chaque push :
[ci.yml](.github/workflows/ci.yml) (`gradlew test assembleDebug`).

## Licence

[MIT](LICENSE)
