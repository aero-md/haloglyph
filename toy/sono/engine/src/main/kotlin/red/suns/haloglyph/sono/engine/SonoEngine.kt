package red.suns.haloglyph.sono.engine

import red.suns.haloglyph.sono.dsp.BandAnalyzer
import red.suns.haloglyph.sono.dsp.Detector
import red.suns.haloglyph.sono.dsp.Filter
import red.suns.haloglyph.sono.dsp.Integrator
import red.suns.haloglyph.sono.dsp.Weighting
import red.suns.haloglyph.sono.dsp.FLOOR_DBFS
import red.suns.haloglyph.sono.dsp.msqToDbfs
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

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
     * Plage plausible d'une bande : celle du cadran, décalée vers le bas.
     *
     * Le décalage n'est pas un réglage au jugé. Une énergie répartie sur 25
     * bandes en laisse 10·log10(25) ≈ 14 dB de moins à chacune, si bien qu'un
     * signal qui met l'aiguille en butée ne monte qu'aux trois quarts de la plage
     * du cadran une fois réparti sur le banc.
     *
     * **Plus aucun mode n'affiche de position absolue sur cette plage.** Le
     * spectre et l'onde rapportent leur niveau au fond de la scène
     * ([NoiseFloor]), parce qu'une échelle absolue laissait le bruit de fond
     * d'une pièce ordinaire occuper le tiers du disque en permanence — et parce
     * que la calibration qui la sous-tend est une estimation. Ce qui reste ici
     * est la plage dans laquelle une valeur de bande *tombe*, ce dont
     * [SonoDemo] a besoin pour fabriquer une scène plausible.
     */
    const val SPREAD = 14.0
    const val BAND_MIN = MIN_DB - SPREAD
    const val BAND_MAX = MAX_DB - SPREAD
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
         * Crête **instantanée** depuis l'instantané précédent, en dB SPL.
         *
         * Ni Fast ni Slow : aucune constante de temps du tout, juste le plus fort
         * échantillon pondéré A du dernier trentième de seconde. Les autres modes
         * montrent des niveaux, celui-ci montre le signal — une forme d'onde
         * tirée du niveau Fast serait lissée sur 125 ms, donc une colline là où on
         * attend une onde.
         *
         * C'est le maximum de [lpeaks], gardé pour qui veut une seule valeur.
         */
        val lpeak: Double,
        /**
         * Les crêtes **par tranche de [PEAK_SLICE_SECONDS]**, en dB SPL, de la
         * plus ancienne à la plus récente ; la dernière se termine à [t]. Seules
         * les [lpeakCount] premières valent quelque chose.
         *
         * C'est ce que lit la forme d'onde, et la raison d'être de ce découpage :
         * une colonne par instantané, c'est une colonne par image de matrice, et
         * la vitesse de défilement est alors bornée par la cadence d'affichage.
         * Pour défiler plus vite il faudrait étirer la même valeur sur plusieurs
         * colonnes — un escalier, pas une onde. Le son, lui, a toute la finesse
         * qu'on veut : on la lui prend ici, à la source, et chaque colonne porte
         * une mesure qui lui est propre.
         *
         * **Tableau réutilisé d'un instantané à l'autre**, comme [bands].
         */
        val lpeaks: DoubleArray = DoubleArray(0),
        val lpeakCount: Int = 0,
        /**
         * Niveau par bande, en dB SPL.
         *
         * **Tableau réutilisé d'une image à l'autre**, comme `Frame.toBrightness` :
         * on produit trente instantanés par seconde et on n'alloue pas dans la
         * boucle. Qui garde ces valeurs au-delà de l'image en cours doit les
         * copier.
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

    /**
     * Moyenne glissante de la puissance par bande, et le drapeau qui dit qu'elle
     * est amorcée. Voir [TAU_BAND].
     */
    private val bandAvg = DoubleArray(bandCount)
    private var bandAvgSeeded = false

    private var lafmax = Calibration.MIN_DB
    private var peak = Calibration.MIN_DB
    private var peakAt = 0.0
    private var lastT = 0.0

    private var overloadUntil = -1.0
    private var zeroRun = 0L
    private var muted = false

    /**
     * Les crêtes par tranche, entre le fil du micro qui les écrit et celui du
     * rendu qui les vide.
     *
     * Le verrou n'est pris qu'une fois par tranche — quatre-vingts fois par
     * seconde, pour recopier un nombre — et une fois par instantané. C'est
     * assez peu pour qu'un fil audio n'ait rien à craindre, et assez pour ne pas
     * avoir à raconter une course bénigne dans un commentaire.
     */
    private val peakLock = Any()
    private val slicePeaks = DoubleArray(MAX_SLICES)
    private var sliceCount = 0

    /** Découpage en cours, sur le fil du micro seul. */
    private var sliceAccum = 0.0
    private var sliceSamples = 0

    /**
     * Longueur d'une tranche, **en échantillons**.
     *
     * Compter des échantillons plutôt que des secondes cale le découpage sur
     * l'horloge du son, qui est la seule qui compte ici : une tranche vaut
     * toujours la même durée de signal, que le fil de rendu soit en retard ou
     * non.
     */
    private val slicePeriod = max(1, (fs * PEAK_SLICE_SECONDS).roundToInt())

    /** Tampon de sortie, réutilisé — voir [Snapshot.lpeaks]. */
    private val lpeaksDb = DoubleArray(MAX_SLICES)

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
            val magnitude = abs(a)
            if (magnitude > sliceAccum) sliceAccum = magnitude
            if (++sliceSamples >= slicePeriod) {
                pushSlice()
                sliceAccum = 0.0
                sliceSamples = 0
            }
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

    /**
     * Clôt une tranche.
     *
     * Le tampon plein, c'est la **plus ancienne** qui saute : elle est déjà sortie
     * de l'écran, et garder les récentes vaut mieux que garder les premières
     * arrivées. Ça n'arrive que si le rendu décroche d'un demi-second — un
     * ramasse-miettes, un service suspendu — auquel cas l'onde a de toute façon un
     * trou à combler.
     */
    private fun pushSlice() {
        synchronized(peakLock) {
            if (sliceCount == MAX_SLICES) {
                System.arraycopy(slicePeaks, 1, slicePeaks, 0, MAX_SLICES - 1)
                sliceCount--
            }
            slicePeaks[sliceCount++] = sliceAccum
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
        bandAvg.fill(0.0)
        bandAvgSeeded = false
        synchronized(peakLock) { sliceCount = 0 }
        sliceAccum = 0.0
        sliceSamples = 0
    }

    fun snapshot(now: Double): Snapshot {
        val dt = if (lastT == 0.0) 0.0 else (now - lastT).coerceIn(0.0, 0.5)
        lastT = now

        val laf = msqToDbfs(fast.meanSquare) + calibrationK
        val las = msqToDbfs(slow.meanSquare) + calibrationK
        val laeq = if (integrator.isEmpty) Calibration.MIN_DB
        else msqToDbfs(integrator.meanSquare) + calibrationK

        // Crêtes consommées : chacune est une colonne de forme d'onde, et lire
        // vide le tampon. Converties hors du verrou — il ne tient que la recopie.
        var count: Int
        synchronized(peakLock) {
            count = sliceCount
            System.arraycopy(slicePeaks, 0, lpeaksDb, 0, count)
            sliceCount = 0
        }
        var lpeak = FLOOR_DBFS + calibrationK
        for (j in 0 until count) {
            val db = msqToDbfs(lpeaksDb[j] * lpeaksDb[j]) + calibrationK
            lpeaksDb[j] = db
            if (db > lpeak) lpeak = db
        }

        if (status == Status.OK) {
            if (laf > lafmax) lafmax = laf
            if (laf >= peak) {
                peak = laf
                peakAt = now
            } else if (now - peakAt > PEAK_HOLD) {
                peak = max(laf, peak - PEAK_FALL * dt)
            }

            analyzer.analyze(bandMsq)
            // Moyennage en **puissance**, comme le fait un analyseur de spectre,
            // et pour la raison qui l'y oblige : l'estimation d'une bande est
            // bruitée par construction. Une bande grave ne tient qu'un ou deux
            // points de FFT, sa puissance suit donc une loi à deux degrés de
            // liberté — dont l'écart-type vaut la moyenne, soit une oscillation
            // de plus de dix décibels d'une image à l'autre sur un bruit pourtant
            // parfaitement stable.
            //
            // C'est ce qui restait de la tache de bruit : le plancher suit le
            // minimum de cette oscillation, si bien que l'image courante se
            // trouvait en permanence dix à vingt décibels au-dessus de lui. Cinq
            // images moyennées divisent l'écart-type par plus de deux, et le fond
            // repasse sous la marge de NoiseFloor.GATE_DB.
            val a = if (dt <= 0.0 || !bandAvgSeeded) 1.0 else 1.0 - exp(-dt / TAU_BAND)
            bandAvgSeeded = true
            for (k in 0 until bandCount) {
                bandAvg[k] += (bandMsq[k] - bandAvg[k]) * a
                bandsDb[k] = BandAnalyzer.bandDb(bandAvg[k], calibrationK)
            }
        }

        return Snapshot(
            laf = laf,
            las = las,
            laeq = laeq,
            lafmax = lafmax,
            peak = peak,
            lpeak = lpeak,
            lpeaks = lpeaksDb,
            lpeakCount = count,
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

        /**
         * Durée d'une tranche de crête, en secondes — donc d'une colonne de
         * forme d'onde.
         *
         * Soixante-deux colonnes par seconde : le disque en porte vingt-cinq,
         * l'onde le traverse donc en quatre dixièmes de seconde.
         *
         * Ce qui compte n'est pas tant la valeur que le fait qu'elle reste
         * nettement sous la durée d'une syllabe et au-dessus de la période d'une
         * voix grave. En dessous, on mesurerait le timbre plutôt que le débit et
         * l'onde se hacherait sans rien dire de plus ; au-dessus, deux colonnes
         * se partagent la même crête et le défilement redevient un étirement.
         */
        const val PEAK_SLICE_SECONDS = 0.016

        /**
         * Tranches gardées entre deux instantanés.
         *
         * Trente-deux valent quatre dixièmes de seconde, soit plus que l'écran
         * n'en montre : au-delà, ce qui attend est déjà périmé.
         */
        const val MAX_SLICES = 32

        /**
         * Constante du moyennage en puissance des bandes, en secondes.
         *
         * Deux dixièmes : de quoi moyenner cinq à six images sans que le spectre
         * traîne derrière ce qu'on entend. C'est la plage qu'emploie n'importe
         * quel analyseur en mode « fast average ».
         */
        const val TAU_BAND = 0.2
    }
}
