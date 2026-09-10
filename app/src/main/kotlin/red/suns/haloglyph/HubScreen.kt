package red.suns.haloglyph

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.suns.haloglyph.core.glyph.GlyphAvailability
import red.suns.haloglyph.core.glyph.GlyphSettings
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.ui.AnimatedMatrixPreview
import red.suns.haloglyph.core.ui.ChipRow
import red.suns.haloglyph.core.ui.ChipState
import red.suns.haloglyph.core.ui.HaloCard
import red.suns.haloglyph.core.ui.HaloControlBg
import red.suns.haloglyph.core.ui.HaloMuted
import red.suns.haloglyph.core.ui.HaloScreen
import red.suns.haloglyph.core.ui.HaloText
import red.suns.haloglyph.core.ui.Legend
import red.suns.haloglyph.core.ui.LinkedTiles
import red.suns.haloglyph.core.ui.MonoLabel
import red.suns.haloglyph.core.ui.MonoValue
import red.suns.haloglyph.core.ui.OpenChevron
import red.suns.haloglyph.core.ui.PillButton
import red.suns.haloglyph.core.ui.ScreenTitle
import red.suns.haloglyph.core.ui.SectionLabel
import red.suns.haloglyph.core.ui.StatusChip
import red.suns.haloglyph.core.ui.ToyEntry

/**
 * Le hub, écran 01 de la maquette.
 *
 * Une paire de tuiles en tête — les deux endroits où un toy peut s'afficher,
 * liés par le pont central — puis la liste des toys, pleine largeur parce que
 * chaque toy porte une description.
 *
 * **Tous les états affichés sont mesurés, aucun n'est écrit en dur.** « Dans
 * Glyph Interface » interroge le `PackageManager`, le nombre de widgets vient de
 * l'`AppWidgetManager`, la matrice est sondée pour de vrai. Ce sont les mêmes
 * sondes qui disaient « pas dans Glyph Interface » du temps du prototype et qui
 * disent le contraire depuis que les toys sont déclarés : rien n'a changé ici.
 *
 * Sous les toys, la ligne des réglages de l'application — l'étage au-dessus,
 * celui des valeurs qui ne sont à aucun toy en particulier (PRODUIT §5.4).
 */
