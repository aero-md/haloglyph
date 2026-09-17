package red.suns.haloglyph.lapse.widget

import android.content.Context
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.WidgetConfig
import red.suns.haloglyph.core.widget.WidgetToy
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.R
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.render.LapseRenderer
import red.suns.haloglyph.lapse.render.MatrixLabels
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Lapse dans le hublot d'écran d'accueil : la matrice, sur un téléphone qui n'en
 * a pas.
 *
 * Au repos, le rendu est celui de l'Always-On — les unités, sans l'anneau des
 * secondes : un hublot rafraîchi de loin en loin qui afficherait une aiguille de
 * secondes figée mentirait sur ce qu'il montre. Au tap, la rafale rend l'anneau
 * et l'animation.
 *
 * ## Chaque hublot son lapse
 *
 * Le tap fait défiler les lapses — c'est **l'appui long du Glyph Button**, porté
 * sur le hublot, parce qu'un appui long y appartient au launcher et qu'aucune
 * application ne peut le lui prendre.
 *
 * Il ne fait défiler **que ce hublot-là**. L'index vit dans les réglages du
 * widget ([WidgetConfig.toyInt]) et non dans ceux du toy : écrit chez le toy, il
 * faisait défiler tous les hublots à la fois et la matrice avec, ce qui est le
 * contraire de l'intérêt d'en poser deux. Deux hublots côte à côte peuvent donc
 * compter deux choses différentes.
 *
 * Tant qu'on n'a pas tapé, un hublot suit l'index de la matrice : il naît sur ce
 * que le téléphone affiche, et ne devient indépendant qu'au premier doigt.
 *
 * ## Un moteur par hublot
 *
 * Conséquence directe : deux hublots sur deux lapses différents ne peuvent pas
 * partager un moteur. Un seul, reconfiguré d'un rendu à l'autre, remettrait sa
 * référence et son format à chaque image et ne saurait plus jamais où il en est.
 *
 * Ce toy **n'a aucun lien** avec [red.suns.haloglyph.lapse.toy.LapseToyService].
 * Il instancie les moteurs et le renderer en direct, dans le processus de l'app,
 * et lit les mêmes préférences. C'est la contrepartie du fait que le moteur soit
 * du Kotlin pur — et la raison pour laquelle il ne faut jamais binder le service
 * depuis ici : ce bind déclencherait une connexion au service Glyph, inexistant
 * sur un téléphone non-Nothing.
 */
class LapseWidgetToy : WidgetToy {

    override val id: String = LapseConfig.TOY_ID
    override val nameRes: Int = R.string.toy_lapse_name

    private val zone: ZoneId = ZoneId.systemDefault()
    private val renderer by lazy { LapseRenderer() }

    /**
     * Un moteur par hublot, créé à la demande.
     *
     * Touché depuis le fil de l'animation et depuis le fil principal — le
     * receiver repose la frame de repos pendant qu'une rafale tourne ailleurs —
     * d'où la table concurrente. Elle ne grandit qu'au rythme des hublots posés,
     * et un hublot retiré laisse un moteur derrière lui : quelques centaines
     * d'octets, contre le risque d'en fermer un qu'une rafale tient encore.
     */
    private val engines = ConcurrentHashMap<Int, LapseEngine>()

    /**
     * Le lapse de ce hublot — le sien, ou celui de la matrice tant qu'il n'a rien
     * choisi.
     */
    private fun indexOf(context: Context, widgetId: Int): Int {
        val prefs = LapseConfig.prefs(context)
        val fallback = LapseConfig.activeIndex(prefs)
        val saved = WidgetConfig.toyInt(context, widgetId, id, KEY_INDEX, fallback)
        return saved.coerceIn(0, LapseConfig.lapseCount(prefs) - 1)
    }

    /**
     * `live` est ignoré : passer au lapse suivant se fait de la même façon qu'un
     * hublot soit en train d'animer ou non — et la rafale repart pour montrer les
     * secondes du nouveau.
     */
    override fun onTap(context: Context, widgetId: Int, live: Boolean): Boolean {
        val prefs = LapseConfig.prefs(context)
        val count = LapseConfig.lapseCount(prefs)
        if (count <= 1) return true
        val next = (indexOf(context, widgetId) + 1) % count
        WidgetConfig.setToyInt(context, widgetId, id, KEY_INDEX, next)
        return true
    }

    /**
     * Arriver sur un lapse n'est **pas** taper dessus.
     *
     * Le tap fait défiler les lapses ; le double tap fait défiler les toys. Laisser
     * le second appeler le premier ferait avancer le lapse à chaque passage, si
     * bien qu'on ne retomberait jamais sur celui qu'on venait voir. La rafale part
     * quand même — c'est elle qui déroule l'anneau des secondes.
     */
    override fun onArrive(context: Context, widgetId: Int): Boolean = true

    override fun renderIdle(context: Context, widgetId: Int, frame: Frame) {
        renderer.render(frame, snapshot(context, widgetId, 0.0), includeSeconds = false)
    }

    override fun renderBurst(
        context: Context,
        widgetId: Int,
        frame: Frame,
        elapsedSeconds: Double,
    ): Boolean {
        renderer.render(frame, snapshot(context, widgetId, elapsedSeconds), includeSeconds = true)
        return true
    }

    private fun snapshot(
        context: Context,
        widgetId: Int,
        elapsedSeconds: Double,
    ): LapseEngine.Snapshot {
        renderer.labels = MatrixLabels.current()
        val engine = engines.getOrPut(widgetId) { LapseEngine(zone) }
        LapseConfig.applyAt(LapseConfig.prefs(context), engine, zone, indexOf(context, widgetId))
        return engine.update(System.currentTimeMillis(), elapsedSeconds)
    }

    private companion object {
        /** Sous-clé de l'état de ce toy dans les réglages d'un hublot. */
        const val KEY_INDEX = "index"
    }
}
