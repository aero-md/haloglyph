package red.suns.haloglyph.core.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec

/**
 * Ce qui anime les hublots : la fenêtre de double tap, la rafale, et la boucle.
 *
 * ## Pourquoi un service, et pas le receiver
 *
 * L'animation tournait dans `onReceive`, sous `goAsync()`. Trois symptômes en
 * sortaient, et c'était un seul défaut : **Android sérialise la livraison des
 * diffusions**. Tant qu'un receiver n'a pas rendu son `PendingResult`, la file
 * ne repart pas. Un hublot qui animait cinq secondes empêchait donc tout autre
 * hublot d'animer, mettait en attente les taps qu'on lui donnait entre-temps, et
 * les rejouait tous à la suite quand il avait fini. Sortir la boucle du receiver
 * règle les trois d'un coup.
 *
 * Un service apporte deux choses de plus : il n'a pas de borne à dix secondes —
 * celle au-delà de laquelle le système tue un receiver, et d'où venaient les
 * cinq secondes de rafale d'autrefois — et il peut se promouvoir au premier
 * plan, ce dont un toy qui ouvre le micro a besoin.
 *
 * ## Deux régimes
 *
 * - **la rafale** : déclenchée par un tap, bornée par [WidgetToy.burstDurationMs]
 *   — trente secondes par défaut, cinq pour un toy qui écoute, et le toy peut
 *   finir avant en rendant `false` ;
 * - **la boucle** : un hublot réglé en continu anime sans fin, tant que l'écran
 *   est allumé. Elle coûte un service qui vit en permanence, d'où le réglage :
 *   personne ne l'a demandée en posant un widget.
 *
 * Les deux passent par la même table et le même battement. Une rafale posée sur
 * un hublot en boucle la remplace le temps qu'elle dure, puis la boucle reprend
 * — c'est ce qui permet de relancer un dé sur un hublot qui tourne déjà.
 *
 * ## L'écran éteint, on ne dessine pas
 *
 * Un widget qu'on ne regarde pas n'a aucune raison d'être repeint vingt-cinq
 * fois par seconde. La boucle se suspend donc à l'extinction et reprend à
 * l'allumage ; le service, lui, reste vivant, parce qu'il n'y a pas de diffusion
 * d'allumage d'écran qu'un receiver de manifeste puisse recevoir — il faut être
 * là pour l'entendre.
 *
 * ## Une boucle pour tous
 *
 * Un seul fil, un seul battement : à chaque échéance, chaque hublot animé est
 * rendu puis poussé. Deux hublots qui animent ensemble se partagent donc le
 * budget binder au lieu de se bloquer — ce qui est honnête, c'est bien le même
 * tuyau.
 *
 * Le battement est calé sur des **échéances absolues** ([Handler.postAtTime]) et
 * non sur un sommeil fixe après le travail. L'ancienne boucle dormait 60 ms
 * *après* avoir rendu et poussé : la cadence réelle était donc 60 ms plus le
 * temps du binder, soit un bon tiers de moins que les seize images par seconde
 * annoncées. Une échéance ratée n'est jamais rattrapée — on repart de maintenant
 * — parce que rattraper reviendrait à pousser deux images d'affilée dans un
 * tuyau qui vient justement de dire qu'il était plein.
 *
 * ## Les sous-classes
 *
 * Elles n'ont rien à fournir. Elles existent pour être déclarées dans le
 * manifeste de `app` avec les types de premier plan que les toys embarqués
 * réclament — ce que `core:widget`, qui ne sait pas lesquels sont embarqués, ne
 * peut pas faire.
 */
abstract class WidgetBurstService : Service() {

    /** Le fil de l'animation. Tout l'état ci-dessous n'est touché que par lui. */
    private lateinit var loop: HandlerThread
    private lateinit var work: Handler

    /** Pour repasser sur le fil principal : seul lui peut arrêter le service. */
    private val main = Handler(Looper.getMainLooper())

    /** Taps comptés dans la fenêtre courante, par hublot. */
    private val taps = HashMap<Int, Int>()

    /** Ce qui anime, rafales et boucles confondues, par hublot. */
    private val running = LinkedHashMap<Int, Anim>()

    /**
     * Le catalogue, relevé sur le premier fournisseur qui nous parle.
     *
     * Les deux formats de hublot portent la même liste — c'est `app` qui
     * l'assemble, une fois — donc n'importe lequel fait l'affaire, et le garder
     * ici évite de trimballer un fournisseur dans chaque méthode.
     */
    private var toys: List<WidgetToy> = emptyList()
    private var spec: MatrixSpec = MatrixSpec.Phone3

    private var beat = 0L

    /**
     * Une boucle est en vol.
     *
     * Sans ce drapeau, la course est réelle et tient en trois lignes : le tick se
     * replanifie à `beat + 40 ms`, puis un tap vide [running] et le remplit — et
     * la branche « c'est la première animation, je démarre le battement »
     * postait un second tick alors que le premier était toujours en file. Deux
     * boucles sur le même fil, donc deux images par échéance dans un tuyau qui
     * n'en tient qu'une.
     */
    private var beating = false

