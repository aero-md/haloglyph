package red.suns.haloglyph.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.FrameSink
import red.suns.haloglyph.core.matrix.MatrixLook
import red.suns.haloglyph.core.matrix.MatrixSpec
import kotlin.math.min

/**
 * La matrice, dessinée dans l'app.
 *
 * **C'est le moteur d'aperçu de tous les toys.** Un écran de réglages n'a pas à
 * savoir dessiner une matrice : il fournit un rendu — la même fonction que celle
 * qui alimente le service Glyph — et reçoit une matrice fidèle. Un toy qui
 * dessinerait la sienne finirait par diverger, et c'est précisément ce que le
 * produit promet de ne pas faire.
 *
 * La fidélité tient à trois choses, toutes portées par
 * [red.suns.haloglyph.core.matrix.MatrixLook] :
 *
 * - des LEDs **carrées** au tiers de gouttière, pas des points ronds ;
 * - le masque du disque, donc des coins vides ;
 * - un état éteint visible à 5 %, pour que la silhouette existe avant la première
 *   LED allumée.
 */
@Composable
fun MatrixPreview(
    brightness: IntArray,
    modifier: Modifier = Modifier,
    spec: MatrixSpec = MatrixSpec.Phone3,
    litColor: Color = Color(MatrixLook.LIT_ARGB),
    fieldColor: Color = Color(MatrixLook.FIELD_ARGB),
) {
    Canvas(modifier) {
        val disc = min(size.width, size.height)
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Le champ est un disque : c'est lui qui donne la silhouette.
        drawCircle(color = fieldColor, radius = disc / 2f, center = Offset(centerX, centerY))

        // La grille est rentrée dans le disque : sur la vraie matrice, aucune LED
        // ne touche le bord du hublot — il reste une couronne de noir tout autour.
        // Une grille à ras bord donnait un disque qui semblait trop petit pour ce
        // qu'il porte, et les LEDs des extrémités mordaient sur la découpe.
        val grid = disc * GRID_SCALE
        val originX = centerX - grid / 2f
        val originY = centerY - grid / 2f

        val pitch = grid / spec.size
        val led = Size(MatrixLook.ledSize(pitch), MatrixLook.ledSize(pitch))
        val inset = MatrixLook.inset(pitch)

        for (index in spec.leds) {
            val topLeft = Offset(
                originX + spec.xOf(index) * pitch + inset,
                originY + spec.yOf(index) * pitch + inset,
            )
            // Toujours l'état éteint, puis l'allumé par-dessus : une LED faible
            // reste au moins aussi visible qu'une LED au repos.
            drawRect(color = litColor.copy(alpha = MatrixLook.OFF_ALPHA), topLeft = topLeft, size = led)

            val value = brightness.getOrElse(index) { 0 }
            if (value > MatrixLook.MIN_VISIBLE) {
                // `perceived` et non `value / 255f` : la frame porte des rapports
                // cycliques, le Canvas compose en linéaire. Voir MatrixLook.
                drawRect(
                    color = litColor.copy(alpha = MatrixLook.perceived(value)),
                    topLeft = topLeft,
                    size = led,
                )
            }
        }
    }
}

/**
 * Part du disque occupée par la grille de LEDs. Le reste est la couronne de
 * fond qui dépasse — c'est elle qui fait lire le hublot comme un objet, et non
 * comme un carré de points qu'on aurait arrondi.
 */
private const val GRID_SCALE = 0.93f

/**
 * Frame observable par Compose, plus le [FrameSink] qui l'alimente.
 *
 * Le sink **copie** la frame reçue : le contrat de [FrameSink.push] dit que le
 * tableau est réutilisé par l'appelant, et Compose ne recomposerait de toute
 * façon pas sur une instance identique.
 */
class MatrixFrameState(val spec: MatrixSpec) {

    var brightness by mutableStateOf(IntArray(spec.cellCount))
        private set

    val sink: FrameSink = FrameSink { frame -> brightness = frame.copyOf() }
}

@Composable
fun rememberMatrixFrameState(spec: MatrixSpec = MatrixSpec.Phone3): MatrixFrameState =
    remember(spec) { MatrixFrameState(spec) }

/**
 * Une matrice animée, cadencée par Compose.
 *
 * `withFrameNanos` accroche le rendu au vsync : l'aperçu s'arrête de lui-même
 * quand l'écran s'éteint ou que les réglages passent en arrière-plan. Un
 * `Handler` à 33 ms, lui, continuerait de tourner dans le vide.
 *
 * @param render appelé avec un tampon déjà effacé et le temps écoulé en
 * secondes — exactement la signature que le service de toy passe à ses
 * renderers, pour que l'app et la matrice ne puissent pas diverger.
 */
@Composable
fun AnimatedMatrixPreview(
    modifier: Modifier = Modifier,
    spec: MatrixSpec = MatrixSpec.Phone3,
    litColor: Color = Color(MatrixLook.LIT_ARGB),
    fieldColor: Color = Color(MatrixLook.FIELD_ARGB),
    render: (frame: Frame, elapsedSeconds: Double) -> Unit,
) {
    val state = rememberMatrixFrameState(spec)
    val frame = remember(spec) { Frame(spec) }

    // `rememberUpdatedState` et non `LaunchedEffect(render)` : la lambda de rendu
    // est réécrite à chaque recomposition de l'appelant, et relancer la boucle à
    // chaque fois remettrait l'horloge de l'animation à zéro sans prévenir.
    val current by rememberUpdatedState(render)

    LaunchedEffect(spec) {
        val startedAt = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            frame.clear()
            current(frame, (now - startedAt) / 1_000_000_000.0)
            frame.pushTo(state.sink)
        }
    }

    MatrixPreview(
        brightness = state.brightness,
        modifier = modifier,
        spec = spec,
        litColor = litColor,
        fieldColor = fieldColor,
    )
}
