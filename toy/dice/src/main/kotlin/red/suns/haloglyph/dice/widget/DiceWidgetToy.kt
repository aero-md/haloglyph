package red.suns.haloglyph.dice.widget

import android.content.Context
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.WidgetConfig
import red.suns.haloglyph.core.widget.WidgetToy
import red.suns.haloglyph.core.widget.WidgetVariant
import red.suns.haloglyph.dice.DiceConfig
import red.suns.haloglyph.dice.R
import red.suns.haloglyph.dice.engine.Dice
import red.suns.haloglyph.dice.engine.Die
import red.suns.haloglyph.dice.engine.Quat
import red.suns.haloglyph.dice.engine.Roll
import red.suns.haloglyph.dice.engine.T_END
import red.suns.haloglyph.dice.engine.drawValue
import red.suns.haloglyph.dice.render.DiceRenderer
import java.util.concurrent.ConcurrentHashMap

/**
 * Le dé dans le hublot d'écran d'accueil : la matrice sur un téléphone qui n'en
 * a pas, et le jet à portée de pouce sur un téléphone qui en a une.
 *
 * Au repos, la dernière face obtenue, posée. Au tap, un jet complet : le tirage
 * est fait tout de suite et mémorisé, l'animation n'est que la fonction du temps
 * qui y conduit. C'est ce qui permet à la rafale de s'interrompre sans mentir —
 * le résultat est déjà écrit, la frame de repos qui suit montre la même face.
 *
 * ## Chaque hublot son dé
 *
 * Le solide se choisit dans les réglages du hublot, et pas ailleurs : sur la
 * matrice il se change à l'appui long du Glyph Button, que le launcher garde pour
 * lui, et le jet part en secouant le téléphone, ce qu'on ne fait pas à un écran
 * d'accueil. Ces deux gestes n'ont aucun équivalent ici, donc le hublot s'écarte
 * de la matrice — voir [WidgetToy.variants]. C'est une divergence assumée : deux
 * hublots peuvent porter un d6 et un d20, et aucun des deux ne suit ce que le
 * téléphone a en main.
 *
 * La face obtenue suit le même chemin, et il le fallait : un résultat commun
 * ferait changer la silhouette d'un d20 posé parce qu'on a lancé le d6 d'à côté.
 * Un hublot neuf part quand même de ce que montre la matrice — c'est le repli, et
 * il ne sert qu'une fois.
 *
 * ## Pourquoi il ne boucle pas
 *
 * Un jet a une fin, et c'est tout son sujet : trois secondes de culbute, une face
 * qui se pose, on la lit. En boucle continue, la rafale qui s'achève était
 * relancée sur place — un dé qui roule sans jamais s'arrêter, donc un dé qui ne
 * dit plus rien. `loopable` à faux dit la seule chose vraie : ce toy n'a pas
 * d'animation continue, il a un événement. Un hublot réglé en continu l'affiche
 * posé et attend le doigt, exactement comme les autres.
 *
 * Ce toy **n'a aucun lien** avec [red.suns.haloglyph.dice.toy.DiceToyService].
 * Il instancie le renderer en direct, dans le processus de l'app, et lit les
 * mêmes préférences. Binder le service déclencherait une connexion à
 * `com.nothing.thirdparty`, inexistant sur un téléphone non-Nothing.
 */
class DiceWidgetToy : WidgetToy {

    override val id: String = DiceConfig.TOY_ID
    override val nameRes: Int = R.string.toy_dice_name

    private val renderer by lazy { DiceRenderer() }

    /**
     * Les quatre solides, étiquetés par leur nombre de faces — `6`, `10`, `12`,
     * `20`. Pas `d6` : la rangée porte déjà le nom du toy au-dessus d'elle, et le
     * `d` ne dit rien que le contexte ne dise mieux.
     */
    override val variants: List<WidgetVariant> =
        Dice.ALL.map { WidgetVariant(it.id.key, it.faceCount.toString()) }

    /** Le dé en main sur la matrice, tant que ce hublot n'a rien choisi. */
    override fun defaultVariant(context: Context): String =
        DiceConfig.die(DiceConfig.prefs(context)).id.key

    /** Voir l'en-tête : un jet est un événement, pas un cycle. */
    override val loopable: Boolean = false

    /**
     * Le jet en cours, **par hublot**.
     *
     * Posé par [onTap], lu par [renderBurst]. Un seul champ suffisait tant qu'il
     * n'y avait qu'un dé ; depuis que chacun a le sien, deux hublots qui animent
     * ensemble rendraient le même solide — celui du dernier doigt. La table est
     * concurrente parce que les deux méthodes tournent sur le fil de la rafale,
     * mais que `onTap` peut arriver d'ailleurs.
     */
    private val rolls = ConcurrentHashMap<Int, Roll>()

    /** Le solide de ce hublot-là. */
    private fun dieOf(context: Context, widgetId: Int): Die =
        Dice.byKey(WidgetConfig.variant(context, widgetId, this))

    /**
     * La pose de repos de ce hublot : sa dernière face, à son cran de rotation.
     *
     * Le bornage n'est pas défensif pour rien — changer de solide garde la
     * mémoire du précédent, et un 17 relu sur un d6 n'a aucune face où se poser.
     */
    private fun restOf(context: Context, widgetId: Int, die: Die): Quat {
        val prefs = DiceConfig.prefs(context)
        val value = WidgetConfig.toyInt(
            context, widgetId, id, KEY_VALUE,
            fallback = DiceConfig.lastValue(prefs, die),
        )
        val twist = WidgetConfig.toyInt(context, widgetId, id, KEY_TWIST, fallback = 0)
        return die.restQuat(value.coerceIn(1, die.faceCount), twist.coerceIn(0, die.spin - 1))
    }

    override fun renderIdle(context: Context, widgetId: Int, frame: Frame) {
        val die = dieOf(context, widgetId)
        renderer.render(frame, die, Roll.resting(die, restOf(context, widgetId, die)))
    }

    /** `live` est ignoré : retaper un dé qui roule, c'est le relancer. */
    override fun onTap(context: Context, widgetId: Int, live: Boolean): Boolean {
        val die = dieOf(context, widgetId)
        val throwing = Roll.make(die, restOf(context, widgetId, die), drawValue(die))
        rolls[widgetId] = throwing
        WidgetConfig.setToyInt(context, widgetId, id, KEY_VALUE, throwing.value)
        WidgetConfig.setToyInt(context, widgetId, id, KEY_TWIST, throwing.twist)
        return true
    }

    override fun renderBurst(
        context: Context,
        widgetId: Int,
        frame: Frame,
        elapsedSeconds: Double,
    ): Boolean {
        val current = rolls[widgetId] ?: return false
        if (elapsedSeconds >= T_END) return false
        renderer.render(frame, current.die, current.viewAt(elapsedSeconds))
        return true
    }

    private companion object {
        /** Sous-clés de l'état de ce toy dans les réglages d'un hublot. */
        const val KEY_VALUE = "value"
        const val KEY_TWIST = "twist"
    }
}
