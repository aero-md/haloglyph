package red.suns.haloglyph.lapse.render

import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.matrix.centeredX
import red.suns.haloglyph.core.matrix.drawCentered
import red.suns.haloglyph.core.matrix.drawLines
import red.suns.haloglyph.core.matrix.drawText
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.engine.TimeBreakdown
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rendu de la matrice : formats d'affichage, anneau/sablier des secondes,
 * slide de changement de format, animation d'arrivée. Pur Kotlin, port direct
 * de la préview web.
 *
 * Migré depuis GlyphLapse : la géométrie du disque et les primitives de dessin
 * qu'il portait en interne (`Disc`, `set`, `drawText`, `bestDx`, `layoutLine`)
 * vivent maintenant dans `core:matrix`, où les autres toys les partagent. La
 * logique de rendu, elle, n'a pas bougé — c'est ce qui garantit que la matrice
 * affiche encore exactement la même chose.
 *
 * [labels] porte les étiquettes d'unité dans la langue courante. C'est une
 * `var` et non une valeur figée à la construction : le renderer survit à un
 * changement de langue — l'app comme le toy tournent en boucle depuis leur
 * création — et les deux boucles la rafraîchissent à chaque tick.
 */
class LapseRenderer(
    val spec: MatrixSpec = MatrixSpec.Phone3,
    var labels: MatrixLabels = MatrixLabels.current(),
) {

    /**
     * L'anneau des secondes est une **bande**, pas le contour strict : 88 LEDs
     * contre 68. C'est ce qui lui donne son épaisseur, et un pas assez fin pour
     * que 60 secondes se lisent.
     */
    internal val ring: IntArray = spec.ringBand(RING_MIN_DISTANCE)

    /** Tampons de la transition de format, alloués une fois. */
    private val previous = Frame(spec)
    private val mixed = Frame(spec)

    internal data class UnitEntry(
        val inline: String,
        val label5: String,
        val cycleLabel: String,
        val value: Int,
    )

    /** Unités pertinentes (hors secondes) : les zéros de tête sont masqués. */
    internal fun units(d: TimeBreakdown.Diff): List<UnitEntry> {
        val l = labels
        val all = listOf(
            UnitEntry(l.years, l.years, l.years, d.years),
            UnitEntry(l.months, l.months, l.months, d.months),
            UnitEntry(l.days, l.days, l.days, d.days),
            UnitEntry(l.hours, l.hours, l.hours, d.hours),
            UnitEntry(l.minutes, l.minutes, l.minutesCycle, d.minutes),
        )
        var i = 0
        while (i < all.size - 1 && all[i].value == 0) i++
        return all.subList(i, all.size)
    }

    // ---------- formats ----------

    internal fun renderContent(
        frame: Frame,
        fmt: LapseEngine.Format,
        d: TimeBreakdown.Diff,
        t: Double,
    ) {
        val u = units(d)
        if (d.years == 0 && d.months == 0 && d.days == 0 && d.hours == 0 && d.minutes == 0) {
            frame.drawCentered(Fonts.F5, "${d.seconds}${labels.seconds}", spec.centerY, 1f)
            return
        }
        when (fmt) {
            LapseEngine.Format.DETAIL2 -> {
                // Appariement : 2 unités par ligne si besoin, 3×5 partout
                fun s(x: UnitEntry) = "${x.value}${x.inline}"
                val rows = when (u.size) {
                    5 -> listOf(s(u[0]), "${s(u[1])} ${s(u[2])}", "${s(u[3])} ${s(u[4])}")
                    4 -> listOf(s(u[0]), "${s(u[1])} ${s(u[2])}", s(u[3]))
                    else -> u.map { s(it) }
                }
                frame.drawLines(if (u.size <= 2) Fonts.F5 else Fonts.F3, rows, 1f)
            }

            LapseEngine.Format.COMPACT ->
                frame.drawLines(Fonts.F5, u.take(2).map { "${it.value}${it.label5}" }, 1f)

            LapseEngine.Format.CYCLE -> {
                val per = 2.0
                val n = u.size
                val tt = t.mod(per * n)
                val idx = (tt / per).toInt()
                val ph = tt - idx * per
                val sl = min(1.0, ph / 0.3)
                val e = 1 - (1 - sl).pow(3)
                val cur = u[idx]
                val prev = u[(idx + n - 1) % n]

                // Défilement vertical (vers le haut) : le suivant monte depuis le bas,
                // le précédent sort par le haut — distinct du slide horizontal de lapse.
                fun page(unit: UnitEntry, dy: Int) {
                    val s = unit.value.toString()
                    frame.drawText(Fonts.F5, s, frame.centeredX(Fonts.F5, s), 4 + dy, 1f)
                    frame.drawText(
                        Fonts.F3,
                        unit.cycleLabel,
                        frame.centeredX(Fonts.F3, unit.cycleLabel),
                        15 + dy,
                        0.55f,
                    )
                }
                if (sl < 1 && n > 1) page(prev, (-e * spec.size).roundToInt())
                page(cur, if (n > 1) ((1 - e) * spec.size).roundToInt() else 0)
            }

            LapseEngine.Format.DAYS -> {
                // « J-42 » en compte à rebours : la notation du jour J, dont
                // chaque langue a son initiale (D-Day, Tag X, día D, giorno G).
                val s = if (d.direction == TimeBreakdown.Direction.UNTIL) {
                    "${labels.days}-${d.totalDays}"
                } else {
                    "${d.totalDays}${labels.days}"
                }
                val f = if (Fonts.F5.textWidth(s) <= spec.size) Fonts.F5 else Fonts.F3
                frame.drawCentered(f, s, spec.centerY, 1f)
            }
        }
    }

    // ---------- secondes : anneau ou sablier ----------

    internal fun renderRing(frame: Frame, d: TimeBreakdown.Diff) {
        val n = ring.size
        val k = (d.seconds / 60.0 * n).roundToInt()
        for (j in 0 until k) {
            val idx = if (d.direction == TimeBreakdown.Direction.SINCE) j else (n - 1 - j) % n
            frame.setAt(ring[idx], if (j == k - 1) 1f else 0.32f)
        }
    }

    private fun hash2(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return ((h.toLong() and 0xFFFFFFFFL) % 1000L) / 1000f
    }

    /**
     * Sablier en arrière-plan : surface en cône (pointe au centre en depuis,
     * entonnoir en jusqu'à), hauteur de base ajustée par dichotomie pour que
     * le nombre de cellules de sable reste s/60 × 489.
     */
    internal fun renderHourglass(frame: Frame, d: TimeBreakdown.Diff, t: Double) {
        val target = (d.seconds / 60.0 * spec.ledCount).roundToInt()
        if (target <= 0) return
        val since = d.direction == TimeBreakdown.Direction.SINCE
        fun prof(dx: Int): Double = if (since) SLOPE * dx else SLOPE * (spec.centerX - dx)

        var lo = -14.0
        var hi = spec.size + 14.0
        repeat(14) {
            val mid = (lo + hi) / 2
            var cnt = 0
            for (i in spec.leds) {
                if (spec.yOf(i) >= mid + prof(abs(spec.xOf(i) - spec.centerX))) cnt++
            }
            if (cnt > target) lo = mid else hi = mid
        }
        val yB = (lo + hi) / 2

        for (i in spec.leds) {
            val x = spec.xOf(i)
            val y = spec.yOf(i)
            val depth = y - (yB + prof(abs(x - spec.centerX)))
            if (depth >= 0) {
                // surface irrégulière, corps avec bruit granulaire figé
                val b = if (depth < 1) {
                    if (hash2(x, y) < 0.55f) 0.10f else 0.055f
                } else {
                    0.06f + 0.03f * hash2(x, y)
                }
                frame.setAt(i, b)
            }
        }

        if (since) {
            val topC = ceil(yB).toInt() // premier pixel de sable au centre (sommet du cône)
            if (topC > 2) {
                // filet continu jusqu'au sommet, grain brillant qui descend
                // + creux sombre en opposition de phase : paquets de sable
                for (y in 0 until topC) frame.set(spec.centerX, y, 0.07f)
                val gy = floor((t * 16).mod(topC.toDouble())).toInt()
                frame.set(spec.centerX, gy, 0.2f)
                val dark = spec.index(spec.centerX, ((gy + topC / 2.0).mod(topC.toDouble())).toInt())
                if (frame.values[dark] <= 0.08f) frame.put(dark, 0.02f)
                // éclaboussure posée sur la surface du talus, alternance gauche/droite
                val sx = spec.centerX + if (floor(t * 5).toInt() % 2 == 0) -1 else 1
                frame.set(sx, ceil(yB + SLOPE).toInt() - 1, 0.15f)
            }
        }
    }

    private fun renderSeconds(
        frame: Frame,
        mode: LapseEngine.SecondsMode,
        d: TimeBreakdown.Diff,
        t: Double,
    ) {
        when (mode) {
            LapseEngine.SecondsMode.RING -> renderRing(frame, d)
            LapseEngine.SecondsMode.HOURGLASS -> renderHourglass(frame, d, t)
        }
    }

    // ---------- animation d'arrivée ----------

    internal fun renderArrival(frame: Frame, t: Double) {
        // double flash plein disque
        val f = maxOf(
            exp(-6 * t),
            if (t > 0.25) 0.9 * exp(-6 * (t - 0.25)) else 0.0,
        ) * 1.1
        if (f > 0.04) frame.fill(min(1.0, f).toFloat())

        // 3 ondes concentriques
        for (w in 0 until 3) {
            val r = 14 * (t - 0.3 - w * 0.35)
            if (r > 0 && r < 14) {
                for (i in spec.leds) if (abs(spec.distance[i] - r) < 0.7) frame.put(i, 1f)
            }
        }
        // anneau complet pulsé, fade sur la durée
        val p = ((0.5 + 0.5 * sin(2 * PI * 3 * t)) * (1 - t / LapseEngine.ARRIVAL_DUR)).toFloat()
        for (i in ring) frame.setAt(i, p)
    }

    // ---------- frame complète ----------

    /**
     * Dessine l'instantané dans [frame], qu'on suppose vierge.
     *
     * [includeSeconds] = false en AOD : rendu statique sans anneau ni sablier.
     */
    fun render(frame: Frame, snap: LapseEngine.Snapshot, includeSeconds: Boolean = true) {
        require(frame.spec === spec) { "frame ${frame.spec} pour un renderer $spec" }

        renderContent(frame, snap.format, snap.diff, snap.t)
        if (includeSeconds) renderSeconds(frame, snap.secondsMode, snap.diff, snap.t)

        val slideT = snap.slideT
        if (slideT != null && snap.prevFormat != null) {
            // slide horizontal ancien → nouveau format
            previous.clear()
            renderContent(previous, snap.prevFormat, snap.diff, snap.t)
            if (includeSeconds) renderSeconds(previous, snap.secondsMode, snap.diff, snap.t)

            val e = 1 - (1 - min(1.0, slideT / LapseEngine.SLIDE)).pow(3)
            val dx = (e * spec.size).roundToInt()
            mixed.clear()
            blit(previous, dx)
            blit(frame, dx - spec.size)
            frame.copyFrom(mixed)
        }

        val arrivalT = snap.arrivalT
        if (arrivalT != null) {
            frame.dim(0.25f)
            renderArrival(frame, arrivalT)
        }
    }

    /** Recopie [source] dans [mixed], décalée de [offset] colonnes. */
    private fun blit(source: Frame, offset: Int) {
        for (y in 0 until spec.size) {
            for (x in 0 until spec.size) {
                val sx = x + offset
                if (sx < 0 || sx >= spec.size) continue
                val b = source.values[spec.index(sx, y)]
                if (b > 0) mixed.set(x, y, b)
            }
        }
    }

    /** Rendu complet dans une frame neuve. Confort des tests et des aperçus. */
    fun render(snap: LapseEngine.Snapshot, includeSeconds: Boolean = true): IntArray {
        val frame = Frame(spec)
        render(frame, snap, includeSeconds)
        return frame.toBrightness().copyOf()
    }

    private companion object {
        /** Angle de talus du sablier (ligne/colonne). */
        const val SLOPE = 0.45

        /** Rayon à partir duquel une LED appartient à l'anneau des secondes. */
        const val RING_MIN_DISTANCE = 11.3f
    }
}
