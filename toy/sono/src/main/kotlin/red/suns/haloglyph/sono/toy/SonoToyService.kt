package red.suns.haloglyph.sono.toy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.config.PrefsWatcher
import red.suns.haloglyph.core.glyph.MatrixToyService
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.sono.R
import red.suns.haloglyph.sono.SonoConfig
import red.suns.haloglyph.sono.audio.MicSource
import red.suns.haloglyph.sono.engine.SonoEngine
import red.suns.haloglyph.sono.engine.SonoMode
import red.suns.haloglyph.sono.render.SonoRenderer

/**
 * Glyph Toy « Sono » : le micro, montré de trois façons.
 *
 * **Appui long = mode suivant** — spectre, aiguille, spectrogramme — comme Dice
 * change de solide. Un seul toy dans Glyph Interface là où Sonoglyph en
 * exposait deux : ils ouvraient le même micro et faisaient tourner la même FFT
 * pour ne différer que par le dernier étage du rendu.
 *
 * ## Le point dur : capter le micro depuis un toy
 *
 * Un Glyph Toy est un service **lié** par Glyph Interface. Le process est donc
 * en arrière-plan, et depuis Android 11 le micro y est coupé — sans erreur : le
 * flux arrive, rempli de zéros exacts. Un toy qui se contenterait d'ouvrir un
 * `AudioRecord` afficherait un silence parfait et parfaitement faux.
 *
 * La seule sortie est de se promouvoir en service de premier plan de type
 * `microphone` le temps de la mesure, d'où la notification permanente. Cette
 * promotion peut être refusée (`ForegroundServiceStartNotAllowedException`)
 * selon l'état de l'app au moment du bind : on ne la suppose donc pas acquise.
 * Elle est tentée, l'échec est journalisé, et le moteur détecte le cas des zéros
 * exacts pour afficher `---` plutôt qu'un faux 30 dB.
 *
 * ## Cadence
 *
 * 30 images par seconde, tout le temps. Contrairement à Lapse et à Dice, il n'y
 * a pas d'état de repos : tant que le toy est affiché, le son bouge. Une cadence
 * adaptative n'aurait rien à quoi s'adapter.
 */
class SonoToyService : MatrixToyService(TAG) {

    private val engine = SonoEngine()
    private val mic = MicSource(engine)
    private val renderer by lazy { SonoRenderer(spec) }

    private lateinit var prefs: SharedPreferences
    private var watcher: PrefsWatcher? = null

    private var mode: SonoMode = SonoMode.DEFAULT
    private var foreground = false

    private val vibrator: Vibrator by lazy {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    override val frameIntervalMs: Long get() = FRAME_MS

    override fun onGlyphConnected(context: Context, manager: GlyphMatrixManager) {
        prefs = SonoConfig.prefs(context)
        mode = SonoConfig.mode(prefs)
        watcher = PrefsWatcher(prefs) { onPrefsChanged() }.also { it.start() }
        goForeground()
        mic.start(context)
        super.onGlyphConnected(context, manager)
    }

    override fun onGlyphDisconnected(context: Context) {
        watcher?.stop()
        watcher = null
        // Le micro s'arrête **avant** la boucle : mieux vaut une dernière image
        // sans mesure qu'une seconde de capture sans personne pour la regarder.
        mic.stop()
        renderer.clearHistory()
        leaveForeground()
        super.onGlyphDisconnected(context)
    }

    /** Appui long = mode suivant. */
    override fun onTouchPointLongPress() {
        setMode(mode.next)
        // Persiste après avoir basculé : l'écriture réveille le watcher sur le
        // fil principal, et il ne doit trouver que du déjà-fait.
        SonoConfig.setMode(prefs, mode)
    }

    override fun renderFrame(frame: Frame, elapsedSeconds: Double, animated: Boolean) {
        // `animated` est ignoré, et c'est le seul toy du pack dans ce cas : il
        // n'a pas de mode Always-On à distinguer, parce qu'il n'en déclare pas.
        // Un micro ouvert écran éteint pour une image à la minute serait un
        // mauvais marché — pour la batterie comme pour l'indicateur micro.
        renderer.render(frame, engine.snapshot(now()), mode)
    }

    private fun now(): Double = System.nanoTime() / 1e9

    private fun onPrefsChanged() {
        val next = SonoConfig.mode(prefs)
        if (next != mode) setMode(next)
    }

    private fun setMode(next: SonoMode) {
        mode = next
        tick()
        // L'histoire n'est pas effacée : les cinq dernières secondes valent
        // autant vues en spectrogramme qu'elles valaient en spectre, et un
        // écran qui se repeuple lentement donnerait l'impression d'un démarrage.
        renderNow()
    }

    // ---------- premier plan ----------

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.sono_mic_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.sono_mic_notice))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            foreground = true
        }.onFailure {
            // Pas fatal : sur certains chemins de bind le système refuse la
            // promotion. On mesurera si le micro veut bien, et le moteur
            // signalera `MUTED` sinon.
            Log.w(TAG, "premier plan refusé : ${it.message}")
        }
    }

    private fun leaveForeground() {
        if (!foreground) return
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        foreground = false
    }

    private fun tick() {
        runCatching {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private companion object {
        const val TAG = "SonoToy"

        /** ~30 fps. Le son ne se repose pas, la boucle non plus. */
        const val FRAME_MS = 33L

        const val CHANNEL = "haloglyph-sono-mic"
        const val NOTIF_ID = 4201
    }
}
