package red.suns.haloglyph.core.glyph

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Les tuiles de réglages rapides n'existent **que devant une Glyph Matrix**.
 *
 * Le pack tourne sur n'importe quel Android — c'est tout l'intérêt du hublot
 * d'écran d'accueil, qui émule la matrice sur un téléphone qui n'en a pas. Les
 * tuiles, elles, ne servent qu'à la matrice : armer le micro avant de retourner
 * le téléphone. Sur un Pixel, elles proposeraient d'allumer quelque chose qui
 * n'est pas là.
 *
 * ## Activées par défaut, retirées après constat
 *
 * L'inverse aurait été plus pur — déclarées éteintes, allumées quand la matrice
 * répond — et pire à l'usage : sur un Phone (3), les tuiles n'existeraient pas
 * tant que l'application n'a pas été ouverte une fois. Personne n'ouvre une app
 * de Glyph Toys avant d'aller chercher sa tuile ; il l'installe et va dans le
 * volet.
 *
 * On fait donc l'inverse : présentes d'emblée, **retirées** dès que la sonde dit
 * qu'il n'y a pas de matrice — ce qui arrive au premier lancement, et sur un
 * téléphone non-Nothing le premier lancement est obligatoire, c'est par là qu'on
 * pose un hublot.
 *
 * `DONT_KILL_APP` : sans lui le système tue le processus pour appliquer le
 * changement, c'est-à-dire pendant qu'on regarde le hub.
 */
object GlyphTiles {

    /**
     * Met les tuiles d'accord avec le matériel.
     *
     * @param available résultat de [GlyphAvailability.probe]. Ne rien appeler
     * tant qu'il est inconnu : effacer une tuile sur un doute serait pire que
     * l'afficher un instant de trop.
     */
    fun sync(context: Context, available: Boolean, tiles: List<Class<*>>) {
        val pm = context.packageManager
        val state = if (available) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        for (tile in tiles) {
            val component = ComponentName(context, tile)
            // Ne rien écrire si c'est déjà le cas : chaque appel réveille le
            // PackageManager et réécrit l'état des composants du paquet.
            if (pm.getComponentEnabledSetting(component) == state) continue
            runCatching {
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
