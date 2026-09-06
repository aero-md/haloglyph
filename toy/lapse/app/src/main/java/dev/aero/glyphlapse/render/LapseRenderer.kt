package dev.aero.glyphlapse.render

import dev.aero.glyphlapse.engine.LapseEngine
import dev.aero.glyphlapse.engine.TimeBreakdown
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Géométrie du disque 25×25 : masque circulaire + anneau du bord trié par angle. */
internal object Disc {
    const val SIZE = 25
    const val CX = 12
    const val CY = 12
    const val RADIUS = 12.5f

    val dist = FloatArray(SIZE * SIZE)
    val inside: IntArray
    /** Cellules du bord (dist ≥ 11,3), triées par angle depuis 12 h, sens horaire. */
    val ring: IntArray

    init {
        val ins = mutableListOf<Int>()
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val i = y * SIZE + x
            dist[i] = hypot((x - CX).toFloat(), (y - CY).toFloat())
            if (dist[i] < RADIUS) ins.add(i)
        }
        inside = ins.toIntArray()
        ring = ins.filter { dist[it] >= 11.3f }
            .sortedBy { i ->
                val x = i % SIZE
                val y = i / SIZE
                val a = atan2((x - CX).toDouble(), -(y - CY).toDouble())
                if (a < 0) a + 2 * PI else a
            }
            .toIntArray()
    }
}

/**
 * Rendu de la matrice : formats d'affichage, anneau/sablier des secondes,
 * slide de changement de format, animation d'arrivée. Sortie : IntArray(625)
 * de luminosités 0..255. Pur Kotlin, port direct de la préview web.
 */
class LapseRenderer {

    internal data class UnitEntry(
        val inline: String,
        val label5: String,
        val cycleLabel: String,
        val value: Int,
    )

    /** Unités pertinentes (hors secondes) : les zéros de tête sont masqués. */
    internal fun units(d: TimeBreakdown.Diff): List<UnitEntry> {
        val all = listOf(
            UnitEntry("A", "A", "A", d.years),
            UnitEntry("M", "M", "M", d.months),
            UnitEntry("J", "J", "J", d.days),
            UnitEntry("H", "H", "H", d.hours),
            UnitEntry("'", "'", "MIN", d.minutes),
        )
        var i = 0
        while (i < all.size - 1 && all[i].value == 0) i++
        return all.subList(i, all.size)
    }

    // ---------- primitives de dessin ----------

    private fun set(g: FloatArray, x: Int, y: Int, b: Float) {
        if (x < 0 || y < 0 || x >= Disc.SIZE || y >= Disc.SIZE) return
        val i = y * Disc.SIZE + x
        if (Disc.dist[i] < Disc.RADIUS && b > g[i]) g[i] = b
    }

    internal fun drawText(g: FloatArray, f: Font, s: String, x0: Int, y0: Int, b: Float) {
        var x = x0
        for (c in s) {
            if (c == ' ') { x += 1; continue }
            val gl = f.glyphs[c]
            if (gl == null) { x += 4; continue }
            for (r in 0 until f.height) for (k in gl[r].indices) {
                if (gl[r][k] == '1') set(g, x + k, y0 + r, b)
            }
            x += gl[0].length + 1
        }
    }

    /**
     * Nudge horizontal : minimise les pixels clippés par le disque (bandes
     * haut/bas). Renvoie (décalage retenu, pixels clippés restants).
     */
    internal fun bestDx(f: Font, s: String, x0: Int, y0: Int): Pair<Int, Int> {
        var best = 0
        var bestClip = Int.MAX_VALUE
        for (dx in intArrayOf(0, -1, 1, -2, 2, -3, 3)) {
            var clip = 0
            var x = x0 + dx
            for (c in s) {
                if (c == ' ') { x += 1; continue }
                val gl = f.glyphs[c]
                if (gl == null) { x += 4; continue }
                for (r in 0 until f.height) for (k in gl[r].indices) {
                    if (gl[r][k] == '1') {
                        val px = x + k
                        val py = y0 + r
                        if (px < 0 || px >= Disc.SIZE || py < 0 || py >= Disc.SIZE ||
                            Disc.dist[py * Disc.SIZE + px] >= Disc.RADIUS
                        ) clip++
                    }
                }
                x += gl[0].length + 1
            }
            if (clip < bestClip) {
                bestClip = clip
                best = dx
                if (clip == 0) break
            }
        }
        return best to bestClip
    }

