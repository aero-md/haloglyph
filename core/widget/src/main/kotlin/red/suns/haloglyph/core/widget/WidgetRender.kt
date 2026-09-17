package red.suns.haloglyph.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import red.suns.haloglyph.core.look.MatrixPainter
import red.suns.haloglyph.core.matrix.MatrixLook
import red.suns.haloglyph.core.matrix.MatrixSpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Le support : ce qui transforme une frame en pixels et les pousse au launcher.
 *
 * Séparé du fournisseur parce que **deux appelants** en ont besoin et qu'aucun
 * des deux ne peut tenir l'état de l'autre : le receiver, qui pose la frame de
 * repos et ne vit que le temps d'une diffusion, et [WidgetBurstService], qui
 * anime. Le cache de peintres doit survivre aux deux, donc il est ici, dans un
 * objet du processus.
 *
 * ## Le plafond, et où il est vraiment
 *
 * Chaque image part **en entier** dans une transaction binder vers le launcher :
 * 256 × 256 en ARGB_8888, soit 260 Ko. C'est le vrai mur, il n'est pas dans
 * notre code, et rien ne rendra un widget aussi fluide que la matrice. Ce qui est
 * dans notre code, en revanche, et qui a été récupéré :
 *
 * 1. la bitmap est **réutilisée** au lieu d'être réallouée par image — un quart
 *    de mégaoctet jeté seize fois par seconde faisait passer le ramasse-miettes
 *    pendant l'animation ;
 * 2. le hublot au repos et les halos sont peints **une fois** par [MatrixPainter],
 *    qui vit aussi longtemps que le processus ;
 * 3. une rafale passe par [pushBurst], qui n'envoie que l'image — pas le layout
 *    entier, pas l'intention de tap reconstruite à chaque fois. Voir plus bas ;
 * 4. l'apparence et la définition sont relevées **une fois par rafale** par
 *    l'appelant, jamais dans la boucle. Elles ne peuvent pas changer pendant :
 *    les faire relire soixante fois coûtait un verrou de `SharedPreferences` par
 *    image pour une valeur qu'on connaissait déjà.
 *
 * ## La même définition au repos et en rafale
 *
 * Il y en a eu deux, la rafale rendant plus petit pour alléger ses transactions.
 * C'est une économie qu'on ne peut pas faire : la cellule du hublot occupe un
 * nombre **entier** de pixels, donc changer de définition change le pas de la
 * trame, la taille d'une LED dans sa cellule et la largeur du cerne. Autrement
 * dit les proportions du dessin, qui sautaient à chaque tap. C'est
 * [MatrixBitmap.MAX_SIDE_PX] qui paie la note.
 *
 * La boucle vise aujourd'hui vingt-cinq images par seconde et non seize, sans que
 * ce plafond bouge : ce qui passe, passe, et ce qui ne passe pas est abandonné
 * plutôt que rattrapé. Monter la cadence visée ne coûte donc rien quand le tuyau
 * ne suit pas, et rend ce qu'il a à donner quand il suit.
 */
internal object WidgetRender {

    const val TAG = "MatrixWidget"

    /** Repli quand le launcher n'a pas encore annoncé de taille. */
    const val DEFAULT_SIDE_PX = 240

    /**
     * Les peintres, **par apparence**.
     *
     * Deux hublots peuvent être réglés différemment ; ils ne peuvent donc pas
     * partager un peintre, dont tout le cache — hublot au repos, halos — dépend
     * justement de l'apparence. Les combinaisons sont deux trames fois deux
     * reflets, et un écran d'accueil n'en porte jamais qu'une ou deux.
     *
     * Ce qui n'y figure pas : le toy. Le dessin d'un hublot ne dépend pas de ce
     * qu'il affiche, seulement de la matrice et de l'apparence — c'est
     * précisément ce qui permet à un même hublot de changer de toy sans rien
     * reconstruire.
     */
    private val surfaces = ConcurrentHashMap<String, WidgetSurface>()

    /**
     * Pose une image et **tout ce qui va avec** : le layout, et l'intention qui
     * transmet le tap. C'est ce qu'il faut à un widget qui se (re)pose.
     */
    fun push(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        brightness: IntArray,
        style: WidgetStyle,
        sidePx: Int,
        tap: PendingIntent,
    ) {
        val views = RemoteViews(context.packageName, R.layout.haloglyph_matrix_widget).apply {
            setImageViewBitmap(R.id.haloglyph_matrix_image, render(brightness, style, sidePx))
            setOnClickPendingIntent(R.id.haloglyph_matrix_image, tap)
        }
        runCatching { manager.updateAppWidget(widgetId, views) }
            .onFailure { Log.w(TAG, "mise à jour du widget $widgetId refusée", it) }
    }

