package red.suns.haloglyph.core.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * « Est-ce qu'il y a une Glyph Matrix en face ? »
 *
 * Le hub en a besoin pour savoir quoi montrer : les toys installables dans
 * Glyph Interface, ou seulement les widgets.
 *
 * **La seule preuve est le callback.** Tester la présence du paquet
 * `com.nothing.thirdparty` ne prouve rien : la visibilité des paquets
 * (Android 11+) peut le masquer sur un téléphone où il existe. On tente donc un
 * `init()` réel et on attend `onServiceConnected`, avec un délai de garde —
 * sur un téléphone qui n'est pas un Nothing, le composant n'existe pas et le
 * callback ne vient jamais.
 *
 * La sonde est volontairement bornée et jetable : elle initialise, constate,
 * puis relâche. Elle ne laisse pas de manager vivant derrière elle.
 */
object GlyphAvailability {

    private const val TAG = "GlyphAvailability"
    private const val DEFAULT_TIMEOUT_MS = 1_500L

    /** Résultat mémorisé pour la durée du processus : le matériel n'apparaît pas en cours de route. */
    @Volatile
    private var cached: Boolean? = null

    val known: Boolean? get() = cached

    /**
     * @param onResult appelé sur le thread principal, exactement une fois.
     */
    fun probe(
        context: Context,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onResult: (Boolean) -> Unit,
    ) {
        cached?.let {
            onResult(it)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val app = context.applicationContext
        var settled = false

        fun settle(available: Boolean, manager: GlyphMatrixManager?) {
            if (settled) return
            settled = true
            cached = available
            manager?.let { runCatching { it.unInit() } }
            handler.post { onResult(available) }
        }

        val manager = runCatching { GlyphMatrixManager.getInstance(app) }.getOrNull()
        if (manager == null) {
            Log.d(TAG, "GlyphMatrixManager indisponible")
            settle(false, null)
            return
        }

        handler.postDelayed({ settle(false, manager) }, timeoutMs)

        val started = runCatching {
            manager.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName) {
                    // `register` peut lever si l'appareil n'est pas celui qu'on croit.
                    settle(runCatching { manager.register(Glyph.DEVICE_23112) }.isSuccess, manager)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    settle(false, null)
                }
            })
        }.isSuccess

        if (!started) settle(false, manager)
    }
}
