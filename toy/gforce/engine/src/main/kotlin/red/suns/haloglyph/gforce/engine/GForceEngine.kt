package red.suns.haloglyph.gforce.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * L'accéléromètre de bord : ce que la voiture fait subir, en g.
 *
 * Kotlin pur, comme tous les moteurs du pack. On lui verse des flottants, il rend
 * un [Reading] — c'est ce qui permet à la matrice, au hublot et à la vignette du
 * hub d'afficher le même cadran, et c'est ce qui rend tout ceci testable sans
 * voiture.
 *
 * ## Le vrai problème n'est pas le capteur, c'est l'orientation
 *
 * Le LSM6DSV d'un Phone (3) laisse passer moins d'un millième de g de bruit une
 * fois filtré à 4 Hz. Son défaut d'usine — quelques dizaines de milli-g d'offset,
 * un pour cent de gain — borne la mesure autour de **±0,03 g**, ce qui est
 * largement sous ce qu'on lit sur un cadran de vingt-cinq LEDs.
 *
 * L'orientation, elle, se trompe d'un ordre de grandeur au-dessus. **Un téléphone
 * penché de six degrés laisse fuir 0,10 g de gravité dans ses axes horizontaux**,
 * soit cinq fois l'erreur du capteur, et un support de pare-brise n'est jamais
 * d'aplomb. D'où la construction ci-dessous, qui ne fait pas confiance au support :
 *
 * 1. la **gravité fusionnée** donne la verticale du monde, dans le repère de
 *    l'appareil ;
 * 2. l'accélération propre est **projetée sur le plan horizontal** — ce qui
 *    supprime d'un coup l'inclinaison du support *et* la pente de la route. Freiner
 *    dans une descente à 5 % ajouterait sinon 0,05 g de pure géométrie ;
 * 3. l'avant du véhicule est déduit de la pose du téléphone, voir [FORWARD_KEEP].
 *
 * Ce qui reste hors de portée est le **lacet** : rien dans les capteurs ne dit où
 * pointe la voiture. On suppose que le téléphone est tenu, et que son dos regarde
 * la route. Un téléphone qui roule dans un vide-poches ne mesure rien d'utile, et
 * aucun filtrage n'y changera quoi que ce soit.
 *
 * ## Le filtre est un plancher, pas un confort
 *
 * Un passe-bas à 4 Hz n'est pas là pour lisser l'affichage : il est là parce que
 * **la suspension bat plus vite que la voiture n'accélère**. Un nid-de-poule
 * envoie deux g en trente millisecondes ; sans filtre, le premier trou de la route
 * devient le pic de la journée et le cadran ment pour le reste du trajet.
 *
 * Il **divise**, il n'efface pas : une secousse d'un seul échantillon passe au
 * tiers, trois échantillons passent aux trois quarts. Couper plus bas abîmerait la
 * montée d'un vrai freinage, qui s'établit en trois cents millisecondes — et un
 * cadran qui sous-évalue un freinage est plus gênant qu'un cadran qui compte un
 * pavé de travers.
 *
 * ## Les pics ne survivent pas au toy
 *
 * Ils vivent dans cette instance et nulle part ailleurs : pas de préférence, pas
 * de fichier. Fermer le toy les efface, et c'est le comportement demandé — un pic
 * de g est l'histoire d'un trajet, pas un record à battre.
 */
class GForceEngine {

    /**
     * Ce que subit le conducteur, à un instant.
     *
     * Les quatre pics sont nommés par **la direction dans laquelle le corps est
     * jeté**, qui est aussi celle où part la bille sur le cadran : [front] est donc
     * le freinage, [rear] l'accélération, [right] un virage à gauche. C'est le seul
     * nommage qui reste vrai des deux côtés — la bille et le nombre disent la même
     * chose au même endroit.
     */
    data class Reading(
        /** Poussée ressentie vers l'avant, en g. Positif = on freine. */
        val towardFront: Float,
        /** Poussée ressentie vers la droite, en g. Positif = virage à gauche. */
        val towardRight: Float,
        /** Au moins une mesure complète est arrivée. */
        val hasFix: Boolean,
        val front: Float,
        val rear: Float,
        val left: Float,
        val right: Float,
    )

    // ---------- l'état ----------

    private var ax = 0f
    private var ay = 0f
    private var az = 0f
    private var hasAccel = false

    private var gx = 0f
    private var gy = 0f
    private var gz = 1f
    private var hasGravity = false

    /**
     * Sur quel axe de l'appareil on lit l'avant du véhicule : `true` pour le dos
     * (−Z), `false` pour le haut (+Y).
     */
    private var backIsForward = true

    private var towardFront = 0f
    private var towardRight = 0f

    private var peakFront = 0f
    private var peakRear = 0f
    private var peakLeft = 0f
    private var peakRight = 0f

    // ---------- entrées ----------

    /**
     * La gravité, fusionnée par la plateforme (accéléromètre + gyroscope).
     *
     * Non lissée : elle l'est déjà, et la lisser encore ferait traîner la verticale
     * pendant un virage — exactement le moment où elle doit être juste.
     */
    fun onGravity(x: Float, y: Float, z: Float) {
        gx = x
        gy = y
        gz = z
        hasGravity = true
        recompute()
    }

    /**
     * L'accélération **propre**, gravité déjà retirée par la plateforme
     * (`TYPE_LINEAR_ACCELERATION`), dans le repère de l'appareil.
     */
    fun onLinear(x: Float, y: Float, z: Float) {
        if (!hasAccel) {
            // Le premier échantillon **est** la valeur : démarrer à zéro ferait
            // monter la mesure pendant une demi-seconde après chaque ouverture.
            ax = x
            ay = y
            az = z
            hasAccel = true
        } else {
            ax += (x - ax) * ALPHA
            ay += (y - ay) * ALPHA
            az += (z - az) * ALPHA
        }
        recompute()
    }

