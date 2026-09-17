package red.suns.haloglyph.core.widget

import android.content.Context
import red.suns.haloglyph.core.config.ToyPrefs
import red.suns.haloglyph.core.matrix.MatrixLook

/**
 * À quoi ressemble **ce** hublot-là.
 *
 * Deux réglages, et ils ne parlent ni l'un ni l'autre du toy : ils disent à quoi
 * doit ressembler le hublot qui le porte. C'est pour ça qu'ils vivent ici et non
 * dans les préférences d'un toy — le même choix vaut pour un dé et pour un
 * compte à rebours, et un toy qui n'existe pas encore l'aura sans rien écrire.
 *
 * @param led la trame : l'appareil émulé, ou la trame telle qu'un écran
 * l'affiche. Voir [MatrixLook.LedStyle].
 * @param glass le reflet du verre. C'est ce qui distingue un objet posé sur le
 * fond d'écran d'un disque noir avec des points dedans — et c'est aussi ce qui
 * fait mentir la lumière quand le fond d'écran est clair, d'où le réglage.
 */
data class WidgetStyle(
    val led: MatrixLook.LedStyle = MatrixLook.LedStyle.SHARP,
    val glass: Boolean = true,
)

/**
 * Les réglages des widgets posés, un jeu **par widget**.
 *
 * ## Par instance, et pas par toy
 *
 * C'est ce que le système impose et ce que l'usage réclame. Android configure un
 * widget par son `appWidgetId` — l'écran de réglages est lancé avec cet
 * identifiant et rien d'autre — et deux hublots posés sur deux écrans n'ont
 * aucune raison de se ressembler : celui qui est sur un fond d'écran clair veut
 * son reflet, celui d'à côté ne le veut pas.
 *
 * Depuis le passage au hublot générique, la règle vaut aussi pour **le contenu**.
 * Deux hublots côte à côte peuvent tourner sur des toys différents, et en avoir
 * chacun une rotation différente : l'un montre le dé et rien d'autre, l'autre
 * alterne entre deux compteurs. Rien ici n'est global, jamais.
 *
 * ## Les identifiants sont recyclés
 *
 * Un `appWidgetId` libéré est réattribué. Des réglages laissés derrière un widget
 * supprimé finiraient donc par s'appliquer au suivant, qui n'a rien demandé :
 * [forget] est appelé depuis `onDeleted`, et ce n'est pas du ménage, c'est une
 * correction.
 *
 * ## L'absence vaut défaut, et c'est délibéré
 *
 * La rotation n'est pas écrite tant qu'on n'y a pas touché, et une rotation non
 * écrite veut dire **tous les toys**. C'est ce qui fait qu'un toy ajouté par une
 * mise à jour apparaît dans les hublots déjà posés sans migration : personne
 * n'avait dit qu'il n'en voulait pas. Une liste figée à la pose aurait gelé le
 * pack à ce qu'il contenait ce jour-là.
 *
 * Un seul fichier pour tous les widgets — [ToyPrefs] le nomme comme il nomme
 * ceux des toys, et les clés sont préfixées par l'identifiant. Les widgets ne
 * sont pas un toy, mais ils obéissent aux mêmes règles : un fichier, un
 * propriétaire, et le tout lisible en synchrone depuis un receiver qui ne vit que
 * le temps d'une diffusion.
 */
object WidgetConfig {

    /** Le nom du fichier de préférences, via la convention des toys. */
    const val STORE_ID = "widgets"

    private const val KEY_LED = "led"
    private const val KEY_GLASS = "glass"
    private const val KEY_ROTATION = "toys"
    private const val KEY_CURRENT = "current"
    private const val KEY_LOOP = "loop"

    // ---------- apparence ----------

    fun style(context: Context, widgetId: Int): WidgetStyle {
        val prefs = ToyPrefs.of(context, STORE_ID)
        val led = prefs.getString(key(widgetId, KEY_LED), null)
        return WidgetStyle(
            // Repli sur le défaut plutôt que sur une exception : une valeur
            // inconnue veut dire « écrite par une version qui n'est plus là ».
            led = MatrixLook.LedStyle.entries.firstOrNull { it.name == led }
                ?: MatrixLook.LedStyle.SHARP,
            glass = prefs.getBoolean(key(widgetId, KEY_GLASS), true),
        )
    }

