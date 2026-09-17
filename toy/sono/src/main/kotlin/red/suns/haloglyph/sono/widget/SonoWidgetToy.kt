package red.suns.haloglyph.sono.widget

import android.content.Context
import android.content.pm.ServiceInfo
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.BurstNeeds
import red.suns.haloglyph.core.widget.WidgetToy
import red.suns.haloglyph.sono.MicPermission
import red.suns.haloglyph.sono.R
import red.suns.haloglyph.sono.SonoConfig
import red.suns.haloglyph.sono.audio.SonoMic
import red.suns.haloglyph.sono.engine.SonoEngine
import red.suns.haloglyph.sono.render.SonoRenderer
import red.suns.haloglyph.sono.render.SonoWidgetIdle

/**
 * Sono dans le hublot d'écran d'accueil — le seul toy du pack qui ouvre quelque
 * chose en s'affichant.
 *
 * ## Il n'écoute que pendant qu'on le regarde
 *
 * C'est toute la différence avec le widget qu'on avait refusé d'écrire. Un
 * hublot qui tiendrait le micro en permanence sur un écran d'accueil est
 * indéfendable, et le dire dans une note de version ne le rendrait pas
 * défendable. Ici, le micro s'ouvre **au tap** et se referme trente secondes plus
 * tard, avec la pastille d'Android allumée pendant tout ce temps. Au repos, il ne
 * capte rien et le hublot le dit — voir [SonoWidgetIdle].
 *
 * ## Un geste, deux sens
 *
 * Le tap réveille le micro, et pendant qu'il écoute il passe au mode suivant. Ce
 * n'est pas une surcharge : c'est l'appui long de la matrice, qui n'existe pas
 * ici — le launcher le garde — rendu au seul geste qui reste. Voir [onTap].
 *
 * ## Pourquoi ça marche alors que le toy, lui, n'y arrivait pas
 *
 * Depuis Android 14, un service de premier plan démarré depuis l'arrière-plan
 * n'obtient pas les permissions « pendant l'utilisation ». C'est ce qui casse le
 * Glyph Toy, lié par Glyph Interface quand on retourne le téléphone — voir
 * [SonoMic]. Les exemptions sont une liste fermée, et **le tap sur un widget y
 * figure** : « le service démarre par interaction avec des widgets d'app ». Le
 * hublot est donc, avec la notification, l'une des deux surfaces du pack qui
 * puissent légalement ouvrir le micro sans qu'un écran soit allumé.
 *
 * D'où [needs] : c'est ce qui dit au receiver de démarrer la rafale en
 * `startForegroundService` plutôt qu'en service ordinaire. La distinction se
 * joue dans la diffusion du tap et nulle part ailleurs.
 *
 * ## Ce que le hublot lit
 *
 * Le moteur partagé, comme le toy et comme la tuile. Si le micro est **déjà
 * armé**, la rafale ne fait que prendre un jeton de plus sur une capture déjà
 * ouverte : rien à promouvoir, rien à rouvrir, et la mesure est celle qui court
 * depuis tout à l'heure plutôt qu'une intégration qui redémarre à zéro.
 */
class SonoWidgetToy : WidgetToy {

    override val id: String = SonoConfig.TOY_ID
    override val nameRes: Int = R.string.toy_sono_name

    private val renderer by lazy { SonoRenderer() }

    override val needs: BurstNeeds = BurstNeeds(
        serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        permission = MicPermission.NAME,
    )

    /**
     * Trente secondes, comme les autres toys.
     *
     * Il y en a eu cinq, par prudence : les trente secondes des autres ne coûtent
     * que des pixels, celles-ci coûtent une demi-minute de micro ouvert. Mais
     * cinq secondes ne laissent pas le temps de regarder l'onde défiler — le
     * temps de retourner le poignet, c'est déjà fini — et la prudence qui empêche
     * de voir ce qu'on est venu voir n'en est plus.
     *
     * Ce qu'on ne lâche pas : le micro ne s'ouvre **que** sur un tap, la pastille
     * d'Android est allumée pendant tout ce temps, et ce toy ne boucle jamais —
     * [loopable] le déduit tout seul de [needs].
     */
    override val burstDurationMs: Long = 30_000L