    /**
     * Le repli : l'accélération **brute**, gravité comprise.
     *
     * On y retranche la gravité fusionnée, ce qui donne la même chose à ceci près
     * que le gyroscope n'a pas eu son mot à dire sur la séparation des deux. C'est
     * moins bon et c'est juste quand même ; on ne s'en sert que si le matériel ne
     * propose pas la version composite, ce qui n'est pas le cas d'un Phone (3).
     */
    fun onAcceleration(x: Float, y: Float, z: Float) {
        if (!hasGravity) return
        onLinear(x - gx, y - gy, z - gz)
    }

    // ---------- sortie ----------

    fun snapshot(): Reading = Reading(
        towardFront = towardFront,
        towardRight = towardRight,
        hasFix = hasAccel && hasGravity,
        front = peakFront,
        rear = peakRear,
        left = peakLeft,
        right = peakRight,
    )

    /** Efface les pics. Le trajet recommence. */
    fun clearPeaks() {
        peakFront = 0f
        peakRear = 0f
        peakLeft = 0f
        peakRight = 0f
    }

    /** Oublie tout. Une surface qui se rouvre repart d'ici. */
    fun reset() {
        hasAccel = false
        hasGravity = false
        backIsForward = true
        towardFront = 0f
        towardRight = 0f
        clearPeaks()
    }

    private fun recompute() {
        if (!hasAccel || !hasGravity) return

        val n = sqrt(gx * gx + gy * gy + gz * gz)
        if (n < MIN_GRAVITY) return
        // La verticale du monde, vue par l'appareil. Elle pointe vers le **haut** :
        // c'est la convention d'Android, un téléphone à plat écran en l'air mesure
        // `+g` sur son axe Z.
        val ux = gx / n
        val uy = gy / n
        val uz = gz / n

        // ---- l'avant du véhicule ----
        //
        // Deux poses seulement ont un sens dans une voiture, et elles désignent deux
        // axes différents : debout dans un support, l'avant sort par le **dos** de
        // l'appareil (−Z) ; posé à plat sur la planche de bord, il sort par le
        // **haut** (+Y). On garde celui qu'on a tant qu'il n'est pas devenu vertical
        // — voir [FORWARD_KEEP].
        val backUpright = abs(uz)
        val topUpright = abs(uy)
        backIsForward =
            if (backIsForward) backUpright < FORWARD_KEEP else topUpright >= FORWARD_KEEP
        var fx = 0f
        var fy = if (backIsForward) 0f else 1f
        var fz = if (backIsForward) -1f else 0f

        // Projection sur le plan horizontal, puis normalisation : ce qui reste est
        // l'avant du véhicule, à plat, quelle que soit l'inclinaison du support.
        val fDotU = fx * ux + fy * uy + fz * uz
        fx -= fDotU * ux
        fy -= fDotU * uy
        fz -= fDotU * uz
        val fn = sqrt(fx * fx + fy * fy + fz * fz)
        // L'axe retenu est presque vertical : il n'y a pas d'avant à en tirer, et
        // la dernière valeur connue vaut mieux qu'une direction inventée.
        if (fn < MIN_FORWARD) return
        fx /= fn
        fy /= fn
        fz /= fn

        // La droite du véhicule : avant × haut. Produit vectoriel, rien de plus.
        val rx = fy * uz - fz * uy
        val ry = fz * ux - fx * uz
        val rz = fx * uy - fy * ux

        // ---- l'accélération, à plat ----
        val aDotU = ax * ux + ay * uy + az * uz
        val hx = ax - aDotU * ux
        val hy = ay - aDotU * uy
        val hz = az - aDotU * uz

        // La poussée **ressentie** est l'opposée de l'accélération : on freine, le
        // corps part en avant. C'est elle qu'on affiche, parce que c'est elle qu'on
        // sent — et parce qu'une bille posée sur une plaque ferait exactement ça.
        towardFront = -(hx * fx + hy * fy + hz * fz) / GRAVITY
        towardRight = -(hx * rx + hy * ry + hz * rz) / GRAVITY

        peakFront = max(peakFront, towardFront)
        peakRear = max(peakRear, -towardFront)
        peakRight = max(peakRight, towardRight)
        peakLeft = max(peakLeft, -towardRight)
    }

    private companion object {
        /** L'accélération normale de la pesanteur, en m/s². La définition, pas 9,81. */
        const val GRAVITY = 9.80665f

        /**
         * Coefficient du passe-bas, pour un capteur à `SENSOR_DELAY_GAME` (~50 Hz) :
         * une coupure autour de 4 Hz. Voir l'en-tête — c'est la suspension qu'on
         * coupe, pas le confort de lecture qu'on achète.
         */
        const val ALPHA = 0.35f

        /** En deçà, le vecteur n'est pas de la gravité : on ne conclut rien. */
        const val MIN_GRAVITY = 1f

        /**
         * Cosinus au-delà duquel l'axe d'avant retenu est jugé trop vertical, et
         * cède à l'autre.
         *
         * Un seul seuil pour les deux sens, donc une hystérésis : il faut coucher
         * le téléphone à plus de 37° de l'horizontale pour qu'il change d'avis, et
         * il ne revient qu'une fois l'autre axe aussi franc. Sans ça, un téléphone
         * tenu de biais dans un support mal réglé changerait d'avant à chaque
         * cahot, et le cadran ferait un quart de tour.
         */
        const val FORWARD_KEEP = 0.8f

        /** En deçà, la projection horizontale de l'avant n'a plus de direction. */
        const val MIN_FORWARD = 0.2f
    }
}
