package red.suns.haloglyph.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Les signes dessinés de l'interface.
 *
 * **Aucun caractère ASCII ni glyphe de police pour un signe d'interface.** Un
 * « ▼ » ou un « + » posé dans un `Text` n'est pas un dessin : sa taille dépend
 * des métriques de la police installée, son épaisseur de sa graisse, son
 * centrage de son interligne — trois choses qu'on ne contrôle pas et qui
 * changent d'un appareil à l'autre. Ce qui est ici est tracé au trait, dans une
 * boîte connue, et se met à l'échelle et à la couleur de l'endroit qui l'emploie.
 *
 * Une seule forme par sens, partagée par tous les écrans : le triangle dit
 * « cette valeur s'ouvre sur une liste », la croix dit « ajouter », l'engrenage
 * dit « ceci mène aux réglages ». Un écran qui en redessinerait un se verrait
 * immédiatement.
 */

/**
 * Le triangle des sélecteurs — pointe en bas, angles légèrement arrondis.
 *
 * Le tracé se fait en deux temps : un triangle rentré vers son centre, puis le
 * même chemin repassé au trait épais à joints ronds. Le trait regonfle la forme
 * jusqu'à sa taille nominale **et** arrondit les trois angles du même rayon —
 * ce qu'un `Path` à arcs écrits à la main ne donnerait pas d'aplomb sur un
 * sommet aussi aigu que celui du bas.
 *
 * [width] est la seule mesure à donner : la hauteur en découle, pour que le
 * triangle garde sa silhouette qu'il fasse 9 dp dans une carte ou 14 dp à côté
 * d'un titre.
 */
@Composable
fun SelectChevron(
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 10.dp,
) {
    Canvas(modifier.size(width, width * TRIANGLE_RATIO)) {
        val corner = size.width * TRIANGLE_CORNER
        val cx = size.width / 2f
        // Centroïde d'un triangle : aux deux tiers de la hauteur depuis la base.
        val cy = size.height * 2f / 3f
        val shrink = 1f - 2f * corner / size.height

        fun inward(x: Float, y: Float) = Offset(cx + (x - cx) * shrink, cy + (y - cy) * shrink)

        val a = inward(0f, 0f)
        val b = inward(size.width, 0f)
        val c = inward(cx, size.height)
        val path = Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
            close()
        }
        drawPath(path, color)
        drawPath(
            path,
            color,
            style = Stroke(width = corner * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }
}

/** Un triangle plus large que haut se lit mieux qu'un équilatéral à petite taille. */
private const val TRIANGLE_RATIO = 0.66f

/** Rayon d'arrondi, en fraction de la largeur. Assez pour adoucir, pas pour émousser. */
private const val TRIANGLE_CORNER = 0.13f

/**
 * La croix d'ajout : deux traits à bouts ronds, dans une boîte carrée.
 *
 * Un point plus épaisse que le « + » de police qu'elle remplace — à cette
 * taille, un trait fin sur fond noir s'efface, et le pointillé du cadre qui
 * l'entoure est déjà à 1,5 dp.
 */
@Composable
fun PlusGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    side: Dp = 11.dp,
    stroke: Dp = 1.8.dp,
) {
    Canvas(modifier.size(side)) {
        val sw = stroke.toPx()
        // Les bouts ronds débordent d'un demi-trait : on rentre d'autant, sinon
        // la croix est rognée par sa propre boîte.
        val pad = sw / 2f
        val mid = size.width / 2f
        drawLine(color, Offset(pad, mid), Offset(size.width - pad, mid), sw, StrokeCap.Round)
        drawLine(color, Offset(mid, pad), Offset(mid, size.height - pad), sw, StrokeCap.Round)
    }
}

/**
 * L'engrenage : « cette entrée mène à un écran de réglages ».
 *
 * Un anneau et des dents radiales, et non une silhouette découpée. À la taille où
 * il sert — la hauteur d'une ligne de liste déroulante — une roue pleine à
 * créneaux devient une tache : ses dents et son moyeu se rejoignent dès que la
 * forme descend sous la vingtaine de pixels.
 *
 * Les dents sont tracées **plus épaisses que l'anneau** : au même trait que lui,
 * huit rayons fins autour d'un cercle se lisaient comme un soleil, pas comme une
 * roue. Un trait plus large les rapproche d'une vraie dent — courte et large
 * plutôt que longue et fine — sans aller jusqu'à la silhouette pleine que la
 * petite taille écraserait.
 *
 * Huit dents : six font une étoile, douze font un disque.
 *
 * Les dents sont des **trapèzes évasés**, pas des rectangles à bout carré : à
 * largeur constante, huit rayons identiques autour d'un centre se lisaient
 * encore comme une fleur (un rectangle qui s'élargit brusquement au bord de
 * l'anneau est déjà la moitié d'un pétale). Étroite à la racine et large au
 * sommet, une dent dessine un coin droit à sa pointe — la marque d'une roue —
 * là où un pétale s'arrondit ou se referme.
 *
 * La couleur se pose en une seule fois, sur l'ensemble déjà opaque
 * ([graphicsLayer] + [CompositingStrategy.Offscreen]), plutôt que traît par
 * traît : [color] est souvent translucide (l'icône au repos du dock), et
 * l'anneau et les dents se chevauchent à leur jonction — les recomposer
 * séparément y aurait doublé l'opacité, une tache plus dense que le reste du
 * signe au lieu d'une teinte unie.
 */
