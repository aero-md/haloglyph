package red.suns.haloglyph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import red.suns.haloglyph.core.glyph.GlyphAvailability
import red.suns.haloglyph.core.glyph.GlyphTiles
import red.suns.haloglyph.core.ui.CastGlyph
import red.suns.haloglyph.core.ui.GearGlyph
import red.suns.haloglyph.core.ui.HaloCardBg
import red.suns.haloglyph.core.ui.HaloFaint
import red.suns.haloglyph.core.ui.HaloRed
import red.suns.haloglyph.core.ui.HaloScreen
import red.suns.haloglyph.core.ui.ToyEntry
import red.suns.haloglyph.core.ui.ToysGlyph

/**
 * Le socle de l'app : trois onglets sur un même fond, un dock flottant en bas.
 *
 * **Toys** est l'écran d'accueil d'hier — le hub sans son étage de réglages.
 * **Cast** est une place réservée, sans écran derrière (PRODUIT §6). **Params**
 * est l'ancienne `AppSettingsActivity`, qui n'a plus de raison d'être une
 * activité à part dès lors qu'un onglet y mène directement.
 *
 * Les insets ne se posent qu'ici, une fois : le haut et les côtés sur la
 * colonne entière, le bas sur le seul dock, qui est ce qui touche le bord de
 * l'écran. Un onglet plus haut dans l'arbre n'a donc plus à s'en soucier — voir
 * [HubScreen] et [ParamsScreen], qui ne posent plus que leur marge
 * horizontale.
 *
 * La sonde matérielle vit ici, et non dans l'onglet toys qui la lisait avant :
 * c'est l'onglet paramètres qui en a besoin maintenant, pour décider si les
 * deux boutons Glyph du système ont leur place. La lever d'un cran évite de
 * la relancer à chaque changement d'onglet.
 */
@Composable
fun AppShell(toys: List<ToyEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var matrixPresent by remember { mutableStateOf<Boolean?>(null) }

    // Sonde réelle : on tente une connexion et on attend le callback. Tester la
    // présence du paquet ne prouverait rien (visibilité des paquets, Android 11+).
    //
    // C'est aussi le seul moment où l'on sait quoi faire des tuiles du volet :
    // elles n'ont de sens que devant une matrice, et sur un téléphone qui n'en a
    // pas elles proposeraient d'allumer ce qui n'existe pas. Voir [GlyphTiles].
    LaunchedEffect(Unit) {
        GlyphAvailability.probe(context) {
            matrixPresent = it
            GlyphTiles.sync(context, it, ToyCatalog.TILES)
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HaloScreen)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            ),
    ) {
        TabPager(
            tab = tab,
            onTabChange = { tab = it },
            onDraggingChange = { dragging = it },
            pageCount = 3,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                // Coupée pendant le glissement, pas seulement hors onglet : la
                // boucle des cinq vignettes coûte cher (voir [HubScreen]), et
                // deux onglets sont visibles à la fois pendant un swipe — les
                // geler plutôt que les faire tourner en double évite de payer
                // ce coût juste pour un aperçu qui défile hors du cadre.
                0 -> HubScreen(toys, active = tab == 0 && !dragging, modifier = Modifier.fillMaxSize())
                1 -> CastScreen(modifier = Modifier.fillMaxSize())
                else -> ParamsScreen(matrixPresent, modifier = Modifier.fillMaxSize())
            }
        }
        AppTabBar(selected = tab, onSelect = { tab = it })
    }
}

/**
 * Les trois onglets, côte à côte, glissés sous le doigt.
 *
 * Les trois restent composés en permanence, comme avant : démonter toys
 * jetterait avec lui ses cinq `MatrixPainter` et leurs bitmaps déjà peintes
 * (voir [HubScreen]), et les reconstruire à chaque retour se voyait
 * exactement là où c'est le plus cher — l'ouverture de l'onglet. Ce qui
 * change, c'est qu'aucun ne tombe plus à une taille nulle : un glissement
 * montre deux onglets à la fois, et les deux doivent donc garder leur pleine
 * taille en permanence. C'est le [clipToBounds] du conteneur qui cache celui
 * qui déborde — le toucher suit la même transformation que le dessin
 * ([graphicsLayer]), donc un onglet poussé hors champ ne le capte plus non
 * plus.
 *
 * [tab] est la position *validée*, celle que porte le dock. [dragPx] est
 * l'écart au doigt, en pixels, autour de cette position — nul au repos, et
 * remis à zéro en [TAB_MOTION] (le même ease-out que la pilule du dock) une
 * fois le doigt relâché, que le glissement aboutisse ou revienne en place.
 * Deux façons d'aboutir : passer la moitié de la largeur, ou relâcher vite —
 * un doigt lancé n'a pas besoin d'aller loin pour que l'intention soit claire.
 *
 * `pointerInput` n'est ici armé qu'une fois ([pageCount] ne change jamais) :
 * son bloc tourne donc pour toute la durée de vie de l'écran, sans jamais se
 * relancer. Y lire directement le paramètre [tab] le figerait à sa valeur de
 * cette toute première composition — exactement le bug observé, où le seuil
 * et la cible du glissement raisonnaient sur un onglet resté à 0 quel que
 * soit celui réellement affiché, au point de rendre le milieu inaccessible.
 * [currentTab] est la parade : une [rememberUpdatedState] que la coroutine
 * relit à chaque geste, jamais une valeur qu'elle aurait capturée une fois.
 */
