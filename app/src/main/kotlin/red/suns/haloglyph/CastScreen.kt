package red.suns.haloglyph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.suns.haloglyph.core.ui.HaloMuted
import red.suns.haloglyph.core.ui.ScreenTitle

/**
 * L'onglet cast — réservé à GlyphCast, aujourd'hui un outil web
 * ([glyph.suns.red](https://glyph.suns.red)), porté en toy un jour (PRODUIT §6,
 * « Envisagé après »). Pour l'instant, un onglet et rien derrière : mieux
 * vaut annoncer la place que la cacher, et mieux vaut la laisser vide que d'y
 * mettre un écran qui prétendrait faire quelque chose.
 */
@Composable
fun CastScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        ScreenTitle(stringResource(R.string.title_cast))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.cast_soon),
                color = HaloMuted,
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
        }
    }
}
