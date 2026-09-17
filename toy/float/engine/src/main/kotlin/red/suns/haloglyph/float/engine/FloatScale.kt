package red.suns.haloglyph.float.engine

import kotlin.math.exp
import kotlin.math.ln

/**
 * Où se pose la bulle, et **pourquoi ce n'est pas proportionnel à l'inclinaison**.
 *
 * ## Le défaut qu'on répare
 *
 * Un niveau à bulle ordinaire — et les niveaux d'écran qui le copient — déplace
 * sa bulle proportionnellement à l'angle. Sur une fiole en verre, ça marche :
 * elle fait dix centimètres de long, la bulle glisse en continu, et l'œil lit un
 * dixième de millimètre. Sur un disque de vingt-cinq LEDs, non. Le rayon utile
 * fait onze cellules ; à pleine échelle de 15°, **une cellule vaut 1,4°**, et
 * tout ce qui se passe en dessous ne déplace rien du tout. L'instrument affiche
 * « c'est droit » pour une étagère qui penche d'un degré, ce qui est précisément
 * l'erreur qu'on cherchait à voir.
 *
 * Le problème n'est pas la résolution du capteur — un accéléromètre de téléphone
 * distingue le centième de degré sans effort — mais **l'échelle d'affichage**,
 * qui dépense sa dynamique là où il ne se passe rien.
 *
 * ## La réponse : une échelle logarithmique
 *
 * ```
 * r(θ) = R · ln(1 + θ/K) / ln(1 + θmax/K)
 * ```
 *
 * Chaque cellule vers le bord vaut un **rapport** d'angle constant, et non une
 * différence constante — le même parti pris que le décibel, qui vit déjà dans ce
 * dépôt du côté de Sono. Conséquences, et ce sont exactement celles qu'on
 * voulait :
 *
 * - **au centre, la bulle est vive.** Le gain vaut `R / (K · ln(1 + θmax/K))`,
 *   soit une quinzaine de cellules par degré : un quinzième de degré se voit.
 * - **au bord, elle sature.** Un téléphone franchement penché colle la bulle au
 *   cerne au lieu de sortir du disque, sans qu'on ait à écrêter quoi que ce soit
 *   d'autre que la valeur elle-même.
 * - **la portée ne change presque rien près de zéro.** C'est la propriété
 *   surprenante du logarithme, et elle est utile : passer de 5° à 45° de pleine
 *   échelle ne dégrade la finesse centrale que d'un facteur 1,7, là où une
 *   échelle linéaire la diviserait par neuf. Choisir « large » ne coûte donc
 *   presque rien ; c'est pour ça que le réglage s'appelle une portée et non une
 *   sensibilité.
 *
 * [K] est le **genou** de la courbe : en deçà, la réponse est quasi linéaire, et
 * c'est lui qui borne le gain. Sans genou (`ln θ` nu), le gain serait infini en
 * zéro et la bulle passerait son temps à danser sur le bruit du capteur.
 */
object FloatScale {

    /**
     * Le genou, en degrés.
     *
     * Choisi sur le bruit et non sur l'esthétique : après filtrage, un
     * accéléromètre de téléphone laisse passer quelques centièmes de degré
     * d'agitation. C'est lui qui borne le gain central, et le baisser **creuse** la
     * courbe — plus de course au centre, moins au bord.
     *
     * Il a valu 0,35° et vaut maintenant 0,20°, ce qui porte le gain central à une
     * quinzaine de cellules par degré au lieu de sept : **un quinzième de degré
     * déplace la bulle d'une cellule**. Ce qui rend la chose tenable n'est pas le
     * genou mais ce qu'il y a en amont — la gravité fusionnée au gyroscope et le
     * passe-bas lent du régime calme, qui laissent passer nettement moins d'un
     * centième de degré d'agitation. Sans eux, à ce gain-là, la bulle danserait.
     */
    const val KNEE = 0.20f

    /**
     * Rayon de la bulle, en cellules, pour une inclinaison de [tiltDegrees].
     *
     * @param fullScale l'inclinaison qui colle la bulle au bord. Voir [FloatRange].
     * @param maxRadius le rayon utile du disque, bulle comprise — au renderer de
     * retrancher le rayon du point dessiné, sinon il déborde de la moitié.
     */
    fun radiusOf(tiltDegrees: Float, fullScale: Float, maxRadius: Float): Float {
        if (tiltDegrees <= 0f) return 0f
        val r = maxRadius * ln(1f + tiltDegrees / KNEE) / ln(1f + fullScale / KNEE)
        return r.coerceIn(0f, maxRadius)
    }

