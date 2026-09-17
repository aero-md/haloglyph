package red.suns.haloglyph

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.ui.AnimatedMatrixPreview
import red.suns.haloglyph.core.ui.ChipRow
import red.suns.haloglyph.core.ui.ChipState
import red.suns.haloglyph.core.ui.HaloCard
import red.suns.haloglyph.core.ui.HaloMuted
import red.suns.haloglyph.core.ui.HaloText
import red.suns.haloglyph.core.ui.OpenChevron
import red.suns.haloglyph.core.ui.ScreenTitle
import red.suns.haloglyph.core.ui.StatusChip
import red.suns.haloglyph.core.ui.ToyEntry

/**
 * L'onglet toys : la liste des toys embarqués, chacun ouvrant son écran de
 * réglages.
 *
 * Le reste de ce qu'affichait le hub — le matériel, puis l'étage des
 * paramètres — est parti ailleurs : voir PRODUIT.md, § « Menus retirés » et
 * § « Le hub à onglets ». Cet écran ne fait plus qu'une chose.
 *
 * [active] dit si cet onglet est celui qu'on regarde. [AppShell] garde les
 * trois onglets composés en permanence — démonter puis remonter celui-ci à
 * chaque passage rejouerait le coût de première image de chaque vignette
 * (voir [red.suns.haloglyph.core.look.MatrixPainter], « une bitmap peinte une
 * fois ») — mais rien n'oblige à faire **tourner** cinq animations pendant
 * qu'on est sur un autre onglet. [active] coupe donc juste la boucle de
 * chaque vignette, sans jamais démonter l'écran ni perdre ses peintres.
 */
@Composable
fun HubScreen(toys: List<ToyEntry>, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Une permission peut avoir été accordée pendant qu'on regardait
    // ailleurs — l'écran de réglages du toy, ou ceux du système. On relit au
    // retour au premier plan plutôt que d'inventer un canal de notification.
    var epoch by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) epoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        ScreenTitle(stringResource(R.string.title_toys))
        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            toys.forEach { toy ->
                HaloCard {
                    ToyRow(toy, blocked = remember(epoch, toy) { toy.isBlocked(context) }, running = active)
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

/**
 * Une ligne de toy : l'aperçu, le nom, la description, l'état.
 *
 * L'aperçu est un **vrai rendu de matrice**, pas une icône — c'est l'objet
 * signature de l'app, et un toy se reconnaît à ce qu'il dessine.
 *
 * [blocked] atténue la ligne comme un toy à venir, mais **sans la désactiver** :
 * un toy empêché par une permission refusée est précisément celui qu'on veut
 * pouvoir ouvrir, puisque son écran de réglages est l'endroit où le refus se
 * défait. Griser et rendre inerte enfermerait l'utilisateur dehors.
 */
@Composable
private fun ToyRow(toy: ToyEntry, blocked: Boolean, running: Boolean) {
    val context = LocalContext.current
    val target = toy.settingsActivity
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (target == null) Modifier
                else Modifier.clickable {
                    context.startActivity(Intent(context, target))
                }
            )
            .alpha(if (toy.upcoming || blocked) 0.42f else 1f),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MatrixThumb(size = 72, running = running) { frame, seconds -> toy.preview?.render(frame, seconds) }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(toy.nameRes),
                color = HaloText,
                fontSize = 17.sp,
                fontFamily = FontFamily.Serif,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(toy.summaryRes),
                color = HaloMuted,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
            // Rien à dire pour un toy qui tourne normalement : les pastilles
            // « dans Glyph Interface » et « N widgets » sont parties, elles ne
            // faisaient que répéter un état sans y ajouter de décision.
            if (toy.upcoming || blocked) {
                Spacer(Modifier.height(9.dp))
                ChipRow {
                    if (toy.upcoming) {
                        StatusChip(stringResource(R.string.hub_chip_soon), ChipState.OFF)
                    } else {
                        // Le toy est bien là et bien déclaré ; ce qui manque est une
                        // autorisation. Le dire ainsi plutôt que « pas dans Glyph
                        // Interface », qui serait faux et enverrait chercher au
                        // mauvais endroit.
                        StatusChip(stringResource(R.string.hub_chip_mic_denied), ChipState.OFF)
                        StatusChip(stringResource(R.string.hub_chip_fix_here), ChipState.OFF)
                    }
                }
            }
        }

        if (target != null) OpenChevron()
    }
}

/** Vignette de matrice : le disque, à l'échelle. */
@Composable
private fun MatrixThumb(
    size: Int,
    running: Boolean,
    render: (frame: Frame, seconds: Double) -> Unit,
) {
    AnimatedMatrixPreview(
        modifier = Modifier.size(size.dp),
        running = running,
        render = render,
    )
}

/**
 * Le toy est-il empêché par une permission refusée ?
 *
 * Mesuré comme tout le reste du hub, et relu à chaque retour au premier plan :
 * la réponse change pendant que l'écran existe.
 */
private fun ToyEntry.isBlocked(context: Context): Boolean {
    val permission = requiredPermission ?: return false
    if (upcoming) return false
    return ContextCompat.checkSelfPermission(context, permission) !=
        PackageManager.PERMISSION_GRANTED
}
