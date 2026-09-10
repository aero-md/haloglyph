package red.suns.haloglyph.sono.engine

import red.suns.haloglyph.sono.dsp.BandAnalyzer
import red.suns.haloglyph.sono.dsp.Detector
import red.suns.haloglyph.sono.dsp.Filter
import red.suns.haloglyph.sono.dsp.Integrator
import red.suns.haloglyph.sono.dsp.Weighting
import red.suns.haloglyph.sono.dsp.msqToDbfs
import kotlin.math.abs
import kotlin.math.max

/**
 * Calibration dBFS → dB SPL.
 *
 * `Lp = dBFS + K`. La valeur ci-dessous **n'est pas mesurée** : c'est l'ordre de
 * grandeur d'un MEMS de téléphone, où la pleine échelle tombe vers 120 dB SPL.
 * Tant qu'elle n'est pas relevée sur l'exemplaire, le chiffre affiché est juste
 * à ±5 dB près, et le toy ne prétend à rien de plus : c'est un indicateur, pas
 * un sonomètre.
 */
object Calibration {
    const val K = 120.0

    /** Bornes de l'échelle affichée, communes aux trois modes. */
    const val MIN_DB = 30.0
    const val MAX_DB = 110.0

    /** Position 0..1 d'un niveau sur l'échelle. */
    fun position(db: Double): Double =
        ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0)

    /**
     * Plage d'une bande : celle du cadran, décalée vers le bas.
     *
     * Le décalage n'est pas un réglage au jugé. Une énergie répartie sur 25
     * bandes en laisse 10·log10(25) ≈ 14 dB de moins à chacune, si bien qu'un
     * signal qui met l'aiguille en butée ne monterait qu'aux trois quarts du
     * spectre avec la plage du cadran. Les trois modes couvrent ainsi la même
     * scène sonore.
     */
    const val SPREAD = 14.0
    const val BAND_MIN = MIN_DB - SPREAD
    const val BAND_MAX = MAX_DB - SPREAD

    /** Position 0..1 d'un niveau de bande sur l'échelle des bandes. */
    fun bandPosition(db: Double): Float =
        (((db - BAND_MIN) / (BAND_MAX - BAND_MIN)).coerceIn(0.0, 1.0)).toFloat()
}

/**
 * Moteur de mesure : consomme des échantillons, produit un état affichable.
 *
 * Kotlin pur, zéro dépendance Android. Le temps est injecté, jamais lu : un test
 * rejoue une salve en quelques millisecondes.
 *
 * Une seule instance sert les trois modes du toy — ils regardent la même mesure
 * sous trois angles, et changer de mode ne redémarre donc ni la capture ni
 * l'intégration.
 */
