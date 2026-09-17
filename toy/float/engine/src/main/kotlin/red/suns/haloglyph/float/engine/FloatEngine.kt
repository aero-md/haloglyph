package red.suns.haloglyph.float.engine

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Le moteur du toy : ce que disent les capteurs, une fois filtré.
 *
 * Kotlin pur, comme tous les moteurs du pack. Il ne connaît ni `SensorManager`,
 * ni `Sensor`, ni le nombre d'axes que déclare le matériel : on lui verse des
 * flottants, il rend un [Reading]. C'est ce qui permet à la matrice, au hublot
 * d'écran d'accueil et à la vignette du hub d'afficher le même instrument — et
 * c'est ce qui rend tout ceci testable sans téléphone.
 *
 * ## L'horizontale est **celle du monde**, et rien d'autre
 *
 * Il y a eu une remise à zéro : on secouait, on reposait, la pose courante
 * devenait la référence. L'idée était de rattraper le fait qu'un Phone (3) ne
 * repose pas à plat sur son dos — il y a un bloc photo.
 *
 * Elle est retirée, et c'est le bon arbitrage. **Un niveau à bulle n'a pas de
 * réglage de zéro.** C'est ce qui en fait un instrument : on le pose, il dit la
 * vérité, et si la réponse déplaît c'est le meuble qui a tort. Une référence
 * réglable transforme la mesure en opinion — et, en pratique, elle se posait
 * toute seule sans que personne l'ait demandé, puis faussait tout d'un degré
 * sans plus rien pour le dire.
 *
 * Ce qui disparaît avec elle : la détection de secousse, qui n'existait que pour
 * l'armer, et l'attente du repos, qui n'existait que pour la capturer.
 *
 * ## D'où vient la gravité, et pourquoi ça compte
 *
 * Un accéléromètre ne distingue pas une inclinaison d'une translation : les deux
 * sont des accélérations, et rien dans la mesure ne dit laquelle. Un niveau qui
 * ne lirait que lui part donc dans tous les sens dès qu'on **glisse** le
 * téléphone à plat sur la table, alors qu'il n'a pas tourné d'un degré. Aucun
 * filtrage ne répare ça : l'information n'est pas dans le signal.
 *
 * Elle est dans le **gyroscope**. On préfère donc la gravité fusionnée que la
 * plateforme sait produire — accéléromètre et gyroscope ensemble, voir
 * [onGravity] — qui ne bouge pas quand l'appareil se déplace sans tourner. Le
 * repli sur l'accéléromètre seul reste écrit et reste juste ; il est simplement
 * sensible à ce que la physique lui impose.
 *
 * L'accélération brute continue d'arriver dans tous les cas. Elle ne sert plus
 * qu'à une chose : savoir que le téléphone **bouge**, pour ne pas annoncer qu'il
 * est de niveau pendant qu'on l'agite.
 *
 * ## Un filtre, et il est adaptatif
 *
 * La gravité est lissée par un passe-bas exponentiel, fusionnée ou non. Son
 * coefficient **n'est pas constant**, et c'est la seule subtilité du fichier :
 *
 * - **près de l'horizontale**, le gain d'affichage est énorme (voir
 *   [FloatScale]) — une dizaine de cellules par degré. Un filtre rapide y
 *   transmettrait le bruit du capteur et la bulle danserait sur place. On filtre
 *   donc lentement : l'instrument se pose.
 * - **franchement penché**, ce gain retombe à moins d'une cellule par degré, et
 *   c'est la réactivité qui compte : on cherche l'horizontale, on veut voir la
 *   bulle suivre la main. On filtre alors vite.
 *
 * Un coefficient unique oblige à choisir entre une bulle nerveuse au repos et une
 * bulle molle en mouvement. Ici les deux régimes sont ceux qu'on veut, chacun là
 * où il sert.
 *
 * ## Deux régimes, et la bascule entre eux
 *
 * À plat, on mesure l'écart de l'axe **Z** de l'appareil à la verticale : c'est
 * un niveau posé sur un meuble, et la bulle se déplace dans les deux dimensions
 * du plateau.
 *
 * Debout, ça ne veut plus rien dire — l'axe Z est horizontal, le niveau sature, et
 * ce qu'on cherche à savoir a changé : **est-ce que ce montant est d'aplomb ?**
 * C'est une question à une seule dimension, et le régime vertical n'en mesure donc
 * qu'une.
 *
 * ### Ce qu'on mesure debout, et ce qu'on ignore
 *
 * Deux écarts existent quand le plan du téléphone est vertical, et un seul
 * intéresse :
 *
 * - **la rotation dans son propre plan** — le téléphone penche à gauche ou à
 *   droite le long de la surface. C'est celui-là. Plaqué contre un chambranle, il
 *   dit exactement de combien le chambranle sort de l'aplomb.
 * - **le décollement hors de son plan** (`uz`) — le téléphone n'est pas plaqué à
 *   fond. Ça ne dit rien de la surface, seulement de la main qui tient, et une
 *   bulle qui en tiendrait compte mélangerait les deux erreurs sans rien pour les
 *   distinguer.
 *
 * ### L'axe mesuré suit la pose
 *
 * En portrait, l'axe **Y** est censé être vertical : ce qui doit être horizontal
 * est **X**, donc c'est `ux` qu'on lit, et la bulle glisse en travers. Couché d'un
 * quart de tour, les rôles s'échangent : `uy`, et la bulle glisse de haut en bas.
 * Le repère retenu est le **quart de tour le plus proche**, et la bulle se
 * réoriente avec lui — c'est la même fiole, tournée comme le téléphone.
 *
 * ### Deux hystérésis, et elles ne servent pas à la même chose
 *
 * La bascule à plat / debout se fait à [ENTER_VERTICAL] degrés et revient à
 * [LEAVE_VERTICAL] : sans elle, un téléphone tenu pile sur le seuil changerait de
 * référence trente fois par seconde, et la lecture sauterait de 70° à 20° à chaque
 * fois.
 *
 * Le choix du quart de tour, lui, tient jusqu'à [QUARTER_SWITCH] degrés de dérive
 * — bien au-delà des 45° qui séparent géométriquement deux quarts. Un téléphone
 * tenu de travers garde donc l'axe qu'il avait, au lieu d'en changer au milieu
 * d'une mesure ; il faut vraiment le coucher pour qu'il bascule.
 */
