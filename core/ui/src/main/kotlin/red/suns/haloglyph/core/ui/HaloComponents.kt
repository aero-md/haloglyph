package red.suns.haloglyph.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp

/**
 * Les briques de l'interface de réglages, communes au hub et à tous les toys.
 *
 * Même raison d'être que [MatrixPreview] pour la matrice : un écran de réglages
 * ne réinvente pas une carte, un sélecteur ou une pastille d'état. Ce qui est
 * ici est ce que la maquette a tranché, et un toy qui s'en écarterait se
 * verrait immédiatement.
 *
 * Les règles que ces composants portent, et qu'on ne renégocie pas au cas par
 * cas :
 *
 * - **Aucune glose.** Pas de paragraphe gris sous un réglage. Un réglage dont le
 *   nom ne suffit pas est un réglage mal nommé — la description masquait le
 *   problème au lieu de le régler.
 * - **La carte n'a pas de bordure.** Elle se détache par son fond.
 * - **Trois familles de contrôle, et pas une de plus** : le rectangle arrondi
 *   (rayon 12–14) pour tout ce qui porte une *valeur* — sélecteur, champ, date
 *   sauvegardée ; la pilule pour une *action* ; l'action nue, hors carte, pour
 *   ce qui n'est pas un réglage (une suppression).
 * - **Deux tuiles ne se retrouvent côte à côte que si elles sont liées par le
 *   sens.** [LinkedTiles] le dit à l'œil avec son pont ; s'en servir pour
 *   remplir une ligne serait un mensonge de mise en page.
 * - **Toutes les listes d'options sont la même liste.** Une seule taille de
 *   texte, un seul signe de sélection — l'accent sur le libellé —, un seul filet
 *   pour séparer une section. Voir [HaloMenu] et [HaloOption].
 */

/** Gouttière unique entre deux blocs, tuiles comprises. */
val HaloGutter = 6.dp

/**
 * Rayon d'une carte. Entre le 22 de Nothing X et les angles vifs de suns.red.
 *
 * Public parce que la liste déroulante d'un sélecteur le reprend : la pop-in est
 * une carte qui s'ouvre par-dessus les autres, pas une surface d'un autre monde.
 */
val HaloCardRadius = 16.dp

/** Rayon d'un contrôle porteur de valeur. */
private val FieldRadius = 12.dp

// ---------------------------------------------------------------- cartes

@Composable
fun HaloCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    spacing: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HaloCardRadius))
            .background(HaloCardBg)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * Une paire de tuiles liées, façon Nothing X.
 *
 * Deux cartes et un pont central qui dit qu'elles se lisent ensemble.
 *
 * Le pont est de la couleur des tuiles, donc invisible en tant que tel : ce
 * qu'on voit, c'est le **creux**. Ses deux bouts sont percés d'un demi-disque au
 * fond de l'écran, si bien que la fente noire qui le surmonte se referme en U et
 * celle du dessous en U retourné.
 *
 * Les tuiles n'ont pas de hauteur à elles : mêmes marges et même interligne
 * qu'une [HaloCard] pleine largeur, donc une paire « légende + contrôle » fait
 * exactement la hauteur de la carte « légende + contrôle » d'à côté. Un ratio
 * imposé les rendait plus hautes que le reste de la colonne sans rien y gagner.
 * Elles s'égalisent seulement entre elles ([IntrinsicSize.Min] +
 * `fillMaxHeight`), pour qu'une légende passée sur deux lignes ne désaligne pas
 * les sélecteurs d'en dessous.
 *
 * [contentPadding] et [spacing] ne sont là que pour une tuile qui empile plus
 * qu'une légende et un contrôle — la tuile matérielle du hub, avec sa vignette
 * et ses pastilles, se resserre. Une paire de réglages, elle, garde les
 * réglages de la carte : c'est tout l'intérêt.
 */
