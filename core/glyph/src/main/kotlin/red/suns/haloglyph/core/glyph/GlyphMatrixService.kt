package red.suns.haloglyph.core.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

/**
 * Enveloppe du GlyphMatrixSDK : bind/unbind, enregistrement de l'appareil, et
 * réception des événements du Glyph Button.
 *
 * Cette classe existait à l'identique dans trois dépôts (67, 67 et 71 lignes de
 * copie). Elle n'existe plus qu'ici — c'est aussi le seul point à réparer si
 * Nothing change son contrat.
 *
 * **Le binder est à sens unique.** `onBind` renvoie le binder d'un `Messenger` :
 * le système *envoie* des événements au toy, le toy ne renvoie rien. Les frames
 * partent dans l'autre sens, par [GlyphMatrixManager], via un binding séparé et
 * explicite vers `com.nothing.thirdparty`. Aucun canal ne permet de lire la
 * frame d'un toy — ni du sien, ni de celui d'un autre éditeur.
 *
 * ## Ce que `register` répond ne veut rien dire
 *
 * Il rend un booléen, et sur un Phone (3) il rend **faux** tout en fonctionnant :
 * le SDK compare la chaîne d'appareil et prévient « You are targeting A024 as
 * your device » — l'avertissement qu'on lit dans tous les logs où la matrice
 * s'allume. S'y fier a déjà coûté un toy entièrement noir, carrousel compris. Ce
 * qui fait foi, c'est `onServiceConnected`.
 */
abstract class GlyphMatrixService(private val tag: String) : Service() {

    private var manager: GlyphMatrixManager? = null

    private val serviceHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> onTouchPointLongPress()
                GlyphToy.EVENT_AOD -> onAodUpdate()
                GlyphToy.EVENT_ACTION_DOWN -> onTouchPointPressed()
                GlyphToy.EVENT_ACTION_UP -> onTouchPointReleased()
            }
        }
        true
    }

    private val serviceMessenger = Messenger(serviceHandler)

    final override fun onBind(intent: Intent?): IBinder {
        val gm = GlyphMatrixManager.getInstance(applicationContext)
        manager = gm
        gm.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName) {
                gm.register(GLYPH_DEVICE)
                onGlyphConnected(applicationContext, gm)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.d(tag, "service Glyph déconnecté")
            }
        })
        return serviceMessenger.binder
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        onGlyphDisconnected(applicationContext)
        manager?.let {
            // `runCatching` : le service d'en face peut déjà être parti, et une
            // exception dans onUnbind emporte le processus.
            runCatching { it.turnOff() }
            runCatching { it.unInit() }
        }
        manager = null
        return false
    }

    /** Manager actif, `null` tant que le service Glyph n'est pas connecté. */
    protected val matrix: GlyphMatrixManager?
        get() = manager

    /** La matrice est prête : c'est ici qu'on démarre une boucle de rendu. */
    protected open fun onGlyphConnected(context: Context, manager: GlyphMatrixManager) {}

    protected open fun onGlyphDisconnected(context: Context) {}

    /** Pression sur le Glyph Button. */
    protected open fun onTouchPointPressed() {}

    /** Appui long : par convention, « élément suivant » dans le toy. */
    protected open fun onTouchPointLongPress() {}

    protected open fun onTouchPointReleased() {}

    /** Le système demande une frame Always-On : rendu statique, sans animation. */
    protected open fun onAodUpdate() {}
}
