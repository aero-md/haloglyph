# glyphlapse — Spécifications techniques

Glyph Toy « compteur temporel » : affiche le temps écoulé **depuis** une date
de référence, ou restant **jusqu'à** elle. La direction est déduite
automatiquement : référence passée → depuis, future → jusqu'à.

Jusqu'à **3 lapse** indépendants (chacun sa date, son format, son animation) ;
un seul est affiché à la fois sur la Glyph, l'appui long passe au suivant.

## Constantes

| Nom | Valeur | Description |
|-----|-------:|-------------|
| `SIZE` | 25 | Matrice 25×25 |
| `RADIUS` | 12,5 | Masque circulaire centré (12,12) — ~489 LEDs |
| `FONTS` | 5×7 / 3×5 | Police selon le nombre de lignes (≤2 lignes / 3+) |
| `RING_BAND` | 11,3–12,5 | Cellules de l'anneau des secondes (bord du disque) |
| `TICK` | 1 Hz | Rendu au repos, aligné sur la seconde |
| FPS anim | 30 | Transitions, cycle, sablier, arrivée |
| `CYCLE_PERIOD` | 2 s | Durée par unité en format Cycle |
| `SLIDE` | 0,3 s | Slide de changement de format (moteur, dormant — cf. Interaction) |
| `LAPSE_SLIDE` | 0,35 s | Slide horizontal de changement de lapse |
| `ARRIVAL` | ~4 s | Animation d'arrivée (compte à rebours atteint) |
| `LAPSE_COUNT` | 3 | Nombre de lapse configurables |
| `SAVED_MAX` | 5 | Dates favorites maximum |

## Décomposition calendaire

- Calcul via `java.time` (`ZonedDateTime`, `Period` + `Duration`), fuseau local :
  années/mois exacts (bissextiles, fins de mois), puis jours/heures/minutes/secondes.
- Unités : `A` (années), `M` (mois), `J` (jours), `H` (heures), `′` (minutes,
  notation prime — étroit, passe dans les bandes basses du disque), secondes
  portées par **l'anneau périphérique** (pas de ligne dédiée).
- **Pertinence** : les unités de tête à zéro sont masquées (diff de 5 jours →
  pas de « 0A 0M », on commence à `J`). En dessous de la première unité non
  nulle, **granularité complète** : jusqu'à 5 unités (`A M J H ′`).
- **Centrage disc-aware** : chaque ligne est centrée puis nudgée de ±1-3 px
  si des pixels sortiraient du disque (bandes haut/bas plus étroites).
- Diff < 1 min : la valeur des secondes s'affiche au centre (5×7), l'anneau
  continue en parallèle.

## Formats d'affichage

Réglés par lapse dans l'app (l'appui long ne cycle plus les formats, il change
de lapse) :

1. **Dense** *(défaut)* — granularité complète, 2 unités par ligne quand
   nécessaire : `[A]` / `[M J]` / `[H ′]` (calendrier au centre, horloge en bas,
   séparateur 2 px). Police 3×5 partout (jamais de 3×4), 5×7 si ≤ 2 unités.
2. **Compact** — les 2 unités les plus significatives, police 5×7.
3. **Cycle** — une unité à la fois plein écran (valeur 5×7, lettre en dessous),
   **défilement vertical vers le haut** toutes les `CYCLE_PERIOD` (le suivant
   monte depuis le bas, le précédent sort par le haut). Le sens vertical évite
   la confusion avec le slide **horizontal** de changement de lapse.
4. **Jours** — total de jours uniquement : `J-n` en compte à rebours
   (classique « J-42 »), `nJ` en écoulé. 5×7 si ≤ 4 caractères, sinon 3×5.

*(Le format « Détail » 1 unité/ligne jusqu'à 5 lignes en 3×4 a été retiré :
Dense le remplace, on limite le nombre de formats.)*

Tous les formats conservent l'anneau/sablier des secondes.

## Secondes — minute en cours (réglage par lapse : anneau ou sablier)