class FloatEngine {

    /**
     * Ce que le toy sait de la pose du téléphone, à un instant.
     *
     * [dirX] et [dirY] pointent vers le **côté haut**, dans le repère de
     * l'appareil et non dans celui de l'écran : c'est au renderer de savoir de
     * quel côté on regarde la matrice. Une bulle va vers le haut, comme dans une
     * fiole en verre — pas vers le bas comme une bille.
     *
     * En régime vertical, la course n'a **qu'une** dimension : l'un des deux est
     * nul, l'autre vaut ±1, et [alongX] dit lequel.
     */
    data class Reading(
        val tiltDegrees: Float,
        val dirX: Float,
        val dirY: Float,
        /** Au moins une mesure est arrivée. Faux = l'instrument n'a rien à dire. */
        val hasTilt: Boolean,
        /** Référence 90° : on mesure l'aplomb et non la planéité. Voir l'en-tête. */
        val vertical: Boolean,
        /**
         * En régime vertical : la bulle court-elle le long de l'axe **X** de
         * l'appareil ?
         *
         * Vrai en portrait, faux d'un quart de tour. Sans objet à plat, où la
         * bulle va dans toutes les directions.
         */
        val alongX: Boolean,
        /** La bulle est dans la couronne, et le téléphone ne bouge pas. */
        val level: Boolean,
        val moving: Boolean,
        val heading: Float,
        /** Un cap est disponible : le matériel a répondu. */
        val hasHeading: Boolean,
        /** Le magnétomètre se dit calibré. Faux = le cap est à prendre avec des pincettes. */
        val headingTrusted: Boolean,
    )

    /**
     * La portée du niveau, relue par la surface à chaque image.
     *
     * Elle vit ici plutôt que dans l'appel de rendu parce que le moteur en a
     * besoin pour une chose que le dessin ne fait pas : décider si c'est de
     * niveau, donc s'il faut vibrer. Même parti pris que Lapse, qui réapplique sa
     * configuration à chaque frame plutôt que de s'inventer un canal de
     * notification.
     */
    var range: FloatRange = FloatRange.DEFAULT