@Composable
fun HubScreen(toys: List<ToyEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var matrixPresent by remember { mutableStateOf<Boolean?>(null) }

    // Sonde réelle : on tente une connexion et on attend le callback. Tester la
    // présence du paquet ne prouverait rien (visibilité des paquets, Android 11+).
    LaunchedEffect(Unit) {
        GlyphAvailability.probe(context) { matrixPresent = it }
    }

    val widgets = remember(context, toys) { toys.sumOf { it.widgetCount(context) } }
    val bundled = toys.count { !it.upcoming }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HaloScreen)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenTitle(stringResource(R.string.app_name))
            MonoLabel("(${toys.size})", modifier = Modifier.padding(bottom = 6.dp))
        }

        // ---------- matériel : les deux surfaces d'affichage ----------

        SectionLabel(
            text = stringResource(R.string.hub_section_hardware),
            trailing = stringResource(
                if (matrixPresent == null) R.string.hub_probe_running else R.string.hub_probe_done
            ),
        )

        LinkedTiles(
            // Vignette + légende + deux pastilles : quatre étages, donc plus
            // serrés qu'une carte de réglage.
            contentPadding = PaddingValues(14.dp),
            spacing = 8.dp,
            left = {
                MatrixThumb(size = 44) { frame, _ -> SelfTest.render(frame, 0.0) }
                Legend(stringResource(R.string.hub_tile_matrix))
                Spacer(Modifier.weight(1f))
                StatusChip(
                    text = stringResource(
                        when (matrixPresent) {
                            null -> R.string.hub_chip_matrix_probing
                            true -> R.string.hub_chip_matrix_connected
                            false -> R.string.hub_chip_matrix_absent
                        }
                    ),
                    // Le seul rouge de l'écran : la matrice répond en face.
                    state = if (matrixPresent == true) ChipState.LIVE else ChipState.OFF,
                )
                StatusChip(
                    text = stringResource(R.string.hub_chip_leds, MatrixSpec.Phone3.ledCount),
                    state = ChipState.ON,
                )
            },
            right = {
                MatrixThumb(size = 44) { frame, seconds ->
                    toys.firstOrNull()?.preview?.render(frame, seconds)
                }
                Legend(stringResource(R.string.hub_tile_widgets))
                Spacer(Modifier.weight(1f))
                StatusChip(
                    text = pluralStringResource(R.plurals.hub_chip_widgets_placed, widgets, widgets),
                    state = if (widgets > 0) ChipState.ON else ChipState.OFF,
                )
                StatusChip(stringResource(R.string.hub_chip_emulation), ChipState.ON)
            },
        )

        // Les deux écrans Glyph du téléphone, quand ils existent : la liste des
        // toys actifs et le gestionnaire. Résolu une fois — le paquet système
        // n'apparaît pas en cours de session.
        val screens = remember(context) { GlyphSettings.screens(context) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (screens.any) {
                screens.active?.let { target ->
                    PillButton(
                        text = stringResource(R.string.hub_active_toys),
                        modifier = Modifier.weight(1f),
                        primary = true,
                    ) { GlyphSettings.open(context, target) }
                }
                screens.manager?.let { target ->
                    PillButton(
                        text = stringResource(R.string.hub_manage_toys),
                        modifier = Modifier.weight(1f),
                        // Le gestionnaire est le second geste : on y va pour
                        // ranger, pas pour regarder.
                        primary = screens.active == null,
                    ) { GlyphSettings.open(context, target) }
                }
            } else {
                // Firmware qui n'expose rien, ou téléphone qui n'est pas un
                // Nothing : les réglages du système, faute de mieux — mais dit
                // comme tel, pas déguisé en « gérer les toys ».
                PillButton(
                    text = stringResource(R.string.hub_open_settings),
                    modifier = Modifier.weight(1f),
                    primary = true,
                ) { context.openGlyphSettings() }
            }
            GlyphMark { matrixPresent = null; GlyphAvailability.probe(context) { matrixPresent = it } }
        }

        // ---------- les toys ----------

        SectionLabel(
            text = stringResource(R.string.hub_section_toys),
            trailing = stringResource(R.string.hub_toys_count, bundled, toys.size),
        )

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

        HaloCard(spacing = 10.dp) {
            toys.forEach { toy ->
                ToyRow(toy, blocked = remember(epoch, toy) { toy.isBlocked(context) })
            }
        }

        // ---------- l'étage au-dessus des toys ----------

        SectionLabel(stringResource(R.string.hub_section_app))

        HaloCard {
            SettingsRow(
                label = stringResource(R.string.settings_title),
                value = AppLanguage.current(context)
                    ?.let { AppLanguage.endonym(it) }
                    ?: stringResource(R.string.language_system),
            ) { context.startActivity(Intent(context, AppSettingsActivity::class.java)) }
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
private fun ToyRow(toy: ToyEntry, blocked: Boolean) {
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
        MatrixThumb(size = 72) { frame, seconds -> toy.preview?.render(frame, seconds) }

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
            Spacer(Modifier.height(9.dp))
            ChipRow {
                if (toy.upcoming) {
                    StatusChip(stringResource(R.string.hub_chip_soon), ChipState.OFF)
                } else if (blocked) {
                    // Le toy est bien là et bien déclaré ; ce qui manque est une
                    // autorisation. Le dire ainsi plutôt que « pas dans Glyph
                    // Interface », qui serait faux et enverrait chercher au
                    // mauvais endroit.
                    StatusChip(stringResource(R.string.hub_chip_mic_denied), ChipState.OFF)
                    StatusChip(stringResource(R.string.hub_chip_fix_here), ChipState.OFF)
                } else {
                    val declared = toy.isDeclaredToGlyph(context)
                    StatusChip(
                        text = stringResource(
                            if (declared) R.string.hub_chip_in_glyph
                            else R.string.hub_chip_not_in_glyph
                        ),
                        state = if (declared) ChipState.ON else ChipState.OFF,
                    )
                    val count = toy.widgetCount(context)
                    StatusChip(
                        text = if (count == 0) stringResource(R.string.hub_chip_no_widget)
                        else pluralStringResource(R.plurals.hub_chip_widgets, count, count),
                        state = if (count > 0) ChipState.ON else ChipState.OFF,
                    )
                }
            }
        }

        if (target != null) OpenChevron()
    }
}

