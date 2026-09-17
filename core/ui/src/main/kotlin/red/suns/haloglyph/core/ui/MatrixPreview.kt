package red.suns.haloglyph.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import red.suns.haloglyph.core.look.MatrixPainter
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
 * Le dessin lui-même n'est pas ici : il est dans
 * [red.suns.haloglyph.core.look.MatrixPainter], que le widget d'écran d'accueil
 * appelle aussi. Ce fichier ne fait que deux choses que le peintre ne sait pas
 * faire — tenir une horloge d'animation, et se raccrocher au cycle de vie de
 * Compose.
 */
@Composable
fun MatrixPreview(
    brightness: IntArray,
    modifier: Modifier = Modifier,
    spec: MatrixSpec = MatrixSpec.Phone3,
    style: MatrixLook.LedStyle = MatrixLook.LedStyle.SHARP,
    bevel: Boolean = false,
) {
    val painter = rememberMatrixPainter(spec, style, bevel)
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            painter.paint(canvas.nativeCanvas, brightness, min(size.width, size.height).toInt())
        }
    }
}

/**
 * La même matrice, alimentée par un [MatrixFrameState].
 *
 * C'est la forme à préférer quand l'appelant tient sa propre boucle de rendu :
 * elle ne recompose jamais, elle redessine — et seulement quand l'image a
 * réellement changé. Voir [MatrixFrameState].
 */
@Composable
fun MatrixPreview(
    state: MatrixFrameState,
    modifier: Modifier = Modifier,
    style: MatrixLook.LedStyle = MatrixLook.LedStyle.SHARP,
    bevel: Boolean = false,
) {
    val painter = rememberMatrixPainter(state.spec, style, bevel)
    Canvas(modifier) {
        // La lecture qui déclenche le redessin, et le seul endroit où elle a le
        // droit d'être : dans la phase de dessin.
        state.revision
        drawIntoCanvas { canvas ->
            painter.paint(
                canvas.nativeCanvas,
                state.brightness,
                min(size.width, size.height).toInt(),
            )
        }
    }
}

/**
 * Un peintre lié à cette position de l'arbre, libéré avec elle.
 *
 * Il porte des bitmaps — le hublot au repos, les halos pré-floutés — calées sur
 * une taille de boîte. Deux aperçus de tailles différentes doivent donc en avoir
 * deux : partagé, il les reconstruirait à chaque image, ce qui coûterait
 * exactement ce que le cache est censé économiser.
 *
 * **Sans le reflet du verre**, par défaut. Un widget est posé sur le fond d'écran
 * et doit se faire passer pour l'objet ; un aperçu dans l'app est une
 * illustration sur une carte, et un éclat simulé en haut à gauche y raconte une
 * lumière qui n'existe pas — d'autant plus visible que le hub en aligne une
 * colonne entière, tous éclairés pareil. Le seul aperçu qui le rallume est celui
 * des réglages d'un widget, dont le travail est justement de montrer ce que le
 * widget montrera.
 *
 * Le style et le reflet sont des **clés** : en changer jette le peintre, et
 * l'effet libère celui qu'on abandonne. Ses bitmaps ne dépendent que de ça et de
 * la taille, donc en garder deux serait payer deux fois le même cache.
 */
@Composable
private fun rememberMatrixPainter(
    spec: MatrixSpec,
    style: MatrixLook.LedStyle,
    bevel: Boolean,
): MatrixPainter {
    val painter = remember(spec, style, bevel) { MatrixPainter(spec, bevel = bevel, style = style) }
    DisposableEffect(painter) { onDispose { painter.release() } }
    return painter
}

/**
 * Frame observable par Compose, plus le [FrameSink] qui l'alimente.
 *
 * ## Pourquoi ce n'est pas un `mutableStateOf(IntArray)`
 *
 * Ça l'a été, et c'est ce qui faisait ramer le hub au défilement. Un tableau
 * neuf par image et par aperçu, c'est deux kilo-octets jetés trente fois par
 * seconde et par vignette — le ramasse-miettes passait pendant le défilement,
 * là où on le remarque. Et remplacer la valeur d'un état lu en composition
 * **recompose** l'aperçu, alors que rien de sa structure ne change : seuls les
 * pixels bougent.
 *
 * Le tableau est donc réutilisé, et ce qui change est un compteur. Lu dans la
 * lambda de dessin et nulle part ailleurs, il n'invalide que la phase de
 * dessin : pas de recomposition, pas de remesure, pas d'allocation.
 */