    /**
     * Ligne centrée + nudge ; si une ligne appariée (séparateur 2 px) clippe
     * encore, on retente avec le groupe resserré (espacement simple).
     */
    internal fun layoutLine(g: FloatArray, f: Font, s: String, y: Int, b: Float) {
        var text = s
        var x0 = ((Disc.SIZE - f.textWidth(s)) / 2.0).roundToInt()
        var fit = bestDx(f, s, x0, y)
        if (fit.second > 0 && ' ' in s) {
            val alt = s.replace(" ", "")
            val ax0 = ((Disc.SIZE - f.textWidth(alt)) / 2.0).roundToInt()
            val afit = bestDx(f, alt, ax0, y)
            if (afit.second < fit.second) {
                text = alt; x0 = ax0; fit = afit
            }
        }
        drawText(g, f, text, x0 + fit.first, y, b)
    }

    private fun linesLayout(g: FloatArray, rows: List<String>, f: Font, b: Float) {
        val h = rows.size * f.height + (rows.size - 1)
        val y0 = ((Disc.SIZE - h) / 2.0).roundToInt()
        rows.forEachIndexed { k, s ->
            layoutLine(g, f, s, y0 + k * (f.height + 1), b)
        }
    }

    private fun drawCentered(g: FloatArray, f: Font, s: String, yc: Int, b: Float) {
        drawText(
            g, f, s,
            ((Disc.SIZE - f.textWidth(s)) / 2.0).roundToInt(),
            (yc - f.height / 2.0).roundToInt(), b,
        )
    }

    // ---------- formats ----------

