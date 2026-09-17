package red.suns.haloglyph.plumb.toy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import red.suns.haloglyph.plumb.engine.Heading
import red.suns.haloglyph.plumb.engine.PlumbEngine

/**
 * Le branchement des capteurs sur [PlumbEngine] — la seule classe du toy qui
 * connaisse `SensorManager`.
 *
 * Tout ce qu'elle fait est du routage : elle choisit les capteurs, les enregistre
 * et verse des flottants horodatés. Le filtrage, l'échelle et les décisions sont
 * dans le moteur, qui est pur et donc testable sans téléphone.
 *
 * ## Le capteur réveillant : demandé, et absent
 *
 * Un Glyph Toy s'utilise **téléphone retourné, écran éteint**. Un capteur
 * ordinaire ne réveille pas le processeur d'application : ses mesures peuvent
 * s'accumuler dans un FIFO et arriver en paquet au prochain réveil. Un niveau
 * dans ces conditions n'est pas lent, il est faux — il affiche la pose d'il y a
 * dix secondes. La variante **réveillante** de l'accéléromètre existe dans l'API
 * exactement pour ce cas, et ne demande aucune permission ; on la demande donc
 * d'abord.
 *
 * **Le Phone (3) n'en a pas.** Mesuré au `dumpsys sensorservice` le 14.09.2026 :
 * le HAL n'expose que `lsm6dsv Accelerometer Non-wakeup` et sa variante non
 * calibrée, aucune entrée réveillante. `getDefaultSensor(…, true)` rend donc
 * `null` et c'est le repli qui tourne, ici comme dans le secoueur de Dice.
 *
 * Ça marche quand même, et pour une raison qui n'a rien à voir avec le capteur :
 * pendant qu'un toy est affiché, Glyph Interface **tient le service lié** et la
 * session ouverte, donc le processus reste éveillé. La demande réveillante est
 * gardée parce qu'elle coûte une ligne et qu'un autre appareil peut la servir —
 * pas parce qu'elle est servie ici.
 *
 * ## La boussole ne tourne que quand on la regarde
 *
 * Le magnétomètre et le vecteur de rotation ne sont enregistrés que dans le mode
 * boussole — voir [compass]. Le niveau n'en a aucun besoin, et un instrument qui
 * consomme pour une mesure qu'il n'affiche pas est un instrument mal réglé.
 */