@Composable
private fun TabPager(
    tab: Int,
    onTabChange: (Int) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    pageCount: Int,
    modifier: Modifier = Modifier,
    page: @Composable (Int) -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val dragPx = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentTab = rememberUpdatedState(tab)

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(pageCount) {
                val velocityTracker = VelocityTracker()
                // Repère de vitesse au-delà duquel un relâché compte comme un
                // « lancer » : la distance parcourue ne fait alors plus foi,
                // seulement le sens. Densité déjà fournie par ce receiver.
                val flingVelocityPx = 1200.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        onDraggingChange(true)
                    },
                    onDragEnd = {
                        onDraggingChange(false)
                        val tab = currentTab.value
                        val velocity = velocityTracker.calculateVelocity().x
                        val fraction = if (widthPx == 0f) 0f else dragPx.value / widthPx
                        val target = when {
                            (velocity <= -flingVelocityPx || fraction <= -0.5f) && tab < pageCount - 1 -> tab + 1
                            (velocity >= flingVelocityPx || fraction >= 0.5f) && tab > 0 -> tab - 1
                            else -> tab
                        }
                        // Continuité visuelle : au moment où [tab] change, la
                        // formule de position change de référence avec lui
                        // (voir plus bas). On décale [dragPx] d'autant pour que
                        // l'image affichée ne saute pas, puis seulement on la
                        // ramène à zéro — le mouvement restant, en ease-out.
                        val carry = dragPx.value + (target - tab) * widthPx
                        if (target != tab) onTabChange(target)
                        scope.launch {
                            dragPx.snapTo(carry)
                            dragPx.animateTo(0f, TAB_MOTION)
                        }
                    },
                    onDragCancel = {
                        onDraggingChange(false)
                        scope.launch { dragPx.animateTo(0f, TAB_MOTION) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPointerInputChange(change)
                    val tab = currentTab.value
                    val min = if (tab == pageCount - 1) 0f else -widthPx
                    val max = if (tab == 0) 0f else widthPx
                    scope.launch {
                        dragPx.snapTo((dragPx.value + dragAmount).coerceIn(min, max))
                    }
                }
            },
    ) {
        for (index in 0 until pageCount) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (index - tab) * widthPx + dragPx.value },
            ) {
                page(index)
            }
        }
    }
}

/** Un onglet : son icône, et le mot qui n'apparaît que dans la pilule. */
private class AppTabSpec(val label: String, val icon: @Composable (Color) -> Unit)

