package red.suns.haloglyph.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * La palette de Haloglyph, figée par la maquette du 06.09.2026.
 *
 * Un seul monde chromatique, celui de l'écran du téléphone : fond noir franc,
 * cartes qui se détachent par leur fond et non par un filet, et **une seule
 * couleur qui porte du sens** — [HaloRed]. Le rouge est une ponctuation : le
 * bouton principal, la pastille du toy affiché sur la matrice. Jamais un aplat
 * décoratif, jamais un chevron.
 *
 * Pas de thème clair. L'app vit à côté d'une matrice de LEDs blanches sur fond
 * noir ; un fond blanc casserait l'objet signature. `darkColorScheme` seul, et
 * `Theme.DeviceDefault.NoActionBar` côté manifeste.
 */

/** L'accent de l'écosystème. Le même que le logo suns.red. */
val HaloRed = Color(0xFFD3001F)

/** Fond de l'app, et fond des fentes entre deux tuiles liées. Noir franc. */
val HaloScreen = Color(0xFF000000)

/** Fond d'une carte. Elle se détache par là, pas par une bordure. */
val HaloCardBg = Color(0xFF17171A)

/** Fond d'un contrôle posé sur une carte : champ, date sauvegardée. */
val HaloControlBg = Color(0xFF202024)

/** Filet discret (séparateurs). */
val HaloHair = Color(0x1AFFFFFF)

/** Filet visible : contour de sélecteur, pointillés d'ajout. */
val HaloHair2 = Color(0x33FFFFFF)

/** Texte plein. */
val HaloText = Color(0xFFF1F1EF)

/** Texte secondaire : description d'un toy, seconde ligne d'une lecture. */
val HaloMuted = Color(0xFF9A9AA0)

/** Texte d'appoint : labels de section, pastilles éteintes. */
val HaloFaint = Color(0x57F1F1EF)

/** Fond des surfaces qui représentent la matrice. Noir franc, jamais un gris. */
val MatrixBlack = HaloScreen

private val HaloColors = darkColorScheme(
    primary = HaloRed,
    onPrimary = Color.White,
    background = HaloScreen,
    onBackground = HaloText,
    surface = HaloCardBg,
    onSurface = HaloText,
    surfaceVariant = HaloControlBg,
    onSurfaceVariant = HaloMuted,
)

@Composable
fun HaloglyphTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HaloColors, content = content)
}
