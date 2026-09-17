package red.suns.haloglyph.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round

/**
 * L'étage du dessus, dans la **même fenêtre** que l'écran.
 *
 * ## Pourquoi pas un `Popup`
 *
 * Parce qu'un `Popup` est une fenêtre, et qu'ouvrir une fenêtre se voit.
 *
 * Compose crée une vue, la passe à `WindowManager`, attend que le système lui
 * alloue une surface, la mesure, la dessine, et la fait composer par
 * SurfaceFlinger. Ça tient en deux ou trois images — quelques dizaines de
 * millisecondes — mais ces images-là sont précisément celles où le contrôle, lui,
 * a déjà réagi : la bordure du sélecteur s'allume à l'image du tap, et la liste
 * arrive après. Le décalage est court et parfaitement lisible, et c'est ce qui
 * fait qu'une liste déroulante « rame » alors que rien n'est lent.
 *
 * Dessinée dans la fenêtre de l'écran, la liste n'a aucune de ces étapes à
 * franchir : elle est un nœud de plus dans l'arbre, publié par la même écriture
 * d'état que la bordure, donc affiché sur **la même image**. Il n'y a plus de
 * décalage à ressentir parce qu'il n'y en a plus.
 *
 * ## Ce qu'il faut pour s'en passer
 *
 * Trois choses qu'un `Popup` donnait et qu'on reprend ici : la fermeture au
 * toucher extérieur ([haloMenuDismissal]), le retour système, et un placement
 * relatif à l'ancre ([HaloMenuLayer]). En échange, la liste ne peut plus déborder
 * de la fenêtre, ce qui n'est une perte pour personne.
 *
 * L'hôte est posé par [HaloglyphTheme] : tout écran de l'app en a un sans avoir à
 * le demander, et un contrôle qui ouvre une liste n'a pas à savoir où elle est
 * réellement dessinée.
 */
internal class HaloOverlayHost {

    /**
     * Ce qui est ouvert, et par qui.
     *
     * Le propriétaire sert à la fermeture : un sélecteur ne referme que **sa**
     * liste. Sans lui, un contrôle qui quitte la composition pendant qu'un autre
     * est ouvert emporterait la liste du voisin.
     */
    var menu: HaloMenuRequest? by mutableStateOf(null)
        private set

    /**
     * Où la liste est réellement posée, en coordonnées de la racine.
     *
     * Champ nu et non état : il est écrit au placement et lu par le gestionnaire
     * de toucher, jamais en composition. En faire un état ferait recomposer
     * l'écran à chaque mesure de la liste, pour une valeur que personne
     * n'affiche.
     */
    var bounds: IntRect = IntRect.Zero

    private var owner: Any? = null

    fun show(id: Any, request: HaloMenuRequest) {
        // Une liste qui s'ouvre n'a pas encore été placée : la position de la
        // précédente ne doit pas servir à décider ce qui est « dedans ».
        if (menu == null) bounds = IntRect.Zero
        owner = id
        menu = request
    }

    fun hide(id: Any) {
        if (owner !== id) return
        owner = null
        menu = null
        bounds = IntRect.Zero
    }
}

/** Une liste ouverte : où, quoi, et comment la refermer. */
internal class HaloMenuRequest(
    /** L'ancre, en coordonnées de la racine — donc de l'hôte. */
    val anchor: IntRect,
    val sections: List<List<HaloOption>>,
    val onDismiss: () -> Unit,
)

internal val LocalHaloOverlay = staticCompositionLocalOf<HaloOverlayHost?> { null }

/**
 * « Une liste d'options est-elle ouverte, là, maintenant ? »
 *
 * Rendu sous forme de fonction et non de booléen : la réponse est lue **au
 * moment du geste**, par un gestionnaire de toucher monté une fois pour toutes.
 * La lire en composition abonnerait l'appelant à chaque ouverture de liste, pour
 * une valeur qu'il n'affiche pas.
 *
 * Ce que ça sert à décider : un écran qui se referme lui-même au toucher
 * extérieur — la pop-in de réglages d'un widget — ne doit pas le faire quand ce
 * toucher ne faisait que refermer une liste déroulante posée par-dessus lui.
 */
@Composable
fun rememberHaloMenuOpen(): () -> Boolean {
    val host = LocalHaloOverlay.current
    return remember(host) { { host?.menu != null } }
}

/** Enveloppe l'écran et lui donne son étage du dessus. Voir [HaloOverlayHost]. */
@Composable
internal fun HaloOverlay(content: @Composable () -> Unit) {
    val host = remember { HaloOverlayHost() }
    CompositionLocalProvider(LocalHaloOverlay provides host) {
        Box(Modifier.fillMaxSize().haloMenuDismissal(host)) {
            content()
            HaloMenuSlot(host)
        }
    }
}