    // ---------- l'état du filtre ----------

    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var settled = false

    /** La plateforme nous donne une gravité fusionnée : l'accéléromètre ne sert
     *  plus qu'à repérer le mouvement. Voir l'en-tête. */
    private var fused = false

    private var tilt = 0f
    private var dirX = 0f
    private var dirY = 0f
    private var moving = false
    private var wasLevel = false
    private var vertical = false

    /**
     * Le quart de tour qui sert de repère au régime vertical, en multiples de 90°
     * depuis le portrait droit. Pair = la bulle court sur X, impair = sur Y.
     */
    private var quarter = 0
    private var alongX = true

    private var heading = 0f
    private var hasHeading = false
    private var headingTrusted = true

    /**
     * La bulle vient d'entrer dans la couronne, et personne ne l'a encore su.
     *
     * Atomique parce que les deux bouts ne sont pas sur le même fil : la mesure
     * arrive sur celui que la surface a donné au capteur, et c'est la boucle de
     * rendu qui consomme. Un `Boolean` nu perdrait des transitions, ou en
     * rejouerait.
     */
    private val levelReached = AtomicBoolean(false)

    // ---------- entrées ----------

    /**
     * Une mesure de l'accéléromètre, en m/s², dans le repère de l'appareil.
     *
     * Elle ne sert qu'à deux choses : dire que le téléphone bouge, et tenir lieu
     * de gravité quand la plateforme n'en fournit pas de fusionnée.
     */
    fun onAcceleration(x: Float, y: Float, z: Float) {
        if (!settled) {
            // La première mesure **est** la gravité, tant qu'on n'a rien de
            // mieux. Partir de zéro ferait passer un téléphone posé pour une
            // secousse de 9,81 m/s².
            seed(x, y, z)
            recompute()
            return
        }

        if (!fused) smooth(x, y, z)

        val residual = sqrt(
            (x - gx) * (x - gx) + (y - gy) * (y - gy) + (z - gz) * (z - gz),
        )
        moving = residual > MOTION_THRESHOLD
        recompute()
    }

    /**
     * La gravité **fusionnée**, quand la plateforme sait la produire.
     *
     * Elle mêle l'accéléromètre et le gyroscope : quand l'appareil se déplace
     * sans tourner, le gyroscope le dit et le vecteur ne bouge pas. C'est la
     * différence entre un niveau qu'on peut glisser sur une table et un niveau qui
     * part dans tous les sens dès qu'on le pousse.
     *
     * Elle est quand même relissée par le filtre adaptatif : la plateforme la
     * livre déjà propre, mais pas assez calme pour le gain qu'on applique près de
     * l'horizontale — une dizaine de cellules par degré ne pardonne rien.
     */
    fun onGravity(x: Float, y: Float, z: Float) {
        fused = true
        if (!settled) seed(x, y, z) else smooth(x, y, z)
        recompute()
    }

    private fun seed(x: Float, y: Float, z: Float) {
        gx = x
        gy = y
        gz = z
        settled = true
    }

    /**
     * Passe-bas exponentiel à coefficient adaptatif.
     *
     * Il dépend de l'inclinaison de l'image précédente — voir l'en-tête. Un degré
     * de retard sur le régime n'a aucune conséquence : ce qu'on règle, c'est la
     * constante de temps, pas la valeur.
     */
    private fun smooth(x: Float, y: Float, z: Float) {
        val blend = min(1f, tilt / ADAPT_DEGREES)
        val alpha = ALPHA_CALM + (ALPHA_QUICK - ALPHA_CALM) * blend
        gx += (x - gx) * alpha
        gy += (y - gy) * alpha
        gz += (z - gz) * alpha
    }