**Anneau** *(défaut)* :
- Cellules du bord (distance ∈ `RING_BAND`) triées par angle depuis 12 h.
- Arc allumé proportionnel à `s/60` : tête à 100 %, traînée dégradée (~32 %).
- Sens **horaire** en mode depuis (le temps s'accumule), **anti-horaire**
  en mode jusqu'à (le temps s'épuise).

**Sablier** (alternative, en arrière-plan du texte) :
- Le disque est la moitié **basse** d'un sablier qui se remplit (depuis) ou
  la moitié **haute** qui se vide (jusqu'à) ; niveau = `s/60` du diamètre,
  quantifié à la ligne, mis à jour à la seconde.
- **Surface en cône** (angle de talus `SLOPE` = 0,45 ligne/colonne) :
  pointe au centre en depuis (le sable s'empile sous le filet), entonnoir
  en jusqu'à. La hauteur de base est ajustée par dichotomie pour que le nombre
  de cellules de sable reste `s/60 × 489` — le cône ne fausse pas la lecture.
- **Texture** : surface irrégulière (dithering déterministe ~55 %), corps
  avec bruit granulaire figé. Aucun pixel détaché au-dessus de la surface.
- Luminosités **volontairement basses** : sable ~5,5-10 % (fond assombri),
  filet central ~7 % — bien en dessous de la traînée d'anneau (32 %) ; le
  texte (100 %) reste parfaitement lisible par-dessus.
- Filet de sable vertical au centre en depuis, avec éclaboussure au point
  d'impact (grain animé à 30 fps ; le sablier force le mode animé).

Les deux modes sont masqués en AOD (rendu statique, cf. Interaction).

## Multi-lapse

- **3 lapse indépendants**. Chaque lapse = `{ ref, format, seconds, enabled }`.
  Le **lapse 0 est toujours actif** ; les lapse 1 & 2 sont **activables** (switch
  dans l'app), désactivés par défaut.
- **Défauts** : lapse 0 & 1 = 1ᵉʳ janvier de l'année courante 00:00 ; lapse 2 =
  31 décembre de l'année courante 23:59 (un lapse a toujours une date, même
  désactivé). Format défaut Dense, anim défaut Anneau.
- `active_lapse` = lapse rendu sur la Glyph.
- **Appui long** → lapse **activé** suivant (rotation, saute les désactivés),
  avec **slide horizontal** `LAPSE_SLIDE` : le lapse courant sort vers la gauche,
  le suivant entre par la droite (composite de deux frames complètes + tick
  haptique). Un seul lapse activé → appui long sans effet.
- **Sélection d'un onglet** dans l'app = change aussi le lapse actif (même slide
  sur la Glyph). Un lapse désactivé reste sélectionnable dans l'app (il est
  seulement exclu de la rotation d'appui long).
- **Dates favorites communes** à tous les lapse (appliquer une date agit sur le
  lapse en cours d'édition).

## Machine à états

```
DISPLAY ──appui long / sélection onglet──▶ LAPSE_SLIDE (0,35 s) ──▶ DISPLAY
DISPLAY ──diff atteint 0 (jusqu'à)──▶ ARRIVAL (~4 s) ──▶ DISPLAY (bascule depuis)
```

(Le `FORMAT_SLIDE` du moteur existe toujours mais est dormant : les changements
de format depuis l'app sont appliqués sans transition — `setFormatQuiet`.)

## Animation d'arrivée (jusqu'à → 0)

1. *0–0,5 s* : double flash plein disque (décroissant).
2. *0,3–2,2 s* : 3 ondes concentriques (r = 14·t, largeur 1 px).
3. *0–4 s* : anneau complet pulsé (sin 3 Hz).
4. Fade-out, puis affichage « depuis » (compteur repart de 0).
- Haptique : pattern long à l'arrivée, tick au changement de lapse.

## Architecture

```
engine/  TimeBreakdown.kt, LapseEngine.kt — logique pure (java.time), testable JUnit
render/  Fonts.kt, LapseRenderer.kt        — IntArray(625)/frame, sans SDK
toy/     LapseToyService.kt,
         GlyphMatrixService.kt             — seul module dépendant du GlyphMatrixSDK
Config.kt                                  — SharedPreferences partagées app ↔ toy
MainActivity.kt                            — configuration Compose + préview live
```

Le temps est injecté (`nowEpochMillis` + zone) : décomposition et machine à
états testables en JVM pure. Le slide de lapse est géré côté service (composite
de deux frames de luminosité), les diff/formats restent dans le moteur.

## Configuration (app, avec raccourci launcher)

Interface Compose, **style Nothing** : fond noir, cartes arrondies, légendes
serif, valeurs monospace, rouge de marque `#D71921` (jaune `#FFC700` réservé à
la section des dates sauvegardées).

- **Sélecteur de lapse** (haut d'écran) : 3 **sabliers filaires** (deux triangles
  vides) posés sur une **ligne de points**, neck aligné sur la ligne, chiffres
  romains I/II/III au-dessus ; l'actif est **rouge**, les autres blancs. Un point
  retiré de chaque côté des sabliers (respiration).
- **Switch d'activation** (lapse 2 & 3 seulement) : hors switch → exclu de la
  rotation d'appui long.
- **Carte Date et heure** : bouton date (rouge) + bouton heure (gris),
  Material 3 DatePicker/TimePicker.
- **Carte Dates sauvegardées** (collapsible, accent jaune) : jusqu'à `SAVED_MAX`
  favoris, chevron gris de repli. Chaque carte = appliquer la date/heure au lapse
  courant ; croix grise de suppression en overlay. « Nouvelle date » = picker
  date puis heure (défaut : maintenant), masqué au-delà de `SAVED_MAX`.
- **Carte Style** : select box **Animation** (Anneau/Sablier) + **Style
  d'affichage** (Dense/Compact/Cycle/Jours) — propres à chaque lapse.
- **Préview live 25×25** : même moteur et même renderer que le toy, reflète le
  lapse sélectionné.
- Persistance `SharedPreferences`, clés par index :
  - Lapse 0 : `ref_epoch_millis`, `format`, `seconds_mode` (clés historiques).
  - Lapse 1/2 : `ref_i`, `format_i`, `seconds_i`, `enabled_i`.
  - `active_lapse` (index affiché), `saved_dates` (CSV de millis, commun).
  - Le service écoute `OnSharedPreferenceChangeListener` → mise à jour immédiate
    (et déclenche le slide si `active_lapse` change).

## Interaction Glyph Button

| Event | Action |
|-------|--------|
| Short press | Système : cycle entre les toys |
| Long press (« change ») | Lapse activé suivant (slide horizontal + tick haptique) |
| `EVENT_AOD` | Rendu statique sans anneau/sablier, 1 update/min |
| `onUnbind` | Stop boucle + extinction matrice |

## Boucle de rendu

- Repos : 1 tick/s **aligné sur la seconde** (`postDelayed` jusqu'à la
  prochaine frontière) — l'anneau avance pile au tic.
- 30 fps pendant `LAPSE_SLIDE`, le format Cycle, le mode Sablier et `ARRIVAL`,
  retour à 1 Hz ensuite (batterie).

## Icône — concept « Grain »

`glyphlapse-icon.svg` et `app/.../drawable/ic_launcher.xml` (miroir) : **sablier
dessiné dans la matrice de points** de la Glyph — rangées `7-5-3-1-3-5-7` (31
points carrés arrondis, façon LED Nothing), occupant ~77 % du canvas 512, sur
fond **blanc** tuile Nothing (coins `rx = 112`). Le **point central de
l'avant-dernière rangée est rouge** `#D71921` : le grain de sable qui tombe / la
seconde qui s'écoule. `toy_preview.xml` = vignette de la matrice pour la liste
des Glyph Toys (`com.nothing.glyph.toy.image`).

## Build / debug

- Signature release via `keystore.properties` (gitignoré) ou secrets CI
  (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) ; à
  défaut, retombe sur la clé debug. Release publiée par tag `vX.Y.Z`
  (`.github/workflows/release.yml`).
- Le build **debug** porte `applicationIdSuffix = ".debug"` : `dev.aero.glyphlapse.debug`
  cohabite avec la prod signée `dev.aero.glyphlapse` (deux icônes, deux toys,
  aucun conflit de signature).