    /**
     * Pose **seulement** l'image, pour une rafale en cours.
     *
     * `partiallyUpdateAppWidget` ne remplace pas la vue : il rejoue une action
     * sur celle qui est déjà là. Le launcher n'a donc ni layout à ré-inflater ni
     * intention de tap à ré-enregistrer soixante fois de suite, et la transaction
     * ne porte que ce qui change — la bitmap, qui pèse déjà bien assez.
     *
     * Sûr parce qu'un widget en rafale a forcément reçu un [push] complet avant :
     * c'est ce qui l'a fait apparaître, et c'est aussi ce qui a posé l'intention
     * dont le tap qu'on est en train de traiter est justement sorti.
     */
    fun pushBurst(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        bitmap: Bitmap,
    ) {
        val views = RemoteViews(context.packageName, R.layout.haloglyph_matrix_widget).apply {
            setImageViewBitmap(R.id.haloglyph_matrix_image, bitmap)
        }
        runCatching { manager.partiallyUpdateAppWidget(widgetId, views) }
            .onFailure { Log.w(TAG, "image de rafale refusée pour $widgetId", it) }
    }

    /**
     * La bitmap seule — pour la rafale, qui rend une fois et pousse à plusieurs
     * hublots réglés pareil.
     */
    fun render(brightness: IntArray, style: WidgetStyle, sidePx: Int): Bitmap =
        surface(style).render(brightness, sidePx)

    /**
     * Côté de la bitmap, déduit de la taille réelle allouée au widget.
     *
     * Les options sont en dp et décrivent une plage : `MIN_WIDTH` et `MIN_HEIGHT`
     * sont les cotes garanties dans **les deux** orientations. On prend la plus
     * petite — la matrice est carrée, le hublot doit tenir en portrait comme en
     * paysage, et déborder n'alourdirait que la transaction.
     */
    fun sidePx(context: Context, manager: AppWidgetManager, widgetId: Int): Int {
        val options = manager.getAppWidgetOptions(widgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val density = context.resources.displayMetrics.density
        val sizeDp = min(widthDp, heightDp)
        if (sizeDp <= 0) return DEFAULT_SIDE_PX
        return MatrixBitmap.clampSide((sizeDp * density).toInt())
    }

    /** Le dernier hublot vient d'être retiré : plus rien à garder en cache. */
    fun releaseAll() {
        for (key in surfaces.keys) surfaces.remove(key)?.release()
    }

    private fun surface(style: WidgetStyle): WidgetSurface =
        surfaces.getOrPut("${style.led.name}.${style.glass}") {
            WidgetSurface(MatrixSpec.Phone3, MatrixLook.LIT_ARGB, style)
        }
}

/**
 * Le matériel de dessin d'une apparence : un peintre et deux tampons par
 * définition utilisée.
 *
 * **Deux tampons, pas un.** La bitmap remise à `updateAppWidget` est lue par le
 * système après le retour de l'appel ; redessiner dedans à l'image suivante
 * ferait scintiller le widget. On alterne donc, et celle qu'on repeint est
 * toujours l'avant-dernière.
 *
 * Un widget garde la même définition du repos à la rafale, mais deux widgets de
 * même apparence peuvent en avoir deux différentes — launchers et écrans ne
 * donnent pas tous la même cote à deux cellules. Et changer de définition
 * reconstruit le hublot au repos et les halos : d'où un peintre par définition,
 * plutôt qu'un seul qu'on ferait osciller.
 *
 * Synchronisé parce que la rafale tourne sur son propre fil, pendant qu'une
 * diffusion du système peut redemander la frame de repos sur le fil principal.
 */
internal class WidgetSurface(
    private val spec: MatrixSpec,
    private val litArgb: Int,
    private val style: WidgetStyle,
) {

    private val painters = HashMap<Int, MatrixPainter>()
    private val buffers = HashMap<Int, Array<Bitmap?>>()
    private var flip = 0

    @Synchronized
    fun render(brightness: IntArray, requestedSidePx: Int): Bitmap {
        val side = MatrixBitmap.clampSide(requestedSidePx)
        // Un launcher qui redimensionne finement pourrait accumuler des
        // définitions ; deux suffisent, la troisième vide la table.
        if (painters.size >= MAX_SIZES && side !in painters) release()

        val painter = painters.getOrPut(side) {
            MatrixPainter(spec, litArgb, bevel = style.glass, style = style.led)
        }
        val pair = buffers.getOrPut(side) { arrayOfNulls(2) }
        flip = flip xor 1
        return MatrixBitmap.render(brightness, spec, side, painter, pair[flip])
            .also { pair[flip] = it }
    }

    /**
     * Les bitmaps du peintre sont recyclées — personne d'autre ne les a vues.
     * Les **tampons**, eux, sont seulement lâchés : le launcher tient peut-être
     * encore le dernier, et recycler une bitmap qu'il affiche le ferait tomber.
     * Le ramasse-miettes s'en occupera quand il n'y aura plus personne.
     */
    @Synchronized
    fun release() {
        for (p in painters.values) p.release()
        painters.clear()
        buffers.clear()
    }

    private companion object {
        const val MAX_SIZES = 2
    }
}
