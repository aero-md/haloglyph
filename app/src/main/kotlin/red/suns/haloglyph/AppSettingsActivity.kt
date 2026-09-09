package red.suns.haloglyph

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import red.suns.haloglyph.core.ui.BackBar
import red.suns.haloglyph.core.ui.HaloCard
import red.suns.haloglyph.core.ui.HaloScreen
import red.suns.haloglyph.core.ui.HaloSelect
import red.suns.haloglyph.core.ui.HaloglyphTheme
import red.suns.haloglyph.core.ui.Legend
import red.suns.haloglyph.core.ui.ScreenTitle
import red.suns.haloglyph.core.ui.SectionLabel

/**
 * Les réglages de l'application — l'étage au-dessus des toys (PRODUIT §5.4).
 *
 * Il ne porte qu'un réglage, et c'est normal : l'écran est créé par un besoin
 * réel — une valeur partagée par tous les toys n'a pas de toy où vivre — pas par
 * anticipation. Le prochain candidat probable est le thème de l'aperçu, si
 * jamais on en propose un.
 *
 * Il vit dans `app` et non dans `core:ui` : c'est le seul écran qui parle de
 * l'application elle-même, et un module de socle n'a pas à connaître son nom ni
 * sa version.
 */
class AppSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloglyphTheme {
                AppSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val languages = remember(context) { AppLanguage.supported(context) }
    // Relu à chaque composition initiale, jamais persisté à nous : le système
    // recrée l'activité quand la langue change, donc cet état repart de la
    // valeur qui fait foi — celle du `LocaleManager`.
    var language by remember { mutableStateOf(AppLanguage.current(context)) }
    val systemLabel = stringResource(R.string.language_system)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloScreen)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        BackBar(stringResource(R.string.app_name), onBack = onBack)
        ScreenTitle(
            stringResource(R.string.settings_title),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            small = true,
        )

        // La version tient dans la mention de droite du label de section : c'est
        // une donnée, pas un réglage, et elle n'a donc pas de carte à elle.
        SectionLabel(
            text = stringResource(R.string.settings_section_app),
            trailing = versionName(context),
        )

        HaloCard {
            Legend(stringResource(R.string.settings_language))
            HaloSelect(
                options = buildList<Pair<String?, String>> {
                    // « Système » en tête : c'est l'état par défaut, celui qui
                    // rend la main plutôt qu'il n'impose.
                    add(null to systemLabel)
                    languages.forEach { add(it to AppLanguage.endonym(it)) }
                },
                selected = language,
            ) {
                language = it
                AppLanguage.set(context, it)
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

/** `versionName` du paquet installé, jamais une constante recopiée à la main. */
private fun versionName(context: Context): String? =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
