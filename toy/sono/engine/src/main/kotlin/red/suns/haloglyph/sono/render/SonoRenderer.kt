package red.suns.haloglyph.sono.render

import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.matrix.drawCentered
import red.suns.haloglyph.core.matrix.drawLine
import red.suns.haloglyph.core.matrix.line
import red.suns.haloglyph.sono.engine.Calibration
import red.suns.haloglyph.sono.engine.NoiseFloor
import red.suns.haloglyph.sono.engine.SonoEngine
import red.suns.haloglyph.sono.engine.SonoMode
import red.suns.haloglyph.sono.engine.WaveScale
import red.suns.haloglyph.sono.engine.Waveform
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
 * Une seule mesure, regardée sous trois angles. Ce qui diffère, c'est la
 * référence :
 *
 * - l'**aiguille** affiche un niveau **absolu**, en dB(A). C'est un instrument,
 *   il doit dire un nombre ;
 * - le **spectre** affiche un écart au **fond de la scène**, en décibels
 *   ([NoiseFloor]). Une pièce ordinaire porte trente à cinquante décibels de
 *   bruit permanent : sur une échelle absolue, il affichait une tache qui
 *   occupait le tiers du disque sans rien vouloir dire ;
 * - l'**onde** affiche une **amplitude**, rapportée au plus fort du moment
 *   ([WaveScale]). Pas un écart en décibels : une forme d'onde est plate dans le
 *   silence parce que l'amplitude est linéaire, et pour aucune autre raison.
 *   C'est ce qui la sépare d'un enregistreur, pas le choix du seuil.
 *
 * ## Le tout ou rien
 *
 * Les trois modes sont en tout ou rien : une LED est allumée ou éteinte. Les
 * dégradés le long des barres lissaient le spectre en une masse grise et
 * n'ajoutaient rien que la hauteur ne disait déjà. Le gris ne sert ici qu'à
 * casser l'escalier d'une oblique et à poser un moyeu — jamais à porter une
 * valeur.
 *
 * Un quatrième mode a existé le temps d'une révision : un spectrogramme, qui
 * codait l'intensité en luminosité faute d'axe libre. Il ne survit pas. Sur
 * vingt-cinq LEDs de côté, même par paliers, une intensité ne se lit pas — le
 * dessin restait un nuage gris dont on ne tirait ni une fréquence ni un instant,
 * et il enfreignait la seule règle que les deux autres tiennent.
 */
