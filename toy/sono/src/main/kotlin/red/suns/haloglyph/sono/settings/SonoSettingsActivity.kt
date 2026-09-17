package red.suns.haloglyph.sono.settings

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.ui.AnimatedMatrixPreview
import red.suns.haloglyph.core.ui.Breadcrumb
import red.suns.haloglyph.core.ui.ChipState
import red.suns.haloglyph.core.ui.HaloCard
import red.suns.haloglyph.core.ui.HaloScreen
import red.suns.haloglyph.core.ui.HaloSelect
import red.suns.haloglyph.core.ui.HaloglyphTheme
import red.suns.haloglyph.core.ui.Legend
import red.suns.haloglyph.core.ui.MonoLabel
import red.suns.haloglyph.core.ui.PillButton
import red.suns.haloglyph.core.ui.ScreenTitle
import red.suns.haloglyph.core.ui.SectionLabel
import red.suns.haloglyph.core.ui.StatusChip
import red.suns.haloglyph.sono.MicPermission
import red.suns.haloglyph.sono.R
import red.suns.haloglyph.sono.SonoConfig
import red.suns.haloglyph.sono.audio.SonoMic
import red.suns.haloglyph.sono.engine.SonoDemo
import red.suns.haloglyph.sono.engine.SonoMode
import red.suns.haloglyph.sono.mic.MicNotice
import red.suns.haloglyph.sono.render.SonoRenderer

/**
 * Les réglages de Sono : le mode affiché, et l'autorisation micro.
 *
 * Deux choses, et elles n'ont pas le même statut. Le **mode** est un doublon
 * assumé de l'appui long — le geste reste la façon normale d'en changer, mais
 * un réglage qui n'existe que sur un bouton au dos du téléphone est un réglage
 * qu'on ne peut pas voir. L'**autorisation**, elle, n'a que cet endroit : une
 * permission d'exécution se demande depuis une activité, et un Glyph Toy n'en
 * a pas.
 */
class SonoSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloglyphTheme {
                SonoSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun SonoSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val prefs = remember(context) { SonoConfig.prefs(context) }
    val spec = MatrixSpec.Phone3

    var mode by remember { mutableStateOf(SonoConfig.mode(prefs)) }
    var granted by remember { mutableStateOf(MicPermission.isGranted(context)) }
    var armed by remember { mutableStateOf(SonoMic.armed) }
    var noticed by remember { mutableStateOf(MicNotice.isAllowed(context)) }

    // Le micro peut être coupé depuis la tuile ou depuis sa notification, sans
    // repasser par ici. Un sondage à la demi-seconde plutôt qu'un canal : cet
    // écran anime déjà un aperçu à trente images par seconde, et un booléen relu
    // pendant qu'on le regarde ne coûte rien. Il s'arrête avec l'écran.
    LaunchedEffect(Unit) {
        while (true) {
            armed = SonoMic.armed
            delay(ARM_POLL_MS)
        }
    }

