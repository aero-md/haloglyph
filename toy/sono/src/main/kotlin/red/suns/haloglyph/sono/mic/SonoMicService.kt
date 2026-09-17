package red.suns.haloglyph.sono.mic

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import red.suns.haloglyph.sono.MicPermission
import red.suns.haloglyph.sono.R
import red.suns.haloglyph.sono.audio.SonoMic

/**
 * Le micro **armé** : un service de premier plan qui tient la capture ouverte
 * jusqu'à ce qu'on la coupe.
 *
 * C'est la pièce qui répare le toy. Un Glyph Toy est lié par Glyph Interface
 * quand on retourne le téléphone — donc depuis l'arrière-plan, donc sans droit
 * au micro depuis Android 14 (voir [SonoMic]). Ce service-ci démarre pendant
 * qu'une surface de l'app est **visible** : il obtient la capability micro, et
 * il la garde tant qu'il vit. Verrouiller l'écran ne la lui retire pas.
 *
 * Il ne dessine rien et n'a pas d'opinion sur ce que la matrice affiche. Il
 * ouvre le micro, nourrit le moteur partagé, et se tait.
 *
 * ## Les deux façons légales de le démarrer
 *
 * Pendant qu'une activité de l'app est au premier plan — l'écran de réglages,
 * ou l'activité éclair de la tuile — ou bien **par le bouton d'une
 * notification**, qui est une exemption à part entière (voir [MicNotice]). Le
 * second chemin est le bon : il ne referme pas le volet.
 *
 * ## Pourquoi il ne redémarre pas tout seul
 *
 * `START_NOT_STICKY`, délibérément. Un service relancé par le système le serait
 * depuis l'arrière-plan — exactement la situation qui ne donne pas le micro. Il
 * repartirait avec une notification, une pastille micro, et des zéros. Mieux
 * vaut qu'il meure pour de bon : la tuile repasse à l'éteint, ce qui est vrai.
 */
class SonoMicService : Service() {

    private var holding = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISARM) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Sans autorisation il n'y a rien à armer, et une notification
        // « micro en cours » au-dessus d'un micro fermé serait un mensonge.
        if (!MicPermission.isGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!holding) {
            if (!goForeground()) {
                stopSelf()
                return START_NOT_STICKY
            }
            holding = true
            SonoMic.hold(this, durable = true)
            // Notre propre notification dit maintenant la vérité : le raccourci
            // « armer » n'a plus rien à proposer.
            MicNotice.hideArm(this)
            SonoMicTile.refresh(this)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (holding) {
            SonoMic.release(durable = true)
            holding = false
        }
        // En partant, on repose le bouton qu'on avait retiré. C'est ce qui fait
        // qu'on peut réarmer depuis le volet sans passer par l'app ni la tuile.
        MicNotice.showArm(this)
        SonoMicTile.refresh(this)
        super.onDestroy()
    }

    /**
     * @return faux si le système refuse la promotion — ce qui arrive quand on
     * arrive ici depuis l'arrière-plan, et c'est précisément le cas qu'on
     * refuse de maquiller.
     */
    private fun goForeground(): Boolean {
        MicNotice.ensureChannel(this)

        val disarm = PendingIntent.getService(
            this,
            0,
            Intent(this, SonoMicService::class.java).setAction(ACTION_DISARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, MicNotice.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.sono_mic_armed_title))
            .setContentText(getString(R.string.sono_mic_armed_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.sono_mic_disarm), disarm)
            .build()

        return runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        }.onFailure { Log.w(TAG, "premier plan refusé : ${it.message}") }.isSuccess
    }

    internal companion object {
        const val TAG = "SonoMicService"

        /** Coupe le micro armé. Émis par la notification. */
        const val ACTION_DISARM = "red.suns.haloglyph.sono.DISARM"

        /** Distinct de celui du toy : les deux notifications peuvent coexister. */
        const val NOTIF_ID = 4202

        /**
         * Coupe le micro armé, d'où qu'on le demande — tuile ou réglages.
         *
         * `@Suppress` assumé : lint croit qu'un `Intent` neuf ne désignera pas
         * le service déjà lancé. Il se trompe ici — `stopService` résout par
         * composant, pas par identité d'objet, et c'est l'usage documenté.
         */
        @Suppress("ImplicitSamInstance")
        fun disarm(context: Context) {
            context.stopService(Intent(context, SonoMicService::class.java))
        }
    }
}
