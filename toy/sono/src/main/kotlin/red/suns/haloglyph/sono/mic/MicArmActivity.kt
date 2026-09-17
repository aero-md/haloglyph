package red.suns.haloglyph.sono.mic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import red.suns.haloglyph.sono.MicPermission
import red.suns.haloglyph.sono.settings.SonoSettingsActivity

/**
 * L'activité qui ne montre rien, et dont c'est tout le métier.
 *
 * Armer le micro demande de démarrer un service de premier plan de type
 * `microphone` **pendant que l'app est visible** — sinon Android lui refuse la
 * capability et la capture ne rend que des zéros (voir
 * [red.suns.haloglyph.sono.audio.SonoMic]). Une tuile de réglages rapides ne
 * suffit pas : elle n'est pas dans la liste d'exemptions. Elle lance donc ceci,
 * qui est visible le temps d'un battement de cil, démarre le service, et s'en
 * va.
 *
 * Ce n'est pas un détour pour contourner une règle, c'est la règle : le système
 * demande que l'utilisateur soit devant quand le micro s'ouvre. Il l'est — il
 * vient d'appuyer sur la tuile.
 */
class MicArmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        // Ni ouverture ni fermeture animées : sans ça, le volet se referme sur
        // une transition d'app, ce qui donne exactement l'impression qu'on
        // vient d'être éjecté vers l'écran d'accueil.
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
    }

    override fun onResume() {
        super.onResume()
        if (!MicPermission.isGranted(this)) {
            // Rien à armer : on emmène là où l'autorisation se donne, qui est
            // le seul endroit de l'app à pouvoir la demander. Ici on veut bien
            // une vraie navigation, donc pas de retrait de tâche.
            startActivity(
                Intent(this, SonoSettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
            return
        }

        // Depuis `onResume` et pas `onCreate` : c'est l'état de l'app au moment
        // de l'appel qui décide de la capability, et une activité créée n'est
        // pas encore une activité au premier plan.
        startForegroundService(Intent(this, SonoMicService::class.java))

        // Puis on rend la main **avant** de mourir. On vit seuls dans une tâche
        // sans affinité et exclue des récents : si on la laisse se vider alors
        // qu'elle est au premier plan, le système n'a rien à réafficher et
        // retombe sur le launcher — c'est ça, l'éjection vers l'écran
        // d'accueil. `moveTaskToBack` remet devant ce qui y était, et le
        // `finish` qui suit se produit hors écran.
        val yielded = moveTaskToBack(true)
        if (!yielded) Log.w(TAG, "tâche non rendue, le finish sera visible")
        finish()
    }

    override fun finish() {
        // Avant `super`, sinon la transition est déjà choisie quand on demande
        // à ne pas en avoir.
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        super.finish()
    }

    private companion object {
        const val TAG = "MicArmActivity"
    }
}