/**
 * Le sélecteur d'onglet d'application : un dock flottant, à la Glyph
 * Museum / Glyph Beat — compact, et l'actif seul porte le rouge et un mot.
 *
 * Flottant et non accroché au bord : une pilule posée sur le fond noir, avec sa
 * propre marge de chaque côté, plutôt qu'une barre pleine largeur qui
 * annexerait le bas de l'écran. C'est la même famille que [HaloCard] — un fond
 * qui se détache, aucune bordure — appliquée à la navigation plutôt qu'à un
 * réglage.
 *
 * Compact, pas les trois onglets à la largeur du mot le plus long : deux
 * disques ([DOCK_ITEM_HEIGHT]) et une seule pilule ouverte ([expandedWidth],
 * mesurée une fois sur le mot le plus long, donc toujours la même quelle que
 * soit la langue). La largeur totale du dock ne dépend donc jamais de qui est
 * actif.
 *
 * ## Une seule pilule, jamais deux signes en même temps
 *
 * La pilule rouge (`pillX`) **ne change jamais de largeur** — seule sa
 * position glisse. Elle porte le signe et le mot de l'onglet actif, assignés
 * d'un bloc, sans fondu ni recouvrement.
 *
 * ## Les trois signes en creux existent en permanence
 *
 * Rien ne se monte ni ne se démonte au tap : les trois disques sont toujours
 * là, y compris celui de l'onglet actif — parfaitement caché sous la pilule,
 * puisque son `restX` vise la même cible qu'elle. Un onglet qui se referme ne
 * *réapparaît* donc pas : son disque **était déjà en train de la suivre**, et
 * continue simplement vers sa propre place pendant que la pilule part vers la
 * sienne. Et l'onglet qu'on vient de toucher ne disparaît pas non plus avant
 * l'heure — son disque glisse jusqu'à ce que la pilule l'y rejoigne, jamais
 * avant.
 *
 * Cette permanence est aussi ce qui rend les taps rapides supportables : il
 * n'y a ni recomposition ni relance à froid quand la cible change en cours de
 * route, seulement un nouvel appel à `animateTo` sur un `Animatable` déjà en
 * mouvement, qui reprend sa route sans à-coup dans l'autre sens.
 *
 * Les trois signes en creux **ne bougent jamais en largeur**, seulement en
 * position : c'est cette différence — largeur fixe partout, seule la position
 * anime — qui fait la différence entre un glissement et un agrandissement sur
 * place.
 *
 * La position se pose au dessin (`Modifier.graphicsLayer { translationX = … }`),
 * pas à la mise en page (`Modifier.offset`) : la première ne coûte qu'un
 * redessin à chaque image, la seconde relance systématiquement mesure et
 * recomposition. Sur quatre éléments qui bougent ensemble à chaque tap, c'est
 * la différence entre du fluide et du haché.
 *
 * ## Le doigt ne vise jamais ce qui bouge
 *
 * Les disques et la pilule qu'on voit glisser **ne portent aucun `clickable`**.
 * La pilule, ouverte, fait plus de deux fois la largeur d'un disque — la
 * laisser capter le toucher aurait voulu dire qu'un tap sur l'onglet voisin,
 * pendant qu'elle passe dessus en glissant, retombe sur elle et rouvre
 * l'onglet déjà actif. C'est très exactement ce qui rendait les taps rapides
 * inertes : la zone tactile la plus large de tout le dock était aussi la
 * seule qui bouge.
 *
 * Les vraies cibles sont trois zones **immobiles**, superposées en dessous,
 * chacune à la position que la mise en page **actuelle** (non animée) lui
 * donne — la même formule que [tabOffsetPx], mais lue tout de suite et non
 * après [TAB_MOTION]. Toucher l'onglet voisin marche donc à l'identique,
 * pilule arrêtée ou pilule en plein vol.
 */