/**
 * Referme la liste au premier toucher qui tombe à côté — **sans le consommer**.
 *
 * ## Pourquoi ce n'est plus un voile
 *
 * Un voile plein écran, c'est ce que fait un `Popup`, et ça se paye : le toucher
 * qui referme s'arrête là. Taper un bouton pendant qu'une liste est ouverte ne
 * faisait que refermer la liste, et il fallait taper une seconde fois pour
 * atteindre le bouton. C'est l'écart le plus net entre une liste déroulante et le
 * reste d'une interface — partout ailleurs, un tap fait ce qu'il vise.
 *
 * Ici il n'y a pas de voile. Ce modificateur est posé sur la **racine**, donc sur
 * un ancêtre de tout ce que l'écran affiche, et il écoute dans la passe
 * [PointerEventPass.Initial] — celle qui descend de la racine vers les feuilles.
 * Il voit donc l'appui avant le bouton visé, referme, et ne consomme rien : le
 * bouton reçoit l'événement et fait son travail. Un seul tap, deux effets, celui
 * qu'on attend.
 *
 * Deux exceptions, et il faut les deux : un appui **dans la liste** ne la referme
 * pas (c'est une option qu'on choisit), et un appui **sur l'ancre** non plus —
 * c'est au contrôle de basculer lui-même, sinon il rouvrirait aussitôt ce qu'on
 * vient de fermer.
 */
private fun Modifier.haloMenuDismissal(host: HaloOverlayHost): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val open = host.menu ?: return@awaitEachGesture
            val at = down.position.round()
            if (host.bounds.contains(at) || open.anchor.contains(at)) return@awaitEachGesture
            open.onDismiss()
        }
    }

/**
 * L'emplacement de la liste, et la raison pour laquelle c'est une fonction.
 *
 * Il lui faut sa **propre portée de recomposition**, et elle doit se situer
 * *après* `content()` dans l'ordre de composition. C'est ce qui permet à
 * [HaloMenu] d'écrire `menu` pendant la composition et d'être lu dans la même
 * passe : Compose reprend une portée invalidée qui est devant lui, mais reporte à
 * l'image suivante celle qu'il a déjà dépassée.
 *
 * Ce détail vaut une image entière. Avec la publication en `SideEffect` — donc
 * après la composition — la bordure du sélecteur était dessinée dans une image et
 * la liste dans la suivante : mesuré à une vingtaine de millisecondes, et
 * parfaitement visible. Écrit ici, le contrôle et sa liste sont composés,
 * mesurés et dessinés ensemble.
 */
@Composable
private fun HaloMenuSlot(host: HaloOverlayHost) {
    host.menu?.let { HaloMenuLayer(host, it) }
}

/**
 * La liste, posée au-dessus de l'écran.
 *
 * Le calque occupe toute la surface mais **n'intercepte rien** : il ne porte aucun
 * gestionnaire de toucher, et Compose ne fait passer un événement que par les
 * nœuds qui en demandent. Ce qui est en dessous reste donc cliquable, ce qui est
 * exactement le but — voir [haloMenuDismissal]. Seule la carte elle-même absorbe,
 * pour qu'un appui sur son rembourrage ne traverse pas.
 *
 * Le rectangle occupé est publié dans [HaloOverlayHost.bounds] au placement :
 * c'est ce qui permet à la fermeture de savoir ce qui est « dedans ».
 *
 * La liste se pose sous son ancre, alignée sur son bord d'attaque : c'est ce qui
 * la fait lire comme le prolongement du contrôle plutôt que comme une boîte qui
 * flotte au-dessus. Elle bascule au-dessus de l'ancre quand le bas de l'écran
 * manque, et se recale dans la fenêtre dans tous les cas — une liste de vingt
 * lapses finirait sinon à moitié dehors.
 *
 * Sa largeur minimale est celle de l'ancre, imposée par les contraintes de mesure
 * plutôt que par un `widthIn` : une pop-in plus étroite que le contrôle dont elle
 * sort a l'air de flotter à côté. Elle peut dépasser, jamais rétrécir.
 */
@Composable
private fun HaloMenuLayer(host: HaloOverlayHost, request: HaloMenuRequest) {
    BackHandler(onBack = request.onDismiss)
    Layout(content = { HaloMenuSurface(request) }) { measurables, constraints ->
        val gap = MENU_GAP.roundToPx()
        val body = measurables.first().measure(
            Constraints(
                minWidth = request.anchor.width.coerceIn(0, constraints.maxWidth),
                maxWidth = constraints.maxWidth,
                maxHeight = constraints.maxHeight,
            )
        )

        val leading =
            if (layoutDirection == LayoutDirection.Ltr) request.anchor.left
            else request.anchor.right - body.width
        val x = leading.coerceIn(0, (constraints.maxWidth - body.width).coerceAtLeast(0))

        val below = request.anchor.bottom + gap
        val above = request.anchor.top - body.height - gap
        val y = when {
            below + body.height <= constraints.maxHeight -> below
            above >= 0 -> above
            else -> (constraints.maxHeight - body.height).coerceAtLeast(0)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            body.place(x, y)
            host.bounds = IntRect(x, y, x + body.width, y + body.height)
        }
    }
}

/** Le jeu entre le contrôle et sa liste : assez pour les distinguer, pas plus. */
private val MENU_GAP = 4.dp
