package red.suns.haloglyph.float.widget

import android.content.Context
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.WidgetToy
import red.suns.haloglyph.float.FloatConfig
import red.suns.haloglyph.float.R
import red.suns.haloglyph.float.engine.FloatEngine
import red.suns.haloglyph.float.engine.FloatMode
import red.suns.haloglyph.float.render.FloatRenderer
import red.suns.haloglyph.float.toy.FloatSensors
import java.util.concurrent.ConcurrentHashMap

/**
 * Float dans le hublot d'écran d'accueil — et c'est sans doute là qu'il est le
 * plus utile.
 *
 * Un niveau se regarde **pendant** qu'on cale quelque chose. Sur la matrice, ça
 * veut dire téléphone retourné sur le meuble, ce qui est la bonne pose pour
 * mesurer et la mauvaise pour lire : on soulève, donc on perd la mesure. Sur un
 * écran d'accueil, on pose le téléphone face en l'air et on lit sans y toucher.
 * Les deux surfaces ne se font pas concurrence, elles répondent à deux gestes
 * différents — et la seconde est mise à l'endroit par [FloatRenderer], puisqu'on
 * la regarde de l'autre côté.
 *
 * ## Le miroir n'est pas une option
 *
 * C'est la seule chose de ce toy qui **doive** différer d'une surface à l'autre.
 * La matrice est au dos du téléphone : lever le flanc droit de l'écran y envoie
 * la bulle à gauche. Le hublot est sur l'écran, donc à droite. Une image
 * identique serait fausse sur l'une des deux, et personne ne s'en apercevrait
 * sans les comparer.
 *
 * ## Le tap change d'instrument
 *
 * Sur la matrice, l'appui long passe du niveau à la boussole. Un hublot n'a pas
 * d'appui long — le launcher le garde — donc c'est le **tap** qui le fait, comme
 * chez Sono, et pour la même raison : tant que rien ne mesure il n'a qu'un sens
 * possible, dès que ça mesure il n'en a plus qu'un autre.
 *
 * Ce toy a d'abord rangé le choix dans les réglages du hublot, à la façon du
 * solide de Dice. C'était un rattrapage, pas un choix : Dice n'a **aucun** geste
 * de hublot disponible pour changer de solide, celui-ci en a un. Un réglage qu'un
 * geste peut faire n'a pas à exister deux fois.
 *
 * Le prix est réel et assumé : le choix est celui du toy, donc deux hublots ne
 * peuvent plus porter un niveau et une boussole côte à côte. En échange
 * l'instrument se change là où on le regarde, et la matrice suit.
 *
 * Le tap n'a **jamais** posé de référence d'horizontale. Il l'a fait le temps
 * d'une version, en silence, sur l'état le plus lourd du toy — et il n'y a plus
 * de référence du tout : un niveau à bulle n'a pas de réglage de zéro.
 */
class FloatWidgetToy : WidgetToy {

    override val id: String = FloatConfig.TOY_ID
    override val nameRes: Int = R.string.toy_float_name

    /** Le hublot se regarde de face : l'abscisse n'est pas celle de la matrice. */
    private val renderer by lazy { FloatRenderer(fromBack = false) }

    /**
     * Le premier toy du pack à en avoir besoin, et ça ne se serait pas vu.
     *
     * Depuis Android 9, un processus en arrière-plan ne reçoit plus les capteurs
     * à report continu — sans erreur, sans rien. Un tap sur un hublot démarrait
     * un service ordinaire pour tout toy sans capability : la bulle serait restée
     * absente, et la fiole vide aurait eu l'air d'un état légitime. Voir
     * [WidgetToy.sensing].
     */
    override val sensing: Boolean = true

