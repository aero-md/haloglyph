package red.suns.haloglyph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import red.suns.haloglyph.core.ui.HaloglyphTheme
import red.suns.haloglyph.sono.MicPermission

/**
 * Le hub : l'écran d'accueil de l'app de réglages.
 *
 * Il montre l'état du matériel et la liste des toys embarqués ; ouvrir un toy
 * ouvre son écran de réglages, qui vit dans le module du toy. Le hub ne sait
 * rien de ce que fait Lapse, seulement qu'il existe et où sont ses réglages.
 *
 * C'est aussi le seul endroit d'où l'autorisation micro de Sono peut être
 * demandée la première fois — voir [askForMicrophoneOnce].
 */
class HubActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloglyphTheme {
                val context = LocalContext.current
                AskForMicrophoneOnce()
                HubScreen(toys = remember(context) { ToyCatalog.of(context) })
            }
        }
    }
}

/**
 * La demande d'autorisation micro, une fois, à la première ouverture.
 *
 * ## Pourquoi ici et pas dans le toy
 *
 * Une permission d'exécution ne se demande que depuis une activité. Un Glyph Toy
 * est un service lié par Glyph Interface : il n'a pas d'écran, donc il ne peut
 * pas poser la question, et sans réponse il n'a rien à afficher. L'app est le
 * seul endroit d'où la question peut partir.
 *
 * ## Pourquoi à l'ouverture et pas derrière un bouton
 *
 * Une tuile « activer Sono » aurait ajouté un geste pour un état binaire que
 * l'utilisateur devrait de toute façon accorder avant de voir quoi que ce soit.
 * On demande donc au moment où le pack se présente, une fois — puis plus jamais
 * de lui-même. Un refus se rattrape depuis l'écran de réglages de Sono, qui est
 * l'endroit où l'on va quand on veut que Sono marche.
 *
 * ## Pourquoi c'est écrit en dur pour Sono
 *
 * Le hub sait qu'un toy peut être empêché par une permission — c'est
 * `ToyEntry.requiredPermission`, et c'est lui qui décide de l'affichage. Mais
 * *demander* suppose de savoir qui a déjà été sollicité, donc un stockage ; le
 * généraliser aujourd'hui produirait un registre à une seule entrée. Le jour où
 * un second toy demande une permission, c'est ici qu'on boucle.
 */
@Composable
private fun AskForMicrophoneOnce() {
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { MicPermission.Asked.mark(context) }

    LaunchedEffect(Unit) {
        if (MicPermission.isGranted(context)) return@LaunchedEffect
        if (MicPermission.Asked.get(context)) return@LaunchedEffect
        // Marqué avant la réponse : si l'utilisateur balaie la boîte de dialogue
        // sans répondre, on ne la lui remet pas au visage à chaque ouverture.
        MicPermission.Asked.mark(context)
        request.launch(MicPermission.NAME)
    }
}
