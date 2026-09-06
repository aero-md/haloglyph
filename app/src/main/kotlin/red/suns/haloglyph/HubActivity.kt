package red.suns.haloglyph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import red.suns.haloglyph.core.ui.HaloglyphTheme

/**
 * Le hub : l'écran d'accueil de l'app de réglages.
 *
 * Il montre l'état du matériel et la liste des toys embarqués ; ouvrir un toy
 * ouvre son écran de réglages, qui vit dans le module du toy. Le hub ne sait
 * rien de ce que fait Lapse, seulement qu'il existe et où sont ses réglages.
 */
class HubActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloglyphTheme {
                val context = LocalContext.current
                HubScreen(toys = remember(context) { ToyCatalog.of(context) })
            }
        }
    }
}
