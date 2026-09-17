package red.suns.haloglyph

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import red.suns.haloglyph.core.glyph.GlyphSettings
import red.suns.haloglyph.core.ui.HaloCard
import red.suns.haloglyph.core.ui.HaloSelect
import red.suns.haloglyph.core.ui.Legend
import red.suns.haloglyph.core.ui.PillButton
import red.suns.haloglyph.core.ui.ScreenTitle
import red.suns.haloglyph.core.ui.SectionLabel

/**
 * L'onglet paramètres — l'étage au-dessus des toys (PRODUIT §5.4), atteint
 * désormais par un onglet et non plus par une activité à part.
 *
 * Deux sections, deux sujets qui ne se mélangent pas : la langue, seule valeur
 * qui ait la même portée pour tous les toys ; et, en dessous, les deux écrans
 * Glyph du système — hérités du hub, qui ne les mesurait que pour décider s'il
 * fallait les montrer. [matrixPresent] est cette même sonde, relevée par
 * l'appelant (`GlyphAvailability.probe`, dans [AppShell]) : un bouton qui
 * ouvrirait un écran système absent sur tout autre Android promettrait ce
 * qu'il ne tient pas.
 */
@Composable
fun ParamsScreen(matrixPresent: Boolean?, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val languages = remember(context) { AppLanguage.supported(context) }
    // Relu à chaque composition initiale, jamais persisté à nous : le système
    // recrée l'activité quand la langue change, donc cet état repart de la
    // valeur qui fait foi — celle du `LocaleManager`.
    var language by remember { mutableStateOf(AppLanguage.current(context)) }
    val systemLabel = stringResource(R.string.language_system)
    val screens = remember(context) { GlyphSettings.screens(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        ScreenTitle(stringResource(R.string.title_params))

        // Pas de padding vertical ici : [SectionLabel] porte déjà sa propre
        // marge du haut (26 dp), la même qu'entre deux sections plus bas sur
        // cet écran. En ajouter une deuxième empilerait deux marges pour un
        // seul espace.
        //
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

        // Réservés au Phone (3) : `matrixPresent` est la sonde matérielle du
        // hub, et un bouton qui ouvrirait un écran système absent sur tout
        // autre Android ne ferait que promettre ce qu'il ne tient pas.
        if (matrixPresent == true && screens.any) {
            SectionLabel(text = stringResource(R.string.settings_section_glyph))

            HaloCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    screens.active?.let { target ->
                        PillButton(
                            text = stringResource(R.string.hub_glyph_matrix),
                            modifier = Modifier.weight(1f),
                            primary = true,
                        ) { GlyphSettings.open(context, target) }
                    }
                    screens.manager?.let { target ->
                        PillButton(
                            text = stringResource(R.string.hub_glyph_toys),
                            modifier = Modifier.weight(1f),
                            primary = screens.active == null,
                        ) { GlyphSettings.open(context, target) }
                    }
                }
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