    internal fun renderContent(
        g: FloatArray,
        fmt: LapseEngine.Format,
        d: TimeBreakdown.Diff,
        t: Double,
    ) {
        val u = units(d)
        if (d.years == 0 && d.months == 0 && d.days == 0 && d.hours == 0 && d.minutes == 0) {
            drawCentered(g, Fonts.F5, "${d.seconds}S", 12, 1f)
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
                linesLayout(g, rows, if (u.size <= 2) Fonts.F5 else Fonts.F3, 1f)
            }

            LapseEngine.Format.COMPACT ->
                linesLayout(g, u.take(2).map { "${it.value}${it.label5}" }, Fonts.F5, 1f)

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
                    drawText(
                        g, Fonts.F5, s,
                        ((Disc.SIZE - Fonts.F5.textWidth(s)) / 2.0).roundToInt(), 4 + dy, 1f,
                    )
                    drawText(
                        g, Fonts.F3, unit.cycleLabel,
                        ((Disc.SIZE - Fonts.F3.textWidth(unit.cycleLabel)) / 2.0).roundToInt(),
                        15 + dy, 0.55f,
                    )
                }
                if (sl < 1 && n > 1) page(prev, (-e * Disc.SIZE).roundToInt())
                page(cur, if (n > 1) ((1 - e) * Disc.SIZE).roundToInt() else 0)
            }

            LapseEngine.Format.DAYS -> {
                val s = if (d.direction == TimeBreakdown.Direction.UNTIL) {
                    "J-${d.totalDays}"
                } else {
                    "${d.totalDays}J"
                }
                val f = if (Fonts.F5.textWidth(s) <= Disc.SIZE) Fonts.F5 else Fonts.F3
                drawCentered(g, f, s, 12, 1f)
            }
        }
    }

    // ---------- secondes : anneau ou sablier ----------

    internal fun renderRing(g: FloatArray, d: TimeBreakdown.Diff) {
        val n = Disc.ring.size
        val k = (d.seconds / 60.0 * n).roundToInt()
        for (j in 0 until k) {
            val idx = if (d.direction == TimeBreakdown.Direction.SINCE) j else (n - 1 - j) % n
            val i = Disc.ring[idx]
            val b = if (j == k - 1) 1f else 0.32f
            if (b > g[i]) g[i] = b
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
    internal fun renderHourglass(g: FloatArray, d: TimeBreakdown.Diff, t: Double) {
        val target = (d.seconds / 60.0 * Disc.inside.size).roundToInt()
        if (target <= 0) return
        val since = d.direction == TimeBreakdown.Direction.SINCE
        fun prof(dx: Int): Double = if (since) SLOPE * dx else SLOPE * (12 - dx)

        var lo = -14.0
        var hi = Disc.SIZE + 14.0
        repeat(14) {
            val mid = (lo + hi) / 2
            var cnt = 0
            for (i in Disc.inside) {
                val x = i % Disc.SIZE
                val y = i / Disc.SIZE
                if (y >= mid + prof(abs(x - Disc.CX))) cnt++
            }
            if (cnt > target) lo = mid else hi = mid
        }
        val yB = (lo + hi) / 2

        for (i in Disc.inside) {
            val x = i % Disc.SIZE
            val y = i / Disc.SIZE
            val depth = y - (yB + prof(abs(x - Disc.CX)))
            if (depth >= 0) {
                // surface irrégulière, corps avec bruit granulaire figé
                val b = if (depth < 1) {
                    if (hash2(x, y) < 0.55f) 0.10f else 0.055f
                } else {
                    0.06f + 0.03f * hash2(x, y)
                }
                if (b > g[i]) g[i] = b
            }
        }

        if (since) {
            val topC = ceil(yB).toInt() // premier pixel de sable au centre (sommet du cône)
            if (topC > 2) {
                // filet continu jusqu'au sommet, grain brillant qui descend
                // + creux sombre en opposition de phase : paquets de sable
                for (y in 0 until topC) set(g, Disc.CX, y, 0.07f)
                val gy = floor((t * 16).mod(topC.toDouble())).toInt()
                set(g, Disc.CX, gy, 0.2f)
                val dark = ((gy + topC / 2.0).mod(topC.toDouble())).toInt() * Disc.SIZE + Disc.CX
                if (g[dark] <= 0.08f) g[dark] = 0.02f
                // éclaboussure posée sur la surface du talus, alternance gauche/droite
                val sx = Disc.CX + if (floor(t * 5).toInt() % 2 == 0) -1 else 1
                set(g, sx, ceil(yB + SLOPE).toInt() - 1, 0.15f)
            }
        }
    }

    private fun renderSeconds(
        g: FloatArray,
        mode: LapseEngine.SecondsMode,
        d: TimeBreakdown.Diff,
        t: Double,
    ) {
        when (mode) {
            LapseEngine.SecondsMode.RING -> renderRing(g, d)
            LapseEngine.SecondsMode.HOURGLASS -> renderHourglass(g, d, t)
        }
    }

    // ---------- animation d'arrivée ----------

    internal fun renderArrival(g: FloatArray, t: Double) {
        // double flash plein disque
        var f = maxOf(
            exp(-6 * t),
            if (t > 0.25) 0.9 * exp(-6 * (t - 0.25)) else 0.0,
        ) * 1.1
        if (f > 0.04) {
            val b = min(1.0, f).toFloat()
            for (i in Disc.inside) if (b > g[i]) g[i] = b
        }
        // 3 ondes concentriques
        for (w in 0 until 3) {
            val r = 14 * (t - 0.3 - w * 0.35)
            if (r > 0 && r < 14) {
                for (i in Disc.inside) {
                    if (abs(Disc.dist[i] - r) < 0.7) g[i] = 1f
                }
            }
        }
        // anneau complet pulsé, fade sur la durée
        val p = ((0.5 + 0.5 * sin(2 * PI * 3 * t)) * (1 - t / LapseEngine.ARRIVAL_DUR)).toFloat()
        for (i in Disc.ring) if (p > g[i]) g[i] = p
    }

    // ---------- frame complète ----------

    /** [includeSeconds] = false en AOD : rendu statique sans anneau/sablier. */
    fun render(snap: LapseEngine.Snapshot, includeSeconds: Boolean = true): IntArray {
        var g = FloatArray(Disc.SIZE * Disc.SIZE)
        renderContent(g, snap.format, snap.diff, snap.t)
        if (includeSeconds) renderSeconds(g, snap.secondsMode, snap.diff, snap.t)

        val slideT = snap.slideT
        if (slideT != null && snap.prevFormat != null) {
            // slide horizontal ancien → nouveau format
            val old = FloatArray(Disc.SIZE * Disc.SIZE)
            renderContent(old, snap.prevFormat, snap.diff, snap.t)
            if (includeSeconds) renderSeconds(old, snap.secondsMode, snap.diff, snap.t)
            val e = 1 - (1 - min(1.0, slideT / LapseEngine.SLIDE)).pow(3)
            val dx = (e * Disc.SIZE).roundToInt()
            val mix = FloatArray(Disc.SIZE * Disc.SIZE)
            fun put(src: FloatArray, off: Int) {
                for (y in 0 until Disc.SIZE) for (x in 0 until Disc.SIZE) {
                    val sx = x + off
                    if (sx < 0 || sx >= Disc.SIZE) continue
                    val b = src[y * Disc.SIZE + sx]
                    if (b > 0) set(mix, x, y, b)
                }
            }
            put(old, dx)
            put(g, dx - Disc.SIZE)
            g = mix
        }

        val arrivalT = snap.arrivalT
        if (arrivalT != null) {
            for (i in Disc.inside) g[i] *= 0.25f
            renderArrival(g, arrivalT)
        }

        return IntArray(Disc.SIZE * Disc.SIZE) { i ->
            (min(1f, g[i]) * 255).toInt()
        }
    }

    private companion object {
        /** Angle de talus du sablier (ligne/colonne). */
        const val SLOPE = 0.45
    }
}
