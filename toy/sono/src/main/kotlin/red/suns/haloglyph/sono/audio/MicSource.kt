package red.suns.haloglyph.sono.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import red.suns.haloglyph.sono.MicPermission
import red.suns.haloglyph.sono.engine.SonoEngine

/**
 * Capture micro et alimentation du moteur.
 *
 * Trois choix qui viennent de la théorie du projet d'origine et qu'on ne
 * renégocie pas :
 *
 * - **`UNPROCESSED`** en premier choix. Les autres sources passent par l'AGC et
 *   la réduction de bruit du HAL, qui bougent le gain sous nos pieds : un
 *   niveau qui monte puis « redescend » tout seul en trois secondes n'est pas
 *   une mesure, c'est un traitement.
 * - **`ENCODING_PCM_FLOAT`**. En 16 bits la détection de plafonnement se fait
 *   sur une valeur déjà saturée par la conversion ; en flottant, l'échantillon
 *   brut dit encore qu'il a tapé le plafond.
 * - **Aucune allocation dans la boucle de lecture.** Le tampon est alloué une
 *   fois ; à 48 kHz un GC au mauvais moment coûte un trou dans le signal.
 *
 * Rien n'est écrit nulle part : les échantillons vont du tampon au moteur, et le
 * moteur n'en garde que des niveaux. Aucun fichier, aucun réseau, aucune trace.
 */
class MicSource(private val engine: SonoEngine) {

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    /** Source réellement obtenue, pour le diagnostic. */
    @Volatile
    var activeSource: String = "—"
        private set

    /** Vrai si la capture a démarré. Le moteur passe alors en [SonoEngine.Status.OK]. */
    fun start(context: Context): Boolean {
        if (running) return true
        if (!MicPermission.isGranted(context)) {
            engine.status = SonoEngine.Status.NO_MIC
            return false
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            engine.status = SonoEngine.Status.NO_MIC
            return false
        }
        // large : la boucle de rendu tourne sur le thread principal et peut
        // laisser le thread audio sans CPU pendant une image entière
        val bufBytes = minBuf * 4

        val rec = openFirstAvailable(context, bufBytes) ?: run {
            engine.status = SonoEngine.Status.NO_MIC
            return false
        }

        record = rec
        running = true
        engine.status = SonoEngine.Status.OK
        rec.startRecording()

        thread = Thread({ loop(rec) }, "haloglyph-sono-mic").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        record?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        record = null
        activeSource = "—"
        engine.status = SonoEngine.Status.NO_MIC
        engine.clear()
    }

    private fun openFirstAvailable(context: Context, bufBytes: Int): AudioRecord? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val unprocessedOk =
            am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

        val candidates = buildList {
            if (unprocessedOk) add("UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED)
            add("VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add("MIC" to MediaRecorder.AudioSource.MIC)
        }

        for ((name, source) in candidates) {
            val rec = runCatching {
                @Suppress("MissingPermission")
                AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufBytes)
            }.getOrNull() ?: continue

            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                activeSource = name
                Log.i(TAG, "capture via $name")
                return rec
            }
            runCatching { rec.release() }
        }
        return null
    }

    private fun loop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = FloatArray(BLOCK)
        while (running) {
            val n = rec.read(buf, 0, BLOCK, AudioRecord.READ_BLOCKING)
            if (n <= 0) {
                // ERROR_INVALID_OPERATION arrive quand le système reprend le
                // micro : on sort proprement plutôt que de tourner à vide
                if (n < 0) {
                    Log.w(TAG, "lecture interrompue ($n)")
                    engine.status = SonoEngine.Status.NO_MIC
                    break
                }
                continue
            }
            engine.feed(buf, n, System.nanoTime() / 1e9)
        }
    }

    private companion object {
        const val TAG = "SonoMic"
        const val SAMPLE_RATE = 48000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT

        /** ~21 ms à 48 kHz : sous la période d'affichage, sans réveiller trop souvent. */
        const val BLOCK = 1024
    }
}
