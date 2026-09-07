package red.suns.haloglyph.dice.engine

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * L'orientation du dé, et rien d'autre.
 *
 * Un dé qui rebondit change d'axe à chaque contact, et c'est exactement ce
 * qu'un quaternion compose sans se coincer. Les angles d'Euler bloqueraient au
 * cardan sur le dé posé à plat — pile la position qu'on regarde le plus, celle
 * du gros plan de révélation.
 *
 * Unitaire, `[x, y, z, w]`, repère du dé → monde.
 */
data class Quat(val x: Double, val y: Double, val z: Double, val w: Double) {

    /** `a ∘ b` : la rotation `b`, **puis** la rotation `a`. */
    operator fun times(b: Quat): Quat = Quat(
        w * b.x + x * b.w + y * b.z - z * b.y,
        w * b.y - x * b.z + y * b.w + z * b.x,
        w * b.z + x * b.y - y * b.x + z * b.w,
        w * b.w - x * b.x - y * b.y - z * b.z,
    )

    fun toMatrix(): Mat3 = Mat3(
        doubleArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
            2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
            2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y),
        ),
    )

    companion object {
        val IDENTITY = Quat(0.0, 0.0, 0.0, 1.0)

        fun axis(axis: Vec3, angle: Double): Quat {
            val h = angle / 2
            val s = sin(h)
            return Quat(axis.x * s, axis.y * s, axis.z * s, cos(h))
        }

        /**
         * Interpolation sur l'arc. Le signe est recalé d'abord : deux
         * quaternions opposés désignent la même orientation, et sans ce
         * recalage le dé prend le chemin long — un tour complet là où un
         * huitième de tour suffisait.
         */
        fun slerp(a: Quat, b: Quat, t: Double): Quat {
            var bx = b.x
            var by = b.y
            var bz = b.z
            var bw = b.w
            var d = a.x * bx + a.y * by + a.z * bz + a.w * bw
            if (d < 0) {
                bx = -bx; by = -by; bz = -bz; bw = -bw
                d = -d
            }
            // Presque colinéaires : l'arc est plus court que la précision du
            // sinus, on interpole droit et on renormalise.
            if (d > 0.9995) {
                val qx = a.x + (bx - a.x) * t
                val qy = a.y + (by - a.y) * t
                val qz = a.z + (bz - a.z) * t
                val qw = a.w + (bw - a.w) * t
                // Pas `hypot` : voir [length] dans `Solids.kt`.
                val n = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).let { if (it == 0.0) 1.0 else it }
                return Quat(qx / n, qy / n, qz / n, qw / n)
            }
            val th = acos(d)
            val s = sin(th)
            val wa = sin((1 - t) * th) / s
            val wb = sin(t * th) / s
            return Quat(a.x * wa + bx * wb, a.y * wa + by * wb, a.z * wa + bz * wb, a.w * wa + bw * wb)
        }
    }
}

/**
 * La matrice de rotation d'un quaternion, 3 × 3 en **ligne d'abord** — `m[3r+c]`.
 *
 * Calculée une fois par image, puis relue des centaines de fois : c'est elle qui
 * ramène le rayon de chaque cellule dans le repère du dé, où les plans des faces
 * sont des constantes.
 */
class Mat3(private val m: DoubleArray) {

    operator fun get(i: Int): Double = m[i]

    /** Le vecteur [v] du monde, exprimé dans le repère du dé — donc `Mᵗ · v`. */
    fun toLocal(v: Vec3): Vec3 = Vec3(
        m[0] * v.x + m[3] * v.y + m[6] * v.z,
        m[1] * v.x + m[4] * v.y + m[7] * v.z,
        m[2] * v.x + m[5] * v.y + m[8] * v.z,
    )

    /** Le vecteur [v] du dé, exprimé dans le monde — donc `M · v`. */
    fun toWorld(v: Vec3): Vec3 = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    /**
     * La composante verticale de `M · v` — la deuxième ligne, rien de plus.
     *
     * Nommée parce que c'est le seul critère de « quelle face est en haut », et
     * qu'un `m[3]`, `m[4]`, `m[5]` écrit à trois endroits finit par se lire
     * comme trois critères différents.
     */
    fun upComponent(v: Vec3): Double = m[3] * v.x + m[4] * v.y + m[5] * v.z
}
