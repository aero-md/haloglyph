package red.suns.haloglyph.core.widget

import android.content.Context
import androidx.annotation.StringRes
import red.suns.haloglyph.core.matrix.Frame

/**
 * Un toy, vu par le widget d'écran d'accueil.
 *
 * ## Un seul widget, tous les toys
 *
 * Il y a eu un fournisseur par toy et par format — quatre receivers pour deux
 * toys, et un cinquième à chaque toy ajouté. C'est fini : le pack ne pose plus
 * qu'un **hublot générique**, qui porte n'importe lequel des toys et se change
 * au double tap. Un écran d'accueil ne veut pas choisir entre un dé et un
 * compteur au moment de poser le widget ; il veut un hublot, et décider après.
 *
 * Ce que le toy garde pour lui — son moteur, son renderer, ses préférences — ne
 * bouge pas. Ce qu'il expose ici est le strict minimum pour qu'un hublot sache
 * le dessiner et lui transmettre un doigt.
 *
 * ## Où vit une implémentation
 *
 * **Dans le module du toy**, à côté de son service Glyph. Le module `app` les
 * assemble et les donne à [MatrixWidgetProvider], exactement comme il assemble
 * `ToyCatalog` : il n'y a pas de registre global, parce qu'il n'existe pas de
 * catalogue commun à plusieurs apps.
 *
 * ## Une instance vit longtemps
 *
 * Contrairement à un `AppWidgetProvider`, qui est recréé à chaque diffusion,
 * l'instance d'un [WidgetToy] est tenue par [WidgetBurstService] tant qu'une
 * rafale tourne. Un renderer coûteux à construire peut donc être un `by lazy`
 * de l'implémentation, et il le sera d'un tap à l'autre.
 */
interface WidgetToy {

    /**
     * Identifiant stable. C'est celui du toy — `LapseConfig.TOY_ID` et ses
     * pareils — parce que c'est lui qui est écrit dans les réglages du widget et
     * qui doit survivre à une mise à jour de l'app.
     */
    val id: String

    /** Le nom affiché : dans l'onglet de rotation, et dans la notification de rafale. */
    @get:StringRes
    val nameRes: Int

    /**
     * Les variantes que ce toy propose dans les réglages d'un hublot — vide pour
     * qui n'en a qu'une, et c'est le cas de presque tous.
     *
     * ## Ce que ça répare
     *
     * Sur la matrice, le dé change de solide à l'appui long et se lance en
     * secouant le téléphone. Un hublot n'a ni l'un ni l'autre : le launcher garde
     * l'appui long pour lui, et on ne secoue pas un écran d'accueil. Le solide
     * n'avait donc aucun moyen d'être choisi depuis un widget, et deux hublots
     * posés côte à côte ne pouvaient de toute façon pas porter deux dés
     * différents.
     *
     * D'où ces variantes : la rangée de segments de l'onglet des toys n'est plus
     * un interrupteur mais **la liste de ce que ce toy sait être**, `OFF` en tête.
     * Le hublot s'écarte de la matrice sur ce point, et c'est assumé — c'est la
     * surface qui décide, pas le toy.
     *
     * L'ordre est celui de l'affichage. La valeur retenue est rangée sous le
     * hublot, donc deux hublots n'ont jamais la même par accident — voir
     * [WidgetConfig.variant].
     */
    val variants: List<WidgetVariant> get() = emptyList()

    /**
     * La variante d'un hublot **qui n'a rien choisi**, et rien d'autre.
     *
     * Par défaut la première de la liste. Un toy qui a un repli plus juste le dit
     * ici : le dé part sur le solide qu'on a en main sur la matrice, si bien qu'un
     * hublot fraîchement posé montre ce que montre le téléphone — et ne s'en
     * détache qu'au premier réglage.
     */
    fun defaultVariant(context: Context): String? = variants.firstOrNull()?.key