    /**
     * Rayon de la **couronne de visée**, en part du rayon utile.
     *
     * C'est l'approche, et plus le verrou. Elle a longtemps *été* la tolérance —
     * « de niveau » voulait dire « la bulle est dans la couronne » — et c'était un
     * bel invariant pour une tolérance d'un demi-degré. Il n'y survit pas à
     * [LOCK_FRACTION] : une couronne au rayon du verrou serait entièrement sous la
     * bulle, donc invisible, donc invisable.
     *
     * L'instrument se lit maintenant en deux temps, et c'est plus honnête qu'un
     * seul : **la couronne dit « approche »**, elle ne bouge jamais ; **le cerne dit
     * « c'est bon »**, et il ne s'allume qu'au centre exact.
     */
    const val TARGET_FRACTION = 0.32f

    /**
     * La course en deçà de laquelle la bulle est encore dessinée **au centre**, en
     * part du rayon utile.
     *
     * Une demi-cellule sur les 12,37 d'un Phone (3) : au-delà, l'arrondi pose la
     * bulle sur la cellule d'à côté. C'est la seule définition de « exactement au
     * centre » qu'un affichage de vingt-cinq LEDs puisse tenir, et elle a la même
     * nature que la zone morte du cap — **le pas de l'affichage**, pas un chiffre
     * choisi.
     */
    const val LOCK_FRACTION = 0.04f

    /**
     * L'inclinaison en deçà de laquelle le toy dit « c'est de niveau », pour une
     * portée donnée.
     *
     * C'est exactement l'angle que représente [LOCK_FRACTION] : la bulle est
     * dessinée sur la cellule centrale, et pas à côté. Le seuil n'est donc jamais
     * une seconde constante qui pourrait mentir sur ce que l'œil voit — il est, par
     * construction, ce que le dessin montre.
     *
     * En pratique : **±0,028° en portée fine, ±0,038° en normale, ±0,048° en
     * large** — deux à trois minutes d'arc, soit moins d'un millimètre par mètre.
     *
     * ## Ce que ça coûte, et pourquoi c'est le bon prix
     *
     * Le cerne ne s'allumera **pas souvent**. Un plan de travail ordinaire n'est
     * pas plat à un millimètre par mètre, et un Phone (3) posé sur son dos repose
     * sur son bloc photo — dans cette pose-là, il ne s'allumera jamais. C'est
     * assumé : le seuil précédent, un demi-degré, revenait à certifier « de niveau »
     * une étagère qui tombe d'un centimètre sur un mètre.
     *
     * Un niveau qui s'allume tout le temps ne dit rien. Celui-ci dit une seule
     * chose, et elle est vraie.
     */
    fun tolerance(range: FloatRange): Float = tiltAt(LOCK_FRACTION, range.degrees, 1f)

    /**
     * L'inverse : l'inclinaison que représente un rayon donné.
     *
     * Elle n'est pas là pour la symétrie. C'est elle qui définit la **tolérance**
     * du toy : la couronne de visée est dessinée à un rayon fixe, et « c'est de
     * niveau » veut dire « la bulle est dedans ». En passant par ici, le seuil
     * n'est jamais une seconde constante qui pourrait mentir sur ce que l'œil
     * voit — il est, par construction, ce que le dessin annonce.
     */
    fun tiltAt(radius: Float, fullScale: Float, maxRadius: Float): Float {
        if (radius <= 0f || maxRadius <= 0f) return 0f
        return KNEE * (exp(radius / maxRadius * ln(1f + fullScale / KNEE)) - 1f)
    }
}

/**
 * La portée du niveau : l'inclinaison qui colle la bulle au bord.
 *
 * Trois valeurs, et pas un curseur : ce qui change d'une portée à l'autre est ce
 * qu'on regarde, pas un degré de plus ou de moins. [FINE] pour un plan de
 * travail ou un cadre, [NORMALE] pour à peu près tout, [LARGE] pour une pente
 * qu'on veut chiffrer plutôt que corriger.
 *
 * Les libellés sont des **valeurs** — `5°`, `15°`, `45°` — et non des phrases :
 * ils n'ont pas à être traduits, et le nombre dit mieux que l'adjectif.
 */
enum class FloatRange(val degrees: Float, val label: String) {
    FINE(5f, "5°"),
    NORMALE(15f, "15°"),
    LARGE(45f, "45°");

    companion object {
        val DEFAULT = NORMALE

        fun byKey(key: String?): FloatRange = entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}