    /**
     * `commit()` et non `apply()` : le widget est redessiné **juste après**, par
     * une diffusion qui atteindra peut-être un autre processus. L'écriture
     * asynchrone laissait une course où le hublot se repeignait avec l'ancien
     * réglage, une fois sur cinq et jamais sur la machine de qui l'a écrit.
     */
    @Suppress("ApplySharedPref")
    fun setStyle(context: Context, widgetId: Int, style: WidgetStyle) {
        ToyPrefs.of(context, STORE_ID).edit()
            .putString(key(widgetId, KEY_LED), style.led.name)
            .putBoolean(key(widgetId, KEY_GLASS), style.glass)
            .commit()
    }

    // ---------- ce qui ne dépend d'aucun hublot ----------

    /**
     * Le dernier fournisseur qui ait parlé au service.
     *
     * Une seule chose en a besoin, et elle est essentielle : quand le système
     * redémarre le service de lui-même (`START_STICKY`), il ne rend **aucun
     * intent**. Le service n'a alors ni catalogue de toys ni matrice, et il ne
     * peut pas les deviner — c'est `app` qui les assemble. Retenir le nom de
     * classe suffit à les retrouver.
     *
     * Les deux formats de hublot portent la même liste, donc n'importe lequel
     * fait l'affaire.
     */
    fun host(context: Context): String? =
        ToyPrefs.of(context, STORE_ID).getString(KEY_HOST, null)

    fun setHost(context: Context, className: String) {
        val prefs = ToyPrefs.of(context, STORE_ID)
        if (prefs.getString(KEY_HOST, null) == className) return
        prefs.edit().putString(KEY_HOST, className).apply()
    }

    private const val KEY_HOST = "host"

    // ---------- l'état d'un toy dans un hublot ----------

    /**
     * Un entier appartenant à **un toy, dans un hublot**.
     *
     * C'est ce qui manquait pour qu'un toy ait autre chose qu'un état global.
     * Lapse s'en sert pour le lapse affiché : le tap faisait défiler tous les
     * hublots à la fois, parce que le seul endroit où écrire était la préférence
     * du toy — celle que lit aussi la matrice.
     *
     * Rangé ici et pas dans le fichier du toy : la clé est celle d'un widget, et
     * c'est [forget] qui doit s'en débarrasser quand le hublot disparaît. Un toy
     * qui l'écrirait chez lui laisserait derrière lui des entrées indexées par
     * des identifiants recyclés — exactement le piège que ce fichier existe pour
     * éviter.
     *
     * @param fallback ce que vaut l'état tant que ce hublot n'a rien choisi.
     * L'appelant le calcule : pour Lapse c'est le lapse de la matrice, si bien
     * qu'un hublot qu'on n'a jamais tapé suit ce que fait le téléphone, et ne
     * devient indépendant qu'au premier doigt.
     */
    fun toyInt(context: Context, widgetId: Int, toyId: String, key: String, fallback: Int): Int =
        ToyPrefs.of(context, STORE_ID).getInt(toyKey(widgetId, toyId, key), fallback)

    @Suppress("ApplySharedPref")
    fun setToyInt(context: Context, widgetId: Int, toyId: String, key: String, value: Int) {
        ToyPrefs.of(context, STORE_ID).edit()
            .putInt(toyKey(widgetId, toyId, key), value)
            .commit()
    }

    private fun toyKey(widgetId: Int, toyId: String, key: String) = "w$widgetId.$toyId.$key"

    // ---------- la variante d'un toy ----------

    /**
     * La forme sous laquelle **ce hublot-là** porte ce toy — le solide du dé.
     *
     * Rangée comme le reste sous l'identifiant du widget, donc indépendante d'un
     * hublot à l'autre et effacée avec lui. Les règles sont dans
     * [WidgetRotation.variant] ; ici il n'y a que la lecture.
     */
    fun variant(context: Context, widgetId: Int, toy: WidgetToy): String? =
        WidgetRotation.variant(
            ToyPrefs.of(context, STORE_ID).getString(toyKey(widgetId, toy.id, KEY_VARIANT), null),
            toy.variants.map { it.key },
            toy.defaultVariant(context),
        )