    /**
     * Ce que la rafale de ce toy réclame au système, ou `null` — le cas de tous
     * ceux qui se contentent de dessiner. Voir [BurstNeeds].
     */
    val needs: BurstNeeds? get() = null

    /**
     * Ce toy lit-il des **capteurs continus** pendant sa rafale ?
     *
     * ## Ce que ça répare, et pourquoi ça ne se voyait pas
     *
     * Depuis Android 9, un processus en arrière-plan **ne reçoit plus les
     * événements des capteurs à report continu** — accéléromètre, gyroscope,
     * magnétomètre, vecteur de rotation. Pas d'erreur, pas d'exception :
     * `registerListener` rend `true` et plus rien n'arrive. C'est exactement le
     * piège du micro de Sono, transposé aux capteurs, et il est plus sournois
     * parce qu'aucune permission n'est en jeu — on n'a donc aucune raison de se
     * méfier.
     *
     * Or un tap sur un hublot démarrait jusqu'ici un service **ordinaire** pour
     * tout toy qui ne réclamait pas de capability : moins cher, aucune
     * notification, et parfaitement suffisant tant que les toys ne faisaient que
     * dessiner. Un niveau à bulle dans ce service-là serait resté vide sans rien
     * dire.
     *
     * La boucle continue, elle, n'avait pas le problème : elle se promeut déjà en
     * `specialUse` pour survivre à la pression mémoire. Le défaut n'aurait donc
     * frappé que les hublots réglés au tap — la moitié des cas, en silence.
     *
     * ## Ce que ça ne dit pas
     *
     * Ni une permission, ni une capability : [needs] reste `null` pour un toy qui
     * se contente de lire la pose de l'appareil. La seule chose demandée ici est
     * que le processus **ne soit pas en arrière-plan** le temps de la rafale, ce
     * que le type `shortService` suffit à obtenir. Et ça ne touche pas à
     * [loopable] : un instrument qui lit un capteur est justement ce qui a le plus
     * de sens en boucle.
     */
    val sensing: Boolean get() = false

    /**
     * Peut-il tourner **sans fin**, dans un hublot réglé en boucle continue ?
     *
     * Par défaut : oui, sauf s'il réclame une capability. Ce n'est pas une
     * exception faite à Sono, c'est la même règle vue de l'autre côté — un toy
     * qui a besoin du micro pour dire quelque chose de vrai le tiendrait ouvert
     * tout l'après-midi, et une matrice de vingt-cinq pixels ne vaut pas ça.
     * Cinq secondes après un tap, c'est un geste ; en continu, c'est une écoute.
     *
     * L'autre raison de répondre non n'a rien à voir avec le coût : **certaines
     * animations sont des événements**. Un jet de dé culbute, se pose, et on lit
     * la face — le faire recommencer indéfiniment ne donne pas un dé animé, ça
     * donne un dé qui ne s'arrête jamais sur rien. Le hublot en continu affiche
     * alors le repos et attend le doigt, ce qui est son état juste.
     *
     * Un hublot en boucle qui tombe sur un toy non bouclable ne boucle pas : il
     * affiche son repos et attend qu'on le tape, comme avant.
     */
    val loopable: Boolean get() = needs == null

    /**
     * A-t-il ce qu'il faut pour que [needs] vaille la peine d'être demandé ?
     *
     * **Ce n'est pas « peut-il s'afficher ».** Un toy répond `false` et s'affiche
     * quand même : Sono sans autorisation micro montre `MIC` sur une comète qui
     * tourne, exactement comme sur la matrice, parce que c'est la vérité et que
     * c'est ce que le toy dit de lui-même. Le sauter pour montrer le voisin
     * serait la seule façon de mentir ici.
     *
     * Ce que ça décide, et rien d'autre : faut-il démarrer l'animation par un
     * service de premier plan pour réclamer une capability. Demander le micro
     * quand l'autorisation manque n'obtiendrait rien et afficherait une
     * notification pour rien.
     */
    fun isReady(context: Context): Boolean = true