@Composable
fun LinkedTiles(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    spacing: androidx.compose.ui.unit.Dp = 14.dp,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(HaloGutter),
        ) {
            HaloCard(Modifier.weight(1f).fillMaxHeight(), contentPadding, spacing, left)
            HaloCard(Modifier.weight(1f).fillMaxHeight(), contentPadding, spacing, right)
        }

        // Le pont, par-dessus l'arête partagée. Le canevas déborde de la hauteur
        // d'un rayon en haut et en bas, sinon les demi-disques des bouts seraient
        // rognés par ses propres limites.
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = BRIDGE_WIDTH, height = BRIDGE_HEIGHT + NOTCH_RADIUS * 2),
        ) {
            val notch = NOTCH_RADIUS.toPx()
            val midX = size.width / 2f
            drawRect(
                color = HaloCardBg,
                topLeft = Offset(0f, notch),
                size = Size(size.width, size.height - notch * 2),
            )
            drawCircle(HaloScreen, notch, Offset(midX, notch))
            drawCircle(HaloScreen, notch, Offset(midX, size.height - notch))
        }
    }
}

private val BRIDGE_WIDTH = 10.dp

/**
 * Le pont prend l'essentiel de l'arête commune d'une paire de réglages : assez
 * pour que les deux encoches noires se lisent comme un creux, pas assez pour
 * que la fente disparaisse dans l'arrondi des angles.
 */
private val BRIDGE_HEIGHT = 80.dp
private val NOTCH_RADIUS = 3.dp

// ---------------------------------------------------------------- textes

/** Titre d'écran. Serif 34 — la seule vraie respiration typographique de l'app. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        color = HaloText,
        fontSize = if (small) 30.sp else 34.sp,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
    )
}

/** Légende de carte. Serif 17 — le nom du réglage, et rien d'autre. */
@Composable
fun Legend(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = HaloText,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        fontFamily = FontFamily.Serif,
    )
}

/**
 * Label de section : mono capitales, filet pointillé, mention à droite.
 *
 * Le filet est une répétition de traits de 1,5 px tous les 6 px, comme la
 * grille de points de l'écosystème — pas un trait plein.
 */
@Composable
fun SectionLabel(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonoLabel(text)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        ) {
            drawLine(
                color = HaloFaint,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 4.5.dp.toPx())),
            )
        }
        if (trailing != null) MonoLabel(trailing)
    }
}

/** Mono 10, capitales, très espacé : l'étiquette de l'écosystème. */
@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = HaloFaint) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace,
    )
}

/** Valeur : mono 14. Tout ce qui est une donnée et non une phrase. */
@Composable
fun MonoValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HaloText,
    size: androidx.compose.ui.unit.TextUnit = 14.sp,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
    )
}

// ---------------------------------------------------------------- contrôles

/**
 * Une entrée de liste déroulante : un libellé, ce qu'elle fait, et deux choses
 * facultatives — l'état sélectionné et une icône.
 *
 * Il n'y a **qu'une façon de dire « c'est celle-ci »** : le libellé passe en
 * accent. La pastille rouge à gauche que portait le quick switch des lapses
 * disait la même chose d'une deuxième manière, dans une seule liste de l'app ;
 * deux vocabulaires pour un seul état, c'est un vocabulaire de trop.
 *
 * [icon] reçoit la couleur de la ligne pour que le signe suive le libellé sans
 * que l'appelant ait à connaître la règle de l'accent.
 *
 * [onClick] ne dit **que** ce que l'entrée fait : refermer la pop-in est l'affaire
 * de la pop-in, et le moment où elle le fait compte trop pour être laissé à
 * l'appelant — voir [HaloMenu].
 */
class HaloOption(
    val label: String,
    val selected: Boolean = false,
    val icon: (@Composable (Color) -> Unit)? = null,
    val onClick: () -> Unit,
)