    /**
     * Le cap, déjà calculé par [Heading] ou par le matériel.
     *
     * ## Pas de lissage, une zone morte
     *
     * Le cap a été filtré par un passe-bas, comme la gravité, et c'était le
     * mauvais outil. Un filtre ne supprime pas le tremblement : il l'**étale**. La
     * rose glissait derrière la main — un cinquième de l'écart rattrapé par mesure,
     * soit près d'un quart de seconde pour arriver — et frémissait quand même une
     * fois posée, puisque le bruit qu'on moyenne ne vaut jamais exactement zéro.
     *
     * Une **zone morte** fait l'inverse des deux côtés. Au-delà de
     * [HEADING_DEADBAND], le cap est pris tel quel, sans retard d'aucune sorte :
     * on pointe une direction, l'instrument l'affiche. En deçà, rien ne bouge du
     * tout — pas « moins », **rien**. Le chiffre est affiché au degré près : une
     * zone morte d'un degré est donc exactement la marche de l'affichage, et il n'y
     * a rien en dessous à montrer.
     *
     * Le prix est un retard permanent d'au plus un degré sur une rotation lente,
     * qui est précisément la chose qu'une rose de vingt-cinq pixels ne peut pas
     * montrer.
     *
     * @param degrees `null` quand il n'y a pas de cap à donner — pas de
     * magnétomètre, ou deux vecteurs colinéaires. L'instrument le dit alors au
     * lieu d'afficher un nord inventé.
     */
    fun onHeading(degrees: Float?, trusted: Boolean) {
        headingTrusted = trusted
        if (degrees == null) {
            hasHeading = false
            return
        }
        if (!hasHeading || abs(Heading.delta(heading, degrees)) >= HEADING_DEADBAND) {
            heading = Heading.normalize(degrees)
        }
        hasHeading = true
    }

    // ---------- sortie ----------

    fun snapshot(): Reading = Reading(
        tiltDegrees = tilt,
        dirX = dirX,
        dirY = dirY,
        hasTilt = settled,
        vertical = vertical,
        alongX = alongX,
        level = wasLevel,
        moving = moving,
        heading = heading,
        hasHeading = hasHeading,
        headingTrusted = headingTrusted,
    )

    /**
     * La bulle vient-elle d'entrer dans la couronne ?
     *
     * Vrai **une fois** par entrée. C'est la seule information du toy qu'on ne
     * puisse pas lire sans retourner le téléphone, donc la seule qui mérite une
     * vibration — et une surface qui ne vibre pas peut ne jamais appeler cette
     * méthode sans rien perdre du dessin.
     */
    fun consumeLevel(): Boolean = levelReached.getAndSet(false)

    /** Oublie le filtre. Une surface qui se rouvre repart d'ici. */
    fun reset() {
        settled = false
        fused = false
        moving = false
        wasLevel = false
        vertical = false
        quarter = 0
        alongX = true
        tilt = 0f
        dirX = 0f
        dirY = 0f
        hasHeading = false
        levelReached.set(false)
    }

    private fun recompute() {
        val n = sqrt(gx * gx + gy * gy + gz * gz)
        if (n < MIN_GRAVITY) return

        val ux = gx / n
        val uy = gy / n

        // L'angle de l'axe Z de l'appareil à la verticale : 0 à plat, 90 debout.
        // C'est lui qui décide du régime, et jamais l'inclinaison affichée — qui,
        // elle, change de sens d'un régime à l'autre et ne pourrait donc pas
        // servir de critère à la bascule sans se mordre la queue.
        val fromFlat = degrees(hypot(ux, uy))
        vertical = if (vertical) fromFlat > LEAVE_VERTICAL else fromFlat > ENTER_VERTICAL

        if (vertical) floatAxis(ux, uy) else flatPlane(ux, uy)

        val tolerance = FloatScale.tolerance(range)
        // Hystérésis : sans elle, une bulle posée pile sur le seuil allume et
        // éteint la couronne trente fois par seconde, et vibre autant.
        val limit = if (wasLevel) tolerance * LEVEL_RELEASE else tolerance
        val level = tilt <= limit && !moving
        if (level && !wasLevel) levelReached.set(true)
        wasLevel = level
    }

    /**
     * Le régime à plat : deux axes, et la bulle va où monte le plateau.
     *
     * L'inclinaison est la **projection de la gravité** sur le plan de l'appareil,
     * et rien de plus : l'angle de l'axe Z à la verticale vaut exactement `asin` de
     * cette projection. Il n'y a aucune référence à retrancher — c'est tout le
     * sujet d'un niveau à bulle.
     */
    private fun flatPlane(ux: Float, uy: Float) {
        val mag = hypot(ux, uy).coerceAtMost(1f)
        tilt = degrees(mag)
        alongX = true
        if (mag > 1e-4f) {
            dirX = ux / mag
            dirY = uy / mag
        }
    }

