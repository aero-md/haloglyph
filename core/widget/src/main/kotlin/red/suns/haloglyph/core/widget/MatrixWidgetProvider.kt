package red.suns.haloglyph.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec

/**
 * Le hublot d'écran d'accueil : un widget, tous les toys.
 *
 * ## Un hublot, rien d'autre
 *
 * Le widget n'a pas de fond, pas de carte, pas de coins : il est **rond**, parce
 * qu'il ne représente qu'une chose, le hublot de la Glyph Matrix. Il n'est
 * jamais redimensionnable — la matrice est carrée, et un hublot qu'on étire en
 * ovale n'est plus un hublot. Deux formats, **2 × 2** et **1 × 1**, donc deux
 * fournisseurs, parce que le système attache une taille à un fournisseur et non
 * à une instance posée. Un seul rendu pour les deux.
 *
 * ## Deux gestes, et c'est tout
 *
 * - **tap** : interagir avec le toy affiché — relancer le dé, dérouler l'anneau
 *   des secondes, écouter.
 * - **double tap** : passer au toy suivant de la rotation de ce hublot.
 *
 * Les deux arrivent ici par la même diffusion, et rien ne les distingue à
 * l'arrivée : c'est [WidgetBurstService] qui attend la fenêtre de double tap et
 * tranche. Le receiver, lui, ne fait que **transmettre**, et c'est le point
 * essentiel de ce fichier.
 *
 * ## Le receiver ne fait plus rien de long, et c'est un correctif
 *
 * L'animation tournait ici, dans `goAsync()`. Ça marchait, à trois choses près
 * qui sont toutes la même : **Android sérialise la livraison des diffusions à un
 * receiver**. Tant que le `PendingResult` n'est pas rendu, les diffusions
 * suivantes attendent en file. Un hublot en train d'animer bloquait donc les
 * autres, un tap sur un second hublot restait coincé cinq secondes, puis se
 * rejouait — avec ses voisins, à la queue leu leu. Ce n'était pas une limite de
 * la plateforme, c'était l'animation qui squattait la file.
 *
 * Depuis, `onReceive` démarre un service et rend la main dans la milliseconde.
 * Plusieurs hublots peuvent animer ensemble, un tap n'attend jamais, et la borne
 * des dix secondes au-delà desquelles le système tue un receiver ne s'applique
 * plus à rien.
 *
 * ## Le piège à ne jamais réintroduire
 *
 * **Un widget ne binde jamais le `ToyService`.** Ce service existe pour Glyph
 * Interface ; le binder depuis un widget déclenche `GlyphMatrixManager.init()`,
 * qui tente une connexion à `com.nothing.thirdparty` — inexistant hors Nothing —
 * et laisse un service à moitié initialisé. Le widget instancie les moteurs et
 * les renderers **en direct**, dans son propre processus. Même code, deux points
 * d'entrée indépendants ; c'est d'ailleurs pour ça que les moteurs sont du
 * Kotlin pur.
 */
abstract class MatrixWidgetProvider : AppWidgetProvider() {

    /**
     * Les toys que ce hublot sait porter, dans l'ordre où ils tournent.
     *
     * **Doit renvoyer la même liste d'un appel à l'autre.** Un `AppWidgetProvider`
     * est un receiver : le système en construit une instance neuve à chaque
     * diffusion, et une liste fabriquée dans le getter reconstruirait tous les
     * renderers du pack à chaque image de repos. L'implémentation tient donc ses
     * instances dans un objet du processus — voir `HaloToys` dans `app`.
     */
    protected abstract val toys: List<WidgetToy>

    /**
     * Le service qui anime. Déclaré par l'implémentation parce que c'est son
     * manifeste qui le porte, avec les types de premier plan que les toys
     * embarqués réclament.
     */
    protected abstract val burstService: Class<out WidgetBurstService>

    /** V1 : Phone (3). Les widgets tournent sur n'importe quel Android. */
    protected open val spec: MatrixSpec get() = MatrixSpec.Phone3

