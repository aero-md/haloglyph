package red.suns.haloglyph.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * La liste déroulante d'un sélecteur — la « pop-in ».
 *
 * Une **carte qui s'ouvre par-dessus les autres** : même rayon ([HaloCardRadius])
 * et même famille de fond que les blocs de réglages, aucune ombre portée, aucune
 * teinte d'élévation. La liste des options de Material arrive autrement avec ses
 * angles à 4 dp et son gris tonal, qui n'appartiennent à rien d'autre dans
 * l'app.
 *
 * Ce composant n'est qu'une enveloppe : ce sont [HaloMenuItem] et le sélecteur
 * qui décident du contenu.
 */
@Composable
fun HaloMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(HaloCardRadius),
        containerColor = HaloControlBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/**
 * Une option de liste déroulante.
 *
 * En **linéale**, comme les descriptions de toys du hub, et non en monospace :
 * une option est un mot à lire, pas une donnée à comparer colonne par colonne.
 * La famille n'est pas nommée — celle du thème est déjà la bonne, et l'imposer
 * ferait diverger cette ligne du reste des phrases de l'app.
 *
 * [size] vient du sélecteur qui ouvre la liste : les options d'un réglage de
 * carte se lisent à la taille de sa valeur, celles d'un titre à la taille d'un
 * titre. Le rembourrage suit, pour qu'une petite liste reste dense et qu'une
 * grande ne colle pas.
 */
@Composable
fun HaloMenuItem(
    label: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 13.sp,
    color: Color = HaloText,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = MENU_ITEM_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(label, color = color, fontSize = size, maxLines = 1)
    }
}

/** Serré : la liste doit se lire d'un coup d'œil, pas se parcourir au pouce. */
private val MENU_ITEM_PADDING = 8.dp

/**
 * Sélecteur : rectangle arrondi bordé, valeur à gauche, triangle à droite.
 *
 * Le triangle est **de la couleur du texte**. Il n'indique pas une action
 * dangereuse ni un état particulier : le teindre en accent dépenserait la seule
 * couleur qui porte du sens sur une décoration.
 *
 * La valeur affichée est en linéale, comme les options qu'elle ouvre : c'est le
 * même texte, il change juste de place quand on choisit.
 */
@Composable
fun <T> HaloSelect(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    textSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FieldRadius))
                .border(1.5.dp, if (expanded) HaloText else HaloHair2, RoundedCornerShape(FieldRadius))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = HaloText, fontSize = textSize, maxLines = 1)
            SelectChevron(color = HaloText, width = 9.dp)
        }
        HaloMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                HaloMenuItem(
                    label = text,
                    size = textSize,
                    color = if (value == selected) HaloRed else HaloText,
                ) { onSelect(value); expanded = false }
            }
        }
    }
}

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
