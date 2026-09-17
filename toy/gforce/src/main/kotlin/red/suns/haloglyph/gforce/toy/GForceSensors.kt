package red.suns.haloglyph.gforce.toy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import red.suns.haloglyph.gforce.engine.GForceEngine

/**
 * Le branchement des capteurs sur [GForceEngine] — la seule classe du toy qui
 * connaisse `SensorManager`.
 *
 * Tout ce qu'elle fait est du routage : elle choisit les capteurs, les enregistre
 * et verse des flottants. Le repère du véhicule, la projection horizontale et les
 * pics sont dans le moteur, qui est pur et donc testable sans voiture.
 *
 * ## Deux capteurs, et le second n'est pas optionnel
 *
 * - **`TYPE_GRAVITY`** donne la verticale du monde dans le repère de l'appareil.
 *   Sans elle on ne sait pas séparer une accélération d'un support penché, et
 *   six degrés d'erreur valent 0,10 g de mesure fausse.
 * - **`TYPE_LINEAR_ACCELERATION`** donne l'accélération propre, gravité déjà
 *   retirée. C'est un capteur **composite** : la plateforme le calcule à partir de
 *   l'accéléromètre et du gyroscope, et ce dernier est ce qui rend la séparation
 *   honnête pendant un virage.
 *
 * Les deux sont présents sur un Phone (3) — relevé au `dumpsys sensorservice` le
 * 15.09.2026 : `0x5b gravity` et `0x65 linear_acceleration`, tous deux QTI, 5 à
 * 200 Hz. Le repli sur l'accéléromètre brut reste écrit pour le matériel qui ne
 * les propose pas, et il reste juste ; il est simplement moins bien renseigné.
 *
 * ## Cadence
 *
 * `SENSOR_DELAY_GAME`, soit une cinquantaine d'échantillons par seconde. Ce n'est
 * pas pour la fluidité : le moteur filtre à 4 Hz, et un passe-bas a besoin de
 * plusieurs échantillons par période pour être ce qu'il prétend. Échantillonner à
 * la cadence de l'UI mettrait la coupure du filtre au même endroit que celle du
 * capteur, et les deux se battraient.
 */
internal class GForceSensors(
    context: Context,
    private val engine: GForceEngine,
) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)

    /**
     * Le fil sur lequel les mesures seront livrées : **celui qui a construit cet
     * objet**.
     *
     * Sans lui, `SensorManager` livre sur le fil principal, et le moteur se
     * retrouverait écrit là pendant qu'un hublot le lit depuis le fil de sa
     * rafale. En le capturant ici, chaque surface garde son moteur sur un seul fil.
     */
    private val delivery: Handler? = Looper.myLooper()?.let { Handler(it) }

    private val gravity: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val linear: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    /** Le repli, et seulement quand il n'y a pas mieux : voir l'en-tête. */
    private val accelerometer: Sensor? =
        if (linear != null) null else sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var listening = false

    /** Le matériel sait-il de quoi tenir un accéléromètre de bord ? */
    val available: Boolean get() = gravity != null && (linear != null || accelerometer != null)

    fun start() {
        if (listening || !available) return
        listening = true
        gravity?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, delivery) }
        (linear ?: accelerometer)?.let {
            sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, delivery)
        }
    }

    fun stop() {
        if (!listening) return
        listening = false
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY ->
                engine.onGravity(event.values[0], event.values[1], event.values[2])

            Sensor.TYPE_LINEAR_ACCELERATION ->
                engine.onLinear(event.values[0], event.values[1], event.values[2])

            Sensor.TYPE_ACCELEROMETER ->
                engine.onAcceleration(event.values[0], event.values[1], event.values[2])
        }
    }

    /**
     * Rien à en faire.
     *
     * Un accéléromètre ne se déclare jamais imprécis au point de gêner une mesure
     * au dixième de g, et il n'y a de toute façon rien à afficher de plus — un
     * cadran qui dirait « je ne suis pas sûr » sans pouvoir chiffrer son doute ne
     * rendrait service à personne.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
