package red.suns.haloglyph.sono.mic

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import red.suns.haloglyph.sono.MicPermission
import red.suns.haloglyph.sono.R
import red.suns.haloglyph.sono.audio.SonoMic

/**
 * La tuile de réglages rapides : armer le micro avant de retourner le
 * téléphone.
 *
 * ## Ce qu'elle fait, et ce qu'elle ne peut pas faire
 *
 * Elle **n'accorde rien**. Android ne sait pas rendre `RECORD_AUDIO` permanent,
 * et taper une tuile ne figure pas dans les exemptions qui permettent d'ouvrir
 * le micro depuis l'arrière-plan. Ce qu'elle fait, c'est **allumer le micro
 * pendant qu'on est devant** — un geste, deux secondes avant de poser le
 * téléphone écran contre la table — et laisser vivre le service qui le tient.
 * Le toy trouve alors une capture déjà ouverte quand Glyph Interface le lie.
 *
 * Elle est donc asymétrique, et c'est normal : **couper** ne demande aucune
 * permission et part d'ici directement ; **armer** passe par [MicArmActivity],
 * le temps que le système voie une surface de l'app au premier plan.
 *
 * Le détour est mesuré, pas supposé. Démarrer le service directement depuis
 * `onClick` échoue — le système accorde bien quinze secondes de droit de
 * *démarrage*, mais pas le while-in-use qui va avec :
 *
 * ```
 * Background started FGS: Allowed [...
 *     tempAllowListReason:<tile onclick, duration:15000>; allowWiu:-1 ...]
 * W ActivityManager: Foreground service started from background can not have
 *                    location/camera/microphone access
 * ```
 *
 * ## Pourquoi elle n'est jamais indisponible
 *
 * Une tuile `STATE_UNAVAILABLE` ne reçoit pas les clics. Sans autorisation
 * micro elle serait donc grise et muette, alors que c'est exactement le moment
 * où l'utilisateur a besoin qu'on l'emmène quelque part. Elle reste éteinte,
 * cliquable, et le clic mène à l'écran de réglages.
 */
class SonoMicTile : TileService() {

    override fun onStartListening() {
        paint(SonoMic.armed)
    }

    override fun onClick() {
        if (SonoMic.armed) {
            SonoMicService.disarm(this)
            // Peint tout de suite, sans relire [SonoMic.armed] : le service ne
            // meurt pas dans la milliseconde, et l'attendre laissait la tuile
            // allumée une dizaine de secondes après le geste. Une tuile n'a pas
            // à sonder ce qu'elle vient elle-même de demander.
            paint(armed = false)
            return
        }
        paint(armed = true)
        // Le volet se referme et l'activité éclair prend le relais : c'est elle
        // qui donne au service le droit d'ouvrir le micro. Si elle échoue —
        // autorisation retirée entre-temps — le service repeindra la vérité.
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MicArmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    private fun paint(armed: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (armed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(
            when {
                armed -> R.string.sono_tile_listening
                MicPermission.isGranted(this) -> R.string.sono_tile_off
                else -> R.string.sono_permission_denied
            },
        )
        tile.updateTile()
    }

    internal companion object {
        /**
         * Redemande au système de nous laisser peindre.
         *
         * Une tuile ne peut se redessiner que pendant qu'elle écoute, ce qui
         * n'arrive normalement que volet ouvert. Le service arme et désarme en
         * dehors de ces fenêtres : sans ça, la tuile garderait l'état d'avant
         * jusqu'à la prochaine ouverture du volet.
         */
        fun refresh(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, SonoMicTile::class.java))
            }
        }
    }
}
