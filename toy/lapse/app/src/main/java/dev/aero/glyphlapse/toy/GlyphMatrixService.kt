package dev.aero.glyphlapse.toy

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

/**
 * Wrapper Kotlin autour du GlyphMatrixSDK, repris du GlyphMatrix-Example-Project
 * de Nothing : gère le bind/unbind, l'enregistrement du device et les événements
 * du Glyph Button reçus via Messenger.
 */
abstract class GlyphMatrixService(private val tag: String) : Service() {

    private var manager: GlyphMatrixManager? = null

    private val serviceHandler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            GlyphToy.MSG_GLYPH_TOY -> {
                when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                    GlyphToy.EVENT_CHANGE -> onTouchPointLongPress()
                    GlyphToy.EVENT_AOD -> onAodUpdate()
                    GlyphToy.EVENT_ACTION_DOWN -> onTouchPointPressed()
                    GlyphToy.EVENT_ACTION_UP -> onTouchPointReleased()
                }
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
                gm.register(Glyph.DEVICE_23112)
                performOnServiceConnected(applicationContext, gm)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.d(tag, "Glyph service disconnected")
            }
        })
        return serviceMessenger.binder
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        performOnServiceDisconnected(applicationContext)
        manager?.let {
            runCatching { it.turnOff() }
            runCatching { it.unInit() }
        }
        manager = null
        return false
    }

    /** Manager actif, null tant que le service Glyph n'est pas connecté. */
    protected val matrix: GlyphMatrixManager?
        get() = manager

    open fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {}
    open fun performOnServiceDisconnected(context: Context) {}
    open fun onTouchPointPressed() {}
    open fun onTouchPointLongPress() {}
    open fun onTouchPointReleased() {}
    open fun onAodUpdate() {}
}