/**
 * La liste déroulante d'un sélecteur — la « pop-in ».
 *
 * Une **carte qui s'ouvre par-dessus les autres** : même rayon ([HaloCardRadius])
 * et même famille de fond que les blocs de réglages, aucune ombre portée, aucune
 * teinte d'élévation. La liste des options de Material arrive autrement avec ses
 * angles à 4 dp et son gris tonal, qui n'appartiennent à rien d'autre dans
 * l'app.
 *
 * ## Des sections, et un filet entre elles
 *
 * [sections] est une liste de groupes, séparés par un filet. C'est ce qui permet
 * à une liste de choix de porter en plus une **sortie** — « gérer les lapses »,
 * qui n'est pas une valeur mais un écran. Le filet dit que la dernière entrée ne
 * se choisit pas comme les autres ; c'est le seul signal dont elle a besoin, et
 * c'est pour ça qu'elle n'est plus en accent : le rouge est réservé à ce qui est
 * sélectionné.
 *
 * ## Pas de gouttière d'icône
 *
 * Une entrée sans icône commence au bord, point. Réserver la place de l'icône sur
 * toute la liste aurait aligné les libellés, mais au prix d'un décalage que rien
 * n'explique à l'œil : une colonne vide devant des noms qui n'ont pas de signe.
 * Les entrées à icône sont de toute façon dans leur propre section, séparées par
 * un filet — elles n'ont pas à s'aligner sur les autres, elles ne se lisent pas
 * avec elles.
 *
 * ## Elle apparaît et disparaît d'un coup
 *
 * Pas de transition, ni à l'ouverture ni à la fermeture, et **pas de fenêtre** :
 * ce n'est ni un `DropdownMenu` ni un `Popup`, mais un simple dépôt dans l'étage
 * du dessus de l'écran — voir [HaloOverlayHost], qui explique ce que coûtait la
 * fenêtre et pourquoi ça se voyait.
 *
 * Le tap ferme la liste et agit dans la **même image** que le contrôle ; l'ouvrir
 * l'affiche dans la même image que l'allumage de sa bordure. Il n'y a plus rien à
 * regarder entre les deux, donc plus rien à désynchroniser. Une liste d'options
 * n'a de toute façon pas à s'annoncer — elle est là ou elle n'y est pas.
 *
 * [anchor] est le contrôle qui l'ouvre, en coordonnées de la racine : il donne à
 * la fois la position de la liste et sa largeur minimale. L'appelant le relève
 * avec `onGloballyPositioned` — c'est tout ce qu'il a à faire.
 *
 * Ce composant **n'émet rien** là où il est appelé. Il peut donc être posé
 * n'importe où dans l'arbre du contrôle sans en changer la mise en page.
 */
@Composable
fun HaloMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    sections: List<List<HaloOption>>,
    anchor: IntRect,
) {
    val host = LocalHaloOverlay.current ?: return
    val id = remember { Any() }
    // Écrit **pendant** la composition, et non dans un `SideEffect` : l'hôte lit
    // `menu` après nous dans la même passe, donc le contrôle et sa liste sont
    // composés ensemble. Publier après coup coûtait une image pleine — voir
    // [HaloMenuSlot], qui explique pourquoi le sens de lecture compte.
    if (expanded) host.show(id, HaloMenuRequest(anchor, sections, onDismissRequest))
    else host.hide(id)

    DisposableEffect(Unit) { onDispose { host.hide(id) } }
}

/**
 * Le contrôle qui ouvre une liste : il bascule **à l'appui du doigt**, pas au
 * relâchement.
 *
 * La mesure est sans appel : entre le doigt qui se pose et le `onClick` d'un
 * `clickable`, il s'écoule cinquante à soixante-dix millisecondes — la durée du
 * tap lui-même. C'était, et de loin, le plus gros poste du délai ressenti à
 * l'ouverture d'une liste, devant tout ce qui relève du rendu. Un contrôle qui
 * réagit à l'appui économise ce temps-là sans rien accélérer : il arrête
 * simplement d'attendre.
 *
 * Le prix, et il est réel : un doigt qui se pose pour **faire défiler** la page
 * ouvre la liste avant de partir. On la retire dès que le geste dépasse le seuil
 * de glissement du système, donc au bout d'une image ou deux. C'est le seul cas
 * où ce choix se voit, et c'est le compromis assumé de l'immédiateté.
 *
 * Pas de `clickable`, donc pas d'ondulation : la bordure qui s'allume **en même
 * temps que la liste** est déjà la réponse au doigt, et elle arrive maintenant à
 * l'appui. L'accessibilité, elle, est déclarée à la main — un lecteur d'écran
 * active le contrôle par la sémantique, pas par le toucher.
 *
 * [expanded] est une lambda et non un booléen : le détecteur de geste est monté
 * une fois pour toutes et doit lire l'état **au moment du geste**. Le passer par
 * valeur obligerait à remonter le détecteur à chaque ouverture, ce qui annulerait
 * le geste en cours et avec lui l'abandon au défilement.
 */
