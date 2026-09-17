package red.suns.haloglyph.core.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.roundToIntRect
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixLook
import red.suns.haloglyph.core.ui.HaloCardBg
import red.suns.haloglyph.core.ui.HaloCardRadius
import red.suns.haloglyph.core.ui.HaloSegments
import red.suns.haloglyph.core.ui.HaloTabs
import red.suns.haloglyph.core.ui.HaloglyphTheme
import red.suns.haloglyph.core.ui.Legend
import red.suns.haloglyph.core.ui.MatrixPreview
import red.suns.haloglyph.core.ui.MonoLabel

/**
 * Les réglages d'un hublot posé, ouverts **depuis l'écran d'accueil**.
 *
 * ## Une activité, et pourtant une pop-in
 *
 * C'est le seul format que le système propose : un widget se configure par une
 * activité que le launcher lance avec `EXTRA_APPWIDGET_ID`, et rien d'autre n'est
 * atteignable depuis un appui long sur un widget. Elle est donc habillée pour ce
 * qu'elle doit paraître — fenêtre transparente, voile sombre, une carte au milieu
 * — plutôt que pour ce qu'elle est. On reste sur son écran d'accueil, on règle,
 * on retourne au fond d'écran qu'on n'a jamais vraiment quitté.
 *
 * ## Deux onglets, parce qu'il y a deux sujets
 *
 * **Toys** dit ce que le hublot montre ; **Apparence** dit à quoi il ressemble.
 * Les empiler dans une seule colonne aurait fait une liste où le premier réglage
 * n'a rien à voir avec le troisième — un onglet dit qu'on change de sujet, pas
 * qu'on descend dans le même.
 *
 * L'aperçu, lui, est **au-dessus des onglets et commun aux deux**. Il montre le
 * hublot tel qu'il sera posé : le toy actuellement affiché, dans l'apparence
 * réglée. C'est la seule chose que les deux onglets ont vraiment en commun, et
 * c'est aussi la seule qui bouge quand on touche à l'un ou à l'autre — retirer le
 * toy affiché de la rotation le fait changer sous les yeux, ce qui est exactement
 * ce qui vient de se passer.
 *
 * ## Ce qu'elle écrit, et quand
 *
 * Tout de suite, à chaque choix. Pas de bouton « valider » parce qu'il n'y a rien
 * à valider : le hublot derrière le voile est déjà repeint, et c'est la seule
 * confirmation qui vaille. Le résultat de l'activité est posé à `RESULT_OK` dès
 * l'ouverture, pour qu'un retour arrière ne fasse jamais disparaître le widget
 * qu'on venait régler.
 *
 * ## Exportée, donc méfiante
 *
 * Une activité de configuration **doit** être exportée : c'est le launcher qui la
 * lance, depuis un autre processus. N'importe quelle app peut donc l'appeler avec
 * l'identifiant de son choix. D'où la vérification d'entrée — l'identifiant doit
 * désigner un widget **de ce paquet**, servi par un de nos fournisseurs — après
 * quoi on ferme sans rien avoir écrit.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val provider = resolveProvider(widgetId)
        if (provider == null) {
            finish()
            return
        }

        // Le refus, et c'est le seul endroit d'où l'on puisse le prononcer : une
        // application ne peut pas empêcher un launcher de poser un widget, mais
        // une activité de configuration qui rend `RESULT_CANCELED` fait retirer
        // celui qu'on vient de poser. C'est pour ça que les fournisseurs ne
        // déclarent plus `configuration_optional` — sans ça le launcher ne nous
        // demande rien et il n'y a plus de « non » possible.
        //
        // Le hublot qu'on est en train de configurer est déjà compté, donc c'est
        // bien un de trop qu'on mesure ici.
        if (MatrixWidgetProvider.postedCount(this) > MatrixWidgetProvider.MAX_WIDGETS) {
            Toast.makeText(
                this,
                getString(R.string.widget_limit, MatrixWidgetProvider.MAX_WIDGETS),
                Toast.LENGTH_LONG,
            ).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        // Posé avant tout le reste : le widget reste en place quoi qu'il arrive
        // ensuite, y compris si l'utilisateur ressort par le bouton retour.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        enableEdgeToEdge()
        setContent {
            HaloglyphTheme {
                WidgetConfigDialog(
                    widgetId = widgetId,
                    provider = provider,
                    onDismiss = { finish() },
                )
            }
        }
    }

    /**
     * Le fournisseur derrière cet identifiant — s'il est bien à nous.
     *
     * `getAppWidgetInfo` renvoie `null` pour un identifiant inconnu, et le nom du
     * paquet écarte l'app tierce qui pointerait vers un widget qui n'est pas le
     * sien. Le reste est affaire de classe : seul un [MatrixWidgetProvider] porte
     * un catalogue de toys.
     */
    private fun resolveProvider(widgetId: Int): MatrixWidgetProvider? {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId) ?: return null
        if (info.provider.packageName != packageName) return null
        return MatrixWidgetProvider.instantiate(info.provider.className)
    }
}