    /**
     * Un moteur et un jeu de capteurs **par hublot**.
     *
     * Deux hublots qui mesurent ensemble pourraient partager un moteur depuis que
     * l'instrument est commun — mais pas le filtre : il est adaptatif, sa
     * constante de temps suit l'inclinaison, et deux rafales démarrées à dix
     * secondes d'écart n'ont aucune raison d'en être au même point. Chacun le
     * sien, donc, et chacun ferme le sien.
     *
     * La table est concurrente parce que [onTap] arrive du fil principal pendant
     * que les rendus tournent sur celui de la rafale.
     */
    private val rigs = ConcurrentHashMap<Int, Rig>()

    private class Rig(val engine: FloatEngine = FloatEngine()) {
        var sensors: FloatSensors? = null
    }

    private fun modeOf(context: Context): FloatMode = FloatConfig.mode(FloatConfig.prefs(context))

    /**
     * Au repos, `TAP` à la place de la mesure — et la marque de **l'instrument
     * qu'on va réveiller**.
     *
     * Aucun capteur n'est en écoute entre deux rafales, donc il n'y a rien à
     * montrer — et montrer la dernière mesure connue serait le pire des deux
     * mondes : un niveau qui affiche l'horizontale d'hier, sans rien pour dire
     * qu'il ne mesure plus.
     *
     * Ce qui reste dessiné n'est donc pas une mesure mais une identité, et elle
     * suit le mode : la rose pour la boussole, la fiole pour le niveau. Le tap
     * change d'instrument — sans ça, on ne savait pas lequel on allait réveiller
     * avant de l'avoir réveillé. Voir [FloatRenderer.renderIdle].
     */
    override fun renderIdle(context: Context, widgetId: Int, frame: Frame) {
        renderer.renderIdle(frame, modeOf(context))
    }

    /**
     * Le tap réveille la mesure ; pendant qu'elle court, il passe à **l'instrument
     * suivant**.
     *
     * C'est l'appui long de la matrice, rendu au seul geste qu'un hublot possède —
     * exactement la grammaire de Sono, et pour la même raison : tant que rien ne
     * mesure, le tap n'a qu'un sens possible ; dès que ça mesure, il n'en a plus
     * qu'un autre.
     *
     * Le choix est écrit dans les préférences du **toy**, donc il vaut partout : la
     * matrice comprise, et les hublots d'à côté avec. C'est la contrepartie
     * assumée d'avoir un geste plutôt qu'un réglage — on a perdu la possibilité de
     * poser un niveau et une boussole côte à côte, on a gagné un instrument qui se
     * change là où on le regarde.
     *
     * `false` : la rafale n'est ni relancée ni rallongée, et l'image suivante —
     * dans quarante millisecondes — montre déjà l'autre instrument.
     */
    override fun onTap(context: Context, widgetId: Int, live: Boolean): Boolean {
        if (!live) return true
        val prefs = FloatConfig.prefs(context)
        FloatConfig.setMode(prefs, FloatConfig.mode(prefs).next)
        return false
    }

    override fun onBurstStart(context: Context, widgetId: Int) {
        val rig = rigs.getOrPut(widgetId) { Rig() }
        rig.engine.reset()
        rig.engine.range = FloatConfig.range(FloatConfig.prefs(context))
        rig.sensors = FloatSensors(context, rig.engine).also {
            it.compass = modeOf(context) == FloatMode.BOUSSOLE
            it.start()
        }
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
        val prefs = FloatConfig.prefs(context)
        val mode = FloatConfig.mode(prefs)
        // Relus à chaque image : le tap vient peut-être de changer d'instrument, et
        // la portée se règle depuis l'écran des réglages pendant que ça tourne. Un
        // hublot ne consomme pas le signal de mise à niveau — il ne vibre pas, et
        // ce qu'il annoncerait est sous les yeux de qui regarde l'écran.
        rig.engine.range = FloatConfig.range(prefs)
        rig.sensors?.compass = mode == FloatMode.BOUSSOLE

        renderer.render(frame, rig.engine.snapshot(), mode, rig.engine.range, elapsedSeconds)
        return true
    }
}
