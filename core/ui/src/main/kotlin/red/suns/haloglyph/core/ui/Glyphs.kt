package red.suns.haloglyph.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * Tracé importé tel quel d'un dessin fait à la main dans un éditeur SVG
 * ([gear-icon.svg]) plutôt que reconstruit en primitives ([Canvas.drawCircle],
 * [Canvas.drawLine]) — les dents à bout rond et à flancs galbés qu'un vrai
 * dessin donne ne se laissent pas approcher par des rectangles ou des
 * trapèzes composés à la main, comme les tentatives précédentes l'ont montré.
 * [GEAR_PATH] est le contenu du `d=` de ce SVG, [GEAR_VIEWBOX_SIZE] son
 * `viewBox` — la mise à l'échelle vers [side] se fait au dessin, une fois le
 * chemin analysé.
 *
 * La couleur se pose en une seule fois, sur l'ensemble déjà opaque
 * ([graphicsLayer] + [CompositingStrategy.Offscreen]), plutôt que sur le
 * chemin directement : [color] est souvent translucide (l'icône au repos du
 * dock), et le remplissage `evenodd` du SVG — le disque central est un second
 * sous-tracé soustrait, pas un trou réellement absent — recouperait deux fois
 * la même zone si l'alpha se posait avant la découpe plutôt qu'après.
 */
@Composable
fun GearGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    // Même taille par défaut que ToysGlyph/CastGlyph : les trois signes du
    // dock doivent occuper la même place, pas juste la même hauteur de boîte.
    side: Dp = 17.dp,
) {
    val solid = color.copy(alpha = 1f)
    val path = remember {
        PathParser().parsePathString(GEAR_PATH).toPath().apply { fillType = PathFillType.EvenOdd }
    }
    Canvas(
        modifier
            .size(side)
            .graphicsLayer(alpha = color.alpha, compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        val scale = size.minDimension / GEAR_VIEWBOX_SIZE
        scale(scale, pivot = Offset.Zero) {
            drawPath(path, solid)
        }
    }
}

/** `viewBox="0 0 16 16"` du SVG source. */
private const val GEAR_VIEWBOX_SIZE = 16f

/** Le `d=` de [gear-icon.svg], recopié tel quel. */
private const val GEAR_PATH =
    "M5.98,3.87 Q6.2,3.77 6.19,3.32 L6.14,1.91 Q6.13,1.46 6.53,1.26 Q8,0.5 9.47,1.26 Q9.87,1.46 9.86,1.91 " +
        "L9.81,3.32 Q9.8,3.77 10.02,3.87 A4.6,4.6 0 0 1 10.57,4.19 Q10.77,4.33 11.15,4.09 L12.34,3.35 " +
        "Q12.72,3.11 13.1,3.35 Q14.5,4.25 14.58,5.91 Q14.6,6.35 14.2,6.57 L12.96,7.23 Q12.57,7.44 12.59,7.68 " +
        "A4.6,4.6 0 0 1 12.59,8.32 Q12.57,8.56 12.96,8.77 L14.2,9.43 Q14.6,9.65 14.58,10.09 Q14.5,11.75 13.1,12.65 " +
        "Q12.72,12.89 12.34,12.65 L11.15,11.91 Q10.77,11.67 10.57,11.81 A4.6,4.6 0 0 1 10.02,12.13 " +
        "Q9.8,12.23 9.81,12.68 L9.86,14.09 Q9.87,14.54 9.47,14.74 Q8,15.5 6.53,14.74 Q6.13,14.54 6.14,14.09 " +
        "L6.19,12.68 Q6.2,12.23 5.98,12.13 A4.6,4.6 0 0 1 5.43,11.81 Q5.23,11.67 4.85,11.91 L3.66,12.65 " +
        "Q3.28,12.89 2.9,12.65 Q1.5,11.75 1.42,10.09 Q1.4,9.65 1.8,9.43 L3.04,8.77 Q3.43,8.56 3.41,8.32 " +
        "A4.6,4.6 0 0 1 3.41,7.68 Q3.43,7.44 3.04,7.23 L1.8,6.57 Q1.4,6.35 1.42,5.91 Q1.5,4.25 2.9,3.35 " +
        "Q3.28,3.11 3.66,3.35 L4.85,4.09 Q5.23,4.33 5.43,4.19 A4.6,4.6 0 0 1 5.98,3.87 Z " +
        "M9.8,8 A1.8,1.8 0 1 0 6.2,8 A1.8,1.8 0 1 0 9.8,8 Z"

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
