package red.suns.haloglyph.dice.toy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * La secousse — la seule commande du toy.
 *
 * Un passe-haut sur la norme de l'accélération, un seuil, un temps mort. La
 * gravité est retirée quand le capteur sait le faire, et soustraite par une
 * moyenne glissante sinon : sans ça un téléphone posé mesure déjà 9,81 et
 * n'importe quel seuil utile serait franchi en permanence.
 *
 * ### Le capteur réveillant : demandé, et absent
 *
 * Un Glyph Toy s'utilise **téléphone retourné, écran éteint**. Or un capteur
 * ordinaire ne réveille pas le processeur d'application : les mesures peuvent
 * s'accumuler dans le FIFO du capteur et arriver en paquet au prochain réveil,
 * ce qui donnerait un dé qui se lance trente secondes après le geste. Le
 * [Sensor.TYPE_ACCELEROMETER] **réveillant** existe dans l'API exactement pour
 * ce cas et ne demande aucune permission — ni `WAKE_LOCK`, ni service au premier
 * plan. On le demande donc d'abord.
 *
 * **Le Phone (3) n'en a pas.** Mesuré au `dumpsys sensorservice` le 14.09.2026 :
 * le HAL n'expose que `lsm6dsv Accelerometer Non-wakeup` et sa variante non
 * calibrée. `getDefaultSensor(…, true)` rend donc `null`, et c'est le repli qui
 * a toujours tourné.
 *
 * Si le dé part quand même, c'est pour une autre raison : pendant qu'un toy est
 * affiché, Glyph Interface **tient le service lié** et la session ouverte, donc
 * le processus reste éveillé. La demande réveillante est gardée parce qu'elle
 * coûte une ligne et qu'un autre appareil peut la servir.
 */
class Shaker(context: Context, private val onShake: () -> Unit) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)

    /**
     * Le capteur retenu, et s'il faut lui retirer la gravité.
     *
     * `TYPE_LINEAR_ACCELERATION` donnerait l'accélération nette sans calcul,
     * mais il n'a pas de variante réveillante sur la plupart des appareils —
     * c'est un capteur composite, calculé par le système. Entre « juste sans
     * filtre » et « qui arrive au bon moment », c'est le second qui compte : le
     * filtre tient en trois lignes, le réveil ne se rattrape pas.
     */
    private val sensor: Sensor? = sensors?.let {
        it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    /** Accélération nette lissée, m/s². */
    private var level = 0f

    /** Gravité estimée, dans le repère du téléphone quelle que soit sa pose. */
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var settled = false

    private var lastShake = 0L
    private var listening = false

    val available: Boolean get() = sensor != null

    fun start() {
        val s = sensor ?: return
        if (listening) return
        level = 0f
        settled = false
        listening = sensors?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME) == true
    }

    fun stop() {
        if (!listening) return
        sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rx = event.values[0]
        val ry = event.values[1]
        val rz = event.values[2]

        if (!settled) {
            // Première mesure : la gravité, c'est elle. Partir de zéro ferait
            // passer le repos initial pour une secousse de 9,81 m/s².
            gravityX = rx
            gravityY = ry
            gravityZ = rz
            settled = true
            return
        }

        gravityX += (rx - gravityX) * GRAVITY_SMOOTH
        gravityY += (ry - gravityY) * GRAVITY_SMOOTH
        gravityZ += (rz - gravityZ) * GRAVITY_SMOOTH

        val ax = rx - gravityX
        val ay = ry - gravityY
        val az = rz - gravityZ
        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        level += (magnitude - level) * SMOOTH

        if (level <= THRESHOLD) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastShake < REFRACTORY_MS) return
        lastShake = now
        onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** Seuil, en m/s² d'accélération nette. Un geste franc, pas une marche. */
        const val THRESHOLD = 14f

        /** Temps mort après une secousse retenue : un geste, pas une rafale. */
        const val REFRACTORY_MS = 550L

        /** Lissage de la mesure, et de la gravité. */
        const val SMOOTH = 0.4f
        const val GRAVITY_SMOOTH = 0.06f
    }
}
