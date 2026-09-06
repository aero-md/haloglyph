package red.suns.haloglyph.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Rafraîchissement périodique de la frame de repos.
 *
 * Par ordre de préférence, un widget se remet à jour :
 * 1. quand une préférence change — c'est immédiat et c'est le cas qui compte ;
 * 2. sur les déclencheurs du système (ajout, redimensionnement, redémarrage) ;
 * 3. et, en dernier recours, périodiquement — d'où cette classe.
 *
 * **15 minutes est un plancher système**, pas un réglage : `WorkManager` refuse
 * plus court. Deux mécanismes plus fins ont été écartés : `ACTION_TIME_TICK`,
 * qu'un receiver déclaré au manifeste ne peut pas recevoir, et les alarmes
 * exactes, qui demandent une permission spéciale — injustifiable pour un widget
 * décoratif, et le genre de demande qui fait échouer une revue Play.
 */
object MatrixWidgetRefresh {

    private const val WORK_PREFIX = "haloglyph-widget-"
    internal const val KEY_PROVIDER = "provider"

    /** Plancher imposé par le système. Écrit ici pour qu'on arrête d'espérer. */
    const val MIN_INTERVAL_MINUTES = 15L

    fun schedule(context: Context, provider: Class<out BaseMatrixWidgetProvider>) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            MIN_INTERVAL_MINUTES, TimeUnit.MINUTES,
        )
            .setInputData(workDataOf(KEY_PROVIDER to provider.name))
            .setConstraints(
                // Aucune contrainte réseau : un widget de matrice ne dépend de
                // rien. `setRequiresBatteryNotLow` évite de réveiller un
                // téléphone à 3 % pour redessiner des points.
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PREFIX + provider.name,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context, provider: Class<out BaseMatrixWidgetProvider>) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + provider.name)
    }

    /**
     * Réveille un fournisseur de widgets par diffusion.
     *
     * Passe par `ACTION_APPWIDGET_UPDATE` plutôt que par une instance : le
     * provider est un receiver, il n'a pas d'instance stable à qui parler, et
     * cette route est la même depuis un `Worker`, un écran de réglages ou un
     * service de toy.
     */
    fun requestUpdate(context: Context, provider: Class<out BaseMatrixWidgetProvider>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return
        val intent = Intent(context, provider)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }

    class RefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            val name = inputData.getString(KEY_PROVIDER) ?: return Result.failure()
            val provider = runCatching {
                @Suppress("UNCHECKED_CAST")
                Class.forName(name) as Class<out BaseMatrixWidgetProvider>
            }.getOrNull() ?: return Result.failure()

            requestUpdate(applicationContext, provider)
            return Result.success()
        }
    }
}
