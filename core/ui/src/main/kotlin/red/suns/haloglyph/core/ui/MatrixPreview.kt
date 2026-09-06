package red.suns.haloglyph.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.FrameSink
import red.suns.haloglyph.core.matrix.MatrixSpec
import kotlin.math.min

/**
 * La matrice, dessinée dans l'app.
 *
 * Troisième surface, même contrat que les deux autres : on reçoit des
 * luminosités 0..255 et on applique le masque. Une préview qui allumerait les
 * coins ne serait plus une préview.
 *
 * Compose dessine ici directement sur un `Canvas` plutôt que de passer par une
 * `Bitmap` : contrairement au widget, il n'y a pas de binder à traverser, donc
 * pas de budget à tenir — et une frame de plus ne coûte qu'un tour de rendu.
 */
@Composable
fun MatrixPreview(
    brightness: IntArray,
    modifier: Modifier = Modifier,
    spec: MatrixSpec = MatrixSpec.Phone3,
    litColor: Color = Color.White,
    background: Color = Color.Black,
    /** Luminosité résiduelle d'une LED éteinte : une matrice noire a l'air cassée. */
    floor: Float = 0.10f,
    dotRatio: Float = 0.80f,
) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val pitch = side / spec.size
        val radius = pitch * dotRatio / 2f
        val originX = (size.width - side) / 2f
        val originY = (size.height - side) / 2f

        drawRect(color = background)

        for (index in spec.leds) {
            val level = (brightness.getOrElse(index) { 0 } / 255f).coerceIn(floor, 1f)
            drawCircle(
                color = lerp(background, litColor, level),
                radius = radius,
                center = Offset(
                    originX + (spec.xOf(index) + 0.5f) * pitch,
                    originY + (spec.yOf(index) + 0.5f) * pitch,
                ),
            )
        }
    }
}

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
 * `withFrameNanos` accroche le rendu au vsync : la préview s'arrête d'elle-même
 * quand l'écran s'éteint ou que l'écran de réglages passe en arrière-plan. Un
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
    litColor: Color = Color.White,
    background: Color = Color.Black,
    render: (frame: Frame, elapsedSeconds: Double) -> Unit,
) {
    val state = rememberMatrixFrameState(spec)
    val frame = remember(spec) { Frame(spec) }

    LaunchedEffect(spec, render) {
        val startedAt = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            frame.clear()
            render(frame, (now - startedAt) / 1_000_000_000.0)
            frame.pushTo(state.sink)
        }
    }

    MatrixPreview(
        brightness = state.brightness,
        modifier = modifier,
        spec = spec,
        litColor = litColor,
        background = background,
    )
}
