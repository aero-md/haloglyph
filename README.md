# Haloglyph

Un pack de toys 25×25 pour la **Glyph Matrix du Nothing Phone (3)** — qui tourne
aussi **sur les téléphones qui n'en ont pas**.

Sur un Phone (3), les toys s'installent dans Glyph Interface et s'affichent sur
la matrice arrière. Sur n'importe quel autre Android, les mêmes toys tournent
dans des **widgets d'écran d'accueil** qui émulent la matrice au pixel près —
mêmes moteurs, mêmes renderers, même masque de 489 LEDs.

Un widget n'a pas de fond : c'est le **hublot**, rond, posé sur le fond d'écran.
Il n'y a pas de carte, pas de coins à négocier avec le launcher et rien à
redimensionner — la matrice est carrée, l'étirer ne lui apporterait rien. Deux
formats au choix, **2 × 2** et **1 × 1**.

Il n'y en a **qu'un**, et il les porte tous. On ne pose pas « le widget du dé »,
on pose un hublot et on décide ensuite ce qu'il montre : **tap** pour interagir
avec le toy affiché, **double tap** pour passer au suivant — qui **part tout
seul**, sans qu'on ait à le retaper : personne ne fait défiler la rotation pour
contempler des `TAP`. Un appui long ouvre
ses réglages, en deux onglets — les toys qu'il fait tourner, et son apparence :
l'animation, la trame (`sharp` ou `soft`, les deux rendus du portail) et le
reflet du verre. Tout est réglé **par hublot posé**, contenu compris : deux
hublots côte à côte peuvent tourner sur deux toys différents, parce que deux
écrans d'accueil n'ont ni le même fond ni le même usage.

Un tap anime **trente secondes**, ou moins si le toy a fini avant — le dé est
posé et lu en 3,15 s, il n'y a plus d'images à faire. Un hublot peut aussi être
réglé **en continu** : il anime alors sans fin, et se suspend dès que l'écran
s'éteint. Repeindre un widget que personne ne regarde ne fait pas une animation
plus fluide, seulement une batterie plus vide.

**Trois hublots au maximum**, tous formats confondus. Ce n'est pas une limite de
produit mais de coût : chacun ajoute sa part de réveils et de bitmaps dans le
même tuyau, et au-delà de trois ce qu'un hublot de plus apporte ne couvre plus ce
qu'il coûte aux autres. Le quatrième est refusé à la pose, avec un message qui le
dit. Une application ne peut pas empêcher un launcher de poser un widget — elle
peut seulement, depuis son écran de configuration, rendre `RESULT_CANCELED` et
le faire retirer. C'est pour ça que cet écran s'ouvre désormais à la pose.

> **État : les cinq toys sont écrits et déclarés au système.** La plomberie est
> en place — géométrie de la matrice, les trois sinks, le socle des services
> Glyph et du hublot, le hub. **Lapse**, **Dice**, **Sono**, **Plumb** et
> **G-Forces** apparaissent dans Glyph Interface, et tous les cinq tournent dans
> le hublot d'écran d'accueil. Le hub ne l'écrit nulle part en dur, il interroge le
> `PackageManager`.

## Les toys