    /**
     * Les toys dont le réveil est **en vol** : lancé, pas encore démarré.
     *
     * Un toy qui mesure quelque chose fait un aller-retour par le fil principal
     * avant d'ouvrir ses capteurs — voir [wake]. Pendant ce trajet, [running] est
     * vide alors que quelque chose arrive, et deux décisions se trompaient :
     *
     * - [idleIfDone] concluait qu'il n'y avait plus rien à faire et arrêtait le
     *   service. La rafale tombait ensuite dans un service mort, et le hublot
     *   restait figé sur sa dernière image — c'est ce que faisait un switch répété.
     * - [recountMic] ne voyait aucun toy à capability et laissait retirer le type
     *   `microphone`, juste avant que la capture s'ouvre. Elle n'aurait rendu que
     *   des zéros.
     *
     * Ne vit que sur le fil de l'animation, comme [running] : le fil principal n'en
     * fait que le trajet, sans le lire.
     */
    private val arming = mutableListOf<WidgetToy>()

    private var screenOn = true
    private var screenWatch: BroadcastReceiver? = null

    private var lastStartId = 0
    private var foreground = false

    /** Les types déjà demandés. Redemander le même est inutile ; en changer, non. */
    private var fgTypes = 0

    /**
     * Une animation en cours tient une capability — le micro de Sono.
     *
     * Lu depuis le fil principal par [desiredTypes], écrit depuis le fil de
     * l'animation : `@Volatile` pour que la première voie voie ce que la seconde
     * a posé.
     */
    @Volatile
    private var micHeld = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        loop = HandlerThread("haloglyph-widget-burst").also { it.start() }
        work = Handler(loop.looper)
        watchScreen()
    }

    override fun onDestroy() {
        screenWatch?.let { runCatching { unregisterReceiver(it) } }
        screenWatch = null
        // Ce qui animait doit rendre ses jetons avant que le fil disparaisse : un
        // micro tenu par un service mort ne se referme jamais.
        for (anim in running.values) runCatching { anim.toy.onBurstEnd(this, anim.widgetId) }
        running.clear()
        arming.clear()
        loop.quitSafely()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId

        // `intent` est **null** quand le système nous relance de lui-même après
        // nous avoir tués — c'est tout l'intérêt de `START_STICKY`, et c'est le
        // seul filet qui rattrape une boucle continue dont le service n'a pas
        // survécu à une nuit d'écran éteint. On retrouve alors le fournisseur là
        // où il a été noté, parce qu'on ne peut pas le deviner.
        val providerName = intent?.getStringExtra(EXTRA_PROVIDER) ?: WidgetConfig.host(this)
        val provider = providerName?.let { MatrixWidgetProvider.instantiate(it) }
        if (provider == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        toys = provider.widgetToys
        spec = provider.matrixSpec
        WidgetConfig.setHost(this, providerName)

        if (intent == null) {
            Log.i(WidgetRender.TAG, "relancé par le système, on reprend les boucles")
            armForeground(AppWidgetManager.INVALID_APPWIDGET_ID)
            work.post { sync() }
            return stickiness()
        }

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )

        // Avant toute chose : si on a été démarré par `startForegroundService`,
        // le système attend une promotion sous quelques secondes et tue le
        // service sinon. Le receiver le dit plutôt que de nous laisser le
        // deviner — voir `MatrixWidgetProvider.promote`.
        if (intent.getBooleanExtra(EXTRA_PROMOTED, false)) armForeground(widgetId)

        when (intent.action) {
            ACTION_TAP -> {
                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                work.post { onTapped(widgetId) }
            }

            // Idempotent : on relit qui doit boucler et on se met d'accord avec.
            // C'est ce qu'envoient l'écran de réglages, l'arrivée d'un hublot et
            // le réveil du système après un redémarrage.
            ACTION_SYNC -> work.post { sync() }

            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return stickiness()
    }

    /**
     * Faut-il que le système nous relance s'il nous tue ?
     *
     * Oui, et seulement, quand une boucle continue est réglée : c'est la seule
     * chose du hublot qui doive survivre à une nuit d'écran éteint, et le seul
     * filet qui la rattrape quand la pression mémoire a eu raison du service.
     * Une rafale, elle, n'a rien à reprendre — personne n'a regardé sa fin, et la
     * relancer trente secondes plus tard n'animerait rien pour personne.
     */
    private fun stickiness(): Int =
        if (wantedLoops().isEmpty()) START_NOT_STICKY else START_STICKY

    // ---------- la fenêtre de double tap ----------

    private fun onTapped(widgetId: Int) {
        val count = (taps[widgetId] ?: 0) + 1
        taps[widgetId] = count
        // Seul le premier tap arme l'échéance ; les suivants ne font que compter.
        if (count == 1) work.postDelayed({ resolve(widgetId) }, TAP_WINDOW_MS)
    }

    /**
     * La fenêtre est close : on sait enfin ce qu'on nous a demandé.
     *
     * Trois taps ou plus valent un double tap. Personne ne tape trois fois pour
     * avancer de deux toys — c'est un double tap dont un doigt a rebondi, et le
     * traiter comme « avancer deux fois » ferait sauter un toy sans raison
     * lisible.
     */
    private fun resolve(widgetId: Int) {
        val count = taps.remove(widgetId) ?: return
        if (count >= 2) switchToy(widgetId) else interact(widgetId)
    }

    /**
     * Double tap : toy suivant, **et il part tout seul**.
     *
     * Il n'y a longtemps eu aucune rafale à l'arrivée, au motif que changer de toy
     * n'est pas interagir avec lui. C'était une distinction de code, pas d'usage :
     * personne ne fait défiler la rotation pour contempler des `TAP`, on la fait
     * défiler pour atteindre un toy — et il fallait alors retaper pour le voir
     * faire quoi que ce soit. Deux gestes pour arriver quelque part, dont un qui
     * ne servait qu'à réparer l'autre.
     *
     * **Sono n'est plus une exception**, et il ne l'a jamais été pour une raison de
     * plateforme. Le service était démarré ordinaire quand le toy affiché ne
     * réclamait rien, et le micro ne se rattrape pas après coup — c'était la
     * question posée trop tard, pas une interdiction. Le receiver demande
     * maintenant pour le toy affiché **et pour le suivant**, tant que le geste
     * n'est pas tranché ; voir `MatrixWidgetProvider.reachable`.
     *
     * ## Le repos ne clignote pas au passage
     *
     * On repeignait le repos du nouveau toy avant de le lancer, et il apparaissait
     * un quart de seconde avant d'être remplacé — un `TAP` qui passe est pire que
     * pas d'image du tout. Le repos n'est donc posé que si **rien ne va animer** :
     * dans le cas contraire le hublot garde sa dernière image le temps que la
     * première du nouveau toy arrive, ce qui est quelques dizaines de
     * millisecondes.
     *
     * ## Le réveil passe **avant** la synchronisation
     *
     * Et ce n'est pas cosmétique : [sync] finit par [idleIfDone], qui arrête le
     * service quand plus rien n'anime. Appelé avant le réveil, il voyait une table
     * vide, postait l'arrêt sur le fil principal, et le service mourait sous la
     * rafale qu'on venait de lancer — `stopSelf(lastStartId)` ne se défend que
     * contre un `onStartCommand` plus récent, et il n'y en a pas. Le hublot restait
     * figé sur sa dernière image, et c'est exactement ce que faisait un switch
     * répété : une fois sur deux, on tuait ce qu'on venait d'allumer.
     *
     * Sur un hublot en boucle, [sync] passe juste après et voit la rafale dans la
     * table : il ne remet pas de boucle par-dessus, et la reprendra quand la rafale
     * aura fini, par le chemin habituel.
     */
    private fun switchToy(widgetId: Int) {
        val rotation = WidgetConfig.rotation(this, widgetId, toys.map { it.id })
        val before = WidgetConfig.current(this, widgetId, rotation)
        val after = WidgetConfig.advance(this, widgetId, rotation)

        // Un seul toy dans la rotation : le double tap n'a nulle part où aller.
        // Ne rien faire est alors la bonne réponse — repeindre pour afficher la
        // même chose se voyait, parce que repeindre se voit toujours un peu.
        if (after == before) {
            idleIfDone()
            return
        }

        stop(widgetId)
        val woken = wake(widgetId)
        sync()
        if (!woken) pushIdle(AppWidgetManager.getInstance(this), widgetId)
    }

    /**
     * Le toy qui vient d'arriver lance sa séquence, sans qu'on ait à le retaper.
     *
     * Deux raisons de ne rien faire : quelque chose anime déjà sur ce hublot, ou le
     * toy répond lui-même qu'il n'a pas de séquence à lancer.
     *
     * ## La promotion passe devant, et pas à côté
     *
     * Un toy qui lit des capteurs ou qui ouvre le micro a besoin que le service
     * **porte déjà** le bon type quand il s'ouvre : Android ne livre pas les
     * capteurs continus à un processus en arrière-plan, et une capture démarrée
     * avant que le type `microphone` soit posé ne rend que des zéros. Or
     * [armForeground] s'exécute sur le fil principal et [start] sur celui de
     * l'animation : les poster tous les deux ne dit rien de leur ordre.
     *
     * D'où l'aller-retour — le fil principal promeut, puis rend la main au fil de
     * l'animation pour ouvrir. Ça coûte deux sauts de fil sur un geste qui en vaut
     * la peine, et ça ne concerne que les toys qui mesurent quelque chose.
     *
     * Le temps de cet aller-retour, **rien n'anime et rien ne doit s'arrêter** :
     * [arming] tient la porte, sinon [idleIfDone] trouve la table vide et coupe le
     * service dans l'intervalle. Et au retour on revérifie le toy : deux double
     * taps rapprochés laissent deux réveils en vol, et le premier n'a plus rien à
     * démarrer.
     *
     * @return quelque chose va-t-il animer ? `true` vaut aussi pour un démarrage
     * différé — l'appelant ne doit pas repeindre le repos entre-temps.
     */
    private fun wake(widgetId: Int): Boolean {
        if (widgetId in running) return true
        val toy = currentToy(widgetId) ?: return false

        val go = runCatching { toy.onArrive(this, widgetId) }
            .onFailure { Log.w(WidgetRender.TAG, "arrivée refusée par ${toy.id}", it) }
            .getOrDefault(false)
        if (!go) return false

        if (!toy.sensing && toy.needs == null) {
            start(widgetId, toy, endless = false)
            return true
        }

        arming += toy
        recountMic()
        main.post {
            armForeground(widgetId)
            work.post {
                arming.remove(toy)
                val still = currentToy(widgetId)?.id == toy.id
                if (still && widgetId !in running) {
                    start(widgetId, toy, endless = false)
                } else {
                    recountMic()
                    idleIfDone()
                }
            }
        }
        return true
    }

    /**
     * Tap simple : le toy affiché prend la main, puis la rafale.
     *
     * L'ordre a changé — le toy est prévenu **avant** qu'on ferme ce qui tournait,
     * et il apprend qu'il tournait. Les deux vont ensemble : un toy qui répond
     * « laisse tourner » ne peut le dire que si rien n'est encore fermé, et il ne
     * peut le vouloir que s'il sait qu'il y avait quelque chose. Voir
     * [WidgetToy.onTap] — Sono s'en sert pour changer de mode sans rouvrir le
     * micro, et il est le seul.
     */
    private fun interact(widgetId: Int) {
        val toy = currentToy(widgetId)
        if (toy == null) {
            idleIfDone()
            return
        }
        val live = widgetId in running
        val again = runCatching { toy.onTap(this, widgetId, live) }
            .onFailure { Log.w(WidgetRender.TAG, "tap refusé par ${toy.id}", it) }
            .getOrDefault(true)

        // « Laisse tourner » : l'animation garde son échéance, et l'image
        // suivante — dans quarante millisecondes — montre déjà ce qui a changé.
        if (!again && live) return

        stop(widgetId)
        if (!again) {
            idleIfDone()
            return
        }
        start(widgetId, toy, endless = false)
    }

    // ---------- la boucle continue ----------

    /**
     * Remet les boucles d'accord avec les réglages.
     *
     * Idempotent et sans mémoire : on relit qui doit boucler, on ferme ce qui ne
     * doit plus, on ouvre ce qui manque. C'est ce qui permet de l'appeler depuis
     * n'importe où — un réglage changé, un hublot ajouté, un toy changé au double
     * tap — sans jamais se demander ce qui a bougé depuis la dernière fois.
     *
     * Les rafales en cours ne sont pas interrompues : un hublot qui anime après
     * un tap reprendra sa boucle tout seul, à la fin, par le même chemin. Elles
     * sont en revanche **relancées** si leur apparence a changé sous elles.
     */
    private fun sync() {
        val wanted = wantedLoops()
        val manager = AppWidgetManager.getInstance(this)

        // Ce qui bouclait et ne doit plus : le réglage a été coupé, le hublot
        // retiré, ou le double tap l'a posé sur un toy qui ne boucle pas.
        val gone = running.filterValues { it.endless }.keys.filter { it !in wanted }
        for (id in gone) stop(id)
        if (gone.isNotEmpty()) refreshIdle()

        // Ce qui anime avec une apparence périmée : on relance sur la bonne.
        // Sans ça, changer la trame d'un hublot en boucle ne se voyait qu'après
        // l'avoir tapé.
        for (anim in running.values.toList()) {
            if (!stale(anim, manager)) continue
            val toy = anim.toy
            val endless = anim.endless
            stop(anim.widgetId)
            start(anim.widgetId, toy, endless)
        }

        for (id in wanted) {
            // Une rafale en cours a la priorité : elle finira, et le passage
            // suivant remettra la boucle.
            if (id in running) continue
            val toy = currentToy(id) ?: continue
            start(id, toy, endless = true)
        }

        // Le type de premier plan dépend de ce qui tourne, et vient peut-être de
        // changer : une boucle qui apparaît doit faire tomber `shortService`.
        main.post { armForeground(AppWidgetManager.INVALID_APPWIDGET_ID) }
        idleIfDone()
    }

    /**
     * Une animation doit-elle repartir de zéro ?
     *
     * L'apparence et la définition sont relevées **une fois** au démarrage d'une
     * animation — c'est ce qui évite un verrou de préférences par image — mais
     * une boucle vit indéfiniment, et un réglage changé pendant qu'elle tourne ne
     * l'atteignait jamais. Changer la trame d'un hublot en boucle ne se voyait
     * donc qu'après l'avoir tapé, ce qui est la définition d'un réglage qui ne
     * s'applique pas.
     */
    private fun stale(anim: Anim, manager: AppWidgetManager): Boolean =
        anim.style != WidgetConfig.style(this, anim.widgetId) ||
            anim.sidePx != WidgetRender.sidePx(this, manager, anim.widgetId)

    /** Les hublots qui doivent boucler : réglés pour, et sur un toy qui le peut. */
    private fun wantedLoops(): Set<Int> {
        val manager = AppWidgetManager.getInstance(this)
        val ids = mutableSetOf<Int>()
        for (info in manager.installedProviders) {
            if (info.provider.packageName != packageName) continue
            for (id in manager.getAppWidgetIds(info.provider)) {
                if (!WidgetConfig.loop(this, id)) continue
                if (currentToy(id)?.loopable == true) ids += id
            }
        }
        return ids
    }

    /**
     * L'écran, écouté depuis le service et pas depuis le manifeste.
     *
     * `ACTION_SCREEN_ON` et `ACTION_SCREEN_OFF` ne sont **délivrées qu'aux
     * receivers enregistrés à l'exécution** — c'est une décision d'Android, pour
     * éviter de réveiller la moitié des apps installées à chaque coup d'œil. Il
     * faut donc être déjà là pour les entendre, ce qui est exactement le cas d'un
     * service qui fait tourner une boucle.
     */
    private fun watchScreen() {
        val watch = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // `ACTION_SYNC` arrive aussi par ici, et pas seulement par
                // `onStartCommand` : un fournisseur qui traite `APPWIDGET_UPDATE`
                // n'a le droit de démarrer aucun service, donc il diffuse. Si on
                // vit, on entend ; sinon le message tombe dans le vide, ce qui
                // est exactement ce qu'on veut — voir
                // `MatrixWidgetProvider.syncLoops`.
                if (intent.action == ACTION_SYNC) {
                    work.post { sync() }
                    return
                }
                val on = intent.action != Intent.ACTION_SCREEN_OFF
                work.post { onScreen(on) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_SYNC)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            // Le déverrouillage, en plus de l'allumage. Les deux ne se valent
            // pas : `SCREEN_ON` arrive devant l'écran de verrouillage, où aucun
            // hublot n'est visible, et sur un téléphone qu'on rallume sans
            // déverrouiller il n'y a **que** celui-là. Écouter les deux garantit
            // qu'on reprend au moment où l'écran d'accueil réapparaît, quel que
            // soit le chemin par lequel on y est arrivé.
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, watch, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenWatch = watch
    }

    private fun onScreen(on: Boolean) {
        // `USER_PRESENT` arrive après `SCREEN_ON`, donc souvent sur un état déjà
        // allumé. On resynchronise quand même : c'est le seul moment où on est
        // sûr que l'écran d'accueil est atteignable, et c'est gratuit.
        if (on && screenOn) {
            sync()
            kick()
            return
        }
        if (on == screenOn) return
        screenOn = on

        if (!on) {
            // Les rafales meurent : personne n'a vu la fin, et la garder en vie
            // reviendrait à animer pour un écran noir. Les boucles, elles, sont
            // seulement suspendues — le battement s'arrête de lui-même au
            // prochain tick.
            for (id in running.filterValues { !it.endless }.keys.toList()) stop(id)
            refreshIdle()
            idleIfDone()
            return
        }

        // On revient : les réglages ont pu changer pendant ce temps, et de toute
        // façon il faut redémarrer le battement.
        sync()
        kick()
    }

    // ---------- le battement ----------

    private fun start(widgetId: Int, toy: WidgetToy, endless: Boolean) {
        val manager = AppWidgetManager.getInstance(this)
        val anim = Anim(
            widgetId = widgetId,
            toy = toy,
            frame = Frame(spec),
            // Relevés **une fois**. Ils ne peuvent pas changer pendant
            // l'animation, et les relire à chaque image coûtait un verrou de
            // préférences et une requête au gestionnaire de widgets pour une
            // réponse connue d'avance.
            style = WidgetConfig.style(this, widgetId),
            sidePx = WidgetRender.sidePx(this, manager, widgetId),
            startedAt = SystemClock.uptimeMillis(),
            endless = endless,
        )
        running[widgetId] = anim
        recountMic()
        runCatching { toy.onBurstStart(this, widgetId) }
            .onFailure { Log.w(WidgetRender.TAG, "ouverture refusée par ${toy.id}", it) }
        kick()
    }

    /** Arrête ce qui anime sur ce hublot, s'il y a quelque chose. */
    private fun stop(widgetId: Int) {
        val anim = running.remove(widgetId) ?: return
        runCatching { anim.toy.onBurstEnd(this, anim.widgetId) }
            .onFailure { Log.w(WidgetRender.TAG, "fermeture refusée par ${anim.toy.id}", it) }
        recountMic()
    }

    /**
     * Qui tient une capability, **réveils en vol compris**.
     *
     * [micHeld] décidait à partir de [running] seul, et ça ouvrait une fenêtre :
     * entre le moment où un toy à micro est réveillé et celui où il démarre, une
     * synchronisation recalculait les types sans lui et retirait `microphone` —
     * puis la capture s'ouvrait, sans type, et ne rendait que des zéros. Voir
     * [arming].
     */
    private fun recountMic() {
        micHeld = arming.any { it.needs != null } || running.values.any { it.toy.needs != null }
    }

    private fun kick() {
        if (beating || !screenOn || running.isEmpty()) return
        beating = true
        beat = SystemClock.uptimeMillis()
        work.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            // L'écran s'est éteint : on lâche le battement sans rien fermer. Les
            // boucles sont toujours là, `kick` les reprendra au réveil.
            if (!screenOn) {
                beating = false
                return
            }

            val manager = AppWidgetManager.getInstance(this@WidgetBurstService)
            val now = SystemClock.uptimeMillis()

            var closed = false
            val iterator = running.entries.iterator()
            while (iterator.hasNext()) {
                val anim = iterator.next().value
                val elapsed = now - anim.startedAt
                val within = anim.endless || elapsed < anim.toy.burstDurationMs
                if (within && draw(manager, anim, elapsed)) continue

                // En boucle, un toy qui a fini son cycle le recommence, sur
                // place : repasser par la remise au repos repeindrait tous les
                // hublots à chaque tour.
                //
                // Le dé est passé par là, et c'était un bug — un jet relancé
                // sans interruption est un dé qui ne dit plus rien. La réponse
                // n'était pas ici : un toy dont l'animation est un événement et
                // non un cycle déclare `loopable = false`, et n'atteint jamais
                // cette ligne. Voir [WidgetToy.loopable].
                if (anim.endless) {
                    anim.startedAt = now
                    continue
                }

                iterator.remove()
                runCatching { anim.toy.onBurstEnd(this@WidgetBurstService, anim.widgetId) }
                recountMic()
                closed = true
            }

            if (closed) {
                // Une seule remise au repos, même si deux animations se sont
                // terminées dans le même battement : elle repeint déjà tout le
                // monde. Puis on rend la main aux boucles que la rafale couvrait.
                refreshIdle()
                sync()
            }

            if (running.isEmpty()) {
                beating = false
                idleIfDone()
                return
            }

            // Une échéance ratée n'est pas rattrapée : on repart de maintenant.
            beat += FRAME_MS
            if (beat <= now) beat = now + FRAME_MS
            work.postAtTime(this, beat)
        }
    }

    /**
     * Une image : rendue toujours, **poussée seulement si elle a changé**.
     *
     * C'est la principale économie de batterie du hublot, et elle ne coûte qu'une
     * comparaison de 625 entiers. Ce qui est cher dans une image de widget n'est
     * pas de la calculer — les moteurs sont du Kotlin pur, quelques dizaines de
     * microsecondes — mais de la **rasteriser** puis de la faire traverser le
     * binder : 260 Ko par image, vingt-cinq fois par seconde.
     *
     * Or une boucle passe l'essentiel de son temps à redessiner la même chose.
     * Lapse en continu ne change qu'à la seconde : vingt-quatre images sur
     * vingt-cinq étaient identiques à la précédente, rasterisées et poussées pour
     * rien. Le dé, lui, change à chaque image pendant son jet — il ne perd donc
     * rien, et c'est bien ainsi que ça doit se répartir.
     *
     * @return `true` tant que l'animation continue.
     */
    private fun draw(manager: AppWidgetManager, anim: Anim, elapsedMs: Long): Boolean {
        anim.frame.clear()
        val goingOn = runCatching {
            anim.toy.renderBurst(this, anim.widgetId, anim.frame, elapsedMs / 1000.0)
        }.getOrElse {
            Log.w(WidgetRender.TAG, "animation interrompue pour ${anim.toy.id}", it)
            false
        }
        if (!goingOn) return false

        val brightness = anim.frame.toBrightness()
        if (!anim.echo.accept(brightness)) return true

        val bitmap = WidgetRender.render(brightness, anim.style, anim.sidePx)
        WidgetRender.pushBurst(this, manager, anim.widgetId, bitmap)
        return true
    }

    /**
     * Repose le repos, partout — **sans faire clignoter personne**.
     *
     * Partout et pas seulement sur le hublot concerné : un dé relancé ici change
     * la face que montrent tous les hublots qui l'affichent, quel que soit leur
     * format, et ils ne l'apprendraient pas autrement.
     *
     * ## Pourquoi ce n'est plus une diffusion
     *
     * Ça l'a été, et ça se voyait : `requestUpdateAll` diffuse
     * `APPWIDGET_UPDATE`, chaque fournisseur répond par un `RemoteViews`
     * **complet**, et un `RemoteViews` complet fait ré-inflater la vue au
     * launcher. Tous les hublots de l'écran clignotaient donc à chaque double
     * tap, y compris ceux qui n'avaient pas bougé — et y compris quand le double
     * tap ne changeait rien.
     *
     * Le service a tout ce qu'il faut pour peindre lui-même, et
     * `partiallyUpdateAppWidget` ne remplace que l'image. Pas de diffusion, pas
     * de ré-inflate, pas de clignotement. La diffusion reste la bonne route pour
     * qui n'a pas ce qu'il faut sous la main — un service de toy qui change une
     * préférence depuis la matrice, par exemple.
     *
     * Les hublots qui animent sont sautés : leur image suivante arrive dans
     * quarante millisecondes, et l'écraser avec un repos ferait un à-coup.
     */
    private fun refreshIdle() {
        val manager = AppWidgetManager.getInstance(this)
        for (info in manager.installedProviders) {
            if (info.provider.packageName != packageName) continue
            for (id in manager.getAppWidgetIds(info.provider)) pushIdle(manager, id)
        }
    }

    /**
     * Le repos d'**un** hublot, s'il n'anime pas.
     *
     * Séparé de [refreshIdle] pour le double tap, qui n'a qu'un hublot à repeindre
     * et ne doit surtout pas le repeindre s'il va animer : le repos du nouveau toy
     * apparaissait le temps d'un quart de seconde avant d'être remplacé.
     */
    private fun pushIdle(manager: AppWidgetManager, id: Int) {
        if (id in running) return
        val toy = currentToy(id) ?: return
        val frame = Frame(spec)
        runCatching { toy.renderIdle(this, id, frame) }.onFailure {
            Log.w(WidgetRender.TAG, "repos refusé par ${toy.id}", it)
            return
        }
        val bitmap = WidgetRender.render(
            frame.toBrightness(),
            WidgetConfig.style(this, id),
            WidgetRender.sidePx(this, manager, id),
        )
        WidgetRender.pushBurst(this, manager, id, bitmap)
    }

    // ---------- premier plan, et fin ----------

    /**
     * Se promeut, ou ajoute un type à une promotion déjà obtenue.
     *
     * Deux raisons de passer ici, et elles se cumulent : une rafale qui réclame
     * une capability — le micro de Sono — et une boucle continue, qui a besoin
     * que le service ne soit pas tuable à la première pression mémoire.
     *
     * Appelé depuis `onStartCommand`, donc **sans attendre la fenêtre de double
     * tap**. Le quart de seconde de la fenêtre tiendrait largement dans le délai
     * que le système accorde, mais pas l'exemption qui va avec : une permission
     * « pendant l'utilisation » se joue dans la diffusion du tap et nulle part
     * après — voir [MatrixWidgetProvider].
     *
     * La notification n'est jamais vue. Elle est obligatoire, elle n'a rien à
     * dire, et tout est fait pour qu'elle ne montre rien : canal d'importance
     * minimale, visibilité secrète, catégorie service. C'est exactement ce que
     * fait Glyph Museum, dont le service d'animation tourne des minutes sans que
     * personne voie passer quoi que ce soit.
     */
    private fun armForeground(micFor: Int) {
        val types = desiredTypes(micFor)
        if (foreground && types == fgTypes) return

        // **Le système fusionne les types, il ne les remplace pas.** Un
        // `startForeground` en `shortService` suivi d'un `specialUse` donne un
        // service qui porte les deux — mesuré au `dumpsys` : `types=0x40000080`.
        // C'est tout sauf anodin : `shortService` est borné à trois minutes, et
        // sa limite s'applique alors à un service qui n'aurait jamais dû
        // l'endosser. La boucle continue mourait là.
        //
        // Le seul moyen de **retirer** un type est de quitter le premier plan
        // puis d'y revenir. `DETACH` garde la notification en place — elle n'est
        // de toute façon jamais vue — donc rien ne clignote nulle part.
        if (foreground && (fgTypes and types.inv()) != 0) {
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            }
            foreground = false
            fgTypes = 0
        }

        runCatching {
            ServiceCompat.startForeground(this, NOTIF_ID, notice(), types)
            foreground = true
            fgTypes = types
            Log.i(WidgetRender.TAG, "premier plan : types=0x${types.toString(16)}")
        }.onFailure {
            // Pas fatal pour le rendu : le toy mesurera ce qu'il peut et
            // affichera ce qu'il a. Le système, lui, peut décider que ça l'est.
            Log.w(WidgetRender.TAG, "premier plan refusé : ${it.message}")
        }
    }

    /**
     * Le type de premier plan, **recalculé** et jamais accumulé.
     *
     * Il l'a été, par un `or` sur ce qui avait déjà été demandé, et ça coûtait
     * cher : un tap posait `shortService`, une boucle ajoutait `specialUse`, et
     * `shortService` ne repartait plus. Or `shortService` est **borné à trois
     * minutes** — passé ce délai le système réclame l'arrêt, et la boucle
     * s'arrêtait avec. C'était ça, les animations qui ne reprenaient pas.
     *
     * D'où la règle, qui tient en une phrase : `shortService` est le type de
     * **qui n'a rien d'autre à demander**. Dès qu'une boucle existe, c'est
     * `specialUse`, sans borne ; le micro s'ajoute aux deux quand une rafale le
     * réclame.
     *
     * Les besoins sont relus dans les préférences et non dans [running], parce
     * que cette méthode s'exécute sur le fil principal pendant que le fil de
     * l'animation, lui, écrit dans [running].
     */
    private fun desiredTypes(micFor: Int): Int {
        var types = 0
        // `micHeld` autant que le tap qui arrive : une animation en cours peut
        // tenir le micro, et une synchronisation déclenchée entre-temps par
        // l'écran de réglages recalculerait les types sans elle. Le type
        // disparaîtrait sous une capture ouverte, qui se mettrait à rendre des
        // zéros sans rien dire.
        if (micHeld || needsOf(micFor) != null) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (wantedLoops().isNotEmpty()) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        return if (types != 0) types else ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
    }

    /**
     * La capability que réclame le toy affiché sur ce hublot, si elle est
     * accordée. C'est le même calcul que celui du receiver, et il tombe
     * forcément sur la même réponse — rien n'a changé entre les deux.
     */
    private fun needsOf(widgetId: Int): BurstNeeds? {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        val toy = currentToy(widgetId) ?: return null
        val needs = toy.needs ?: return null
        if (!toy.isReady(this)) return null
        return needs.takeIf {
            ContextCompat.checkSelfPermission(this, it.permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun notice(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.widget_burst_channel),
                NotificationManager.IMPORTANCE_MIN,
            ).apply { setShowBadge(false) },
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.widget_burst_channel))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    /**
     * `shortService` a une limite de trois minutes, et le système la réclame ici.
     *
     * **Seules les rafales s'arrêtent.** Une boucle n'est jamais sous
     * `shortService` — [desiredTypes] bascule en `specialUse` dès qu'il y en a
     * une — donc ce signal ne la concerne pas, et l'arrêter était le bug : le
     * type s'accumulait, `shortService` survivait à l'apparition d'une boucle, et
     * trois minutes plus tard tout s'arrêtait ensemble.
     *
     * Une rafale de trente secondes ne devrait jamais arriver ici. Ne pas
     * l'implémenter serait parier sur ce « jamais ».
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(WidgetRender.TAG, "onTimeout du système : type=0x${fgsType.toString(16)}")
        if (fgsType != ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE) return
        work.post {
            for ((id, anim) in running.entries.toList()) {
                if (!anim.endless) stop(id)
            }
            refreshIdle()
            // Le type a peut-être changé de sens depuis : une boucle a pu
            // apparaître pendant la rafale.
            main.post { armForeground(AppWidgetManager.INVALID_APPWIDGET_ID) }
            idleIfDone()
        }
    }

    /**
     * Plus rien à animer, plus rien à attendre : on s'en va.
     *
     * Une boucle suspendue compte comme quelque chose à animer — elle reprendra
     * au prochain allumage, et c'est justement pour l'entendre qu'il faut rester
     * en vie. Un réveil en vol aussi : voir [arming].
     *
     * `stopSelf(lastStartId)` et non `stopSelf()` : un tap peut arriver dans
     * l'intervalle, et la forme à identifiant laisse le système refuser l'arrêt
     * quand un `onStartCommand` plus récent est passé. C'est la seule protection
     * correcte contre cette course.
     */
    private fun idleIfDone() {
        if (running.isNotEmpty() || taps.isNotEmpty() || arming.isNotEmpty()) return
        val startId = lastStartId
        main.post {
            if (foreground) {
                runCatching {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                }
                foreground = false
                fgTypes = 0
            }
            stopSelf(startId)
        }
    }

    private fun currentToy(widgetId: Int): WidgetToy? {
        val rotation = WidgetConfig.rotation(this, widgetId, toys.map { it.id })
        val current = WidgetConfig.current(this, widgetId, rotation) ?: return null
        return toys.firstOrNull { it.id == current }
    }

    /** Une animation en cours, et tout ce qui a été relevé une fois pour elle. */
    private class Anim(
        val widgetId: Int,
        val toy: WidgetToy,
        val frame: Frame,
        val style: WidgetStyle,
        val sidePx: Int,
        /** Remis à l'heure courante quand une boucle recommence son cycle. */
        var startedAt: Long,
        /** Boucle continue : aucune borne de durée, seulement l'écran. */
        val endless: Boolean,
    ) {
        /** Ce qui évite de pousser deux fois la même image. Voir [FrameEcho]. */
        val echo = FrameEcho()
    }

    companion object {
        /** Un tap sur un hublot. */
        const val ACTION_TAP = "red.suns.haloglyph.WIDGET_BURST_TAP"

        /** « Relis les réglages de boucle et mets-toi d'accord avec. » */
        const val ACTION_SYNC = "red.suns.haloglyph.WIDGET_LOOP_SYNC"

        /** Le fournisseur d'où vient l'ordre — il porte le catalogue de toys. */
        const val EXTRA_PROVIDER = "provider"

        /**
         * On a été démarrés par `startForegroundService`, donc on doit se
         * promouvoir. Le receiver le sait, le service ne peut pas le deviner.
         */
        const val EXTRA_PROMOTED = "promoted"

        /**
         * Le temps qu'on laisse à un second doigt.
         *
         * 250 ms est la valeur du système pour un double tap
         * (`ViewConfiguration.getDoubleTapTimeout`). On ne la lit pas au système
         * parce qu'elle vaut pour une `View` qui reçoit des événements de
         * pointeur ; ici on compte des diffusions, qui arrivent plus tard et pas
         * régulièrement. La même durée, mesurée autrement.
         */
        const val TAP_WINDOW_MS = 250L

        /**
         * ~25 images par seconde **visées**. Ce qui sort vraiment dépend du
         * binder, et la boucle ne cherche pas à le forcer : une échéance ratée
         * est abandonnée, pas rattrapée.
         */
        const val FRAME_MS = 40L

        private const val CHANNEL = "haloglyph-widget-burst"

        /** Hors de la plage des toys : ce n'est pas la notification d'un toy. */
        private const val NOTIF_ID = 4301
    }
}
