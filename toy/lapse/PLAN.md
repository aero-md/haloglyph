# glyphlapse — Plan de développement

Glyph Toy « compteur temporel » pour la Glyph Matrix du Nothing Phone (3) :
temps écoulé depuis / restant jusqu'à une date de référence configurable.

## 1. Setup projet

- Boilerplate repris de **GlyphSlot** : Gradle Kotlin DSL, AGP + Kotlin + Compose,
  minSdk 34, targetSdk 36 (pas de clé API Nothing requise sur Android 16).
- `GlyphMatrixSDK.aar` téléchargé automatiquement au premier build
  (tâche `downloadGlyphSdk` sur `preBuild`), non versionné.
- Wrapper `GlyphMatrixService.kt` repris tel quel (bind/unbind, register,
  events Messenger).
- Workflow release GitHub Actions repris : tag annoté `v*` → APK signé en asset.

## 2. Enregistrement du toy + launcher

- `Service` avec intent-filter `com.nothing.glyph.TOY`, meta-data
  `toy.name` / `toy.image` / `toy.summary` / `toy.longpress = 1`.
- **Différence avec GlyphSlot** : icône launcher présente **aussi en release**
  (catégorie LAUNCHER dans le manifest principal) — l'app est l'interface de
  configuration, elle doit être accessible.

## 3. Moteur de décomposition (engine/)

- `TimeBreakdown.kt` : `(refMillis, nowMillis, zone) → Diff(direction, années,
  mois, jours, heures, minutes, secondes, totalJours)` via `java.time`
  (`Period` pour la partie date, `Duration` pour la partie heure).
- Règle de pertinence : masquer les unités de tête à zéro ; cap à 4 lignes
  (MIN sacrifiée en premier).
- `LapseEngine.kt` : format courant, transitions (slide de format, pages du
  format Cycle), détection du passage par zéro → événement `Arrived`.
- Temps injecté partout → tests JUnit purs.

## 4. Rendu (render/)

- `Fonts.kt` : police 3×5 (chiffres + A M J H I N S) et 5×7 (reprise du
  bandeau JACKPOT de GlyphSlot, étendue).
- `LapseRenderer.kt` : layout des lignes selon le format, centrage, clipping
  disque, `IntArray(625)` par frame.
- `Ring.kt` : anneau des secondes (cellules du bord triées par angle,
  arc + traînée, sens selon la direction).
- `Arrival.kt` : flashs, ondes concentriques, anneau pulsé (~4 s).

## 5. Service toy (toy/)

- `LapseToyService.kt` : tick 1 Hz aligné sur la seconde au repos, 30 fps
  pendant les animations ; appui long → format suivant ; `EVENT_AOD` → rendu
  statique sans anneau.
- Lecture de la config via `SharedPreferences` + listener (mise à jour
  immédiate quand l'app modifie la référence).
- Haptique : tick au changement de format, pattern long à l'arrivée.

## 6. App de configuration (ui/)

- `MainActivity.kt` Compose Material 3 : DatePicker + TimePicker (référence),
  boutons segmentés de format, libellé « Depuis le… / Jusqu'au… », préview
  live 25×25 partageant moteur + renderer.
- Écriture directe dans `SharedPreferences` à chaque changement.

## 7. Tests & debug

- JUnit : bissextiles (29 févr.), fins de mois (31 janv. + 1 mois), DST,
  collapsing des unités, cap 4 lignes, bascule jusqu'à → depuis, formats.
- Sur appareil : Settings → Glyph Interface → Glyph Toys → Glyph Lapse.
- Préview Compose pour itérer sans le téléphone ; préview web
  (glyph.suns.red/glyphlapse) comme référence d'implémentation.

## 8. Extensions (v2)

Multi-dates (cycle entre plusieurs références), format pourcentage entre deux
dates, complication AOD dédiée, widget home screen.
