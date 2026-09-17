package red.suns.haloglyph.sono.mic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import red.suns.haloglyph.sono.MicPermission
import red.suns.haloglyph.sono.R

/**
 * Le volet de notifications, utilisé comme interrupteur du micro.
 *
 * ## Pourquoi le volet plutôt que la tuile
 *
 * Une tuile ne peut pas armer le micro toute seule : taper une tuile ne figure
 * pas dans les exemptions *while-in-use*, et la seule issue — lancer une
 * activité — referme le volet, parce que `startActivityAndCollapse` est la
 * seule méthode que l'API expose. Le geste marche, mais il claque.
 *
 * Un **bouton d'action de notification**, lui, est dans la liste : « le service
 * démarre par interaction avec une notification ». C'est le système qui envoie
 * le `PendingIntent`, donc l'app compte comme sollicitée par une surface
 * visible. Et un bouton d'action ne referme pas le volet — seule une
 * destination de type activité le ferait.
 *
 * ## Une ligne, deux visages
 *
 * Le volet montre toujours exactement un état du micro : le raccourci
 * « armer » quand il dort, la notification du service armé quand il écoute.
 * Les deux vivent sur le même canal — quelqu'un qui coupe ces notifications
 * les coupe toutes, ce qui est ce qu'il demande — et [showArm] / [hideArm] se
 * chargent de ne jamais les laisser coexister.
 */
internal object MicNotice {

    const val CHANNEL = "haloglyph-sono-mic"

    /** Distinct de ceux des services : c'est une troisième ligne possible. */
    private const val SHORTCUT_ID = 4203

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.sono_mic_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
    }

    /**
     * Les notifications sont-elles autorisées ? Sans ça, [showArm] ne fait rien.
     *
     * Deux questions en une, et elles ne se recouvrent pas : la permission
     * d'exécution, qu'on a pu ne jamais demander, et l'interrupteur des réglages
     * du système, qu'on a pu couper après l'avoir accordée. L'une sans l'autre
     * laisserait un bouton inerte dans l'écran de réglages de Sono.
     */
    fun isAllowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Ouvre les réglages de notifications de l'app — pas la fiche générale.
     *
     * C'est le seul endroit où un refus définitif se défait, et y arriver
     * directement évite de faire chercher dans une page qui parle d'autre chose.
     */
    fun openSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /**
     * Pose le raccourci d'armement dans le volet.
     *
     * Sans autorisation micro il n'y a rien à armer, et un bouton qui ne
     * pourrait pas tenir sa promesse vaut moins que pas de bouton.
     */
    fun showArm(context: Context) {
        if (!MicPermission.isGranted(context) || !isAllowed(context)) return
        ensureChannel(context)

        // `getForegroundService` et non `getBroadcast` : le trajet le plus court.
        // C'est le système qui envoie ce `PendingIntent`, et c'est de là que le
        // service tient son droit d'ouvrir le micro depuis l'arrière-plan.
        val arm = PendingIntent.getForegroundService(
            context,
            0,
            Intent(context, SonoMicService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.sono_tile_label))
            .setContentText(context.getString(R.string.sono_mic_shortcut_text))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, context.getString(R.string.sono_mic_arm_action), arm)
            .build()

        // Le même test qu'au début, et il n'est pas redondant : l'autorisation a
        // pu être retirée pendant qu'on construisait la notification, et c'est
        // aussi la forme que lint sait reconnaître — un appel gardé, pas un appel
        // rattrapé. `notify` serait silencieux de toute façon ; s'en remettre à
        // ce silence serait s'en remettre à un détail d'implémentation.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(SHORTCUT_ID, notification)
    }

    /** Retire le raccourci — le micro écoute, la notification du service prend le relais. */
    fun hideArm(context: Context) {
        NotificationManagerCompat.from(context).cancel(SHORTCUT_ID)
    }

    /** Remet le volet d'accord avec la réalité, quel que soit l'état. */
    fun refresh(context: Context, armed: Boolean) {
        if (armed) hideArm(context) else showArm(context)
    }
}