/**
 * La carte : l'aperçu, les onglets, puis le contenu de l'onglet.
 *
 * Le voile referme au toucher, mais **seulement hors de la carte**. C'est la même
 * règle que partout ailleurs dans l'app — un toucher fait ce qu'il vise, et une
 * seule chose.
 *
 * Il a fallu garder en plus un œil sur les listes d'options, qui débordaient de la
 * carte : choisir « soft » dans une liste ouverte tombait hors des limites de la
 * carte et refermait l'écran. Plus aucun réglage d'ici n'ouvre de liste — tout est
 * en segments — donc la surface de la carte suffit de nouveau à décider.
 *
 * L'aperçu porte **le reflet** quand il est activé, alors que tous les autres
 * aperçus de l'app s'en passent. C'est l'exception qui confirme la règle : ici on
 * ne montre pas un toy, on montre le widget tel qu'il sera posé.
 */
@Composable
private fun WidgetConfigDialog(
    widgetId: Int,
    provider: MatrixWidgetProvider,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val spec = provider.matrixSpec
    val toys = provider.widgetToys
    var style by remember { mutableStateOf(WidgetConfig.style(context, widgetId)) }
    var rotation by remember {
        mutableStateOf(WidgetConfig.rotation(context, widgetId, toys.map { it.id }))
    }
    var looping by remember { mutableStateOf(WidgetConfig.loop(context, widgetId)) }
    // La forme sous laquelle chaque toy est porté ici — le solide du dé. Relevée
    // une fois : l'écran est la seule chose qui l'écrive tant qu'il est ouvert.
    var variants by remember {
        mutableStateOf(toys.associate { it.id to WidgetConfig.variant(context, widgetId, it) })
    }
    var tab by remember { mutableIntStateOf(0) }

    // Écrit au placement, lu au toucher, jamais en composition : personne ne s'y
    // abonne, donc l'écrire ne recompose rien.
    var card by remember { mutableStateOf(IntRect.Zero) }

    /**
     * L'aperçu du toy affiché. Recalculé quand la rotation change, parce que
     * retirer le toy affiché en désigne forcément un autre — et qu'on veut le
     * voir arriver. Et quand une variante change, parce qu'un d20 n'a pas la
     * silhouette d'un d6.
     */
    val brightness = remember(rotation, variants, toys) {
        val current = WidgetConfig.current(context, widgetId, rotation)
        val toy = toys.firstOrNull { it.id == current } ?: toys.firstOrNull()
        Frame(spec).also { toy?.renderIdle(context, widgetId, it) }.toBrightness()
    }

    fun applyStyle(next: WidgetStyle) {
        style = next
        WidgetConfig.setStyle(context, widgetId, next)
        // Le fournisseur est un receiver : on le réveille par la route normale,
        // celle que le système emprunte lui-même.
        MatrixWidgetRefresh.requestUpdate(context, intArrayOf(widgetId))
        // Et le service, qui peut être en train d'animer ce hublot avec
        // l'ancienne apparence — relevée une fois au démarrage de l'animation, et
        // donc sourde à ce qu'on vient d'écrire. Sans ça, changer la trame d'un
        // hublot en boucle ne se voyait qu'après l'avoir tapé.
        provider.startLoops(context)
    }

    /**
     * Bascule ce hublot entre « au tap » et « en continu ».
     *
     * C'est le seul réglage du pack qui fasse vivre un service : d'où le message
     * au fournisseur juste après, qui le lui dit. Idempotent des deux côtés — on
     * peut appuyer deux fois sur le même choix sans rien casser.
     */
    fun applyLoop(next: Boolean) {
        looping = next
        WidgetConfig.setLoop(context, widgetId, next)
        provider.startLoops(context)
        MatrixWidgetRefresh.requestUpdate(context, intArrayOf(widgetId))
    }

    /**
     * Pose un toy dans la rotation sous la forme choisie, ou l'en retire.
     *
     * Un seul geste pour les deux, parce que c'est une seule question : « ce
     * hublot porte-t-il ce toy, et sous quelle forme ? ». Elle remplace la bascule
     * d'avant, qui ne savait dire que oui ou non — et laissait le dé sans aucun
     * moyen de choisir son solide, puisque l'appui long et la secousse qui le
     * font sur la matrice n'existent pas sur un écran d'accueil.
     *
     * **Le dernier ne se retire pas.** Un hublot sans toy n'a rien à montrer, et
     * le seul geste qui y mènerait est celui qu'on vient de bloquer. Le segment
     * `OFF` reste donc touchable et ne fait simplement rien — plutôt que de se
     * griser, ce qui aurait demandé d'expliquer pourquoi.
     *
     * @param key la variante choisie, ou `null` pour `OFF`.
     */
    fun choose(toy: WidgetToy, key: String?) {
        if (key != null && toy.variants.any { it.key == key }) {
            WidgetConfig.setVariant(context, widgetId, toy.id, key)
            variants = variants + (toy.id to key)
        }

        val next = if (key == null) rotation - toy.id else
            toys.map { it.id }.filter { it in rotation || it == toy.id }
        if (next != rotation) {
            if (next.isEmpty()) return
            rotation = next
            WidgetConfig.setRotation(context, widgetId, next)
        }

        // La rotation décide du toy affiché, et le toy affiché décide si ce
        // hublot peut boucler — ni Sono ni le dé ne bouclent. Le service doit
        // donc relire, et repartir sur la variante qu'on vient d'écrire.
        provider.startLoops(context)
        MatrixWidgetRefresh.requestUpdate(context, intArrayOf(widgetId))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    if (card.contains(down.position.round())) return@awaitEachGesture
                    onDismiss()
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { card = it.boundsInRoot().roundToIntRect() }
                    .clip(RoundedCornerShape(CARD_RADIUS))
                    .background(HaloCardBg)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                MatrixPreview(
                    brightness = brightness,
                    modifier = Modifier.size(PREVIEW_SIDE),
                    spec = spec,
                    style = style.led,
                    bevel = style.glass,
                )

                HaloTabs(
                    labels = listOf(
                        stringResource(R.string.widget_tab_toys),
                        stringResource(R.string.widget_tab_look),
                    ),
                    selected = tab,
                ) { tab = it }

                if (tab == 0) {
                    RotationTab(
                        toys = toys,
                        rotation = rotation,
                        variants = variants,
                        onChoose = ::choose,
                    )
                } else {
                    LookTab(
                        style = style,
                        onApply = ::applyStyle,
                        loop = looping,
                        onLoop = ::applyLoop,
                    )
                }
            }
        }
    }
}