@Composable
private fun AppTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `remember` : recréer la liste à chaque recomposition casserait la
    // mémoïsation de [expandedWidth] juste en dessous, qui la prend pour clé.
    val toysLabel = stringResource(R.string.nav_toys)
    val castLabel = stringResource(R.string.nav_cast)
    val paramsLabel = stringResource(R.string.nav_params)
    val tabs = remember(toysLabel, castLabel, paramsLabel) {
        listOf(
            AppTabSpec(toysLabel) { ToysGlyph(it) },
            AppTabSpec(castLabel) { CastGlyph(it) },
            AppTabSpec(paramsLabel) { GearGlyph(it) },
        )
    }

    // Mesurée une fois sur le mot le plus long des trois. En pixels tout du
    // long : un `Animatable<Dp>` demande un convertisseur que cette version
    // de Compose n'expose pas commodément, alors que `Animatable<Float>` a le
    // sien tout fait — la conversion en `Dp` n'a lieu qu'au bord, pour poser
    // les modificateurs de mise en page.
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val dockItemHeightPx = with(density) { DOCK_ITEM_HEIGHT.toPx() }
    val tabGapPx = with(density) { TAB_GAP.toPx() }
    val expandedWidthPx = remember(tabs, density) {
        with(density) {
            val labelWidth = tabs.maxOf { textMeasurer.measure(it.label, TAB_LABEL_STYLE).size.width }
            dockItemHeightPx + TAB_LABEL_GAP.toPx() + labelWidth
        }
    }
    val expandedWidth = with(density) { expandedWidthPx.toDp() }
    val trackWidth = DOCK_ITEM_HEIGHT * (tabs.size - 1) + expandedWidth + TAB_GAP * (tabs.size - 1)

    // La position de la pilule, et celle de chaque signe en creux — y compris
    // celui de l'onglet actif, qui vise la même cible qu'elle et reste donc
    // couvert. `Animatable` et non `animateDpAsState` : un tap pendant qu'une
    // animation tourne encore doit *rediriger* le mouvement en cours, pas le
    // couper puis en relancer un autre à froid.
    val pillX = remember {
        Animatable(tabOffsetPx(selected, selected, expandedWidthPx, dockItemHeightPx, tabGapPx))
    }
    val restX = remember {
        tabs.indices.map { i ->
            Animatable(tabOffsetPx(i, selected, expandedWidthPx, dockItemHeightPx, tabGapPx))
        }
    }

    LaunchedEffect(selected) {
        launch {
            pillX.animateTo(tabOffsetPx(selected, selected, expandedWidthPx, dockItemHeightPx, tabGapPx), TAB_MOTION)
        }
        tabs.indices.forEach { i ->
            launch {
                restX[i].animateTo(tabOffsetPx(i, selected, expandedWidthPx, dockItemHeightPx, tabGapPx), TAB_MOTION)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(HaloCardBg)
                .padding(6.dp)
                .width(trackWidth)
                .height(DOCK_ITEM_HEIGHT),
        ) {
            // Les cibles tactiles, immobiles : une par onglet, à la position
            // que la mise en page actuelle lui donne — jamais celle, animée,
            // que [restX] ou [pillX] survolent en chemin. Sans elles, un tap
            // pendant le glissement pouvait retomber sur la pilule en transit
            // plutôt que sur l'onglet visé — voir la docstring.
            tabs.forEachIndexed { index, tab ->
                val x = tabOffsetPx(index, selected, expandedWidthPx, dockItemHeightPx, tabGapPx)
                val w = if (index == selected) expandedWidthPx else dockItemHeightPx
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { x.toDp() })
                        .width(with(density) { w.toDp() })
                        .fillMaxHeight()
                        // Pas d'ondulation : c'est la pilule qui glisse qui
                        // répond au doigt, une deuxième réponse ferait double
                        // emploi.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) }
                        .semantics { contentDescription = tab.label },
                )
            }

            // Le rendu, purement visuel : aucun `clickable` ici, voir la
            // docstring — c'est tout l'enjeu de ce fichier.
            tabs.forEachIndexed { index, tab ->
                Box(
                    modifier = Modifier
                        .size(DOCK_ITEM_HEIGHT)
                        // Une transformation posée au dessin, pas à la mise en
                        // page : `graphicsLayer` lit `restX` à chaque image
                        // sans jamais recomposer ni remesurer ce disque — seul
                        // son rendu est repris. `Modifier.offset` aurait
                        // redéclenché mise en page et recomposition à chaque
                        // image, ce qui coûtait le fluide promis.
                        .graphicsLayer { translationX = restX[index].value }
                        .clip(PillShape),
                    contentAlignment = Alignment.Center,
                ) {
                    tab.icon(HaloFaint)
                }
            }

            val active = tabs[selected]
            Row(
                modifier = Modifier
                    .width(expandedWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = pillX.value }
                    .clip(PillShape)
                    .background(HaloRed)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                active.icon(Color.White)
                Spacer(Modifier.width(TAB_LABEL_GAP))
                Text(active.label, color = Color.White, style = TAB_LABEL_STYLE, maxLines = 1)
            }
        }
    }
}

/** La forme unique du dock : un disque au repos, une pilule ouverte. */
private val PillShape = RoundedCornerShape(percent = 50)

/** Position d'un onglet dans la rangée (en pixels), étant donné celui qui est ouvert. */
private fun tabOffsetPx(index: Int, selected: Int, expandedWidthPx: Float, collapsedPx: Float, gapPx: Float): Float {
    var x = 0f
    for (i in 0 until index) {
        x += (if (i == selected) expandedWidthPx else collapsedPx) + gapPx
    }
    return x
}

/** Hauteur d'un onglet au repos — un disque — et de la pilule ouverte. */
private val DOCK_ITEM_HEIGHT = 44.dp

/** Le creux entre deux onglets de la rangée. */
private val TAB_GAP = 6.dp

/** Le creux entre le signe et le mot, dans la pilule ouverte. */
private val TAB_LABEL_GAP = 8.dp

private val TAB_LABEL_STYLE = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

/**
 * La seule chose animée du dock : une position qui glisse.
 *
 * `LinearOutSlowInEasing` et non `FastOutSlowInEasing` : la seconde part
 * lentement avant d'accélérer, ce qui ajoute un temps de latence perçu entre
 * le tap et le premier mouvement visible. La première part à pleine vitesse
 * dès l'image suivante et ne fait que décélérer jusqu'à l'arrivée — un
 * ease-out franc, qui répond tout de suite au doigt.
 */
private val TAB_MOTION = tween<Float>(durationMillis = 180, easing = LinearOutSlowInEasing)