@Composable
fun GearGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    side: Dp = 14.dp,
    stroke: Dp = 1.6.dp,
) {
    val solid = color.copy(alpha = 1f)
    Canvas(
        modifier
            .size(side)
            .graphicsLayer(alpha = color.alpha, compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        val sw = stroke.toPx()
        val rootHalfWidth = sw * TOOTH_ROOT_RATIO / 2f
        val tipHalfWidth = sw * TOOTH_TIP_RATIO / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val tooth = size.minDimension * TOOTH_LENGTH
        // La marge tient compte du coin le plus large — celui de la pointe.
        val radius = size.minDimension / 2f - tooth - tipHalfWidth
        drawCircle(solid, radius, center, style = Stroke(sw))
        for (i in 0 until GEAR_TEETH) {
            val angle = (i * 2.0 * PI / GEAR_TEETH).toFloat()
            val dir = Offset(cos(angle), sin(angle))
            val perp = Offset(-dir.y, dir.x)
            // La dent part de l'anneau lui-même, pas de son bord extérieur :
            // elles se soudent au lieu de flotter autour.
            val root = center + dir * radius
            val tip = center + dir * (radius + tooth)
            val path = Path().apply {
                moveTo(root.x + perp.x * rootHalfWidth, root.y + perp.y * rootHalfWidth)
                lineTo(tip.x + perp.x * tipHalfWidth, tip.y + perp.y * tipHalfWidth)
                lineTo(tip.x - perp.x * tipHalfWidth, tip.y - perp.y * tipHalfWidth)
                lineTo(root.x - perp.x * rootHalfWidth, root.y - perp.y * rootHalfWidth)
                close()
            }
            drawPath(path, solid)
        }
    }
}

private const val GEAR_TEETH = 8

/** Longueur d'une dent, en fraction du côté. */
private const val TOOTH_LENGTH = 0.15f

/**
 * Largeur d'une dent à sa racine (contre l'anneau), en multiple du trait de
 * l'anneau. Assez étroite pour qu'un espace net sépare chaque dent de ses
 * voisines — à huit dents sur un si petit cercle, une pointe trop large les
 * fait se toucher et referme le pourtour en un octogone plein, sans dents du
 * tout.
 */
private const val TOOTH_ROOT_RATIO = 1.0f

/** Largeur d'une dent à sa pointe, en multiple du trait de l'anneau — plus large que la racine : l'évasement. */
private const val TOOTH_TIP_RATIO = 1.7f

/**
 * La mini matrice : disque et neuf pixels — l'onglet qui mène aux toys.
 *
 * Un disque et une grille, pas un rendu de la vraie matrice : à la taille d'un
 * onglet, les 489 LEDs de `MatrixSpec.Phone3` se confondraient en une tache.
 * Trois par trois suffisent à dire « c'est une matrice » sans en simuler une
 * mesure, ce qu'un vrai [red.suns.haloglyph.core.look.MatrixPainter] ferait
 * croire à cette taille.
 *
 * Des **carrés**, pas des ronds : une LED de Glyph Matrix est un pixel, pas un
 * point — neuf ronds se lisaient comme une grappe, neuf carrés se lisent comme
 * une grille.
 */
@Composable
fun ToysGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    side: Dp = 17.dp,
    stroke: Dp = 1.6.dp,
) {
    Canvas(modifier.size(side)) {
        val sw = stroke.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - sw / 2f
        drawCircle(color, radius, center, style = Stroke(sw))

        val dotSide = size.minDimension * DOT_SIDE
        val step = size.minDimension * DOT_STEP
        for (row in -1..1) {
            for (col in -1..1) {
                val topLeft = Offset(
                    center.x + col * step - dotSide / 2f,
                    center.y + row * step - dotSide / 2f,
                )
                drawRect(color, topLeft, Size(dotSide, dotSide))
            }
        }
    }
}

private const val DOT_SIDE = 0.13f
private const val DOT_STEP = 0.22f

/**
 * Cast, en attente : un disque en pointillé.
 *
 * Le même pointillé que [DashedAddRow] et pour la même raison — il dit « il
 * n'y a rien encore ici », pas « ceci est cassé ». L'onglet existe, l'écran
 * qu'il ouvre n'a rien à montrer pour l'instant.
 */
@Composable
fun CastGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    side: Dp = 17.dp,
    stroke: Dp = 1.6.dp,
) {
    Canvas(modifier.size(side)) {
        val sw = stroke.toPx()
        val radius = size.minDimension / 2f - sw / 2f
        drawCircle(
            color,
            radius,
            Offset(size.width / 2f, size.height / 2f),
            style = Stroke(
                width = sw,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.2.dp.toPx(), 2.4.dp.toPx())),
            ),
        )
    }
}