    /**
     * Les mêmes, pour l'écran de réglages : il affiche un aperçu et liste la
     * rotation, donc il lui faut la matrice et le catalogue du fournisseur, pas
     * ceux qu'il aurait supposés.
     */
    internal val matrixSpec: MatrixSpec get() = spec
    internal val widgetToys: List<WidgetToy> get() = toys

    // ---------- cycle de vie AppWidget ----------

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) pushIdle(context, appWidgetManager, id)
        // C'est aussi par ici qu'une boucle continue repart après un
        // redémarrage : le système diffuse `APPWIDGET_UPDATE` au boot, et c'est
        // la seule diffusion qu'un widget reçoive à ce moment-là. Pas de
        // `BOOT_COMPLETED` à demander, donc, ni la permission qui va avec.
        syncLoops(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // La taille est fixe, mais la densité effective d'une cellule ne l'est
        // pas d'un launcher à l'autre : la bitmap doit suivre ce qu'on lui donne.
        pushIdle(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Les identifiants sont recyclés : les réglages d'un widget supprimé
        // s'appliqueraient au prochain posé. Voir [WidgetConfig].
        WidgetConfig.forget(context, appWidgetIds)

        // Le dernier hublot retiré, les bitmaps des peintres n'ont plus de raison
        // d'occuper le tas du processus jusqu'à sa mort. « Le dernier » se compte
        // sur les deux formats : ils partagent les mêmes peintres, puisque ceux-ci
        // ne dépendent que de l'apparence.
        if (postedIds(context).isEmpty()) WidgetRender.releaseAll()
        // Un hublot retiré pendant qu'il bouclait laisserait le service animer
        // un widget qui n'existe plus.
        syncLoops(context)
        super.onDeleted(context, appWidgetIds)
    }

    /**
     * Dit au service de relire les réglages, **sans le démarrer**.
     *
     * Le message est idempotent : il ne dit pas ce qui a changé, il dit
     * « remets-toi d'accord avec ce qui est écrit ». C'est ce qui permet de
     * l'envoyer sans réfléchir depuis n'importe où, y compris quand rien n'a
     * bougé.
     *
     * ## Pourquoi une diffusion, et pas un démarrage
     *
     * Parce qu'on est ici dans `onUpdate`, et qu'**on n'a le droit de rien
     * démarrer depuis là**. Ni un service de premier plan ni même un service
     * ordinaire — mesuré, pas supposé :
     *
     * ```
     * W MatrixWidget: BackgroundServiceStartNotAllowedException:
     *     Not allowed to start service ... WIDGET_LOOP_SYNC:
     *     app is in background uid ... RCVR bg:+179ms
     * ```
     *
     * L'exemption qui nous sert ailleurs est « le service démarre par
     * interaction avec un widget », et un `APPWIDGET_UPDATE` qu'on n'a pas
     * demandé n'est l'interaction de personne.
     *
     * Une diffusion, elle, ne démarre rien : le service l'attrape s'il vit, et
     * elle tombe dans le vide sinon. C'est exactement le contrat qu'on veut —
     * les trois endroits d'où le service peut légalement *naître* sont le tap, le
     * boot, et l'écran de réglages ([startLoops]).
     */
    fun syncLoops(context: Context) {
        context.sendBroadcast(
            Intent(WidgetBurstService.ACTION_SYNC)
                .setPackage(context.packageName)
                .putExtra(WidgetBurstService.EXTRA_PROVIDER, javaClass.name),
        )
    }

    /**
     * Démarre le service pour qu'il prenne ses boucles — **depuis un endroit qui
     * en a le droit**.
     *
     * Il y en a exactement deux : l'écran de réglages d'un hublot, où
     * l'application est au premier plan, et la diffusion `BOOT_COMPLETED`, qui
     * est une exemption nommée. Partout ailleurs, c'est [syncLoops] qu'il faut.
     *
     * On promeut, parce qu'une boucle doit survivre à la pression mémoire :
     * sinon elle s'arrête au premier coup de vent, et le hublot se fige sans que
     * rien ne l'explique.
     */
    fun startLoops(context: Context) {
        val intent = Intent(context, burstService)
            .setAction(WidgetBurstService.ACTION_SYNC)
            .putExtra(WidgetBurstService.EXTRA_PROVIDER, javaClass.name)

        // On ne promeut **que** s'il y a une boucle à protéger. Promouvoir à
        // tout hasard demandait `shortService` faute de mieux, et ce type-là ne
        // se retire pas tout seul : il s'ajoutait à ceux d'une rafale ou d'une
        // boucle ultérieure, et sa limite de trois minutes finissait par tuer un
        // service qui n'aurait jamais dû l'endosser.
        if (!anyLooping(context)) {
            runCatching { context.startService(intent) }
                .onFailure { Log.w(WidgetRender.TAG, "synchronisation refusée", it) }
            return
        }
        promote(context, intent, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    /** Un hublot du paquet, quel qu'en soit le format, est-il réglé en boucle ? */
    private fun anyLooping(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.installedProviders.any { info ->
            info.provider.packageName == context.packageName &&
                manager.getAppWidgetIds(info.provider).any { WidgetConfig.loop(context, it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TAP_ACTION) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) handTap(context, widgetId)
            return
        }
        super.onReceive(context, intent)
    }

    // ---------- le tap ----------

    /**
     * Passe le tap au service, et s'en va.
     *
     * Le seul travail fait ici est de savoir **comment** démarrer le service, et
     * ça ne se décide pas ailleurs : une permission « pendant l'utilisation »
     * n'est accordée qu'à un service de premier plan démarré *dans* la diffusion
     * du tap sur le widget. C'est une exemption nommée — « le service démarre par
     * interaction avec des widgets d'app » — et elle expire avec ce `onReceive`.
     * Repousser la promotion à plus tard, quand on saura si le geste était un tap
     * ou un double tap, la perdrait : le micro s'ouvrirait sans erreur et ne
     * rendrait que des zéros.
     *
     * On paie donc un service de premier plan pour un geste qui se révélera
     * peut-être un double tap. Ça ne coûte rien de visible : sa notification
     * n'est affichée qu'au bout de dix secondes par le système, et une rafale en
     * dure cinq.
     */
    private fun handTap(context: Context, widgetId: Int) {
        val rotation = WidgetConfig.rotation(context, widgetId, toys.map { it.id })
        val current = WidgetConfig.current(context, widgetId, rotation)

        val intent = Intent(context, burstService)
            .setAction(WidgetBurstService.ACTION_TAP)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(WidgetBurstService.EXTRA_PROVIDER, javaClass.name)

        // Deux raisons de promouvoir, et elles ne se ressemblent pas. Une
        // capability — le micro — se joue dans cette diffusion et nulle part
        // ailleurs. Un toy qui **lit des capteurs**, lui, ne demande rien de
        // particulier : il a seulement besoin que le processus ne soit pas en
        // arrière-plan, sans quoi l'accéléromètre ne lui enverra jamais rien et
        // ne le lui dira pas. Voir [WidgetToy.sensing].
        if (reachable(rotation, current).any { toy ->
                toy.sensing || (toy.needs != null && toy.isReady(context))
            }
        ) {
            promote(context, intent, widgetId)
            return
        }

        // Un service ordinaire suffit, et il ne coûte aucune notification. Le
        // droit de le démarrer vient de la diffusion elle-même — une app en
        // arrière-plan ne peut pas le faire, une app qui traite un tap sur son
        // widget le peut, le temps de ce `onReceive`.
        runCatching { context.startService(intent) }.onFailure {
            // Refusé quand même : on paie la promotion plutôt que de laisser le
            // hublot immobile. Elle, elle est toujours permise ici, et elle
            // n'affiche rien — le système diffère la notification d'un service de
            // premier plan d'une dizaine de secondes, une rafale en dure cinq.
            Log.w(WidgetRender.TAG, "service ordinaire refusé, on promeut", it)
            promote(context, intent, widgetId)
        }
    }

    /**
     * Les toys que **ce doigt-là** peut réveiller : celui qui est affiché, et le
     * suivant de la rotation.
     *
     * ## Pourquoi deux, et pas un
     *
     * Parce qu'au moment où l'on décide, le geste n'est pas tranché : la fenêtre
     * de double tap ne se ferme que 250 ms plus tard, dans le service. Ce doigt
     * est peut-être un tap simple sur le toy affiché, peut-être le premier d'un
     * double tap qui amènera le suivant — et **les deux peuvent réclamer quelque
     * chose**.
     *
     * Or ce qu'on décide ici ne se rattrape pas : l'exemption qui autorise à
     * démarrer un service de premier plan depuis l'arrière-plan, et avec lui les
     * permissions « pendant l'utilisation », vaut pour la **diffusion du tap** et
     * expire avec ce `onReceive`. Le service promu plus tard échoue, ou obtient un
     * micro qui ne rend que des zéros.
     *
     * C'était la vraie raison pour laquelle Sono ne partait pas au double tap, et
     * pourquoi un niveau amené au double tap affichait sa visée sans jamais de
     * bulle : le service avait été démarré ordinaire pour le toy précédent, et
     * Android ne livre pas les capteurs continus à un processus en arrière-plan —
     * sans erreur, sans rien. Ce n'était pas une limite de la plateforme, c'était
     * la question posée trop tard.
     *
     * Le prix est un service de premier plan payé pour un geste qui se révélera
     * peut-être un tap simple sur un toy qui n'avait besoin de rien. Il ne coûte
     * rien de visible : le système diffère la notification d'une dizaine de
     * secondes, et une rafale en dure cinq.
     */
    private fun reachable(rotation: List<String>, current: String?): List<WidgetToy> {
        val after = WidgetRotation.next(current, rotation)
        return listOfNotNull(current, after).distinct()
            .mapNotNull { id -> toys.firstOrNull { it.id == id } }
    }

    /**
     * Démarre au premier plan, **et le dit au service**.
     *
     * L'extra n'est pas un confort : `startForegroundService` engage le service à
     * se promouvoir dans les secondes qui suivent, sous peine d'être tué avec une
     * `ForegroundServiceDidNotStartInTimeException`. Le service ne peut pas
     * deviner par quelle porte il est entré, et se promouvoir à tout hasard
     * afficherait une notification là où il n'y avait rien à annoncer.
     */
    private fun promote(context: Context, intent: Intent, widgetId: Int) {
        intent.putExtra(WidgetBurstService.EXTRA_PROMOTED, true)
        if (runCatching { context.startForegroundService(intent) }.isSuccess) return

        // Le système a refusé la promotion, et il a ses raisons : l'exemption
        // vaut pour le **tap** sur un widget, pas pour un `APPWIDGET_UPDATE`
        // qu'on reçoit au démarrage du téléphone ou après une mise à jour. Un
        // service ordinaire, lui, passe — la diffusion en cours lui en donne le
        // droit.
        //
        // Il anime exactement pareil. Ce qu'il perd, c'est la protection contre
        // la pression mémoire, et donc peut-être sa vie pendant que l'écran est
        // éteint. C'est le prix de ne pas avoir de fenêtre légale ici ; le
        // premier tap, lui, en ouvre une et le promeut pour de bon.
        intent.putExtra(WidgetBurstService.EXTRA_PROMOTED, false)
        runCatching { context.startService(intent) }
            .onFailure { Log.w(WidgetRender.TAG, "rafale non démarrée pour $widgetId", it) }
    }

    /**
     * L'intention qui transmet un tap. **Une par widget** : c'est le seul moyen
     * pour le service de savoir lequel a été touché.
     *
     * L'identité d'un `PendingIntent` ignore les extras — deux hublots auraient
     * donc partagé la même, et le second aurait écrasé l'identifiant du premier.
     * D'où l'`Uri` : elle, elle compte.
     */
    private fun tapIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, javaClass)
            .setAction(TAP_ACTION)
            .setData(Uri.parse("haloglyph://widget/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ---------- rendu ----------

    /** Rafraîchit tous les hublots de ce fournisseur. À appeler sur changement de réglage. */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (id in manager.getAppWidgetIds(ComponentName(context, javaClass))) {
            pushIdle(context, manager, id)
        }
    }

    private fun pushIdle(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val toy = toyOf(context, widgetId) ?: return
        val frame = Frame(spec)
        toy.renderIdle(context, widgetId, frame)
        WidgetRender.push(
            context = context,
            manager = manager,
            widgetId = widgetId,
            brightness = frame.toBrightness(),
            style = WidgetConfig.style(context, widgetId),
            sidePx = WidgetRender.sidePx(context, manager, widgetId),
            tap = tapIntent(context, widgetId),
        )
    }

    /**
     * Le toy que ce hublot affiche. Celui qui est réglé, et pas un autre.
     *
     * Il y a eu ici un saut : un toy dont [WidgetToy.isReady] était faux était
     * passé, au motif qu'il ne pouvait rien montrer de vrai. C'était faux, et
     * c'était une invention du hublot — **le toy de la matrice, lui, s'affiche
     * quand même**, et dit ce qui lui manque : `MIC` sur une comète qui tourne
     * quand le micro est absent, `---` quand il est coupé. Un hublot qui saute
     * Sono ne montre pas la vérité, il montre un autre toy.
     *
     * [WidgetToy.isReady] sert toujours, mais seulement là où il a du sens :
     * décider s'il faut demander une capability au système avant d'animer.
     */
    private fun toyOf(context: Context, widgetId: Int): WidgetToy? {
        val rotation = WidgetConfig.rotation(context, widgetId, toys.map { it.id })
        val current = WidgetConfig.current(context, widgetId, rotation) ?: return toys.firstOrNull()
        return toys.firstOrNull { it.id == current }
    }

    /** Les hublots posés, les deux formats confondus. */
    private fun postedIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(ComponentName(context, javaClass))
    }

    internal companion object {

        /**
         * Combien de hublots on accepte de porter, tous formats confondus.
         *
         * Ce n'est pas une limite de produit, c'est une limite de **coût**. Un
         * hublot qui anime rend une image et la fait traverser le binder ; deux
         * hublots qui animent ensemble se partagent le même tuyau, et chacun
         * ajoute sa part de réveils et de bitmaps. Au-delà de trois, ce que
         * chacun gagne à exister ne couvre plus ce qu'il coûte à tous.
         *
         * Susceptible de changer : c'est un curseur, pas une vérité. Le refus se
         * lit dans [WidgetConfigActivity], seul endroit d'où l'on puisse dire non
         * au launcher.
         */
        const val MAX_WIDGETS = 3

        /** Combien de hublots sont posés, les deux formats confondus. */
        fun postedCount(context: Context): Int {
            val manager = AppWidgetManager.getInstance(context)
            return manager.installedProviders
                .filter { it.provider.packageName == context.packageName }
                .sumOf { manager.getAppWidgetIds(it.provider).size }
        }

        /**
         * Une seule action pour tous les hublots : c'est l'`Uri` du
         * `PendingIntent` qui distingue les widgets, pas l'action.
         */
        const val TAP_ACTION = "red.suns.haloglyph.WIDGET_TAP"

        /**
         * Un fournisseur, monté depuis son nom de classe.
         *
         * C'est ce dont [WidgetConfigActivity] et [WidgetBurstService] ont besoin :
         * le premier ne tient qu'un `appWidgetId`, dont il tire un
         * `ComponentName` ; le second reçoit le nom en extra. Les deux veulent le
         * catalogue de toys du fournisseur. L'instance créée ici n'est **pas**
         * celle que le système diffuse — un `AppWidgetProvider` est un receiver,
         * il n'en existe aucune de stable — mais les toys qu'elle expose, eux,
         * sont bien les instances partagées du processus.
         */
        fun instantiate(className: String): MatrixWidgetProvider? = runCatching {
            Class.forName(className).getDeclaredConstructor().newInstance()
                as? MatrixWidgetProvider
        }.getOrNull()
    }
}