fun Modifier.haloMenuAnchor(
    expanded: () -> Boolean,
    onExpandedChange: (Boolean) -> Unit,
): Modifier = this
    .semantics(mergeDescendants = true) {
        role = Role.DropdownList
        onClick {
            onExpandedChange(!expanded())
            true
        }
    }
    .pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val wasOpen = expanded()
            onExpandedChange(!wasOpen)
            // On refermait : plus rien à surveiller.
            if (wasOpen) return@awaitEachGesture
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > slop) {
                    onExpandedChange(false)
                    break
                }
            }
        }
    }

/**
 * La liste elle-même : la carte, ses sections, ses entrées.
 *
 * Séparée de [HaloMenu] parce qu'elle est dessinée ailleurs que là où elle est
 * demandée — [HaloOverlayHost] la pose au-dessus de l'écran.
 *
 * La largeur minimale n'est pas ici : elle arrive par les contraintes de mesure
 * que l'étage du dessus impose, et `width(IntrinsicSize.Max)` s'y coerce.
 */
@Composable
internal fun HaloMenuSurface(request: HaloMenuRequest) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(HaloCardRadius))
            .background(HaloControlBg)
            // Absorbe le toucher : sans ça, presser le rembourrage de la carte
            // tomberait sur le voile qui referme.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 6.dp)
            .width(IntrinsicSize.Max)
            .verticalScroll(rememberScrollState()),
    ) {
        request.sections.forEachIndexed { index, section ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .height(1.dp)
                        .background(HaloHair),
                )
            }
            section.forEach { option ->
                HaloMenuItem(option) { request.onDismiss(); option.onClick() }
            }
        }
    }
}

/**
 * Une option de liste déroulante.
 *
 * En **linéale**, comme les descriptions de toys du hub, et non en monospace :
 * une option est un mot à lire, pas une donnée à comparer colonne par colonne.
 * La famille n'est pas nommée — celle du thème est déjà la bonne, et l'imposer
 * ferait diverger cette ligne du reste des phrases de l'app.
 *
 * La taille est **fixe** ([MENU_ITEM_SIZE]) et non celle du contrôle qui ouvre la
 * liste. Elle en dépendait, et le résultat était qu'un même choix se lisait en 13
 * dans une carte et en 16 sous un titre : deux pop-in qui ne sont pas du même
 * monde alors qu'elles font le même travail. Une liste d'options est une liste
 * d'options, quelle que soit la typographie de ce qui l'a ouverte.
 */
@Composable
private fun HaloMenuItem(option: HaloOption, onClick: () -> Unit) {
    val color = if (option.selected) HaloRed else HaloText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = MENU_ITEM_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        option.icon?.let { icon ->
            Box(Modifier.size(MENU_ICON_SIZE), contentAlignment = Alignment.Center) { icon(color) }
        }
        Text(option.label, color = color, fontSize = MENU_ITEM_SIZE, maxLines = 1)
    }
}

/** Serré : la liste doit se lire d'un coup d'œil, pas se parcourir au pouce. */
private val MENU_ITEM_PADDING = 8.dp

/** La taille de référence : celle qu'avait le quick switch des lapses. */
private val MENU_ITEM_SIZE = 16.sp

/** La gouttière d'icône, dimensionnée sur [GearGlyph]. */
private val MENU_ICON_SIZE = 14.dp

/**
 * Sélecteur : rectangle arrondi bordé, valeur à gauche, triangle à droite.
 *
 * Le triangle est **de la couleur du texte**. Il n'indique pas une action
 * dangereuse ni un état particulier : le teindre en accent dépenserait la seule
 * couleur qui porte du sens sur une décoration.
 *
 * La valeur affichée est en linéale, comme les options qu'elle ouvre : c'est le
 * même texte, il change juste de place quand on choisit.
 *
 * [extra] est une section supplémentaire, sous le filet : des entrées qui ne sont
 * pas des valeurs de [T] et ne seront donc jamais sélectionnées — une sortie vers
 * un écran, typiquement.
 */
