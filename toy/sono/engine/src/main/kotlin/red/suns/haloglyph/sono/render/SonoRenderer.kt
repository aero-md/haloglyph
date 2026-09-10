package red.suns.haloglyph.sono.render

import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.matrix.drawCentered
import red.suns.haloglyph.core.matrix.drawLine
import red.suns.haloglyph.core.matrix.line
import red.suns.haloglyph.sono.engine.Calibration
import red.suns.haloglyph.sono.engine.SonoEngine
import red.suns.haloglyph.sono.engine.SonoMode
import red.suns.haloglyph.sono.engine.Spectrogram
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les trois modes du toy Sono, sur un seul disque.
 *
 * ## Ce qui est commun
 *
 * Une seule mesure, une seule échelle. [Calibration.SPREAD] met les bandes et le
 * niveau large bande sur la même scène sonore, si bien que passer d'un mode à
 * l'autre ne change pas l'impression de fort ou de faible — seulement la façon
 * de la montrer.
 *
 * ## Le tout ou rien, et son unique exception
 *
 * [SonoMode.SPECTRE] et [SonoMode.AIGUILLE] sont en tout ou rien : une LED est
 * allumée ou éteinte. Les dégradés le long des barres lissaient le spectre en
 * une masse grise et n'ajoutaient rien que la hauteur ne disait déjà. Le gris n'y
 * sert qu'à casser l'escalier d'une oblique et à poser un moyeu — jamais à
 * porter une valeur.
 *
 * [SonoMode.SPECTROGRAMME] fait exception, et ne peut pas faire autrement : dans
 * un spectrogramme, l'intensité **est** la donnée — les deux axes sont déjà pris
 * par la fréquence et le temps. On la code donc en luminosité, mais **par
 * paliers** : quatre niveaux, pas une rampe continue. Une rampe sur 25 LEDs se
 * lit comme du bruit ; quatre paliers se lisent comme des courbes de niveau.
 */