    /**
     * La frame de repos : ce que **ce hublot-là** montre 99 % du temps.
     *
     * [widgetId] désigne le hublot, et il est là pour une raison précise : un toy
     * peut avoir un état **par hublot**. Lapse s'en sert pour que deux hublots
     * affichent deux lapses différents — le tap faisait défiler tout le monde
     * tant que le toy ne savait pas d'où venait le doigt. Un toy sans état par
     * instance ignore ce paramètre, et c'est le cas des deux autres.
     *
     * Voir [WidgetConfig.toyInt] pour ranger cet état-là.
     */
    fun renderIdle(context: Context, widgetId: Int, frame: Frame)

    /**
     * Le tap simple, avant la rafale : c'est ici qu'on relance un dé ou qu'on
     * passe au lapse suivant.
     *
     * Appelé **une fois par tap**, sur le hublot qui a reçu le doigt et sur lui
     * seul. Ce qu'un toy écrit ici décide de qui bouge : une préférence de toy
     * change tous les hublots et la matrice avec — c'est ce que fait un dé,
     * puisqu'il n'y a qu'un dé — tandis qu'un état rangé sous [widgetId] ne
     * change que celui-ci.
     *
     * ## Taper une deuxième fois n'est pas taper une première fois
     *
     * [live] le dit, et il fallait bien que quelqu'un le dise : sur la matrice,
     * Sono change de mode à l'appui long, que le launcher garde pour lui. Le seul
     * geste qui reste à un hublot est le tap, et il sert déjà à réveiller le
     * micro — mais **une fois qu'il écoute**, il n'a plus rien à réveiller. C'est
     * là que le mode suivant prend sa place, et nulle part ailleurs.
     *
     * Le dé, lui, ignore la distinction : retaper pendant un jet relance le jet,
     * ce qui est la seule chose qu'on puisse vouloir d'un dé qui roule.
     *
     * @param live ce hublot animait déjà quand le doigt est arrivé.
     * @return faut-il (re)partir en rafale. `false` **laisse tourner** celle qui
     * court — un mode qui change ne rouvre pas le micro et ne rallonge pas la
     * fenêtre d'écoute. Sans effet quand rien n'animait : il n'y a rien à laisser
     * tourner, et le tap est alors sans suite.
     */
    fun onTap(context: Context, widgetId: Int, live: Boolean): Boolean = true

    /**
     * Le double tap vient d'amener ce toy sur ce hublot : y a-t-il une séquence à
     * lancer, et faut-il la préparer ?
     *
     * ## Ce que ça répare
     *
     * Changer de toy affichait son **repos**, et il fallait retaper pour le voir
     * faire quelque chose. Deux gestes pour arriver quelque part, c'est un de
     * trop : on ne fait pas défiler la rotation pour contempler des `TAP`, on la
     * fait défiler pour atteindre un toy. Il part donc tout seul, comme si le
     * doigt avait retapé derrière.
     *
     * ## Pourquoi ce n'est pas simplement [onTap]
     *
     * Parce que c'en est un par défaut — c'est ce qui donne un jet de dé à
     * l'arrivée, et le dé n'anime rien sans lui — mais que le tap de certains toys
     * fait **autre chose que réveiller**. Lapse avance d'un lapse au tap : arriver
     * dessus n'a aucune raison de faire défiler ce qu'on vient voir. Ceux-là
     * redéfinissent la méthode et se contentent de `true`.
     *
     * Un toy qui réclame une capability est appelé ici comme les autres. Il ne
     * l'a pas été longtemps, au motif que le service avait été démarré pour le toy
     * précédent et que l'exemption ne se rattrape pas après coup — c'était vrai,
     * et c'était la question posée trop tard : le receiver demande maintenant pour
     * le toy affiché **et pour le suivant**, tant que le geste n'est pas tranché.
     * Voir `MatrixWidgetProvider.reachable`.
     *
     * @return faut-il partir en rafale. `false` laisse le hublot sur son repos.
     */
    fun onArrive(context: Context, widgetId: Int): Boolean = onTap(context, widgetId, live = false)