@Composable
fun <T> HaloSelect(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    extra: List<HaloOption> = emptyList(),
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    val label = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { anchor = it.boundsInRoot().roundToIntRect() }
                .clip(RoundedCornerShape(FieldRadius))
                .border(FieldStroke, if (expanded) HaloText else HaloHair2, RoundedCornerShape(FieldRadius))
                .haloMenuAnchor({ expanded }) { expanded = it }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = HaloText, fontSize = SELECT_LABEL_SIZE, maxLines = 1)
            SelectChevron(color = HaloText, width = 9.dp)
        }
        HaloMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            sections = buildList {
                add(options.map { (value, text) ->
                    HaloOption(label = text, selected = value == selected) { onSelect(value) }
                })
                if (extra.isNotEmpty()) add(extra)
            },
        )
    }
}

/**
 * La valeur lue dans un sélecteur fermé.
 *
 * Un cran sous ses options : la liste ouverte est ce qu'on compare, le contrôle
 * fermé n'est qu'un rappel de ce qui a été choisi.
 */
private val SELECT_LABEL_SIZE = 15.sp

/**
 * Le contour d'un contrôle porteur de valeur.
 *
 * Un trait, pas un cadre : le sélecteur n'a pas de fond à lui, sa bordure sert à
 * délimiter la zone cliquable et rien de plus. À 1,5 dp elle pesait autant que le
 * pointillé d'ajout, qui lui a vraiment quelque chose à dire.
 */
private val FieldStroke = 1.dp

/**
 * Une rangée de segments dans une gouttière, un seul allumé.
 *
 * Ce n'est ni un sélecteur ni une pilule, et c'est assumé : ni l'un ni l'autre ne
 * portent un choix qu'on refait sans arrêt, à l'aller comme au retour. Le segment
 * allumé prend le fond d'un contrôle, les autres restent dans le creux — la même
 * grammaire que le pont de [LinkedTiles], où c'est le creux qui se voit.
 *
 * ## Quand s'en servir plutôt que d'un [HaloSelect]
 *
 * Quand les choix sont **peu nombreux et se comparent d'un coup d'œil** : un
 * sélecteur cache les options derrière un geste, ce qui est exactement ce qu'on
 * veut pour une valeur qu'on règle une fois, et exactement ce qu'on ne veut pas
 * pour un réglage qu'on parcourt. Les toys d'un hublot sont dans le second cas —
 * on vient voir ce qui est allumé, pas ouvrir trois listes pour l'apprendre.
 *
 * [options] fait la largeur : chaque segment prend sa part, pas sa place. Au-delà
 * de cinq ou six, les libellés se rognent et c'est le sélecteur qu'il faut.
 */
@Composable
fun <T> HaloSegments(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SEGMENT_RADIUS))
            .background(HaloScreen)
            .padding(SEGMENT_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GUTTER),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(SEGMENT_RADIUS - SEGMENT_GUTTER))
                    .background(if (on) HaloControlBg else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = SEGMENT_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                MonoValue(label, color = if (on) HaloText else HaloMuted, size = 13.sp)
            }
        }
    }
}

private val SEGMENT_RADIUS = 12.dp

/** L'épaisseur du creux autour du segment allumé. C'est elle qu'on voit. */
private val SEGMENT_GUTTER = 4.dp

/**
 * La hauteur d'un segment, et elle est courte exprès.
 *
 * Une carte de réglages de hublot en empile six, trois par onglet. À onze points
 * de rembourrage la colonne dépassait la carte avant d'avoir tout dit ; à sept,
 * la rangée reste plus haute que son texte sans prétendre au poids d'un bouton —
 * un segment n'est pas une action, c'est une valeur parmi trois autres.
 */
private val SEGMENT_PADDING = 7.dp