    /**
     * `commit()`, comme tous les réglages de hublot : l'écran de réglages repeint
     * dans la foulée, par une diffusion qui peut atteindre un autre processus.
     */
    @Suppress("ApplySharedPref")
    fun setVariant(context: Context, widgetId: Int, toyId: String, key: String) {
        ToyPrefs.of(context, STORE_ID).edit()
            .putString(toyKey(widgetId, toyId, KEY_VARIANT), key)
            .commit()
    }

    private const val KEY_VARIANT = "variant"

    // ---------- boucle continue ----------

    /**
     * Ce hublot anime-t-il sans fin, ou seulement quand on le touche ?
     *
     * Faux par défaut, et c'est le bon défaut : la boucle fait vivre un service
     * en permanence, et personne ne l'a demandée en posant un widget. Elle
     * s'active hublot par hublot, comme tout le reste — on peut vouloir un
     * compteur qui tourne sur l'écran principal et des hublots sages ailleurs.
     */
    fun loop(context: Context, widgetId: Int): Boolean =
        ToyPrefs.of(context, STORE_ID).getBoolean(key(widgetId, KEY_LOOP), false)

    /**
     * `commit()` comme le reste : le service relit la valeur dans la foulée, par
     * une diffusion qui peut le réveiller avant que l'écriture asynchrone soit
     * passée. C'est exactement la course que `apply()` laisse ouverte.
     */
    @Suppress("ApplySharedPref")
    fun setLoop(context: Context, widgetId: Int, on: Boolean) {
        ToyPrefs.of(context, STORE_ID).edit().putBoolean(key(widgetId, KEY_LOOP), on).commit()
    }

    // ---------- rotation ----------

    /**
     * Les toys que **ce** hublot fait tourner, dans l'ordre du catalogue.
     *
     * [all] est la liste complète telle que `app` l'assemble : c'est elle qui
     * donne l'ordre, et c'est elle qui décide de ce qui existe. Les règles sont
     * dans [WidgetRotation], qui ne dépend de rien et se teste ; ici il n'y a que
     * la lecture.
     */
    fun rotation(context: Context, widgetId: Int, all: List<String>): List<String> =
        WidgetRotation.decode(
            ToyPrefs.of(context, STORE_ID).getString(key(widgetId, KEY_ROTATION), null),
            all,
        )

    @Suppress("ApplySharedPref")
    fun setRotation(context: Context, widgetId: Int, ids: Collection<String>) {
        ToyPrefs.of(context, STORE_ID).edit()
            .putString(key(widgetId, KEY_ROTATION), WidgetRotation.encode(ids))
            .commit()
    }

    /** Le toy affiché en ce moment par ce hublot. Voir [WidgetRotation.resolve]. */
    fun current(context: Context, widgetId: Int, rotation: List<String>): String? =
        WidgetRotation.resolve(
            ToyPrefs.of(context, STORE_ID).getString(key(widgetId, KEY_CURRENT), null),
            rotation,
        )

    @Suppress("ApplySharedPref")
    fun setCurrent(context: Context, widgetId: Int, toyId: String) {
        ToyPrefs.of(context, STORE_ID).edit()
            .putString(key(widgetId, KEY_CURRENT), toyId)
            .commit()
    }

    /** Avance d'un cran et enregistre. C'est ce que fait le double tap. */
    fun advance(context: Context, widgetId: Int, rotation: List<String>): String? {
        val now = current(context, widgetId, rotation)
        val next = WidgetRotation.next(now, rotation) ?: return null
        if (next != now) setCurrent(context, widgetId, next)
        return next
    }

    /**
     * Oublie des widgets supprimés. Voir l'en-tête : les identifiants reviennent.
     *
     * Par **préfixe** et non par liste de clés : toutes les clés d'un hublot
     * commencent par son identifiant, y compris celles qu'un toy s'est rangées
     * sous [toyInt] et dont ce fichier ne connaît ni le nom ni le nombre. Une
     * liste explicite oublierait chaque nouvelle, et ne le dirait pas — le
     * symptôme serait un hublot qui naît avec les réglages du précédent.
     */
    fun forget(context: Context, widgetIds: IntArray) {
        val prefs = ToyPrefs.of(context, STORE_ID)
        val prefixes = widgetIds.map { "w$it." }
        val editor = prefs.edit()
        for (name in prefs.all.keys) {
            if (prefixes.any { name.startsWith(it) }) editor.remove(name)
        }
        editor.apply()
    }

    private fun key(widgetId: Int, name: String): String = "w$widgetId.$name"
}
