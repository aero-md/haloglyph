package red.suns.haloglyph.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème du hub.
 *
 * Volontairement squelettique : c'est de la plomberie, pas la direction
 * artistique. L'app reprendra la DA de l'écosystème suns.red (grille de points,
 * mono capitales, un seul accent rouge) quand il y aura des écrans à habiller.
 * Ce qui est déjà figé, c'est l'accent — [HaloRed], le rouge de suns.red — et le
 * fait que la matrice, elle, se dessine toujours sur fond noir.
 */

/** L'accent de l'écosystème. Le même que le logo suns.red. */
val HaloRed = Color(0xFFD3001F)

/** Fond des surfaces qui représentent la matrice. Noir franc, jamais un gris. */
val MatrixBlack = Color(0xFF000000)

private val DarkColors = darkColorScheme(
    primary = HaloRed,
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
)

private val LightColors = lightColorScheme(
    primary = HaloRed,
)

@Composable
fun HaloglyphTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