class SonoEngine(
    private val fs: Double = 48000.0,
    private val calibrationK: Double = Calibration.K,
    val bandCount: Int = 25,
) {
    enum class Status {
        /** Mesure en cours. */
        OK,

        /** Permission absente ou capture impossible. */
        NO_MIC,

        /**
         * Le flux arrive mais ne contient que des zéros exacts. C'est la
         * signature du micro coupé par le système en arrière-plan, pas celle
         * d'une pièce calme — qui a toujours un plancher de bruit.
         */
        MUTED,
    }

    data class Snapshot(
        /** Niveau instantané, pondération A, temporelle Fast. */
        val laf: Double,
        /** Le même en Slow — l'aiguille s'en sert pour ne pas trembler. */
        val las: Double,
        /** Niveau équivalent depuis le dernier reset. */
        val laeq: Double,
        /** Maximum Fast depuis le dernier reset. */
        val lafmax: Double,
        /** Marqueur de crête : tient [PEAK_HOLD] s puis redescend. */
        val peak: Double,
        /**
         * Niveau par bande, en dB SPL.
         *
         * **Tableau réutilisé d'une image à l'autre**, comme `Frame.toBrightness` :
         * on produit trente instantanés par seconde et on n'alloue pas dans la
         * boucle. Qui garde ces valeurs au-delà de l'image en cours doit les
         * copier — c'est ce que fait [Spectrogram].
         */
        val bands: DoubleArray,
        val overload: Boolean,
        val status: Status,
        /** Secondes d'intégration accumulées. */
        val elapsed: Double,
        val t: Double,
    )

    private val aFilter: Filter = Weighting.aWeighting(fs)
    private val fast = Detector(fs, Detector.TAU_FAST)
    private val slow = Detector(fs, Detector.TAU_SLOW)
    private val integrator = Integrator()
    private val analyzer = BandAnalyzer(fs, bandCount)

    private val bandMsq = DoubleArray(bandCount)
    private val bandsDb = DoubleArray(bandCount) { Calibration.MIN_DB }

    private var lafmax = Calibration.MIN_DB
    private var peak = Calibration.MIN_DB
    private var peakAt = 0.0
    private var lastT = 0.0

    private var overloadUntil = -1.0
    private var zeroRun = 0L
    private var muted = false

    @Volatile
    var status: Status = Status.NO_MIC

    /** Alimente le moteur avec [n] échantillons de [samples], dans [-1, 1]. */
    fun feed(samples: FloatArray, n: Int, now: Double) {
        var clipped = false
        var allZero = true
        for (i in 0 until n) {
            val x = samples[i].toDouble()
            if (x != 0.0) allZero = false
            if (abs(x) >= CLIP_LEVEL) clipped = true
            // la surcharge se lit sur l'échantillon brut : une fois filtré, le
            // plafonnement n'est plus détectable
            val a = aFilter.process(x)
            fast.process(a)
            slow.process(a)
            integrator.process(a)
            analyzer.push(a)
        }
        if (clipped) overloadUntil = now + OVERLOAD_LATCH
        zeroRun = if (allZero) zeroRun + n else 0L
        muted = zeroRun > fs
        if (status != Status.NO_MIC) {
            status = if (muted) Status.MUTED else Status.OK
        }
    }

    /** Remet à zéro l'intégration, le maximum et la crête. */
    fun reset() {
        integrator.reset()
        lafmax = Calibration.MIN_DB
        peak = Calibration.MIN_DB
    }

    /** Vide l'état de mesure — appelé quand la capture s'arrête. */
    fun clear() {
        aFilter.reset()
        fast.reset()
        slow.reset()
        analyzer.reset()
        reset()
        zeroRun = 0L
        muted = false
    }

    fun snapshot(now: Double): Snapshot {
        val dt = if (lastT == 0.0) 0.0 else (now - lastT).coerceIn(0.0, 0.5)
        lastT = now

        val laf = msqToDbfs(fast.meanSquare) + calibrationK
        val las = msqToDbfs(slow.meanSquare) + calibrationK
        val laeq = if (integrator.isEmpty) Calibration.MIN_DB
        else msqToDbfs(integrator.meanSquare) + calibrationK

        if (status == Status.OK) {
            if (laf > lafmax) lafmax = laf
            if (laf >= peak) {
                peak = laf
                peakAt = now
            } else if (now - peakAt > PEAK_HOLD) {
                peak = max(laf, peak - PEAK_FALL * dt)
            }

            analyzer.analyze(bandMsq)
            for (k in 0 until bandCount) {
                bandsDb[k] = BandAnalyzer.bandDb(bandMsq[k], calibrationK)
            }
        }

        return Snapshot(
            laf = laf,
            las = las,
            laeq = laeq,
            lafmax = lafmax,
            peak = peak,
            bands = bandsDb,
            overload = now < overloadUntil,
            status = status,
            elapsed = integrator.samples / fs,
            t = now,
        )
    }

    companion object {
        /** Seuil de détection du plafonnement sur échantillon brut. */
        const val CLIP_LEVEL = 0.999

        /** Durée d'affichage de l'indicateur de surcharge après le dernier clip. */
        const val OVERLOAD_LATCH = 1.5

        const val PEAK_HOLD = 1.2
        const val PEAK_FALL = 22.0 // dB/s
    }
}