/**
 * Onglets : une rangée de libellés sur un filet, celui de la page allumé.
 *
 * Le seul endroit de l'app qui en ait besoin est l'écran de réglages d'un widget,
 * et il en a besoin pour une raison précise : il règle **deux choses qui ne se
 * comparent pas** — ce que le hublot affiche, et à quoi il ressemble. Les empiler
 * dans une même colonne aurait fait une liste où le premier réglage explique le
 * troisième et pas le deuxième. Un onglet dit qu'on change de sujet, pas qu'on
 * descend dans le même.
 *
 * ## Pourquoi ce n'est pas un [HaloSegments]
 *
 * Ça l'a été, et c'était l'erreur : au-dessus d'une colonne de segments, la barre
 * d'onglets devenait le premier segment d'une liste de segments. Or elle ne
 * choisit **pas une valeur**, elle choisit la page — rien de ce qu'elle allume ne
 * sera enregistré nulle part. Deux rôles qui ne se ressemblent en rien portaient
 * le même dessin, et c'est le genre de confusion qu'on paie à chaque ouverture.
 *
 * D'où le filet, qui est le vocabulaire de la navigation et de rien d'autre dans
 * l'app : pas de creux, pas de fond, pas d'angle arrondi — un trait continu sous
 * toute la rangée, et le morceau qui passe en accent sous la page où l'on est.
 * L'accent est ici à sa place, c'est la même règle que dans une liste d'options :
 * une seule façon de dire « c'est celle-ci ».
 */
@Composable
fun HaloTabs(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonoValue(
                    label,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    color = if (on) HaloText else HaloMuted,
                    size = 13.sp,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TAB_RULE)
                        .background(if (on) HaloRed else HaloHair),
                )
            }
        }
    }
}

/**
 * Le filet des onglets : plus épais que celui d'une [SectionLabel], parce qu'il
 * porte l'accent et qu'un cheveu rouge de moins d'un point se lit comme un défaut
 * d'affichage plutôt que comme un état.
 */
private val TAB_RULE = 2.dp

/**
 * Champ : la même famille que le sélecteur et les dates sauvegardées — un
 * rectangle arrondi qui porte une valeur. La pilule reste réservée aux actions.
 */
@Composable
fun HaloField(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(FieldRadius))
            .background(if (accent) HaloRed else HaloControlBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoValue(text, color = if (accent) Color.White else HaloText)
    }
}

/** Action : une pilule. Accent pour l'action principale, gris pour le reste. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (primary) HaloRed else HaloControlBg)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (small) 13.dp else 18.dp,
                vertical = if (small) 8.dp else 14.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = if (small) 11.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

/**
 * Action nue : posée sur le fond, hors de toute carte.
 *
 * Une suppression n'est pas un réglage ; elle ne vit donc pas dans un bloc de
 * réglages. Pas de fond, pas de bordure — le mot seul, en accent.
 */
@Composable
fun BareAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text.uppercase(),
            color = HaloRed,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * Ajout : le seul contrôle en pointillé de l'app. Le pointillé dit « il n'y a
 * rien encore ici » — un cadre plein aurait annoncé un contenu.
 */
@Composable
fun DashedAddRow(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = HaloHair2,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f)),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La croix est de la couleur du texte : c'est le mot qui porte l'action,
        // pas le signe. Dessinée et non tapée — voir `Glyphs.kt`.
        PlusGlyph(color = HaloText)
        Spacer(Modifier.width(8.dp))
        MonoValue(text, size = 13.sp)
    }
}

// ---------------------------------------------------------------- états

/** L'état d'une pastille. L'ordre est celui de la lecture, pas de l'importance. */
enum class ChipState {
    /** Affiché sur la matrice, maintenant. Pleine accent — un seul par écran. */
    LIVE,

    /** Présent, installé, posé. Pleine blanche. */
    ON,

    /** Absent, refusé, à venir. Creuse. */
    OFF,
}

/**
 * Pastille d'état : un point, puis le mot.
 *
 * L'état se lit à la pastille avant de se lire au texte. C'est la raison pour
 * laquelle il n'y a que trois formes possibles et une seule couleur : un
 * quatrième état inventé pour un cas particulier casserait la lecture.
 */
