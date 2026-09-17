package red.suns.haloglyph.sono.audio

import android.content.Context
import red.suns.haloglyph.sono.engine.SonoEngine

/**
 * Le micro du pack : **un seul**, partagé, et qui ne se ferme que quand plus
 * personne ne le tient.
 *
 * ## Pourquoi ce n'est plus le toy qui ouvre le micro
 *
 * Depuis Android 14, un service de premier plan démarré alors que l'app est en
 * arrière-plan **n'obtient pas les permissions « pendant l'utilisation »** —
 * micro, caméra, position. La promotion réussit, la notification s'affiche, et
 * `AudioRecord` rend des zéros exacts : `RECORD_AUDIO` est en mode `foreground`
 * pour l'UID, et sans la capability correspondante l'opération est rejetée sans
 * un mot. C'est exactement le cas du toy : Glyph Interface le lie quand on
 * retourne le téléphone, donc depuis l'arrière-plan, donc trop tard.
 *
 * Les exemptions sont une liste fermée — composant système, widget,
 * notification, `PendingIntent` d'une app visible — et **la tuile de réglages
 * rapides n'y figure pas**. En revanche, une capability acquise au démarrage
 * d'un service **lui reste tant qu'il vit**. C'est tout le principe d'un
 * dictaphone : on lance, on verrouille, ça continue.
 *
 * D'où ce partage. Le micro est ouvert par qui peut légalement le faire —
 * [red.suns.haloglyph.sono.mic.SonoMicService], armé depuis une surface visible
 * — et le toy, lui, se contente de **tenir** ce qui est déjà ouvert et de lire
 * [engine]. S'il arrive le premier, il tente sa chance comme avant : quand
 * l'app est au premier plan, ça marche encore.
 *
 * ## Un compteur, pas un booléen
 *
 * Le service durable et le toy peuvent se tenir en même temps, dans n'importe
 * quel ordre, et se lâcher de même. Fermer sur le premier départ couperait le
 * micro du service parce que la matrice s'est éteinte. Chacun prend un jeton,
 * chacun le rend, et le dernier ferme la porte.
 */
object SonoMic {

    /** Le moteur, unique lui aussi : trois modes regardent la même mesure. */
    val engine = SonoEngine()

    private val source = MicSource(engine)

    private var holders = 0

    /**
     * Vrai quand le micro est tenu par le service **durable**, celui qui
     * survit au verrouillage.
     *
     * Ce n'est pas « le micro est ouvert » : le toy peut l'avoir ouvert pour
     * lui seul, le temps qu'il est affiché. C'est ce que la tuile montre, et ce
     * que le toy interroge pour savoir s'il a encore besoin de se promouvoir.
     */
    @Volatile
    var armed: Boolean = false
        private set

    /** La source réellement obtenue, pour le diagnostic. */
    val activeSource: String get() = source.activeSource

    /**
     * Prend un jeton, et ouvre le micro si personne ne le tenait.
     *
     * @param durable pose aussi [armed] — réservé au service qui survit au
     * verrouillage.
     * @return vrai si la mesure est en cours.
     */
    @Synchronized
    fun hold(context: Context, durable: Boolean = false): Boolean {
        if (durable) armed = true
        holders++
        if (holders == 1) return source.start(context.applicationContext)
        return engine.status == SonoEngine.Status.OK
    }

    /** Rend un jeton, et ferme le micro si c'était le dernier. */
    @Synchronized
    fun release(durable: Boolean = false) {
        if (durable) armed = false
        if (holders == 0) return
        holders--
        if (holders == 0) source.stop()
    }
}