    // La matrice peut avoir changé de mode pendant qu'on regardait ailleurs, et
    // l'autorisation peut avoir été accordée depuis les réglages du système. Les
    // deux se relisent au retour au premier plan plutôt que de s'inventer un
    // canal de notification.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mode = SonoConfig.mode(prefs)
                granted = MicPermission.isGranted(context)
                armed = SonoMic.armed
                noticed = MicNotice.isAllowed(context)
                // Repose le raccourci si on l'avait balayé, et le retire si le
                // micro s'est armé ailleurs. Passer par ici est le seul moment
                // sûr : rien d'autre ne tourne quand rien n'est armé.
                MicNotice.refresh(context, armed)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        MicPermission.Asked.mark(context)
        if (result) MicNotice.refresh(context, armed)
    }

    val requestNotice = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        noticed = result
        when {
            result -> MicNotice.refresh(context, armed)
            // Même piège que pour le micro : après deux refus le système ne
            // montre plus rien et répond « non » tout seul. On emmène alors là
            // où le refus se défait, plutôt que de laisser un bouton inerte.
            activity?.shouldShowRequestPermissionRationale(NOTICE) != true ->
                MicNotice.openSettings(context)
        }
    }

    /**
     * Demander, ou envoyer là où le refus se défait.
     *
     * Après deux refus, le système ne montre plus rien et répond « non » tout
     * seul. Un bouton qui ne ferait rien serait pire que pas de bouton : on
     * bascule alors vers la page des autorisations de l'app.
     */
    fun askOrOpenSettings() {
        val canPrompt = !MicPermission.Asked.get(context) ||
            activity?.shouldShowRequestPermissionRationale(MicPermission.NAME) == true
        if (canPrompt) request.launch(MicPermission.NAME) else MicPermission.openAppSettings(context)
    }

    val demo = remember { SonoDemo() }
    val renderer = remember { SonoRenderer(spec) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloScreen)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Breadcrumb(
            listOf(
                stringResource(R.string.sono_settings_parent),
                stringResource(R.string.toy_sono_name),
            ),
            onBack = onBack,
        )
        ScreenTitle(stringResource(R.string.toy_sono_name), small = true)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // L'aperçu joue le **vrai renderer** sur une scène **inventée** :
            // ouvrir cet écran n'allume pas le micro. Voir `SonoDemo` — c'est la
            // seule entorse du pack à la règle « l'aperçu exécute le toy », et
            // elle est là pour que la pastille micro d'Android ne s'allume pas
            // pour une vignette.
            //
            // L'horloge ne repart pas quand le mode change : arriver sur l'onde
            // doit montrer l'histoire déjà enregistrée, exactement comme sur la
            // matrice.
            AnimatedMatrixPreview(modifier = Modifier.size(252.dp), spec = spec) { frame, seconds ->
                renderer.render(frame, demo.snapshotAt(seconds), mode)
            }
            // Dit à l'écran ce que le code fait : cet aperçu n'écoute rien.
            MonoLabel(stringResource(R.string.sono_preview_simulated))
        }

        Spacer(Modifier.height(18.dp))

        SectionLabel(stringResource(R.string.sono_section_display))

        HaloCard {
            Legend(stringResource(R.string.sono_mode))
            HaloSelect(
                options = SonoMode.entries.map { it to stringResource(labelOf(it)) },
                selected = mode,
            ) {
                mode = it
                // La matrice suit : le service écoute ces préférences.
                SonoConfig.setMode(prefs, it)
            }
            MonoLabel(stringResource(R.string.sono_mode_hint))
        }

        SectionLabel(stringResource(R.string.sono_section_mic))

        HaloCard {
            Legend(stringResource(R.string.sono_permission))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(
                    text = stringResource(
                        if (granted) R.string.sono_permission_granted
                        else R.string.sono_permission_denied
                    ),
                    state = if (granted) ChipState.ON else ChipState.OFF,
                )
                Spacer(Modifier.weight(1f))
                if (!granted) {
                    PillButton(
                        text = stringResource(R.string.sono_permission_action),
                        primary = true,
                        small = true,
                    ) { askOrOpenSettings() }
                }
            }
            MonoLabel(
                stringResource(
                    if (granted) R.string.sono_permission_note else R.string.sono_permission_missing
                ),
            )
        }

        // L'armement n'a de sens qu'une fois l'autorisation obtenue : proposer
        // d'armer ce qu'on n'a pas serait un bouton qui ment.
        //
        // Le raccourci du volet est resté seul, depuis le 2026-09-16 : la carte
        // « Micro armé » et celle de la tuile ont été retirées, l'une parce
        // qu'elle doublait ce raccourci sans rien faire de plus, l'autre parce
        // que le volet arme déjà sans rien refermer, ce que la tuile ne sait
        // pas faire. Voir PRODUIT.md, § « Menus retirés ».
        if (granted) {
            HaloCard {
                Legend(stringResource(R.string.sono_notice_add))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(
                        text = stringResource(
                            if (noticed) R.string.sono_permission_granted
                            else R.string.sono_permission_denied
                        ),
                        state = if (noticed) ChipState.ON else ChipState.OFF,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!noticed) {
                        PillButton(
                            text = stringResource(R.string.sono_permission_action),
                            primary = true,
                            small = true,
                        ) { requestNotice.launch(NOTICE) }
                    }
                }
                MonoLabel(stringResource(R.string.sono_notice_add_note))
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

/** Assez lent pour ne rien coûter, assez court pour qu'on ne le voie pas. */
private const val ARM_POLL_MS = 500L

/** L'autorisation dont dépend le raccourci du volet. */
private const val NOTICE = Manifest.permission.POST_NOTIFICATIONS

/** Le nom d'un mode, dans la langue de l'app. */
private fun labelOf(mode: SonoMode): Int = when (mode) {
    SonoMode.SPECTRE -> R.string.sono_mode_spectrum
    SonoMode.AIGUILLE -> R.string.sono_mode_needle
    SonoMode.ONDE -> R.string.sono_mode_wave
}
