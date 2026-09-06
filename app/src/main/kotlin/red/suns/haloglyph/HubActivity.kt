package red.suns.haloglyph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import red.suns.haloglyph.core.glyph.GlyphAvailability
import red.suns.haloglyph.core.ui.AnimatedMatrixPreview
import red.suns.haloglyph.core.ui.HaloglyphTheme
import red.suns.haloglyph.core.ui.ToyEntry

/**
 * Le hub.
 *
 * À ce stade il ne fait qu'une chose, et c'est celle qui compte : montrer que
 * la plomberie tient debout. La liste des toys est vide tant que la migration
 * n'a pas commencé (TECHNIQUE §12) ; l'écran affiche la matrice de
 * vérification, et dit si un vrai matériel répond en face.
 */
class HubActivity : ComponentActivity() {

    /** Les toys embarqués dans *cette* app. Vide : la migration n'a pas commencé. */
    private val toys: List<ToyEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloglyphTheme {
                Scaffold { padding ->
                    HubScreen(toys = toys, modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun HubScreen(toys: List<ToyEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var matrixPresent by remember { mutableStateOf<Boolean?>(null) }

    // Sonde réelle : on tente une connexion et on attend le callback. Tester la
    // présence du paquet ne prouverait rien (visibilité des paquets, Android 11+).
    LaunchedEffect(Unit) {
        GlyphAvailability.probe(context) { matrixPresent = it }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )

        AnimatedMatrixPreview(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) { frame, seconds -> SelfTest.render(frame, seconds) }

        Text(
            text = when (matrixPresent) {
                null -> stringResource(R.string.hub_matrix_probing)
                true -> stringResource(R.string.hub_matrix_present)
                false -> stringResource(R.string.hub_matrix_absent)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (toys.isEmpty()) {
            Text(
                text = stringResource(R.string.hub_no_toys),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