class SonoRenderer(
    private val spec: MatrixSpec = MatrixSpec.Phone3,
) {
    private val geometry = DiscGeometry(spec)

    /** Le cadran, calculé une fois : les cellules du contour dans le balayage. */
    private val dialArc = geometry.arc(SWEEP)

    /**
     * L'enveloppe récente, remplie à chaque image quel que soit le mode affiché —
     * voir [Waveform]. Elle appartient au renderer parce qu'elle n'est ni une
     * mesure ni un réglage : c'est de l'état d'affichage.
     */
    val waveform = Waveform(spec.size, COLUMN_SECONDS)

    /**
     * Le fond de la scène, une bande à la fois.
     *
     * C'est lui qui décide de ce qui s'allume dans le spectre : un **écart au
     * fond**, pas un niveau absolu. Voir [NoiseFloor] — sans lui, une pièce
     * ordinaire remplit le tiers du disque en permanence et on ne voit plus rien
     * de ce qu'on est venu regarder.
     */
    private val bandFloor = NoiseFloor(spec.size)

    /**
     * L'échelle de l'onde, qui n'est pas celle du spectre.
     *
     * Un spectre se lit en décibels — c'est ce qu'un analyseur affiche, et une
     * bande n'a de sens que rapportée à son propre fond. Une forme d'onde, non :
     * elle trace une **amplitude**, et c'est ce qui la rend plate dans le silence
     * chez un enregistreur. Voir [WaveScale].
     */
    private val waveScale = WaveScale()

    /** Horodatage du dernier instantané versé, pour en déduire le pas de temps. */
    private var lastT = Double.NaN

    fun clearHistory() {
        waveform.clear()
        bandFloor.clear()
        waveScale.clear()
        lastT = Double.NaN
    }

    /**
     * Dessine [snap] dans [frame], déjà effacée.
     *
     * @param feedHistory `false` pour les surfaces qui rejouent le temps à leur
     * guise — un aperçu qui saute en arrière ne doit pas verser ses bonds dans
     * une histoire censée être celle du son, ni dans le suivi du plancher.
     */
    fun render(
        frame: Frame,
        snap: SonoEngine.Snapshot,
        mode: SonoMode,
        feedHistory: Boolean = true,
    ) {
        if (feedHistory && snap.status == SonoEngine.Status.OK) feed(snap)

        if (snap.status != SonoEngine.Status.OK) {
            renderIdle(frame, snap, mode)
            return
        }

        when (mode) {
            SonoMode.SPECTRE -> renderSpectrum(frame, snap)
            SonoMode.AIGUILLE -> renderNeedle(frame, snap)
            SonoMode.ONDE -> renderWave(frame)
        }

        // Surcharge : l'anneau complet en plein, impossible à confondre. Posé
        // après le mode, donc lisible même sur un disque saturé.
        if (snap.overload) for (i in geometry.ring) frame.setAt(i, 1f)
    }

    /**
     * Nourrit l'histoire sans rien dessiner.
     *
     * Le plancher et l'onde sont alimentés **quel que soit le mode affiché** :
     * arriver sur le spectre avec un plancher à rattraper ferait clignoter
     * l'écran le temps qu'il converge.
     *
     * Séparé du rendu pour qui veut remplir une histoire d'avance — le repos du
     * hublot d'écran d'accueil déroule une scène de démonstration une fois pour
     * toutes, puis dessine l'onde obtenue sans plus jamais y toucher.
     */
    fun feed(snap: SonoEngine.Snapshot) {
        val dt = if (lastT.isNaN()) 0.0 else (snap.t - lastT).coerceIn(0.0, MAX_DT)
        lastT = snap.t
        for (k in 0 until minOf(spec.size, snap.bands.size)) {
            bandFloor.update(k, snap.bands[k], dt)
        }
        pushWave(snap, dt)
    }

    /**
     * Verse dans l'onde les crêtes de l'instantané — une colonne par tranche.
     *
     * Le moteur en rapporte deux ou trois par image, chacune mesurée sur sa
     * propre fenêtre de son : c'est ce qui permet à l'onde de défiler plus vite
     * que la matrice ne s'affiche **tout en montrant plus**. Étirer une mesure
     * unique sur trois colonnes irait aussi vite et ne montrerait qu'un escalier.
     *
     * Le repli sur [SonoEngine.Snapshot.lpeak] sert les instantanés qui ne
     * découpent rien — ceux qu'on fabrique à la main, dans un test ou une scène
     * de démonstration simplifiée. Une colonne par image, comme avant.
     */
    private fun pushWave(snap: SonoEngine.Snapshot, dt: Double) {
        if (snap.lpeakCount <= 0) {
            waveScale.decay(dt)
            waveScale.update(snap.lpeak, dt)
            waveform.push(waveScale.amplitude(snap.lpeak), snap.t)
            return
        }
        val slice = SonoEngine.PEAK_SLICE_SECONDS
        for (j in 0 until snap.lpeakCount) {
            val db = snap.lpeaks[j]
            waveScale.decay(slice)
            waveScale.update(db, slice)
            // Les tranches se terminent à `snap.t`, la plus récente en dernier.
            waveform.push(waveScale.amplitude(db), snap.t - (snap.lpeakCount - 1 - j) * slice)
        }
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
     * Chaque colonne est rapportée au **fond de sa propre bande** ([bandFloor]),
     * et non à une échelle absolue. Le bruit de fond d'une pièce est écrasé dans
     * le grave : sur une échelle absolue, les premières colonnes restaient
     * allumées en permanence et le reste ne bougeait jamais. Normalisé, le
     * spectre montre ce qui sort de l'ordinaire — ce pour quoi on le regarde.
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
            // Borné par la largeur de la matrice : c'est elle qui dimensionne
            // `bandFloor`, et un banc plus large que l'écran sortirait du tableau.
            val k = bandOf(x, minOf(spec.size, snap.bands.size))
            val level = bandFloor.position(k, snap.bands[k])
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

    // ---------- mode 3 : l'onde ----------

    /**
     * La forme d'onde, qui défile de droite à gauche.
     *
     * Le même dessin que le spectre — des barres symétriques autour de l'axe
     * médian, tout ou rien, et l'axe toujours allumé — mais l'abscisse est le
     * **temps** : la colonne de droite est l'instant présent, et chaque tranche
     * de [COLUMN_SECONDS] pousse tout le dessin d'une colonne vers la gauche.
     *
     * Ce que montre une colonne est la **crête** du signal sur sa tranche, pas
     * un niveau intégré : voir [SonoEngine.Snapshot.lpeak]. C'est ce qui fait la
     * différence entre une onde et une colline. Et cette crête est rapportée au
     * fond de la scène ([levelFloor]) : dans une pièce calme, il ne reste que
     * l'axe — une ligne droite, comme sur un enregistreur au repos.
     *
     * L'axe médian est tracé sur toute la largeur du disque, y compris là où
     * l'histoire n'est pas encore arrivée : une forme d'onde qui commence à
     * mi-écran se lirait comme un signal coupé.
     */
    private fun renderWave(frame: Frame) = renderWave(frame, spec.centerY, span)

    /**
     * La même onde, **ailleurs et plus petite**.
     *
     * Un seul appelant en a besoin et il a une bonne raison : le repos du hublot
     * d'écran d'accueil pose deux mots au-dessus et au-dessous de l'onde, donc il
     * lui faut une bande et non le disque entier. Ce qui compte est que ce soit
     * **le même code** — mêmes colonnes, même axe, même échelle rapportée à la
     * hauteur disponible. Un dessin qui « ressemblerait » à une forme d'onde
     * n'aurait pas les mêmes crêtes aux mêmes endroits, et ça se voit.
     *
     * @param centerY l'axe médian.
     * @param span la demi-hauteur disponible, en pixels.
     */
    fun renderWave(frame: Frame, centerY: Int, span: Float) {
        for (x in 0 until spec.size) {
            if (geometry.halfSpan(x) <= 0f) continue
            frame.set(x, centerY, 1f)
            // La colonne la plus à droite porte la tranche la plus récente ; en
            // remontant vers la gauche on remonte le temps.
            val level = waveform.at(spec.size - 1 - x)
            if (level <= Waveform.EMPTY) continue
            val h = (level * span).roundToInt()
            for (d in 1..h) {
                frame.set(x, centerY - d, 1f)
                frame.set(x, centerY + d, 1f)
            }
        }
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

    internal companion object {
        /** Demi-balayage du cadran : 90°, soit le demi-disque supérieur en entier. */
        val SWEEP = 90.0 * PI / 180.0

        /** L'aiguille s'arrête sous les graduations, pas dessus. */
        const val R_NEEDLE = 9.5f

        /** Ligne du haut du chiffre. Sept lignes de police, jusqu'à la 21. */
        const val VALUE_Y = 15

        /** Secondes par tour de la comète d'attente. */
        const val IDLE_TURN = 3.0

        /**
         * Une colonne d'onde par **tranche de crête** — 25 colonnes, soit un peu
         * plus de trois dixièmes de seconde à l'écran.
         *
         * Ça l'a été par image de matrice, à 33 ms, et c'était la limite qu'on
         * croyait indépassable : au-delà, deux colonnes auraient porté le même
         * instantané et l'onde se serait étirée au lieu de défiler. La limite
         * était celle de la mesure, pas celle du son — le moteur découpe
         * maintenant chaque image en deux ou trois crêtes
         * ([SonoEngine.PEAK_SLICE_SECONDS]), et chaque colonne porte à nouveau une
         * mesure qui lui appartient.
         *
         * Les deux constantes sont donc la même par construction, et il faut
         * qu'elles le restent : une colonne plus longue qu'une tranche en perdrait
         * une sur deux, plus courte en dupliquerait.
         */
        const val COLUMN_SECONDS = SonoEngine.PEAK_SLICE_SECONDS

        /**
         * Pas de temps maximal pris en compte pour le suivi du plancher.
         *
         * Une image sautée — GC, veille, service suspendu — ne doit pas faire
         * monter le plancher d'un coup de plusieurs décibels.
         */
        const val MAX_DT = 0.25
    }
}
