package red.suns.haloglyph.gforce.widget

import android.content.Context
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.WidgetToy
import red.suns.haloglyph.gforce.GForceConfig
import red.suns.haloglyph.gforce.R
import red.suns.haloglyph.gforce.engine.GForceEngine
import red.suns.haloglyph.gforce.render.GForceRenderer
import red.suns.haloglyph.gforce.toy.GForceSensors
import java.util.concurrent.ConcurrentHashMap

/**
 * G-Forces dans le hublot d'écran d'accueil — et c'est **la** surface du toy.
 *
 * Le raisonnement est le même que pour Plumb, en plus tranché : dans un support de
 * voiture, le téléphone regarde le conducteur, donc la matrice regarde la route.
 * Celui qui conduit lit l'écran, pas le dos. La matrice reste juste et reste
 * offerte, mais elle s'adresse au passager qui se penche.
 *
 * ## La rafale est trop courte pour un trajet, et c'est le vrai défaut
 *
 * Un tap ouvre trente secondes de mesure. Un trajet en fait mille. Le hublot en
 * **boucle continue** est donc le réglage qui a du sens ici — c'est le seul toy du
 * pack pour lequel la boucle n'est pas un luxe mais le mode d'emploi, et il est
 * `loopable` parce qu'il ne réclame aucune capability.
 *
 * Les pics suivent la même règle qu'ailleurs : ils vivent dans le moteur de ce
 * hublot-là, donc une rafale qui s'arrête et redémarre repart de zéro. En boucle,
 * elle ne s'arrête pas.
 *
 * ## Le tap change de face
 *
 * Tant que rien ne mesure, il réveille ; dès que ça mesure, il passe de la bille
 * aux pics. Exactement la grammaire de Sono et de Plumb, et pour la même raison :
 * un hublot n'a pas d'appui long, le launcher le garde.
 */
class GForceWidgetToy : WidgetToy {

    override val id: String = GForceConfig.TOY_ID
    override val nameRes: Int = R.string.toy_gforce_name

    /** Le hublot se regarde de face : l'abscisse n'est pas celle de la matrice. */
    private val renderer by lazy { GForceRenderer(fromBack = false) }

    /**
     * Capteurs continus : sans ça, un tap démarrerait un service **ordinaire** et
     * Android 9 couperait les mesures en silence. Voir [WidgetToy.sensing].
     */
    override val sensing: Boolean = true

    /**
     * Un moteur et un jeu de capteurs **par hublot**.
     *
     * Ici ce n'est pas qu'une question de filtre : le moteur porte les **pics**, et
     * deux hublots qui partageraient le leur afficheraient chacun le trajet de
     * l'autre. La table est concurrente parce que [onTap] arrive du fil principal
     * pendant que les rendus tournent sur celui de la rafale.
     */
    private val rigs = ConcurrentHashMap<Int, Rig>()

    private class Rig(val engine: GForceEngine = GForceEngine()) {
        var sensors: GForceSensors? = null
    }

    /**
     * Au repos, `TAP` et rien d'autre.
     *
     * Aucun capteur n'écoute entre deux rafales. Les graduations seules donneraient
     * une règle sans rien à mesurer, et une bille figée serait pire — un cadran qui
     * montre le virage d'hier sans rien pour le dire.
     */
    override fun renderIdle(context: Context, widgetId: Int, frame: Frame) {
        renderer.renderIdle(frame)
    }

    /**
     * Le tap réveille la mesure ; pendant qu'elle court, il passe à **l'autre
     * face**.
     *
     * `false` : la rafale n'est ni relancée ni rallongée — changer de face ne doit
     * pas remettre trente secondes au compteur, et surtout pas effacer les pics en
     * repartant d'un moteur neuf.
     */
    override fun onTap(context: Context, widgetId: Int, live: Boolean): Boolean {
        if (!live) return true
        val prefs = GForceConfig.prefs(context)
        GForceConfig.setMode(prefs, GForceConfig.mode(prefs).next)
        return false
    }

    override fun onBurstStart(context: Context, widgetId: Int) {
        val rig = rigs.getOrPut(widgetId) { Rig() }
        rig.engine.reset()
        rig.sensors = GForceSensors(context, rig.engine).also { it.start() }
    }

    override fun onBurstEnd(context: Context, widgetId: Int) {
        val rig = rigs[widgetId] ?: return
        rig.sensors?.stop()
        rig.sensors = null
    }

    override fun renderBurst(
        context: Context,
        widgetId: Int,
        frame: Frame,
        elapsedSeconds: Double,
    ): Boolean {
        val rig = rigs[widgetId] ?: return false
        // Relue à chaque image : le tap vient peut-être de changer de face, et
        // l'appui long sur la matrice aussi.
        renderer.render(frame, rig.engine.snapshot(), GForceConfig.mode(GForceConfig.prefs(context)))
        return true
    }
}
