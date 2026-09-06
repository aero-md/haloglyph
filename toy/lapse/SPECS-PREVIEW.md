# glyphlapse — Spécifications de la préview web

> **La préview a déménagé.** Elle vit maintenant dans le dépôt
> [`glyph-portal`](https://github.com/aero-md/glyph-portal) et est servie sur
> **[glyph.suns.red](https://glyph.suns.red)**, avec celles des autres Glyph
> Toys et un noyau commun — une seule géométrie de hublot, mesurée dans les
> pixels de la photo, au lieu de trois copies dont deux relevées à l'œil.
>
> Ce document décrit l'implémentation d'origine. Il est conservé parce qu'il
> documente les choix de rendu, qui eux ont été portés tels quels.


La préview reproduit le toy dans un navigateur, **posé sur une photo du dos
d'un Nothing Phone (3)** : la Glyph Matrix est rendue à sa position et à son
échelle réelles, et le bouton d'interaction est calé sur le Glyph Button
physique. Polices, décomposition calendaire et anneau des secondes sont ceux
du toy Kotlin — voir [SPECS.md](SPECS.md).

## Fichiers

| Fichier | Rôle |
|---|---|
| `glyph-lapse-preview.html` | Source unique de vérité — page autonome, HTML + CSS + JS inline. **Supprimée** : le portage rend le prototype caduc, et le garder aurait laissé deux implémentations dont une morte. Récupérable dans l'historique. |
| [`docs/index.html`](docs/index.html) | Chargeait le HTML depuis `main` et le réécrivait via `document.write`. N'est plus qu'une redirection vers la préview du portail. |
| [`docs/phone3-back.webp`](docs/phone3-back.webp) | Photo du dos, détourée et rognée (704 × 913, ~52 Ko) |

Publication : GitHub Pages en mode *legacy*, source `main` + `/docs` → **le
push suffit**, aucun workflow à déclencher.

## Photo du dos

Source 2048 × 2048 avec canal alpha, détourée au corps du téléphone
(845 × 1768 px), puis rognée aux **62 % supérieurs** — les 40 % du bas ne sont
jamais rendus. Réduite à 704 px de large, encodée en WebP q0.78. Asset partagé
avec le repo glyphslot.

Toutes les positions sont exprimées en **pourcentage du cadre photo**, jamais
en pixels : c'est ce qui garde le calage quand la préview est redimensionnée.

| Élément | Position | Diamètre |
|---|---|---:|
| Disque de la Glyph Matrix (`.disc`) | 79,53 % / 15,36 % | 26,04 % |
| Glyph Button (`.glyphbtn`) | 84,53 % / 74,82 % | 15,86 % |
| Rappel « appui long » (`.glyphhint`) | 74,6 % / 74,82 % | — |

Le cadre `.phone` porte `aspect-ratio: 704/913`. Pour changer de photo : refaire
le relevé des centres par superposition de cercles à l'échelle native, puis
mettre à jour ces valeurs, rien d'autre ne bouge.

La coupe basse se fait par `mask-image` sur l'`<img>` (fondu 86 % → 99 %) et
non par un aplat superposé, sinon la trame de fond disparaîtrait sur la bande.

## Grille LED : pixels entiers

Un canvas de taille fixe redimensionné par le navigateur avec un ratio
fractionnaire produit une trame irrégulière — une colonne sur n gagne un pixel
de gap. La grille est donc calculée depuis le `devicePixelRatio` :

```js
const CELL = Math.max(3, Math.round(6 * DPR));      // px de canvas par LED
const LED  = Math.max(2, Math.round(CELL * 2 / 3)); // côté du carré allumé
const PAD  = (CELL - LED) / 2;                      // entier, donc net
cvs.width = cvs.height = SIZE * CELL;               // 150 @1x, 300 @2x
```

Le disque fait 26,04 % de 576 px, soit les **150 px CSS** visés. Mesuré :
`backing 150`, `cssW 149,98` → **ratio 1,0001**, pas de rééchantillonnage.
Exact pour dpr 1 / 1,5 / 2 / 3 ; approché pour les dpr en quarts. Sous 576 px
de large, la mise en page redevient proportionnelle et l'irrégularité peut
réapparaître.

Le téléphone mesure **576 × 747 px**, la matrice **150 px** de diamètre — soit
l'échelle réelle du composant sur un écran classique. `main` et `footer` sont
passés à `max-width: 1040px` pour que le rack de contrôles tienne à côté.

## Multi-lapse

Miroir de `Config.kt` et `LapseToyService.kt` : **3 lapse indépendants**, chacun
avec sa date de référence, son format et son animation des secondes.

| Règle | Valeur |
|---|---|
| Nombre de lapse | 3 (`LAPSE_COUNT`), notés I / II / III |
| Lapse I | toujours activé, non désactivable |
| Lapse II et III | désactivés par défaut, à activer pour entrer dans la rotation |
| Référence par défaut | début d'année pour I et II, 31 décembre 23:59 pour III (donc en « jusqu'à ») |
| Format par défaut | Dense |
| Animation par défaut | Anneau (côté toy) — la préview ouvre le **lapse I sur le sablier** pour le donner à voir d'entrée |

L'onglet sélectionné est aussi le lapse **affiché**, comme dans l'app de
réglages où `selectTab` écrit `active_lapse` et fait basculer le toy.

`nextEnabledIndex()` reprend la rotation du service : on avance parmi les lapse
activés, et si un seul l'est, la fonction renvoie l'actif — le toy ne fait
alors rien. La préview affiche `UN SEUL LAPSE ACTIVÉ` dans ce cas, sinon
l'absence de réaction passerait pour une panne.

## Formats

Quatre formats, alignés sur `LapseEngine.Format` :

| Index | Nom | Rendu |
|---:|---|---|
| 0 | Dense (`DETAIL2`) | Granularité complète, 2 unités par ligne si besoin, police 3×5 |
| 1 | Compact | Les 2 unités les plus significatives en 5×7 |
| 2 | Cycle | Une unité plein écran, **défilement vertical** toutes les 2 s |
| 3 | Jours | Total de jours, « J-42 » en compte à rebours |

Le cycle défile verticalement — le slide horizontal est réservé au changement
de lapse, les deux animations doivent rester distinguables. L'ancien format
« Détail » (une ligne par unité, police 3×4 à 5 lignes) a été retiré du toy ;
la police `F4` qu'il était seul à utiliser a disparu avec lui.

## Interaction

Le bouton est un `<button>` transparent superposé au Glyph Button de la photo,
avec un glow jaune Nothing (variable CSS `--yellow: 255,229,0`) :

| État | Rendu |
|---|---|
| repos | pulse `glyphPulse` 2,2 s |
| survol | même pulse à 1,1 s + halo radial |
| pressé (`.is-pressed`) | anneau plein, halo large, ombre interne |
| rappel actif (`.is-hint`) | pulse à 0,7 s |

Un appui de **450 ms** passe au **lapse activé suivant**. Relâché avant, ou
pointeur sorti du bouton en cours d'appui, le rappel `APPUI LONG →` apparaît à
gauche du bouton pendant 1,9 s. Les drapeaux `down` / `fired` distinguent les
deux cas.

Au clavier (`Enter` / `Espace`) la bascule se fait directement : il n'y a pas de
notion d'appui long, et le bouton doit rester utilisable.

### Tactile

Sans précautions, l'appui long mobile déclenche l'appui long *système* — sur
Chrome Android, le menu contextuel de l'image du dos, que le bouton recouvre.
Quatre verrous, tous nécessaires :

| Verrou | Rôle |
|---|---|
| `pointer-events: none` sur l'`<img>` | la photo sort du hit-test : plus de menu « enregistrer l'image » |
| `touch-action: none` sur `.glyphbtn` | le navigateur ne prend pas l'appui pour un début de scroll et ne vole pas le geste |
| `-webkit-touch-callout` + `user-select: none` | pas de callout iOS, pas de sélection de texte |
| `preventDefault` sur `touchstart` (`{passive:false}`) et `contextmenu` | désamorce l'appui long système avant nos 450 ms |

`pointerdown` est émis avant `touchstart`, la machine à états est donc intacte.
`pointercancel` remet l'état à zéro **sans** afficher le rappel : le geste a été
repris par le navigateur, l'utilisateur n'a rien fait de travers.

Le changement de lapse s'accompagne d'un `navigator.vibrate(12)` là où c'est
supporté — écho du tick haptique de `LapseToyService`.

## Slides

Deux transitions horizontales, de durées distinctes comme dans le toy :

| Transition | Durée | Source |
|---|---:|---|
| Changement de format | 0,30 s | `LapseEngine.SLIDE` |
| Changement de lapse | 0,35 s | `LapseToyService.LAPSE_SLIDE` |

Même compositing dans les deux cas : l'ancienne frame sort par la gauche, la
nouvelle entre par la droite, easing `1 - (1-p)³`. L'objet `slide` porte sa
propre durée (`{from, start, dur}`). Sur l'appareil, le changement de lapse
s'accompagne d'un tick haptique.

## Fond

Gris très sombre légèrement bleuté + trame de points, façon DA Nothing.

| Élément | Valeur |
|---|---|
| `--bg` | `#0C0D10` |
| point | carré 2 × 2 px, `#9AA3C8` à 22 % |
| pas | 132 px |

Les points sont un SVG en data-URI, pas un `radial-gradient` : à ce rayon un
gradient serait antialiasé et donnerait des ronds flous.
`background-attachment: fixed` — la trame ne défile pas avec le contenu.

## Contrainte de publication

`docs/index.html` charge la page depuis `raw.githubusercontent.com` : les
modifications ne sont visibles sur Pages qu'une fois **poussées sur `main`**.
Les chemins relatifs (`phone3-back.webp`) se résolvent contre l'URL de la page
Pages, pas contre celle du HTML source — l'asset doit donc vivre dans `docs/`.