class MatrixFrameState(val spec: MatrixSpec) {

    /** Réutilisé d'une image à l'autre. Ne pas conserver hors du dessin. */
    val brightness: IntArray = IntArray(spec.cellCount)

    /**
     * Numéro de l'image. **À lire dans la phase de dessin**, sinon l'aperçu se
     * recompose pour rien.
     */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * Le compteur n'avance que si l'image a **changé**.
     *
     * Comparer 489 entiers coûte moins qu'un seul rectangle repeint, et beaucoup
     * de ce que le pack affiche ne bouge pas : un lapse au repos, un dé posé, un
     * aperçu qu'on regarde entre deux secondes. Sans ce test, ils redessinaient
     * la même image trente fois par seconde.
     */
    val sink: FrameSink = FrameSink { frame ->
        if (frame.contentEquals(brightness)) return@FrameSink
        frame.copyInto(brightness)
        revision++
    }
}

@Composable
fun rememberMatrixFrameState(spec: MatrixSpec = MatrixSpec.Phone3): MatrixFrameState =
    remember(spec) { MatrixFrameState(spec) }

/**
 * Une matrice animée, cadencée par Compose.
 *
 * `withFrameNanos` accroche le rendu au vsync : l'aperçu s'arrête de lui-même
 * quand l'écran s'éteint ou que l'app passe en arrière-plan. Un `Handler` à
 * 33 ms, lui, continuerait de tourner dans le vide.
 *
 * La cadence est **plafonnée à celle de la matrice** ([MATRIX_FRAME_NANOS]) et
 * non à celle de l'écran. Un aperçu qui rendrait à 120 Hz montrerait une animation plus
 * fluide que l'appareil qu'il émule, pour quatre fois le travail — et le hub en
 * aligne une demi-douzaine, tous vivants en même temps parce qu'ils sont dans la
 * même colonne défilante.
 *
 * @param render appelé avec un tampon déjà effacé et le temps écoulé en
 * secondes — exactement la signature que le service de toy passe à ses
 * renderers, pour que l'app et la matrice ne puissent pas diverger.
 */
@Composable
fun AnimatedMatrixPreview(
    modifier: Modifier = Modifier,
    spec: MatrixSpec = MatrixSpec.Phone3,
    running: Boolean = true,
    render: (frame: Frame, elapsedSeconds: Double) -> Unit,
) {
    val state = rememberMatrixFrameState(spec)
    val frame = remember(spec) { Frame(spec) }

    // `rememberUpdatedState` et non `LaunchedEffect(render)` : la lambda de rendu
    // est réécrite à chaque recomposition de l'appelant, et relancer la boucle à
    // chaque fois remettrait l'horloge de l'animation à zéro sans prévenir.
    val current by rememberUpdatedState(render)

    // [running] coupe la boucle sans démonter l'aperçu : c'est ce qui permet à
    // un appelant de garder plusieurs vignettes vivantes — peintre et bitmaps
    // de [red.suns.haloglyph.core.look.MatrixPainter] compris — sans les faire
    // tourner pendant qu'on regarde ailleurs. Démonter à la place les aurait
    // gardées d'images, mais aurait rejoué tout leur coût de première image à
    // chaque retour.
    LaunchedEffect(spec, running) {
        if (!running) return@LaunchedEffect
        val startedAt = withFrameNanos { it }
        var lastAt = startedAt
        while (true) {
            val now = withFrameNanos { it }
            if (now - lastAt < MATRIX_FRAME_NANOS) continue
            lastAt = now
            frame.clear()
            current(frame, (now - startedAt) / 1_000_000_000.0)
            frame.pushTo(state.sink)
        }
    }

    MatrixPreview(state = state, modifier = modifier)
}

/**
 * Cadence de la matrice physique : 30 images par seconde.
 *
 * Publique parce que les écrans de réglages qui tiennent leur propre boucle —
 * ceux qui ont autre chose à publier que des pixels — doivent se plafonner de la
 * même façon.
 */
const val MATRIX_FRAME_NANOS: Long = 33L * 1_000_000L