/**
 * La ligne qui ouvre les réglages de l'application.
 *
 * Elle montre la valeur du seul réglage qu'il y ait — la langue — plutôt que de
 * se contenter d'un mot et d'un chevron : le hub dit partout ailleurs un état
 * mesuré, celui-ci ne fait pas exception.
 */
@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Legend(label, modifier = Modifier.weight(1f))
        MonoValue(value, color = HaloMuted, size = 13.sp)
        OpenChevron()
    }
}

/** Vignette de matrice : le disque, à l'échelle. */
@Composable
private fun MatrixThumb(
    size: Int,
    render: (frame: Frame, seconds: Double) -> Unit,
) {
    AnimatedMatrixPreview(
        modifier = Modifier.size(size.dp),
        render = render,
    )
}

/** Le petit repère Glyph du hub : relancer la sonde matérielle. */
@Composable
private fun GlyphMark(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(HaloControlBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedMatrixPreview(modifier = Modifier.size(22.dp)) { frame, seconds ->
            SelfTest.render(frame, seconds)
        }
    }
}

// -------------------------------------------------------------- mesures

/**
 * Le toy est-il visible de Glyph Interface ?
 *
 * On interroge le système sur *notre propre paquet* : c'est la seule réponse qui
 * tienne compte de l'état réel du manifeste — un composant désactivé, comme en
 * prototype, ne résout pas. Lire le manifeste à la main dirait le contraire.
 */
private fun ToyEntry.isDeclaredToGlyph(context: Context): Boolean {
    val service = glyphService ?: return false
    val intent = Intent(GLYPH_TOY_ACTION).setPackage(context.packageName)
    return context.packageManager.queryIntentServices(intent, 0)
        .any { it.serviceInfo?.name == service.name }
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

/** Combien d'instances de ce widget sont réellement posées sur un écran. */
private fun ToyEntry.widgetCount(context: Context): Int {
    val provider = widgetProvider ?: return 0
    return runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, provider))
            .size
    }.getOrDefault(0)
}

/**
 * Le dernier recours, quand aucun écran Glyph ne se laisse résoudre.
 *
 * Ce chemin **était** le comportement normal du bouton, et c'était le défaut :
 * `com.nothing.glyph.SETTINGS` n'existe pas, donc on tombait chaque fois dans
 * les réglages du téléphone. Depuis, [GlyphSettings] résout les vrais écrans et
 * on n'arrive plus ici que sur un appareil qui n'en expose aucun — auquel cas le
 * bouton le dit, au lieu de promettre une gestion des toys.
 */
private fun Context.openGlyphSettings() {
    // `startActivity` sous `runCatching` plutôt qu'un `resolveActivity` préalable :
    // la visibilité des paquets (Android 11+) peut faire répondre « rien » à une
    // requête de résolution pour une activité qui existe et qu'on a le droit de
    // lancer. On essaie, et on retombe sur l'échec.
    for (action in listOf(GLYPH_SETTINGS_ACTION, Settings.ACTION_SETTINGS)) {
        if (runCatching { startActivity(Intent(action)) }.isSuccess) return
    }
}

private const val GLYPH_TOY_ACTION = "com.nothing.glyph.TOY"
private const val GLYPH_SETTINGS_ACTION = "com.nothing.glyph.SETTINGS"
