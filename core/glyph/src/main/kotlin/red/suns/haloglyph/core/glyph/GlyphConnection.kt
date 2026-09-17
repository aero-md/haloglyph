package red.suns.haloglyph.core.glyph

import android.content.Context
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Une connexion au service Glyph **à soi**, que le SDK ne sait pas donner.
 *
 * `GlyphMatrixManager.getInstance(context)` garde une instance **par
 * processus** : un `mService`, un `mConnection`, un `mCallback`. C'est ce qu'il
 * faut à un toy — il n'y en a qu'un de lié à la fois, et [GlyphMatrixService]
 * compte les raisons de tenir cette connexion plutôt que d'en ouvrir une
 * seconde.
 *
 * Ça ne convient pas à tout le monde. [GlyphAvailability] **sonde** : elle
 * initialise, constate, et termine par un `unInit()`. Sur le singleton, ce
 * `unInit` délierait la connexion du toy en train de s'afficher — ouvrir le hub
 * éteindrait la matrice. D'où [own], et le constructeur privé qu'on va chercher
 * par réflexion.
 *
 * Si la réflexion casse un jour, on retombe sur le singleton : la sonde
 * fonctionnera, et le seul risque est celui qu'on vient de décrire. C'est
 * visible et réparable. `consumer-rules.pro` garde tout `com.nothing.**`,
 * membres compris.
 */
internal object GlyphConnection {

    private const val TAG = "GlyphConnection"

    /**
     * Un manager avec sa propre connexion au service Glyph.
     *
     * À réserver à ce qui doit pouvoir se délier sans emporter les autres. Un
     * toy, lui, passe par [GlyphMatrixService] et le singleton du SDK.
     */
    fun own(context: Context): GlyphMatrixManager {
        val app = context.applicationContext
        return runCatching {
            GlyphMatrixManager::class.java
                .getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }
                .newInstance(app)
        }.onFailure {
            Log.w(TAG, "constructeur privé inaccessible, retour au singleton : ${it.message}")
        }.getOrElse { GlyphMatrixManager.getInstance(app) }
    }
}
