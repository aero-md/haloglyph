package red.suns.haloglyph.plumb.engine

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * L'azimut du téléphone, tiré de ce que le matériel veut bien donner.
 *
 * ## Pourquoi ce n'est pas `SensorManager` qui le calcule
 *
 * Android sait le faire : `getRotationMatrixFromVector` puis `getOrientation`,
 * deux appels statiques. Ce sont exactement les formules recopiées ici, et les
 * recopier a un prix — il faut les vérifier, et c'est à ça que servent les tests
 * d'à côté. Ce qu'on achète en échange est la règle du dépôt (TECHNIQUE §3) : le
 * hublot d'écran d'accueil et la vignette du hub instancient le moteur **en
 * direct**, dans un module Kotlin pur, et affichent donc le même cap que la
 * matrice parce qu'ils exécutent le même code. Un moteur qui appellerait
 * `SensorManager` cesserait d'être testable sur une JVM nue, et ferait rentrer
 * Android par la fenêtre du seul module qui n'en veut pas.
 *
 * ## Deux entrées, la même sortie
 *
 * - [fromRotationVector] — le capteur **fusionné**, qui mêle gyroscope,
 *   accéléromètre et magnétomètre. C'est le bon, et de loin : il ne tremble pas
 *   sous une main et encaisse les perturbations magnétiques brèves.
 * - [fromVectors] — le repli, accéléromètre + magnétomètre bruts, pour un
 *   appareil qui ne déclare pas le vecteur de rotation. Nerveux, mais juste.
 *
 * ## Convention
 *
 * L'azimut est le cap du **bord supérieur** du téléphone : 0 au nord, 90 à
 * l'est, mesuré dans le plan horizontal, en degrés dans `[0, 360)`. C'est celle
 * d'Android, et le renderer en fait ce qu'il veut — notamment le miroir, puisque
 * la matrice se regarde par l'arrière et le hublot par l'avant.
 *
 * C'est le **nord magnétique**. Aucune déclinaison n'est appliquée : la corriger
 * demanderait la position, donc une permission, pour un écart que personne ne
 * lit sur une rose de vingt-cinq pixels.
 */
object Heading {

    /**
     * Azimut depuis un quaternion `TYPE_ROTATION_VECTOR`.
     *
     * Les trois premières composantes sont `axe · sin(θ/2)` ; [w] est
     * `cos(θ/2)`, **absent** sur les appareils antérieurs à Android 4.3, d'où le
     * `null` — on le recalcule alors depuis la norme, comme le fait le SDK.
     *
     * Le repli est `null` et non une valeur négative, ce qui a coûté un test :
     * `cos(θ/2)` est légitimement négatif au-delà d'un demi-tour, et prendre le
     * signe pour une sentinelle renvoyait le cap symétrique dès qu'on dépassait
     * 180°. L'absence d'une donnée ne se code pas dans son domaine.
     *
     * La rotation décrite mène du repère de l'appareil au repère du monde (X est,
     * Y nord, Z zénith), et l'azimut vaut `atan2(R[0][1], R[1][1])` : les deux
     * seuls termes développés ici, le reste de la matrice ne servirait à rien.
     */
    fun fromRotationVector(x: Float, y: Float, z: Float, w: Float?): Float {
        val w0 = w ?: run {
            val rest = 1f - x * x - y * y - z * z
            if (rest > 0f) sqrt(rest) else 0f
        }
        val r01 = 2f * (x * y - z * w0)
        val r11 = 1f - 2f * (x * x + z * z)
        return normalize(Math.toDegrees(atan2(r01, r11).toDouble()).toFloat())
    }

    /**
     * Azimut depuis la gravité et le champ magnétique, tous deux dans le repère
     * de l'appareil.
     *
     * La construction est celle de `SensorManager.getRotationMatrix` : le produit
     * vectoriel du champ par la gravité donne l'**est**, la gravité normalisée
     * donne le zénith, et le nord se déduit des deux. L'azimut ne dépend que de
     * la composante Y de l'est et de celle du nord — d'où les quelques produits
     * ci-dessous plutôt qu'une matrice complète dont on jetterait sept termes.
     *
     * @return `null` quand les deux vecteurs sont colinéaires : téléphone pointé
     * pile le long du champ, ou magnétomètre saturé par un aimant. Il n'y a alors
     * pas de cap, et en inventer un serait pire que de le dire.
     */
    fun fromVectors(
        gx: Float, gy: Float, gz: Float,
        mx: Float, my: Float, mz: Float,
    ): Float? {
        // est = champ × gravité
        val hx = my * gz - mz * gy
        val hy = mz * gx - mx * gz
        val hz = mx * gy - my * gx
        val hn = sqrt(hx * hx + hy * hy + hz * hz)
        if (hn < EPSILON) return null

        val gn = sqrt(gx * gx + gy * gy + gz * gz)
        if (gn < EPSILON) return null

        val ex = hx / hn
        val ey = hy / hn
        val ez = hz / hn
        val ax = gx / gn
        val az = gz / gn

        // nord = zénith × est. Seule sa composante Y entre dans l'azimut.
        val northY = az * ex - ax * ez
        if (northY == 0f && ey == 0f) return null
        return normalize(Math.toDegrees(atan2(ey, northY).toDouble()).toFloat())
    }

    /** Ramène un angle dans `[0, 360)`. */
    fun normalize(degrees: Float): Float {
        var a = degrees % 360f
        if (a < 0f) a += 360f
        return a
    }

    /**
     * Écart angulaire signé le plus court de [from] vers [to], dans `]-180, 180]`.
     *
     * C'est ce qui permet de lisser un cap sans qu'il parte faire un tour complet
     * au passage du nord — l'erreur classique, et la seule qui se voie vraiment.
     */
    fun delta(from: Float, to: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    private const val EPSILON = 1e-6f
}