/**
 * L'onglet des toys : un toy par ligne, et sous son nom la rangée de ce qu'il
 * peut être ici.
 *
 * ## Une rangée plutôt qu'une pastille
 *
 * Il y a eu une ligne cliquable et une pastille `DANS LA ROTATION` / `IGNORÉ`,
 * et elle ne savait dire que oui ou non. C'était assez tant qu'un toy n'avait
 * qu'une forme ; le dé en a quatre, et **aucun moyen de les choisir depuis un
 * hublot** — sur la matrice on change de solide à l'appui long, que le launcher
 * garde pour lui, et on lance en secouant le téléphone, ce qu'on ne fait pas à un
 * écran d'accueil. Le solide se règle donc ici, et `OFF` prend simplement la
 * place que la pastille occupait.
 *
 * ## Et pas une liste déroulante
 *
 * Trois toys font trois listes à ouvrir pour apprendre ce qui est allumé. Les
 * segments disent tout sans qu'on touche à rien, ce qui est exactement ce qu'on
 * vient chercher dans cet onglet — voir [HaloSegments], qui porte la règle.
 */
@Composable
private fun RotationTab(
    toys: List<WidgetToy>,
    rotation: List<String>,
    variants: Map<String, String?>,
    onChoose: (WidgetToy, String?) -> Unit,
) {
    val off = stringResource(R.string.widget_toy_off)
    val on = stringResource(R.string.widget_toy_on)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (toy in toys) {
            val options = buildList {
                add(null to off)
                if (toy.variants.isEmpty()) add(ON to on)
                else for (variant in toy.variants) add(variant.key to variant.label)
            }
            Setting(stringResource(toy.nameRes)) {
                HaloSegments(
                    options = options,
                    selected = when {
                        toy.id !in rotation -> null
                        toy.variants.isEmpty() -> ON
                        else -> variants[toy.id]
                    },
                ) { onChoose(toy, it) }
            }
        }
        // Centrée, parce qu'elle ne commente aucune des rangées au-dessus d'elle :
        // alignée à gauche comme elles, elle se lisait comme la légende d'un
        // quatrième réglage qui n'existe pas.
        MonoLabel(
            stringResource(R.string.widget_rotation_note),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp),
        )
    }
}