@Composable
fun StatusChip(text: String, state: ChipState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val dot = when (state) {
            ChipState.LIVE -> HaloRed
            ChipState.ON -> HaloText
            ChipState.OFF -> Color.Transparent
        }
        Canvas(Modifier.size(7.dp)) {
            val r = size.minDimension / 2f
            val center = Offset(r, r)
            if (state == ChipState.OFF) {
                drawCircle(HaloFaint, r - 0.5f, center, style = Stroke(1.dp.toPx()))
            } else {
                drawCircle(dot, r, center)
            }
        }
        Text(
            text.uppercase(),
            color = when (state) {
                ChipState.LIVE -> HaloText
                ChipState.ON -> HaloMuted
                ChipState.OFF -> HaloFaint
            },
            fontSize = 9.5.sp,
            letterSpacing = 1.3.sp,
            fontFamily = FontFamily.Monospace,
            // Une pastille ne se coupe pas en plein mot : elle passe à la ligne
            // entière, ou elle s'élide. C'est [ChipRow] qui gère le passage.
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * La rangée de pastilles.
 *
 * Un `Row` simple laissait la dernière pastille se briser au milieu d'un mot dès
 * que « PAS DANS GLYPH INTERFACE » et « AUCUN WIDGET » se retrouvaient côte à
 * côte — et ces deux-là sont justement l'état par défaut du prototype. Le flux
 * fait passer la pastille entière à la ligne suivante.
 *
 * Le `FlowRowScope` n'est pas exposé : il porte l'opt-in expérimental, et le
 * laisser fuir obligerait chaque écran appelant à l'accepter pour un service
 * qu'il n'utilise pas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** Chevron « cette ligne ouvre un écran ». Discret : c'est une affordance. */
@Composable
fun RowScope.OpenChevron(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .align(Alignment.CenterVertically)
            .size(width = 8.dp, height = 12.dp),
    ) {
        val w = size.width
        val h = size.height
        val sw = 1.4.dp.toPx()
        drawLine(HaloFaint, Offset(w * 0.15f, 0f), Offset(w * 0.85f, h / 2f), sw, StrokeCap.Round)
        drawLine(HaloFaint, Offset(w * 0.85f, h / 2f), Offset(w * 0.15f, h), sw, StrokeCap.Round)
    }
}

/** Flèche de retour, avec le nom de l'écran parent. */
@Composable
fun BackBar(label: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onBack)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
            val sw = 1.4.dp.toPx()
            val midY = size.height / 2f
            drawLine(HaloText, Offset(size.width * 0.34f, 0f), Offset(0f, midY), sw, StrokeCap.Round)
            drawLine(HaloText, Offset(0f, midY), Offset(size.width * 0.34f, size.height), sw, StrokeCap.Round)
            drawLine(HaloText, Offset(0f, midY), Offset(size.width, midY), sw, StrokeCap.Round)
        }
        MonoLabel(label, color = HaloText)
    }
}

/**
 * Fil d'ariane : la même flèche que [BackBar], suivie de la trace des écrans
 * jusqu'ici plutôt que du seul parent.
 *
 * Tous les segments remontent d'un cran — comme la flèche, ils appellent
 * [onBack] — sauf le dernier : c'est l'écran courant, il est inerte. Un écran
 * à deux niveaux de profondeur n'a donc besoin que d'un seul retour, pas d'une
 * pile : la maquette n'en a jamais demandé plus.
 */
@Composable
fun Breadcrumb(segments: List<String>, modifier: Modifier = Modifier, onBack: () -> Unit) {
    Row(
        modifier = modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBack)
                .padding(2.dp),
        ) {
            Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
                val sw = 1.4.dp.toPx()
                val midY = size.height / 2f
                drawLine(HaloText, Offset(size.width * 0.34f, 0f), Offset(0f, midY), sw, StrokeCap.Round)
                drawLine(HaloText, Offset(0f, midY), Offset(size.width * 0.34f, size.height), sw, StrokeCap.Round)
                drawLine(HaloText, Offset(0f, midY), Offset(size.width, midY), sw, StrokeCap.Round)
            }
        }
        segments.forEachIndexed { i, label ->
            if (i > 0) MonoLabel("/", color = HaloFaint)
            val last = i == segments.lastIndex
            MonoLabel(
                label,
                color = if (last) HaloText else HaloMuted,
                modifier = if (last) Modifier else Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onBack)
                    .padding(vertical = 2.dp, horizontal = 1.dp),
            )
        }
    }
}