    /**
     * Ce que ça décide, et rien d'autre : faut-il réclamer le micro au système
     * avant d'animer. Sans autorisation, le demander n'obtiendrait rien et
     * afficherait une notification pour rien.
     *
     * Le hublot, lui, affiche Sono quoi qu'il arrive — c'est le toy qui dit ce
     * qui lui manque, et le sauter serait la seule façon de mentir.
     */
    override fun isReady(context: Context): Boolean = MicPermission.isGranted(context)

    /**
     * Au repos, le hublot **ne montre pas ce que montre la matrice**, et c'est
     * voulu : voir [SonoWidgetIdle].
     *
     * Les deux surfaces ne sont pas dans la même situation. Sur la matrice, le
     * repos de Sono n'a rien à suggérer — on y arrive en retournant le téléphone.
     * Dans un hublot, c'est ce qu'on voit presque tout le temps, et il lui
     * manquait la seule chose qui compte : comment le réveiller. D'où `TAP` et
     * l'onde, sa marque — la grammaire commune à tous les hublots du pack.
     *
     * Dès qu'une mesure court, en revanche, on repasse au renderer commun : là,
     * les deux surfaces montrent la même chose parce qu'elles disent la même
     * chose.
     */
    override fun renderIdle(context: Context, widgetId: Int, frame: Frame) {
        val snap = SonoMic.engine.snapshot(now())
        if (snap.status != SonoEngine.Status.OK) {
            SonoWidgetIdle.render(frame)
            return
        }
        renderer.render(frame, snap, SonoConfig.mode(SonoConfig.prefs(context)))
    }

    /**
     * Le tap pendant qu'il écoute : **mode suivant** — spectre, aiguille, onde.
     *
     * C'est l'appui long de la matrice, rendu au seul geste qu'un hublot possède.
     * Il n'entre pas en concurrence avec le réveil du micro, parce que les deux ne
     * peuvent pas être demandés au même moment : tant que rien n'écoute, le tap
     * n'a qu'un sens possible, et dès que ça écoute il n'en a plus qu'un autre.
     *
     * La rafale n'est pas relancée — d'où le `false`. Rouvrir le micro pour
     * changer de dessin couperait la mesure et ferait repartir l'onde de la page
     * blanche, et rallonger la fenêtre à chaque tap donnerait un hublot qui écoute
     * tant qu'on joue avec. Trente secondes après le premier doigt, quoi qu'il se
     * passe entre-temps.
     *
     * Le mode est écrit dans les préférences du toy, donc il vaut **partout** — la
     * matrice comprise, comme quand on le change depuis l'écran de réglages.
     * [renderBurst] les relit à chaque image : rien à notifier, l'image suivante
     * arrive dans quarante millisecondes.
     */
    override fun onTap(context: Context, widgetId: Int, live: Boolean): Boolean {
        if (!live) return true
        val prefs = SonoConfig.prefs(context)
        SonoConfig.setMode(prefs, SonoConfig.mode(prefs).next)
        return false
    }

    override fun onBurstStart(context: Context, widgetId: Int) {
        SonoMic.hold(context)
    }

    override fun onBurstEnd(context: Context, widgetId: Int) {
        SonoMic.release()
        // L'histoire de l'onde appartient à la capture qui vient de se fermer :
        // la garder ferait démarrer la prochaine rafale sur du son d'il y a une
        // heure, tracé comme s'il arrivait à l'instant.
        renderer.clearHistory()
    }

    /**
     * Pendant une activation, **toujours le renderer du toy** — jamais le repos.
     *
     * Cette méthode déléguait à [renderIdle], et c'était faux depuis que le repos
     * du hublot est autre chose que le rendu commun : une activation qui ne
     * captait rien affichait `MIC` et `TAP`, c'est-à-dire qu'elle invitait à
     * faire ce qu'on venait de faire. Ici on montre ce que le micro donne, même
     * quand il ne donne rien — l'axe seul, ou `---` s'il est coupé, exactement
     * comme sur la matrice.
     */
    override fun renderBurst(
        context: Context,
        widgetId: Int,
        frame: Frame,
        elapsedSeconds: Double,
    ): Boolean {
        renderer.render(frame, SonoMic.engine.snapshot(now()), SonoConfig.mode(SonoConfig.prefs(context)))
        return true
    }

    private fun now(): Double = System.nanoTime() / 1e9

}
