package red.suns.haloglyph.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Socle des widgets d'émulation.
 *
 * Un toy dérive cette classe et fournit deux rendus : la frame de repos et,
 * s'il en a une, l'animation jouée au tap. Le reste — taille réelle, budget
 * binder, cadence, bornage de la rafale — est ici.
 *
 * ### Le piège à ne jamais réintroduire
 *
 * **Un widget ne binde jamais le `ToyService`.** Ce service existe pour Glyph
 * Interface ; le binder depuis un widget déclenche `GlyphMatrixManager.init()`,
 * qui tente une connexion à `com.nothing.thirdparty` — inexistant hors Nothing —
 * et laisse un service à moitié initialisé. Le widget instancie le moteur et le
 * renderer **en direct**, dans son propre processus. Même code, deux points
 * d'entrée indépendants ; c'est d'ailleurs pour ça que les moteurs sont du
 * Kotlin pur.
 *
 * ### Deux régimes, pas trente images par seconde
 *
 * - **repos** : une frame statique représentative. Rafraîchie sur changement de
 *   préférence, par [MatrixWidgetRefresh] (plancher système : 15 minutes), et
 *   sur les déclencheurs fournis par le système.
 * - **rafale** : au tap, `goAsync()` puis ~11 fps pendant 5 secondes au plus,
 *   avant de reposer la frame de repos. Au-delà d'une dizaine de secondes le
 *   système tue le receiver : la borne est dans le code, pas dans la chance.
 */
abstract class BaseMatrixWidgetProvider : AppWidgetProvider() {

    /** V1 : Phone (3). Les widgets tournent sur n'importe quel Android. */
    protected open val spec: MatrixSpec get() = MatrixSpec.Phone3

    protected open val style: MatrixBitmap.Style get() = MatrixBitmap.Style()

    /** ~11 fps : au-delà, le binder travaille plus que l'écran ne montre. */
    protected open val burstFrameMs: Long get() = 90L

    /** Borne dure de la rafale. Le système tue le receiver vers 10 s. */
    protected open val burstDurationMs: Long get() = 5_000L

    /** Frame de repos : ce que le widget montre 99 % du temps. */
    protected abstract fun renderIdle(context: Context, frame: Frame)

    /**
     * Une frame de rafale.
     *
     * @return `true` tant que l'animation continue. `false` arrête la rafale
     * avant la borne — une animation courte n'a pas à occuper cinq secondes.
     * Par défaut : pas d'animation.
     */
    protected open fun renderBurst(context: Context, frame: Frame, elapsedSeconds: Double): Boolean =
        false

    /** Appelé au tap, avant la rafale : c'est ici qu'on relance un dé ou un rouleau. */
    protected open fun onTap(context: Context) {}

    // ---------- cycle de vie AppWidget ----------

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) pushIdle(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Redimensionné : la bitmap doit suivre. Rendre à une taille fixe
        // généreuse coûterait le budget binder sans rien apporter.
        pushIdle(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == tapAction()) {
            handleTap(context)
            return
        }
        super.onReceive(context, intent)
    }

    // ---------- rendu ----------

    /** Rafraîchit tous les widgets de ce fournisseur. À appeler sur changement de réglage. */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        for (id in ids) pushIdle(context, manager, id)
    }

    private fun pushIdle(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val frame = Frame(spec)
        renderIdle(context, frame)
        push(context, manager, widgetId, frame)
    }

    private fun push(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        frame: Frame,
        reuseSide: Int = sidePx(context, manager, widgetId),
    ) {
        val bitmap = MatrixBitmap.render(frame.toBrightness(), spec, reuseSide, style)
        val views = RemoteViews(context.packageName, R.layout.haloglyph_matrix_widget).apply {
            setImageViewBitmap(R.id.haloglyph_matrix_image, bitmap)
            setOnClickPendingIntent(R.id.haloglyph_matrix_image, tapIntent(context))
        }
        runCatching { manager.updateAppWidget(widgetId, views) }
            .onFailure { Log.w(TAG, "mise à jour du widget $widgetId refusée", it) }
    }

    /**
     * Côté de la bitmap, déduit de la taille réelle allouée au widget.
     *
     * Les options sont en dp et décrivent une plage (le launcher peut changer
     * d'orientation) ; on prend le plus petit côté : la matrice est carrée, et
     * déborder ne servirait qu'à alourdir la transaction.
     */
    private fun sidePx(context: Context, manager: AppWidgetManager, widgetId: Int): Int {
        val options = manager.getAppWidgetOptions(widgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val density = context.resources.displayMetrics.density
        val sizeDp = min(widthDp, heightDp)
        if (sizeDp <= 0) return DEFAULT_SIDE_PX
        return MatrixBitmap.clampSide((sizeDp * density).toInt())
    }

    // ---------- rafale ----------

    private fun handleTap(context: Context) {
        onTap(context)

        val key = javaClass.name
        // Les instances d'AppWidgetProvider sont recréées à chaque diffusion :
        // l'état « une rafale est en cours » ne peut pas vivre dans l'instance.
        if (bursting.putIfAbsent(key, true) != null) return

        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(ComponentName(appContext, javaClass))
                val frame = Frame(spec)
                val sides = ids.associateWith { sidePx(appContext, manager, it) }
                val startedAt = SystemClock.elapsedRealtime()

                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    if (elapsed >= burstDurationMs) break
                    frame.clear()
                    val goingOn = renderBurst(appContext, frame, elapsed / 1000.0)
                    for (id in ids) push(appContext, manager, id, frame, sides.getValue(id))
                    if (!goingOn) break
                    Thread.sleep(burstFrameMs)
                }

                // Et on repose la frame de repos : un widget ne reste jamais
                // figé sur la dernière image d'une animation interrompue.
                frame.clear()
                renderIdle(appContext, frame)
                for (id in ids) push(appContext, manager, id, frame, sides.getValue(id))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                Log.w(TAG, "rafale interrompue", e)
            } finally {
                bursting.remove(key)
                pending.finish()
            }
        }.start()
    }

    private fun tapAction(): String = "red.suns.haloglyph.WIDGET_TAP.${javaClass.name}"

    private fun tapIntent(context: Context): PendingIntent {
        val intent = Intent(context, javaClass).setAction(tapAction())
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "MatrixWidget"

        /** Repli quand le launcher n'a pas encore annoncé de taille. */
        const val DEFAULT_SIDE_PX = 240

        val bursting = ConcurrentHashMap<String, Boolean>()
    }
}