/**
 * Ce que porte le segment allumé d'un toy qui n'a qu'une forme.
 *
 * Il faut bien lui donner une valeur, et elle n'est jamais écrite nulle part :
 * `choose` n'enregistre une variante que si le toy en propose. Seul `null` —
 * `OFF` — a un sens pour tout le monde.
 */
private const val ON = "on"

/**
 * L'onglet de l'apparence : ce qui ne parle pas du toy.
 *
 * L'animation y est avec la trame et le reflet, et pas dans l'onglet des toys,
 * parce qu'elle ne dit rien de **ce qui** est affiché : elle dit comment ce
 * hublot-là se comporte. Un hublot en continu et son voisin au tap montrent le
 * même toy.
 *
 * Des segments et non des listes déroulantes, comme dans l'autre onglet. Les
 * trois réglages ont deux valeurs chacun : les cacher derrière un geste demandait
 * d'ouvrir trois listes pour savoir comment ce hublot est réglé, alors que tout
 * tient sur trois rangées. C'est l'écran entier qui se lit d'un coup d'œil, et
 * c'est bien ce qu'on vient y chercher.
 */
@Composable
private fun LookTab(
    style: WidgetStyle,
    onApply: (WidgetStyle) -> Unit,
    loop: Boolean,
    onLoop: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Setting(stringResource(R.string.widget_motion)) {
            HaloSegments(
                options = listOf(
                    false to stringResource(R.string.widget_motion_tap),
                    true to stringResource(R.string.widget_motion_loop),
                ),
                selected = loop,
            ) { onLoop(it) }
        }

        Setting(stringResource(R.string.widget_render)) {
            HaloSegments(
                options = listOf(
                    MatrixLook.LedStyle.SHARP to stringResource(R.string.widget_render_sharp),
                    MatrixLook.LedStyle.SOFT to stringResource(R.string.widget_render_soft),
                ),
                selected = style.led,
            ) { onApply(style.copy(led = it)) }
        }

        Setting(stringResource(R.string.widget_style)) {
            HaloSegments(
                options = listOf(
                    true to stringResource(R.string.widget_style_realistic),
                    false to stringResource(R.string.widget_style_minimal),
                ),
                selected = style.glass,
            ) { onApply(style.copy(glass = it)) }
        }
    }
}

/** Une légende et son contrôle, comme dans une carte de réglages de l'app. */
@Composable
private fun Setting(label: String, control: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Legend(label)
        control()
    }
}

/**
 * Le voile. Assez dense pour que la carte se détache d'un fond d'écran clair,
 * assez transparent pour qu'on sache qu'on n'a pas quitté son écran d'accueil.
 */
private val SCRIM = Color(0xCC000000)

/** Un cran au-dessus d'une carte de l'app : celle-ci flotte pour de bon. */
private val CARD_RADIUS = HaloCardRadius + 6.dp

/** L'aperçu tient le haut de la carte : c'est lui qu'on vient voir. */
private val PREVIEW_SIDE = 172.dp