internal class PlumbSensors(
    context: Context,
    private val engine: PlumbEngine,
) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)

    /**
     * Le fil sur lequel les mesures seront livrées : **celui qui a construit cet
     * objet**.
     *
     * Sans lui, `SensorManager` livre sur le fil principal, et le moteur se
     * retrouverait écrit là pendant qu'un hublot le lit depuis le fil de sa
     * rafale. En le capturant ici, chaque surface garde son moteur sur un seul
     * fil — la matrice sur le principal, un hublot sur celui de son animation —
     * et il n'y a plus de partage à protéger.
     *
     * `null` si le fil appelant n'a pas de boucle, ce qui ne devrait pas arriver :
     * on retombe alors sur le comportement par défaut plutôt que de refuser de
     * mesurer.
     */
    private val delivery: Handler? = Looper.myLooper()?.let { Handler(it) }

    /** Voir l'en-tête : réveillant d'abord, ordinaire en repli. */
    private val accelerometer: Sensor? = sensors?.let {
        it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    /**
     * La gravité **fusionnée**, quand le matériel la propose.
     *
     * C'est un capteur composite : la plateforme le calcule à partir de
     * l'accéléromètre et du gyroscope, et c'est précisément ce qu'on vient
     * chercher. Un accéléromètre seul ne distingue pas une inclinaison d'une
     * translation — glisser le téléphone à plat sur une table le fait partir dans
     * tous les sens — alors que le gyroscope, lui, sait qu'il n'a pas tourné.
     *
     * Il n'a pas de variante réveillante, et ça ne change rien : celle de
     * l'accéléromètre n'existe pas non plus sur un Phone (3).
     */
    private val gravity: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)

    /** Le cap fusionné — gyroscope compris — quand le matériel sait le faire. */
    private val rotation: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** Le repli : magnétomètre brut, recombiné avec l'accéléromètre. */
    private val magnetometer: Sensor? =
        if (rotation != null) null else sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** Dernière accélération brute, pour le seul chemin qui en ait besoin. */
    private var rawX = 0f
    private var rawY = 0f
    private var rawZ = 0f
    private var hasRaw = false

    private var trusted = true
    private var listening = false
    private var compassListening = false

    /** Y a-t-il de quoi tenir une boussole sur cet appareil ? */
    val hasCompass: Boolean get() = rotation != null || magnetometer != null

    /**
     * Le mode boussole est-il affiché ?
     *
     * Écrire cette propriété enregistre ou libère le capteur de cap, sans toucher
     * à l'accéléromètre : le niveau et la boussole se relaient sans que
     * l'instrument s'éteigne entre les deux.
     */
    var compass: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (listening) syncCompass()
        }

    fun start() {
        val accel = accelerometer ?: return
        if (listening) return
        listening = true
        sensors?.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME, delivery)
        // L'accéléromètre reste enregistré même quand la gravité fusionnée est là :
        // les secousses et le repos se lisent sur l'accélération brute, et sur elle
        // seule — le capteur fusionné a justement pour but de les gommer.
        gravity?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, delivery) }
        syncCompass()
    }

    fun stop() {
        if (!listening) return
        listening = false
        compassListening = false
        hasRaw = false
        sensors?.unregisterListener(this)
    }

    private fun syncCompass() {
        val sensor = rotation ?: magnetometer
        if (sensor == null) {
            // Rien à écouter, et il faut le dire : sans ça le moteur garderait le
            // dernier cap connu, c'est-à-dire aucun, et la rose tournerait sur
            // une valeur qui n'a jamais existé.
            engine.onHeading(null, true)
            return
        }
        if (compass && !compassListening) {
            compassListening = true
            // `SENSOR_DELAY_GAME`, comme l'accéléromètre, et pas `SENSOR_DELAY_UI`.
            // Les soixante millisecondes de l'UI étaient un choix d'économie fait
            // quand le cap était lissé : le filtre masquait la cadence, donc la
            // baisser ne coûtait rien de visible. Le filtre est parti — le cap est
            // pris tel quel, avec une zone morte, voir `PlumbEngine.onHeading` — et
            // c'est maintenant la cadence du capteur qui **est** la fluidité de la
            // rose. Trois fois plus d'images, trois fois moins de saccade.
            sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, delivery)
        } else if (!compass && compassListening) {
            compassListening = false
            sensors?.unregisterListener(this, sensor)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY ->
                engine.onGravity(event.values[0], event.values[1], event.values[2])

            Sensor.TYPE_ACCELEROMETER -> {
                rawX = event.values[0]
                rawY = event.values[1]
                rawZ = event.values[2]
                hasRaw = true
                engine.onAcceleration(rawX, rawY, rawZ)
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                // L'absence de `w` se lit à la **longueur** du tableau et jamais à
                // son signe : `cos(θ/2)` est négatif au-delà d'un demi-tour.
                val w = if (event.values.size >= 4) event.values[3] else null
                engine.onHeading(
                    Heading.fromRotationVector(event.values[0], event.values[1], event.values[2], w),
                    trusted,
                )
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (!hasRaw) return
                engine.onHeading(
                    Heading.fromVectors(
                        rawX, rawY, rawZ,
                        event.values[0], event.values[1], event.values[2],
                    ),
                    trusted,
                )
            }
        }
    }

    /**
     * Seule la calibration du cap nous intéresse.
     *
     * Un accéléromètre qui se déclare imprécis n'a rien à nous dire de plus : sa
     * précision annoncée ne descend jamais en dessous de ce qu'un niveau demande,
     * et il n'y a de toute façon rien à en faire — le toy afficherait quoi ? Un
     * magnétomètre non calibré, lui, se trompe de plusieurs dizaines de degrés, et
     * l'instrument doit le montrer.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val type = sensor?.type ?: return
        if (type != Sensor.TYPE_ROTATION_VECTOR && type != Sensor.TYPE_MAGNETIC_FIELD) return
        trusted = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
    }
}