    /**
     * Le régime vertical : **un seul axe**, celui qui devrait être horizontal.
     *
     * Le plan de l'appareil est presque vertical, donc le vecteur vertical tient
     * presque entier dans ce plan : son angle y dit comment le téléphone est tourné
     * — zéro en portrait droit, un quart de tour couché. On garde le quart de tour
     * le plus proche (avec l'hystérésis de [QUARTER_SWITCH]), et **l'erreur
     * d'aplomb est la composante de la verticale sur l'axe qui devrait être
     * horizontal** : `ux` en portrait, `uy` couché.
     *
     * Le `uz` laissé de côté est le décollement hors du plan — l'affaire de la
     * main, pas de la surface. Voir l'en-tête.
     */
    private fun floatAxis(ux: Float, uy: Float) {
        val inPlane = Math.toDegrees(atan2(ux, uy).toDouble()).toFloat()
        if (abs(Heading.delta(quarter * 90f, inPlane)) > QUARTER_SWITCH) {
            quarter = Math.round(inPlane / 90f)
        }
        alongX = quarter % 2 == 0

        val across = if (alongX) ux else uy
        tilt = degrees(abs(across))
        // Un axe, donc une direction : ±1 sur celui qu'on mesure, zéro sur
        // l'autre. La bulle monte, comme dans une fiole — du côté où la
        // verticale penche.
        val way = if (across >= 0f) 1f else -1f
        dirX = if (alongX) way else 0f
        dirY = if (alongX) 0f else way
    }

    /** Angle, en degrés, dont [sine] est le sinus. Borné, jamais `NaN`. */
    private fun degrees(sine: Float): Float =
        Math.toDegrees(asin(sine.coerceIn(-1f, 1f)).toDouble()).toFloat()

    private companion object {
        /**
         * Coefficient du passe-bas près de l'horizontale, pour un capteur à
         * `SENSOR_DELAY_GAME` (~50 Hz) : environ un tiers de seconde de constante
         * de temps. C'est lent pour un capteur, et c'est le but — voir l'en-tête.
         */
        const val ALPHA_CALM = 0.06f

        /** Le même, franchement penché : ~60 ms, la bulle colle à la main. */
        const val ALPHA_QUICK = 0.35f

        /** Au-delà de cette inclinaison, le filtre est à son régime rapide. */
        const val ADAPT_DEGREES = 3f

        /** Au-dessus, le téléphone bouge : on ne prétend pas qu'il est de niveau. */
        const val MOTION_THRESHOLD = 0.9f

        /** En deçà, le vecteur n'est pas de la gravité : on ne conclut rien. */
        const val MIN_GRAVITY = 1f

        /** Il faut sortir de 60 % au-delà de la tolérance pour perdre le verrou. */
        const val LEVEL_RELEASE = 1.6f

        /**
         * Inclinaison à laquelle on passe à la référence 90°, et celle à laquelle
         * on en revient.
         *
         * Les deux valeurs diffèrent exprès. À 70° l'axe Z ne dit plus rien
         * d'utile — le niveau sature quelle que soit la portée — alors que l'axe
         * Y, lui, est à 20° de la verticale et redevient lisible. Le retour à 65°
         * laisse cinq degrés de marge : un téléphone tenu à la main autour du
         * seuil basculerait sinon en permanence, et la lecture sauterait de 70° à
         * 20° à chaque aller-retour.
         */
        const val ENTER_VERTICAL = 70f
        const val LEAVE_VERTICAL = 65f

        /**
         * Dérive tolérée avant de changer de quart de tour, en degrés.
         *
         * Géométriquement, deux quarts se partagent à 45°. Ici il en faut soixante :
         * un téléphone tenu de travers garde l'axe sur lequel on a commencé à lire,
         * et n'en change que quand on le couche pour de bon. Le prix est qu'on peut
         * lire un aplomb sur un axe qui n'est plus tout à fait le plus proche — et
         * c'est exactement ce qu'on veut d'un instrument qu'on tient à la main.
         */
        const val QUARTER_SWITCH = 60f

        /**
         * Zone morte du cap, en degrés. C'est la marche de l'affichage, ni plus ni
         * moins — voir [onHeading].
         */
        const val HEADING_DEADBAND = 1f
    }
}
