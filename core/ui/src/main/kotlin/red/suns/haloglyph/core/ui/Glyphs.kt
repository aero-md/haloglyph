package red.suns.haloglyph.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * « cette valeur s'ouvre sur une liste », la croix dit « ajouter ». Un écran qui
 * en redessinerait un se verrait immédiatement.
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