    /**
     * Une image de rafale.
     *
     * @return `true` tant que l'animation continue. `false` l'arrête avant la
     * borne — une animation courte n'a pas à occuper trente secondes. Par
     * défaut : pas d'animation, le tap ne fait que rafraîchir le repos.
     */
    fun renderBurst(
        context: Context,
        widgetId: Int,
        frame: Frame,
        elapsedSeconds: Double,
    ): Boolean = false

    /**
     * Appelé à l'entrée et à la sortie de rafale, sur le fil de la rafale.
     *
     * C'est la place d'une ressource qui s'ouvre et se ferme — le micro de Sono.
     * [onBurstEnd] est appelé même quand la rafale s'interrompt, y compris si
     * [renderBurst] a levé : un jeton pris est toujours rendu.
     */
    fun onBurstStart(context: Context, widgetId: Int) {}

    fun onBurstEnd(context: Context, widgetId: Int) {}

    /** Borne dure de la rafale de ce toy. Voir [WidgetBurstService]. */
    val burstDurationMs: Long get() = DEFAULT_BURST_MS

    companion object {
        /**
         * Trente secondes.
         *
         * Il y en a eu cinq, et ce n'était pas un choix : c'était la borne du
         * `BroadcastReceiver`, que le système tue vers dix secondes. L'animation
         * a quitté le receiver, la borne est partie avec lui.
         *
         * Ce qui reste vrai, c'est qu'un toy peut **finir avant**, et deux des
         * trois le font : `renderBurst` rend `false` quand il n'y a plus rien à
         * montrer, et le dé est posé et lu au bout de 3,15 s. Cette durée-ci n'est
         * donc pas la durée d'une animation, c'est la limite au-delà de laquelle
         * on cesse de croire celui qui dit qu'il a encore quelque chose à dire.
         */
        const val DEFAULT_BURST_MS = 30_000L
    }
}

/**
 * Une des formes qu'un toy peut prendre dans un hublot. Voir [WidgetToy.variants].
 *
 * @param key ce qui est **persisté**, donc ce qui ne change pas : la clé du toy
 * lui-même — `d20` pour le dé — et pas un rang dans une liste qu'une version
 * suivante réordonnerait.
 * @param label ce qui s'affiche sur le segment. Une **valeur**, pas une phrase :
 * `6`, `10`, `20`. C'est pour ça que ce n'est pas une ressource de chaînes — un
 * nombre ne se traduit pas, et un segment n'a de toute façon la place de rien
 * d'autre.
 */
data class WidgetVariant(val key: String, val label: String)

/**
 * Ce qu'un toy réclame au système pour la durée de sa rafale.
 *
 * ## Pourquoi ça existe
 *
 * Un widget qui veut le micro ne peut pas simplement l'ouvrir : depuis Android
 * 14, une permission « pendant l'utilisation » n'est accordée à un service de
 * premier plan que s'il démarre depuis une surface qui figure dans une liste
 * fermée d'exemptions. Le tap sur un widget **y figure** — « le service démarre
 * par interaction avec des widgets d'app » — mais l'exemption se joue au moment
 * du démarrage, dans la diffusion du tap, pas plus tard.
 *
 * D'où cette déclaration : elle est lue **dans le receiver**, avant même de
 * savoir si le geste est un tap ou un double tap, pour choisir entre
 * `startForegroundService` et un simple `startService`. Un toy qui ne déclare
 * rien démarre un service ordinaire — pas de notification, pas de type, rien à
 * justifier.
 *
 * @param serviceType le `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` à demander.
 * @param permission la permission d'exécution sans laquelle la promotion serait
 * un mensonge. Vérifiée avant de tenter quoi que ce soit.
 */
data class BurstNeeds(
    val serviceType: Int,
    val permission: String,
)