class SonoRenderer(
    private val spec: MatrixSpec = MatrixSpec.Phone3,
    bands: Int = 25,
) {
    private val geometry = DiscGeometry(spec)

    /** Le cadran, calculé une fois : les cellules du contour dans le balayage. */
    private val dialArc = geometry.arc(SWEEP)

    /**
     * L'histoire du spectre, remplie à chaque image quel que soit le mode
     * affiché — voir [Spectrogram]. Elle appartient au renderer parce qu'elle
     * n'est ni une mesure ni un réglage : c'est de l'état d'affichage.
     */
    val spectrogram = Spectrogram(bands, spec.size, ROW_SECONDS)

    fun clearHistory() = spectrogram.clear()

    /**
     * Dessine [snap] dans [frame], déjà effacée.
     *
     * @param feedHistory `false` pour les surfaces qui rejouent le temps à leur
     * guise — un aperçu qui saute en arrière ne doit pas verser ses bonds dans
     * une histoire censée être celle du son.
     */
    fun render(
        frame: Frame,
        snap: SonoEngine.Snapshot,
        mode: SonoMode,
        feedHistory: Boolean = true,
    ) {
        if (feedHistory && snap.status == SonoEngine.Status.OK) {
            spectrogram.push(snap.bands, snap.t)
        }

        if (snap.status != SonoEngine.Status.OK) {
            renderIdle(frame, snap, mode)
            return
        }

        when (mode) {
            SonoMode.SPECTRE -> renderSpectrum(frame, snap)
            SonoMode.AIGUILLE -> renderNeedle(frame, snap)
            SonoMode.SPECTROGRAMME -> renderSpectrogram(frame)
        }

        // Surcharge : l'anneau complet en plein, impossible à confondre. Posé
        // après le mode, donc lisible même sur un spectrogramme saturé.
        if (snap.overload) for (i in geometry.ring) frame.setAt(i, 1f)
    }

    // ---------- mode 1 : le spectre ----------

    /**
     * Barres symétriques autour de l'axe médian.
     *
     * Sonoglyph proposait aussi un style « colonnes », montant du bas ; il ne
     * survit pas au passage à trois modes, où l'appui long a mieux à faire que
     * de proposer deux fois le même spectre. Le miroir est celui qui reste parce
     * qu'il remplit le hublot et qu'il garde une ligne de base allumée : dans le
     * silence, les colonnes s'éteignent, le miroir non.
     *
     * Il n'y a **pas de marqueur de crête**. Chaque bande en portait un, tenu
     * 0,45 s puis retombant : vingt-cinq points en l'air derrière des barres qui
     * bougent au rythme du son, soit une seconde image décalée dans le temps,
     * superposée à celle qu'on regarde.
     */
    private fun renderSpectrum(frame: Frame, snap: SonoEngine.Snapshot) {
        val cy = spec.centerY
        for (x in 0 until spec.size) {
            if (geometry.halfSpan(x) <= 0f) continue
            val level = Calibration.bandPosition(snap.bands[bandOf(x, snap.bands.size)])
            val h = (level * span).roundToInt()
            // L'axe médian traverse le disque en permanence : c'est la ligne de
            // base du spectre, et à bas niveau c'est la seule chose allumée.
            frame.set(x, cy, 1f)
            for (d in 1..h) {
                frame.set(x, cy - d, 1f)
                frame.set(x, cy + d, 1f)
            }
        }
    }

    // ---------- mode 2 : l'aiguille ----------

    /**
     * VU-mètre à aiguille, niveau chiffré en bas.
     *
     * L'aiguille tourne autour du centre du disque et le cadran occupe la moitié
     * haute, en entier. Le cadran **est** le contour du disque restreint aux
     * angles du balayage : il l'épouse marche par marche, là où un arc tracé à
     * rayon constant décrocherait dans les diagonales.
     */
    private fun renderNeedle(frame: Frame, snap: SonoEngine.Snapshot) {
        drawDial(frame)
        drawNeedle(frame, Calibration.position(snap.laf))
        drawHub(frame)
        // Le niveau, arrondi à l'entier. Pas de décimale : à ±5 dB de
        // calibration près, un dixième afficherait une précision qu'on n'a pas.
        frame.drawLine(Fonts.F5, snap.laf.roundToInt().coerceIn(0, 199).toString(), VALUE_Y, 1f)
    }

    private fun drawDial(frame: Frame) {
        for (i in dialArc) frame.setAt(i, 1f)
        // Cinq graduations majeures : 30, 50, 70, 90 et 110 dB(A). Elles rentrent
        // depuis l'arc — celui-ci est sur la découpe, une graduation sortante
        // n'aurait nulle part où aller. Celle de 70 est la plus longue : c'est le
        // repère qu'on cherche quand on lit le cadran d'un coup d'œil.
        for (k in 0..4) {
            val a = angleAt(k / 4.0)
            val r0 = geometry.edgeDistance(a)
            val len = if (k == 2) 4f else 2.5f
            val n = max(1, (len * 4).roundToInt())
            for (s in 0..n) {
                val r = r0 - len * s / n
                frame.set(polarX(r, a).roundToInt(), polarY(r, a).roundToInt(), 1f)
            }
        }
    }

    /**
     * L'aiguille, du moyeu à [R_NEEDLE]. Une seule : il y en a eu une seconde,
     * en demi-teinte, pour la crête — deux mains sur un cadran de 25 pixels se
     * lisent moins comme un instrument que comme une hésitation.
     */
    private fun drawNeedle(frame: Frame, position: Double) {
        val a = angleAt(position)
        frame.line(
            spec.centerX.toFloat(), spec.centerY.toFloat(),
            polarX(R_NEEDLE, a), polarY(R_NEEDLE, a),
            1f,
        )
    }

    /** Moyeu en croix : l'aiguille a besoin d'un axe visible à tout angle. */
    private fun drawHub(frame: Frame) {
        val cx = spec.centerX
        val cy = spec.centerY
        frame.set(cx, cy, 1f)
        frame.set(cx - 1, cy, 0.5f)
        frame.set(cx + 1, cy, 0.5f)
        frame.set(cx, cy - 1, 0.5f)
        frame.set(cx, cy + 1, 0.5f)
    }

    // ---------- mode 3 : le spectrogramme ----------

    /**
     * Fréquence en abscisse — les mêmes colonnes que le mode spectre, donc la
     * même lecture gauche-droite — et le temps qui descend, le plus récent en
     * haut. Une ligne par tranche de [ROW_SECONDS].
     *
     * Le disque rogne les coins, et c'est la bonne perte : ce qu'il coupe, ce
     * sont les extrêmes du spectre aux instants les plus anciens.
     */
    private fun renderSpectrogram(frame: Frame) {
        for (age in 0 until spectrogram.rows) {
            for (x in 0 until spec.size) {
                val db = spectrogram.at(age, bandOf(x, spec.size))
                if (db <= Spectrogram.EMPTY) continue
                val step = quantize(Calibration.bandPosition(db))
                if (step > 0f) frame.set(x, age, step)
            }
        }
    }

    /** Quatre paliers : éteint, trace, présent, fort. */
    private fun quantize(level: Float): Float = when {
        level < 0.10f -> 0f
        level < 0.35f -> 0.22f
        level < 0.65f -> 0.55f
        else -> 1f
    }

    // ---------- pas de mesure ----------

    /**
     * Micro absent ou coupé.
     *
     * L'aiguille garde son cadran — un instrument sans mesure reste un
     * instrument, et le voir à zéro dit exactement ce qui se passe. Les deux
     * autres modes n'ont pas de forme au repos : ils reçoivent une comète qui
     * fait le tour de l'anneau, pour ne pas mourir noirs.
     *
     * C'était un souffle — l'anneau entier respirant entre 10 et 20 % — mais un
     * anneau à 15 % n'est pas un anneau faible sur une matrice de 25 LEDs, c'est
     * un anneau flou. Un sixième d'anneau à plein qui tourne dit la même chose en
     * tout ou rien, et se lit de loin.
     */
    private fun renderIdle(frame: Frame, snap: SonoEngine.Snapshot, mode: SonoMode) {
        val label = if (snap.status == SonoEngine.Status.MUTED) "---" else "MIC"
        if (mode == SonoMode.AIGUILLE) {
            drawDial(frame)
            drawNeedle(frame, 0.0)
            drawHub(frame)
            frame.drawLine(Fonts.F5, label, VALUE_Y, 1f)
            return
        }
        val n = geometry.ring.size
        val head = ((snap.t / IDLE_TURN) * n).toInt()
        for (k in 0 until n / 6) {
            frame.setAt(geometry.ring[Math.floorMod(head + k, n)], 1f)
        }
        frame.drawCentered(Fonts.F3, label, spec.centerY, 1f)
    }

    // ---------- primitives ----------

    /** Angle d'une position 0..1 sur l'échelle, 0 rad = verticale. */
    private fun angleAt(p: Double): Double = -SWEEP + 2 * SWEEP * p.coerceIn(0.0, 1.0)

    private fun polarX(r: Float, a: Double) = spec.centerX + r * sin(a).toFloat()

    private fun polarY(r: Float, a: Double) = spec.centerY - r * cos(a).toFloat()

    /**
     * Colonne → bande. La matrice a 25 colonnes et le banc 25 bandes, donc
     * l'identité — mais l'indirection reste, pour que changer le nombre de
     * bandes ne casse pas silencieusement le rendu.
     */
    private fun bandOf(x: Int, n: Int): Int =
        (x.toLong() * n / spec.size).toInt().coerceIn(0, n - 1)

    /**
     * Hauteur pleine, **commune à toutes les colonnes**, et non la demi-hauteur
     * du disque à cette colonne. Mettre chaque colonne à son échelle donnait un
     * dôme : un spectre plat s'affichait bombé, et deux colonnes de même niveau
     * n'avaient pas la même hauteur. Ici le disque rogne les colonnes du bord,
     * ce qui est la bonne perte — on lit un spectre, pas une silhouette.
     */
    private val span: Float get() = spec.radius

    private companion object {
        /** Demi-balayage du cadran : 90°, soit le demi-disque supérieur en entier. */
        val SWEEP = 90.0 * PI / 180.0

        /** L'aiguille s'arrête sous les graduations, pas dessus. */
        const val R_NEEDLE = 9.5f

        /** Ligne du haut du chiffre. Sept lignes de police, jusqu'à la 21. */
        const val VALUE_Y = 15

        /** Secondes par tour de la comète d'attente. */
        const val IDLE_TURN = 3.0

        /** Une ligne de spectrogramme toutes les 200 ms — 25 lignes = 5 s. */
        const val ROW_SECONDS = 0.2
    }
}