| Toy | Origine | Ce qu'il fait |
|---|---|---|
| **Lapse** | [glyphlapse](https://github.com/aero-md/glyphlapse) | Compteur temporel : temps écoulé depuis / restant jusqu'à une date. Jusqu'à 5 lapse, anneau ou sablier. |
| **Dice** | [justadice](https://github.com/aero-md/justadice) | Dé d6/d10/d12/d20, solides 3D suivis en quaternion, secouer pour jeter. |
| **Sono** | [sonoglyph](https://github.com/aero-md/sonoglyph) | Le micro sur la matrice, en trois modes : spectre 25 bandes, VU-mètre à aiguille en dB(A), forme d'onde qui défile. |
| **Plumb** | écrit ici | Niveau à bulle à **échelle logarithmique**, et boussole en second instrument. |
| **G-Forces** | écrit ici | Accéléromètre de bord : une bille pour ce qu'on subit, les quatre pics du trajet en second. |

Chacun se change à l'**appui long** sur le Glyph Button : le lapse suivant, le
solide suivant, le mode suivant, l'instrument suivant, l'autre face du cadran.

Dans un widget, ce geste-là est le **tap** — un appui long y appartient au
launcher, qui en fait son menu « déplacer, supprimer, régler », et aucune
application ne peut le lui prendre. Le tap fait donc ce que le Glyph Button
fait : il avance d'un lapse, il relance le dé, il passe au mode de Sono ou à
l'instrument de Plumb suivant.

### Plumb, et pourquoi son échelle n'est pas proportionnelle

C'est le seul toy du pack qui ne vienne pas d'une app existante, et il existe
pour corriger un défaut qu'ont tous les niveaux d'écran : leur bulle se déplace
**proportionnellement à l'angle**. Sur une fiole en verre, ça marche — dix
centimètres de course, une bulle qui glisse en continu. Sur un disque de
vingt-cinq LEDs, le rayon utile fait onze cellules : à 15° de pleine échelle,
une cellule vaut 1,4°, et en dessous rien ne bouge. L'instrument affiche « c'est
droit » pour une étagère qui penche d'un degré, ce qui est justement l'erreur
qu'on est venu voir.

La bulle suit donc `r = R · ln(1 + θ/K) / ln(1 + θmax/K)` : chaque cellule vers
le bord vaut un **rapport** d'angle constant et non une différence constante —
le décibel, appliqué à une pente. Avec un genou à 0,20°, un degré déplace la
bulle de cinq cellules au lieu de deux tiers, **un dixième de degré se voit**, et
une inclinaison franche colle la bulle au cerne. La propriété utile en prime :
changer de pleine échelle ne dégrade presque pas la finesse au centre, ce qui est
pour quoi le réglage s'appelle une portée.

À pleine échelle, la bulle **sort à moitié** du champ. Une bulle écrêtée
s'arrêterait entière à deux cellules du bord, comme si l'échelle s'y terminait ;
ici son centre atteint le cerne et la moitié du motif tombe hors du masque, ce
que fait une vraie bulle dans une fiole trop courte.

L'instrument se lit en **deux temps**. La **couronne de visée** au centre est
l'approche : en retrait, elle ne bouge jamais, elle n'a pas d'état. Le **cerne**
est le verdict, tout ou rien — éteint tant qu'on cherche, plein d'un coup quand
c'est droit.

Et « droit » veut dire **exactement au centre** : la bulle est encore posée sur la
cellule centrale, et pas sur celle d'à côté. C'est la seule définition qu'un
affichage de vingt-cinq LEDs puisse tenir, et elle a la même nature que la zone
morte du cap — le pas de l'affichage, pas un chiffre choisi. En degrés : **±0,028°
en portée fine, ±0,038° en normale, ±0,048° en large**, soit deux à trois minutes
d'arc.

Ça se paie, et c'est le bon prix : le cerne ne s'allumera **pas souvent**. Un plan
de travail ordinaire n'est pas plat à un millimètre par mètre, et un Phone (3)
posé sur son dos repose sur son bloc photo — dans cette pose-là, jamais. Le seuil
précédent, un demi-degré, revenait à certifier « de niveau » une étagère qui tombe
d'un centimètre sur un mètre. Un niveau qui s'allume tout le temps ne dit rien.

La couronne a longtemps *été* la tolérance — « de niveau » voulait dire « la bulle
est dans la couronne », et le seuil se calculait depuis le rayon du cercle. Cet
invariant ne survit pas à une tolérance d'une demi-cellule : une couronne à ce
rayon-là serait entièrement sous la bulle, donc invisible, donc invisable.

Au-delà de **70° d'inclinaison**, l'instrument change de question : on ne mesure
plus la planéité mais l'**aplomb**, et sur un seul axe — « est-ce que ce montant
est droit ? » n'a qu'une dimension. La bulle court alors dans un **tunnel**, entre
deux repères qui l'encadrent d'une cellule de jeu de chaque côté : on la voit se
caler entre les deux marques à l'instant précis où le cerne s'allume. Le tunnel se
tourne avec le téléphone — en portrait c'est l'axe X qui devrait être horizontal,
couché d'un quart de tour c'est l'axe Y. Le décollement hors du plan est ignoré :
il dit comment la main tient, pas comment la surface penche.

Deux hystérésis, et elles ne servent pas à la même chose : cinq degrés sur la
bascule à plat / debout, sans quoi un téléphone tenu sur le seuil verrait sa
lecture sauter de 70° à 20° plusieurs fois par seconde ; soixante degrés sur le
choix du quart de tour, là où la géométrie en donnerait quarante-cinq, pour qu'un
téléphone tenu de travers garde l'axe sur lequel on a commencé à lire.

**Il n'y a pas de remise à zéro**, et c'est un choix. Il y en a eu une — on
secouait, la pose courante devenait la référence — pour rattraper le fait qu'un
Phone (3) ne repose pas à plat sur son dos. Elle est partie : un niveau à bulle
n'a pas de réglage de zéro, c'est ce qui en fait un instrument. On le pose, il
dit la vérité, et si la réponse déplaît c'est le meuble qui a tort.

Le vrai piège est ailleurs, et il n'a rien à voir avec le filtrage : **un
accéléromètre ne distingue pas une inclinaison d'une translation.** Glisser le
téléphone à plat sur la table envoyait donc la bulle dans tous les sens.
L'information n'est pas dans le signal, elle est dans le gyroscope — le toy lit
la gravité **fusionnée** de la plateforme, qui ne bouge pas quand l'appareil se
déplace sans tourner. Le repli sur l'accéléromètre seul reste écrit, et reste
sensible à ce que la physique lui impose.

Dernier détail de terrain : la matrice se regarde **par l'arrière** là où un
hublot se regarde de face. Le renderer nie son abscisse d'un côté, sinon l'une
des deux surfaces envoie la bulle du mauvais côté.

La rose, elle, **ne se retourne pas** — et c'est le seul dessin du pack dans ce
cas. Le niveau mesure une direction dans le repère de l'appareil : le flanc droit
de l'écran reste le flanc droit de l'écran, donc le miroir s'applique. La boussole
montre une direction **du monde**, et pour regarder la matrice il faut poser le
téléphone écran en bas — ce retournement est un second miroir, et les deux
s'annulent. Appliquer celui du niveau ici échangeait `e` et `w`, ce qui est
exactement ce qu'on ne pardonne pas à une boussole.

La boussole tient sur **quatre lettres** au cerne — `n`, `e`, `s`, `w` — et une
aiguille au centre. Rien d'autre.

Il y a eu un anneau, puis huit repères triangulaires, puis quatre motifs abstraits
— croix pleine pour le nord, losange creux pour les autres — et un cap chiffré au
milieu. Chaque couche demandait d'apprendre une convention avant de pouvoir lire
l'instrument. **Une lettre ne demande rien** : un `n` au bord du disque dit où est
le nord à quelqu'un qui n'a jamais ouvert le toy.

Les intercardinaux sont partis avec le reste : sur vingt-cinq LEDs, huit repères
font une couronne où le nord — la seule chose qu'on cherche — se perd au milieu
des sept autres. Le cap chiffré aussi : trois chiffres donnaient une précision que
l'instrument n'a pas, le nord magnétique non corrigé de la déclinaison se trompant
déjà de plusieurs degrés. On ne sort pas une boussole pour savoir qu'on est à
214°, mais pour savoir où est le sud.

Les quatre lettres **ne sont pas d'une seule police** : `n` fait trois colonnes
sur quatre lignes, `e` quatre sur cinq, `s` trois sur cinq, `w` cinq sur quatre.
C'est délibéré — chacune est dessinée pour sa place au cerne, où le disque n'offre
pas la même largeur en haut qu'au flanc. Ce qui tourne est leur **position**, pas
leur dessin : une lettre inclinée n'existe pas sur cette grille, et une lettre
redessinée à chaque degré respirerait comme du bruit. Quand le disque se rétrécit
et qu'une boîte n'y tient plus entière, la lettre **rentre** d'un dixième de
cellule à la fois plutôt que de se faire amputer — un `s` sans son pied ne se lit
plus.

L'aiguille est la seule chose qui ne tourne pas : elle traverse le centre et
pointe midi, pointe en haut, fût jusqu'en bas. Elle est le téléphone ; tout le
reste tourne autour d'elle. Sa tête est **évidée** — deux traits qui s'écartent,
et rien entre eux. Pleine, elle faisait une tache de neuf cellules au milieu du
disque : l'œil s'y accrochait au lieu de chercher le `n`, et elle empâtait la zone
que les lettres traversent en tournant.

### Ce qu'un hublot montre quand il ne mesure pas

`TAP` seul a tenu tant que le pack comptait deux toys silencieux. À cinq, trois
hublots l'affichaient au pixel près et on ne savait pas lequel on allait
réveiller. Le repos porte donc **deux étages** : le mot en haut, la marque en
dessous.

Le mot est le même partout — même police 3×4, même taille, même ligne. Un hublot
ne se distingue pas par sa façon d'écrire « tape-moi ». Le `P` y a perdu son angle
droit au passage : `111/101/111` donnait un rectangle plein en haut, qui se lisait
comme un bloc et non comme une lettre.

La marque, elle, est le toy en miniature : la fiole et sa bulle ou la rose pour
Plumb, la bille entre ses quatre graduations pour G-Forces, l'onde pour Sono — qui
montrait déjà la sienne entre ses deux mots, et à qui les deux autres empruntent
la grammaire. Rien de ce qui est dessiné n'est une mesure : aucun capteur n'écoute
entre deux rafales, et un niveau qui afficherait l'horizontale d'hier serait pire
qu'un niveau vide.

Le bloc entier est centré, mot compris — centrer la marque seule poussait le mot
au-dessus d'elle, donc tout l'ensemble trop haut — puis la **marque seule descend
de deux lignes**. Au milieu pile, une image se lit comme la mesure qu'elle n'est
pas ; deux lignes plus bas, le mot et le dessin se séparent franchement.

Chez Plumb, la marque **suit le mode** : la rose pour la boussole, la fiole pour
le niveau, même boîte de neuf cellules et même place. Les deux ont partagé la même
image, au motif qu'un hublot au repos dit quel *toy* on va réveiller et pas ce
qu'il affichera. C'était vrai tant qu'il n'y avait qu'une question à poser : le tap
change d'instrument, et on ne savait pas lequel on allait réveiller avant de
l'avoir réveillé — donc trop tard.

Et le cap est pris **tel quel**, avec une zone morte d'un degré. Il a été lissé
par un passe-bas, et c'était le mauvais outil : un filtre n'enlève pas le
tremblement, il l'étale — la rose glissait derrière la main et frémissait quand
même une fois posée. La zone morte fait l'inverse des deux côtés : au-delà d'un
degré le cap est affiché sans le moindre retard, en deçà rien ne bouge du tout.
Un degré est exactement la marche de l'affichage, donc il n'y a rien dessous à
montrer.

**Aucun anticrénelage nulle part**, dans les deux instruments. Sur vingt-cinq
LEDs, un bord dégradé ne lisse rien : il élargit la forme d'une cellule et
brouille le seul repère dont l'œil dispose. Les nuances qui restent sont des
niveaux — la visée en retrait, le trait de pleine lumière — et chacune code
quelque chose.

### G-Forces, et pourquoi l'orientation compte plus que le capteur

Un accéléromètre de bord, téléphone debout dans un support. Une **bille** dit ce
qu'on subit maintenant ; l'appui long bascule sur les **quatre pics** du trajet.

La tentation serait de lire l'accéléromètre et d'afficher. Ça ne marche pas, et
pas à cause du capteur : le LSM6DSV d'un Phone (3) laisse passer moins d'un
millième de g une fois filtré, et son défaut d'usine borne la mesure autour de
**±0,03 g**. C'est l'orientation qui se trompe d'un ordre de grandeur au-dessus —
**un téléphone penché de six degrés laisse fuir 0,10 g de gravité dans ses axes
horizontaux**, et un support de pare-brise n'est jamais d'aplomb.

D'où la construction : la gravité fusionnée donne la verticale du monde, et
l'accélération propre y est **projetée sur le plan horizontal**. Ça supprime d'un
coup l'inclinaison du support *et* la pente de la route — freiner dans une
descente à 5 % ajouterait sinon 0,05 g de pure géométrie. L'avant du véhicule se
déduit de la pose : il sort par le **dos** de l'appareil quand il est debout, par
le **haut** quand il est posé à plat sur la planche de bord, avec une hystérésis
pour qu'un cahot ne fasse pas tourner le cadran d'un quart de tour.

Ce qui reste hors de portée est le **lacet** : rien ne dit où pointe la voiture.
Un téléphone qui roule dans un vide-poches n'affiche pas une mesure dégradée, il
affiche du bruit, et c'est écrit dans les réglages plutôt que caché.

Le filtre à 4 Hz n'est pas un confort de lecture : **la suspension bat plus vite
que la voiture n'accélère**. Un nid-de-poule envoie deux g en trente
millisecondes ; sans filtre, le premier trou de la route devient le pic de la
journée. Il divise sans effacer — une secousse d'un échantillon passe au tiers —
parce que couper plus bas abîmerait la montée d'un vrai freinage.

L'échelle, elle, est **linéaire**, à l'exact opposé de Plumb : toute la plage est
intéressante, il n'y a aucune zone où l'on regarderait de plus près. Les deux
graduations par côté valent **0,5 g et 1,0 g** — un freinage franc, et la limite
d'adhérence d'un pneu de route sur bitume sec. Ce ne sont pas des tiers de pleine
échelle, c'est l'inverse : les deux nombres sont choisis, la pleine échelle en
découle.

Les quatre pics s'écrivent en **4×5 arrondie**, une police née pour ce cadran et
que la boussole de Plumb a reprise depuis : la 5×7 ne laisse la place qu'à deux
valeurs, et les deux polices étroites dessinent des chiffres où `0` et `8` ne
diffèrent que d'une cellule — pas assez pour un coup d'œil en conduisant. Elle
n'a pas de virgule et ne peut pas en avoir ; le renderer la dessine d'un **seul
pixel**, une ligne sous la ligne de base, dans la gouttière qui sépare déjà les
deux chiffres. Ça ne coûte aucune colonne.

Le centre de la croix est **vide**. Il y a eu un `G` au milieu, et il ne disait
rien que le toy ne dise déjà — son nom dans Glyph Interface, son unité dans les
réglages, et quatre nombres entre 0 et 2 qui ne peuvent pas être autre chose que
des g. Un ornement qui répète l'évidence coûte l'endroit où l'œil se pose
d'abord.

Les pics ne sont **pas persistés**, et c'est un choix : un pic de g est l'histoire
d'un trajet, pas un record à battre. Ils ferment avec le toy.

Dernière chose, et c'est une première dans le pack : **la matrice n'est pas la
surface principale**. Dans un support, le téléphone regarde le conducteur, donc la
matrice regarde la route. C'est le hublot qu'on lit en roulant, et de préférence
en boucle continue — une rafale de trente secondes ne couvre pas un trajet.

### Le rendu émulé

L'aperçu de l'app et le widget passent par le **même peintre** (`core:look`), et
ce peintre reprend au détail près le rendu `sharp` de
[GlyphPortal](https://glyph.suns.red) : LED carrée sur champ presque noir, halo
proportionnel à la luminosité, et le biseau du verre — un liseré à 97 % du rayon
dont la brillance varie avec la direction, relevé sur une photo du dos d'un
Phone (3). Les deux surfaces ont longtemps dessiné chacune de leur côté en
lisant la même table de couleurs, ce qui garantissait les couleurs et rien
d'autre.

Le second rendu du portail, `soft`, est là aussi, et un widget peut le demander :
coins adoucis, aucun halo, contraste inversé, rampe quasi linéaire. Ce n'est plus
ce que montre l'appareil — c'est la trame telle qu'un écran l'affiche, où les
nuances se lisent LED par LED.

Trois étages sur quatre ne changent jamais — le champ, les 489 LEDs éteintes, le
biseau — et sont peints une fois dans une bitmap que chaque image repose. Une
image ne dessine donc que ce qui est allumé.

### Ce qui décide de la fluidité d'un hublot

Le plafond n'est pas dans le rendu : chaque image traverse le binder **en
entier** vers le launcher, 260 Ko à chaque fois, et rien ne rendra un widget
aussi fluide que la matrice. Ce qui était dans notre code, en revanche, a été
repris — la boucle cale des **échéances absolues** au lieu de dormir un temps
fixe après avoir travaillé (elle annonçait seize images par seconde et en rendait
dix), une rafale ne renvoie plus que l'image et non le layout entier avec son
intention de tap, et l'apparence comme la définition sont relevées une fois par
rafale plutôt qu'à chaque image.

Surtout, l'animation a **quitté le receiver**. Elle y tournait sous `goAsync()`,
et Android sérialise la livraison des diffusions : tant que le receiver n'a pas
rendu la main, la file ne repart pas. Un hublot qui animait cinq secondes
empêchait donc tout autre hublot d'animer, mettait en attente les taps qu'on lui
donnait entre-temps, et les rejouait tous à la suite quand il avait fini. Ce
n'était pas une limite de la plateforme. C'est un service qui anime désormais,
un seul pour tous les hublots, et les trois symptômes sont partis ensemble.

### Ce qu'un hublot ne dépense pas

Une image rendue n'est pas une image poussée. Le moteur d'un toy coûte quelques
dizaines de microsecondes ; **rasteriser** cette image puis la faire traverser le
binder coûte 260 Ko, vingt-cinq fois par seconde. Or une boucle passe l'essentiel
de son temps à redessiner exactement la même chose — Lapse en continu ne change
qu'à la seconde, soit vingt-quatre images sur vingt-cinq identiques à la
précédente.

Chaque image est donc comparée à la dernière **réellement partie**, et abandonnée
si rien n'a bougé : une comparaison de 625 entiers contre une rasterisation et une
transaction. Le dé, lui, change à chaque image pendant son jet et ne perd rien —
c'est bien ainsi que la dépense doit se répartir.

Le reste tient en trois règles : l'écran éteint suspend tout, trois hublots au
maximum, et un service qui s'arrête de lui-même dès qu'aucune boucle ne le
réclame.

### Le clignotement

Il n'avait rien à voir avec la cadence. Remettre un
hublot au repos passait par une diffusion `APPWIDGET_UPDATE`, à laquelle chaque
fournisseur répond par un `RemoteViews` **complet** — et un `RemoteViews` complet
fait ré-inflater la vue au launcher. Tous les hublots de l'écran clignotaient
donc à chaque double tap, y compris ceux qui n'avaient pas bougé. Le service
peint lui-même et pousse en `partiallyUpdateAppWidget`, qui ne remplace que
l'image : pas de diffusion, pas de ré-inflate. La diffusion reste la bonne route
pour qui n'a pas de quoi peindre sous la main.

Les cinq secondes de rafale venaient de là, elles aussi : au-delà de dix, le
système tue un receiver. Le receiver n'anime plus, la borne est partie avec lui.
Un hublot en boucle continue vit dans un service de premier plan de type
`specialUse` — le même que celui que Glyph Museum déclare pour la même chose,
avec la justification que la revue Play réclame — et sa notification, obligatoire
mais sans rien à dire, ne se montre jamais : canal d'importance minimale,
visibilité secrète.

Trois toys, pas cinq. **Slot est abandonné** — une machine à sous déclenche un
PEGI 18 qui contaminerait tout le pack, et maintenir une seconde app Play pour un
toy sans réglages coûte plus cher que ce qu'elle rapporte ;
[glyphslot](https://github.com/aero-md/glyphslot) reste un dépôt à part. Et Sono
n'en fait qu'un là où Sonoglyph en exposait deux : « Spectre » et « Aiguille »
ouvraient le même micro et faisaient tourner la même FFT pour ne différer que par
le dernier étage du rendu.

### Sono et le micro

C'est le seul toy du pack qui demande une permission.

L'autorisation est demandée **à la première ouverture de l'app**, une fois. Un
Glyph Toy est un service lié par Glyph Interface : il n'a pas d'écran, donc il ne
peut pas poser la question lui-même. Refusée, la ligne de Sono s'affiche
atténuée dans le hub, avec ce qui manque et où le corriger — la ligne reste
cliquable, et son écran de réglages redemande l'autorisation ou mène à la page
des autorisations de l'app quand Android ne veut plus rien afficher.

Accordée, elle ne suffit pas. `RECORD_AUDIO` n'existe que **pendant
l'utilisation de l'app**, et depuis Android 14 un service de premier plan
démarré depuis l'arrière-plan ne l'obtient pas : la notification s'affiche, la
capture s'ouvre, et elle ne rend que des zéros exacts. C'est exactement le geste
qu'on veut faire — retourner le téléphone pour regarder la matrice — donc
exactement le moment où Android referme le micro.

Une capability, en revanche, **reste acquise tant que le service vit**. D'où le
micro armé : un appui avant de poser le téléphone, et la capture survit au
verrouillage.

Deux surfaces le proposent, et elles ne se valent pas. Le **volet de
notifications** est la bonne : un bouton d'action y arme le micro sans rien
refermer, parce que « le service démarre par interaction avec une notification »
est une exemption à part entière — c'est le système qui envoie le `PendingIntent`,
donc l'app compte comme sollicitée depuis une surface visible. Le volet montre
toujours exactement un état : *Activer* quand le micro dort, *Couper* quand il
écoute. La **tuile de réglages rapides** fait la même chose par un chemin moins
élégant : taper une tuile n'est pas une exemption, il lui faut donc lancer une
activité qui ne montre rien et ne vit que le temps de rendre le démarrage légal
— et lancer une activité referme le volet, `startActivityAndCollapse` étant la
seule méthode que l'API expose. Elle reste utile pour lire l'état d'un coup
d'œil.

Le même interrupteur est dans les réglages de Sono, qui proposent aussi
d'installer la tuile et d'autoriser les notifications. Désarmé, le toy tente
quand même sa chance : app au premier plan, ça marche encore.

Sono a longtemps été **le seul toy sans widget**, et pour une bonne raison : un
hublot qui écouterait le micro en permanence sur un écran d'accueil est
indéfendable, et le dire dans une note de version ne le rendrait pas défendable.
Il y est maintenant, et rien de ce refus n'a été abandonné — il n'écoute que dans
les trente secondes qui suivent un tap, pastille Android allumée pendant tout ce
temps, et il ne boucle jamais. Le tap sur un widget est d'ailleurs la
troisième exemption de la liste : c'est ce qui rend l'ouverture légale, là où le
toy, lui, n'y arrive pas.

Au repos, le hublot **ne montre pas ce que montre la matrice**, et c'est le seul
endroit du pack où les deux surfaces divergent exprès. Sur la matrice, le repos de
Sono dit « pas de mesure » et n'a rien d'autre à dire — on y arrive en retournant
le téléphone, il n'y a aucun geste à suggérer. Dans un hublot, c'est ce qu'on voit
99 % du temps, et il y manquait la seule chose qui compte : comment le réveiller.
D'où `TAP` et une forme d'onde, au lieu du libellé seul. Dès qu'une mesure court,
les deux surfaces se rejoignent, parce qu'elles ont alors la même chose à dire.

Il y a eu trois étages — `MIC`, l'onde, `TAP` — et le premier ne disait rien que le
toy ne dise déjà : ce qui s'ouvre au tap se voit dès la première image de mesure.
Sono suit donc maintenant la grammaire commune, mot en haut et marque en dessous.

L'onde de ce repos a été **dessinée par le renderer du toy**, pas imitée : même axe
médian plein, mêmes barres symétriques, même conversion d'un niveau en pixels — à
qui on poussait des niveaux inventés, faute de pouvoir les emprunter à une scène
qui, stationnaire, finit par devenir son propre fond et donc parfaitement plate.
Elle est maintenant posée cellule par cellule, comme la rose de Plumb et le cadran
de G-Forces : une **vignette d'identité**, qui n'a jamais prétendu mesurer quoi que
ce soit. Un repos qui ferait croire à une mesure serait pire qu'un repos vide. Le
dessin a d'ailleurs été retouché à la main depuis, et ses deux grandes crêtes
montent un peu plus haut qu'elles ne descendent : à vingt-cinq colonnes, une
symétrie parfaite a l'air d'un pochoir, pas d'une voix.

Le hublot a par ailleurs un temps **sauté** Sono quand l'autorisation manquait, au
motif qu'il n'avait rien de vrai à montrer. C'était l'inverse : dire ce qui manque
est exactement ce que fait un toy, et afficher le voisin à la place était la seule
façon de mentir.

Le son est analysé au vol et n'est jamais enregistré ni transmis. **Les aperçus
de l'app n'ouvrent pas le micro** : ils jouent le vrai renderer sur une scène
simulée, pour que la pastille micro d'Android ne s'allume pas pour une vignette.

Les préviews web de chaque toy vivent dans
[GlyphPortal](https://github.com/aero-md/GlyphPortal) → [glyph.suns.red](https://glyph.suns.red).

## L'app

Un dock flottant en bas de l'écran porte trois onglets, à la manière de Glyph
Museum et Glyph Beat : tout en rond, chacun son signe — une mini matrice, un
disque en pointillé, un engrenage — et seul l'onglet actif porte le rouge et
son mot. Changer d'onglet fait glisser cette pilule rouge d'une position à
l'autre.

**Toys** liste le pack, une carte par toy. Un toy ouvre son écran de réglages,
qui vit dans le module du toy. Les quatre en ont un, y compris Dice, qui s'en
est longtemps passé : sur la matrice le solide se change à l'appui long, mais
il n'y a pas de Glyph Button sur un écran d'accueil.

**Cast** est réservé, sans écran derrière pour l'instant.

**Réglages** porte la langue, seule valeur qui ait un sens pour tout le pack,
et — sur un Phone (3) seulement — les deux écrans Glyph du système : la liste
des toys actifs et le gestionnaire, où on active et réordonne. Nothing ne
publie d'action documentée pour ni l'un ni l'autre, alors `GlyphSettings`
énumère ce que `com.nothing.thirdparty` expose vraiment et ne montre que les
boutons qui ouvriront quelque chose.

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
