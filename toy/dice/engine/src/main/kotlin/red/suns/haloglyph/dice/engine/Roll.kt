package red.suns.haloglyph.dice.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le jet : cinématique, caméra, fenêtres d'écoute.
 *
 * Un seul objet, une seule commande. Le toy n'a rien à régler et rien à
 * afficher d'autre — d'où le nom d'origine. Ce qui se joue tient dans trois
 * décisions :
 *
 * 1. **Le dé est un solide, pas une image de dé.** L'orientation est un
 *    quaternion et la face lue est celle que la géométrie met vers le haut. Un
 *    jeu de six sprites aurait été plus court, mais il aurait fallu inventer une
 *    culbute qui *ressemble* à un dé qui tourne, et la culbute est tout le toy.
 *
 * 2. **La caméra fait partie de l'animation.** Sur 25 LEDs de côté, un dé vu de
 *    trois quarts ne se lit pas : les pips de la face du dessus tombent sur cinq
 *    cellules écrasées et un 6 devient deux barres. La position de repos est
 *    donc le **gros plan à la verticale**, où la face remplit le hublot ; les
 *    trois quarts n'existent que pendant le vol, quand il n'y a rien à lire. Le
 *    jet recule la caméra, la pose la ramène : le zoom n'est pas un effet posé à
 *    la fin, c'est la boucle.
 *
 * 3. **Le jet n'écoute pas tout le temps.** Voir [verdict] plus bas.
 *
 * Le renderer ne connaît de tout ça que la [View] : une orientation, une
 * position, une caméra. Il ne sait pas qu'il y a un jet en cours.
 */

/* ------------------------------- la caméra -------------------------------- */

class Cam(
    /** Azimut, radians. */
    val yaw: Double,
    /** Élévation au-dessus du plan de la table, radians. */
    val elev: Double,
    /** Demi-largeur du champ, en unités de dé, ramenée au rayon du hublot. */
    val half: Double,
    /**
     * Où l'on en est du rapprochement : `0` en vol, `1` posé.
     *
     * Les trois autres champs suffisent à tracer l'image, celui-ci dit **ce que
     * la caméra est en train de faire**. Le rendu s'en sert pour n'imprimer les
     * nombres qu'au gros plan — voir [REVEAL_OFF]. Il pourrait le déduire de
     * [half], en le comparant au cadrage large et au `close` du dé, mais ce
     * serait reconstruire par calcul une chose que l'appelant sait déjà.
     */
    val z: Double,
)

/** Le tressaut d'impact, en cellules entières. */
class Jolt(val dx: Int, val dy: Int)

/** Tout ce que le rendu a besoin de savoir. Il ignore qu'un jet existe. */
class View(val q: Quat, val pos: Vec3, val cam: Cam, val jolt: Jolt?)

private const val D2R = PI / 180

/**
 * Le vol : trois quarts, de loin.
 *
 * Un champ de 2,25 unités de dé, et non les 2,55 qu'il faudrait pour garantir
 * que rien ne sorte jamais du hublot. À 2,55 le cube ne fait plus que onze
 * cellules de large au milieu d'un disque de vingt-cinq, et un dé perdu dans sa
 * fenêtre ne culbute pas, il flotte. À 2,25 il en fait treize à quinze, et il
 * arrive qu'un coin passe derrière la découpe au sommet du premier rebond — soit
 * exactement ce que fait un objet qui saute devant une fenêtre ronde.
 */
private const val WIDE_YAW = 35 * D2R
private const val WIDE_ELEV = 40 * D2R
private const val WIDE_HALF = 2.25

/**
 * Le repos : la face du dessus, à l'aplomb, débordant du hublot. Le demi-champ,
 * lui, vient du dé — voir [Die.close].
 *
 * 84° et non 90° : le dernier degré et demi laisse un liseré de la face avant,
 * ce qui dit qu'on regarde un solide et pas une carte. À 90° le dé devient un
 * carré de pips, et un carré de pips ne culbute pas.
 */
private const val CLOSE_YAW = 0.0
private const val CLOSE_ELEV = 84 * D2R

private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t

/**
 * L'avancement de la **pose** : `0` cadrage de vol, `1` caméra arrivée.
 *
 * Ce n'est pas [zoomAt]. La caméra finit sa course à [REVEAL_OFF] et tient
 * ensuite, là où `z` continue jusqu'à 1 — et [REVEAL_OFF] est précisément le
 * seuil où la marque commence à s'allumer. Le rapprochement est donc **terminé
 * avant qu'il y ait quoi que ce soit à lire**, ce qui est tout l'objet de cette
 * fonction : voir [REVEAL_OFF].
 *
 * Une seule constante pour les deux, et pas deux réglages voisins qu'on
 * finirait par désaccorder.
 *
 * Le `smooth` n'est pas décoratif : sans lui la caméra arriverait à pleine
 * vitesse et s'arrêterait net, ce qui se verrait autant que la dérive qu'il
 * s'agit de supprimer.
 */
private fun posed(z: Double): Double = smooth(z / REVEAL_OFF)

/** `z = 0` en vol, `z = 1` posé. */
fun camAt(z: Double, close: Double): Cam {
    val p = posed(z)
    return Cam(
        yaw = mix(WIDE_YAW, CLOSE_YAW, p),
        elev = mix(WIDE_ELEV, CLOSE_ELEV, p),
        half = mix(WIDE_HALF, close, p),
        z = z,
    )
}

/**
 * **Une marque ne s'imprime qu'au gros plan**, et jamais pendant le vol — pips
 * du d6 compris.
 *
 * Le raisonnement est venu par les chiffres. En vol un nombre tombe sur une face
 * de trois quarts, à moitié rognée par une arête : ce qu'on lit n'est pas un
 * nombre mais une tache qui y ressemble, et il en passe un différent à chaque
 * rebond — le dé a l'air d'annoncer des résultats qu'il ne donne pas.
 *
 * Les pips ont tenu un temps comme exception, au motif qu'un pip qui culbute
 * reste un pip et que c'est ce qui fait lire un dé. C'est vrai en soi et faux
 * dans le lot : le d6 était alors le seul dé à porter quelque chose en vol, donc
 * le seul à culbuter *plein* quand les trois autres culbutent en carcasse. Une
 * même cinématique lue de deux façons selon le solide, ça n'a plus l'air d'une
 * règle, ça a l'air d'un oubli. Les quatre dés volent donc nus et se lisent au
 * gros plan.
 *
 * Le seuil porte sur le rapprochement de la caméra et non sur l'inclinaison de
 * la face : la marque monte au milieu du zoom et vite — commencée 240 ms après
 * la pose, pleine à 380 ms, et pleine pour les 220 ms de gros plan qui restent.
 * Le départ est bien plus sec, et c'est le recul de la caméra qui le veut : à la
 * puissance cinq, `z` a franchi [REVEAL_FULL] au bout de **vingt-cinq
 * millisecondes**. La marque ne s'attarde pas sur un dé qui part, elle s'éteint.
 *
 * ### L'invariant : une marque visible ne bouge jamais
 *
 * [REVEAL_OFF] ne sert pas qu'à allumer la marque, il sert aussi de **terminus à
 * la caméra** — voir [posed]. Au moment où la première lueur apparaît, azimut,
 * élévation, cadrage et recentrage sont arrivés et ne bougeront plus : ce qui
 * s'allume est déjà à sa place, et n'a plus une seule cellule à parcourir.
 *
 * Il a fallu un bug pour l'écrire en entier. La première version n'arrêtait que
 * le *recentrage*, et à [REVEAL_FULL], au motif qu'un nombre est tamponné au
 * centre de la face et qu'un centre fixe suffit à le fixer. C'est vrai du nombre
 * et faux des pips : ceux-là sont tamponnés **à côté** du centre, ils suivaient
 * donc l'azimut et le rapprochement jusqu'à `z = 1`. Mesuré sur un d6 posé sur
 * 5 : quatre pips sur cinq sautaient encore d'une cellule — chacun dans sa
 * direction — à pleine luminosité. Arrêter toute la caméra à [REVEAL_FULL] les a
 * figés, mais laissait le pip central sauter une fois à 95 %, parce que la pose
 * n'était encore qu'à 98,8 % quand la marque est devenue visible. Un seuil plus
 * bas, et il ne reste rien à rattraper.
 *
 * Un dé qui a fini de rouler et dont les points bougent encore ne dit pas son
 * résultat, il le cherche.
 *
 * ### Ce que le seuil bas coûte, et pourquoi c'est le bon prix
 *
 * Le rapprochement se fait maintenant en 250 ms au lieu de 600, puis la caméra
 * tient pendant que la marque monte. C'est un temps fort de plus, pas un de
 * moins : la caméra plonge, **puis** le nombre s'allume, au lieu des deux à la
 * fois. L'instant où le résultat devient lisible n'a pas bougé d'une image.
 *
 * Au jet, la même constante fait tenir le gros plan les 66 premières
 * millisecondes — le temps que la marque finisse de s'éteindre — avant que la
 * caméra ne décroche. Le dé n'a alors tourné que d'une vingtaine de degrés, donc
 * rien de la bouillie que le recul brutal est là pour éviter.
 */
const val REVEAL_OFF = 0.35
const val REVEAL_FULL = 0.7

/**
 * L'opacité de la marque, tirée du seul rapprochement.
 *
 * Bande étroite et non un fondu sur toute la durée du zoom : ce qui porte
 * l'information est plein, une marque à mi-teinte pendant six cents
 * millisecondes serait un gris qui prétend dire un nombre.
 */
fun revealAt(z: Double): Double = when {
    z <= REVEAL_OFF -> 0.0
    z >= REVEAL_FULL -> 1.0
    else -> smooth((z - REVEAL_OFF) / (REVEAL_FULL - REVEAL_OFF))
}

/* ------------------------------- le calendrier ----------------------------- */

/**
 * Les contacts avec la table, en secondes depuis le jet. Le premier est le
 * lancer lui-même. Les intervalles se resserrent et les hauteurs s'écrasent,
 * comme n'importe quoi qui rebondit.
 *
 * Il y en a eu un de plus, culminant à 0,02 unité de dé. Sur vingt-cinq LEDs,
 * deux centièmes de dé ne valent **pas une cellule** : ce dernier saut ne se
 * voyait pas, il ne faisait qu'ajouter cent trente millisecondes d'attente avant
 * le roulé. Le retirer ne raccourcit pas la culbute, il retire ce qui n'en
 * faisait pas partie — et [T_LAND] descend d'autant, pour que le roulé garde
 * exactement la durée qu'il avait.
 */
private val CONTACT = doubleArrayOf(0.0, 0.42, 0.78, 1.06, 1.28, 1.45)

/** Sommet de chaque vol, en unités de dé (le dé fait 2 de côté). */
private val PEAK = doubleArrayOf(0.38, 0.21, 0.12, 0.07, 0.04)

/** Fin de l'impulsion : la caméra a fini de reculer, le dé est en l'air. */
const val T_TOSS = 0.35

/** Dernier rebond — au-delà, le dé roule et ne saute plus. */
val T_BRAKE = CONTACT[CONTACT.size - 1]

/** Le dé est posé. Un rebond de moins : voir [CONTACT]. */
const val T_LAND = 2.42

/** Durée du gros plan de révélation. */
const val T_ZOOM = 0.6

/** Fin de l'animation : le dé est posé **et** lisible. */
const val T_END = T_LAND + T_ZOOM

/** Vitesse de culbute au départ, radians par seconde. */
private const val RATE0 = 15.0

/** Ce qu'un rebond garde de la vitesse précédente. */
private const val RATE_KEEP = 0.78

/** Montée en régime de la rotation, le temps que la caméra recule. */
private const val RAMP = 0.14

/** Excursion latérale maximale, en unités de dé. */
private const val DRIFT = 0.35

/** Le dernier balancement, quand le dé bascule sur son arête avant de se poser. */
private const val ROCK_A = 0.09
private const val ROCK_W = 24.0
private const val ROCK_DAMP = 9.0

private fun clamp01(v: Double) = if (v < 0) 0.0 else if (v > 1) 1.0 else v

private fun smooth(v: Double): Double {
    val u = clamp01(v)
    return u * u * (3 - 2 * u)
}

/**
 * Le recul de la caméra au jet, et il est **brutal** : à la puissance cinq, les
 * quatre cinquièmes du chemin sont faits en cent millisecondes. Ce qui est fui
 * n'est pas la lenteur mais le cadrage de départ — un cube en gros plan qui se
 * met à tourner montre trois faces pleines de pips à la fois et devient une
 * bouillie. Il faut l'éloigner avant qu'il ait pris de la vitesse.
 */
private fun easeOut(u: Double) = 1 - (1 - clamp01(u)).pow(5)

/** Recul et retour de la caméra. 0 = trois quarts de loin, 1 = gros plan. */
fun zoomAt(t: Double): Double = when {
    t < 0 -> 1.0
    t < T_TOSS -> 1 - easeOut(t / T_TOSS)
    t < T_LAND -> 0.0
    t < T_END -> smooth((t - T_LAND) / T_ZOOM)
    else -> 1.0
}

/* --------------------------------- le jet ---------------------------------- */

private enum class SegKind { RAMP, SPIN, DECAY }

private class Seg(
    val t0: Double,
    val t1: Double,
    val axis: Vec3,
    /** Radians par seconde sur le segment. */
    val rate: Double,
    val kind: SegKind,
    /** Orientation à [t0], accumulée à la construction du plan. */
    val q0: Quat,
)

private class Hop(val t0: Double, val t1: Double, val peak: Double)

/** Tire une face au hasard, 1..faces. */
fun drawValue(die: Die, rnd: Random = Random.Default): Int = rnd.nextInt(die.faceCount) + 1

/**
 * Le plan d'un jet, calculé une fois au lancement.
 *
 * Le hasard est tiré au départ et la suite est une fonction du temps. Une
 * simulation intégrée image par image dépendrait de la cadence d'affichage — le
 * dé tomberait sur une autre face à 120 Hz qu'à 30 — et ne pourrait pas être
 * rejouée pour une vignette. Ici la vignette du hub, le widget et la matrice
 * jouent le même jet parce qu'ils évaluent la même fonction.
 */
class Roll private constructor(
    val die: Die,
    /** Face tirée. Connue dès le départ ; l'animation l'y conduit. */
    val value: Int,
    /**
     * Cran de rotation de la pose finale, dans `0 until die.spin`.
     *
     * Persisté avec la valeur, et pas seulement tiré : la pose de repos doit
     * être **la même** pour les trois surfaces, sinon le widget repose sa
     * silhouette d'un quart de tour à côté de la dernière image de son animation.
     */
    val twist: Int,
    /** L'orientation posée d'arrivée. Point de départ du jet suivant. */
    val qEnd: Quat,
    private val segs: List<Seg>,
    private val hops: List<Hop>,
    /** Instants où l'image tressaute d'une cellule — les contacts appuyés. */
    private val bumps: DoubleArray,
    private val drift: Vec3,
    /** Axe du dernier balancement. */
    private val rock: Vec3,
) {

    /** La culbute libre, sans correction : la trajectoire que le dé suivrait. */
    private fun freeAt(t: Double): Quat {
        var seg = segs[0]
        for (s in segs) if (t >= s.t0) seg = s

        val s = minOf(t, seg.t1) - seg.t0
        val ang = when (seg.kind) {
            SegKind.RAMP -> seg.rate * s * smooth(s / RAMP)
            SegKind.DECAY -> {
                val tau = seg.t1 - seg.t0
                seg.rate * (s - s * s / (2 * tau))
            }
            SegKind.SPIN -> seg.rate * s
        }
        return Quat.axis(seg.axis, ang) * seg.q0
    }

    /**
     * L'orientation à l'instant [t].
     *
     * Le point délicat est la mise en place. Freiner puis interpoler vers la
     * pose finale donne un dé qui *vise* : il tourne, s'arrête net, puis glisse
     * vers sa face. Ici la culbute libre continue jusqu'à s'éteindre d'elle-même,
     * et la correction vers la pose est mélangée par-dessus avec un poids qui
     * part de zéro. Aux deux bouts la vitesse est celle de la trajectoire libre,
     * il n'y a donc aucun instant où la trajectoire se voit reprise en main.
     */
    fun orientationAt(t: Double): Quat {
        if (t >= T_LAND) {
            val s = t - T_LAND
            val a = ROCK_A * exp(-ROCK_DAMP * s) * sin(ROCK_W * s)
            return Quat.axis(rock, a) * qEnd
        }
        val free = freeAt(t)
        if (t <= T_BRAKE) return free
        val u = (t - T_BRAKE) / (T_LAND - T_BRAKE)
        val w = smooth(u) * smooth(u)
        return Quat.slerp(free, qEnd, w)
    }

    fun heightAt(t: Double): Double {
        for (h in hops) {
            if (t < h.t0 || t >= h.t1) continue
            val u = (t - h.t0) / (h.t1 - h.t0)
            return 4 * h.peak * u * (1 - u)
        }
        return 0.0
    }

    /**
     * La position du centre du dé.
     *
     * Le dé part du centre et y revient. C'est faux — un dé jeté finit ailleurs —
     * et c'est assumé : les deux bouts de l'animation sont cadrés au plus près,
     * et un dé qui se pose hors du hublot n'a pas de face à révéler. Ce que
     * l'excursion doit rendre, c'est qu'il a été jeté, pas où il a atterri.
     */
    fun posAt(t: Double): Vec3 {
        val u = clamp01(t / T_LAND)
        val f = 4 * u * (1 - u)
        return Vec3(drift.x * f, heightAt(t), drift.z * f)
    }

    /**
     * Le tressaut d'impact : **une cellule entière**, jamais un décalage
     * fractionnaire. Les LEDs d'une Glyph Matrix sont soudées — ce qui bouge,
     * c'est ce qu'on y écrit.
     */
    fun joltAt(t: Double): Jolt? {
        val s = t - T_LAND
        if (s >= 0 && s < 0.05) return DOWN
        if (s >= 0.05 && s < 0.1) return UP_JOLT
        for (b in bumps) if (t >= b && t < b + 0.04) return DOWN
        return null
    }

    fun viewAt(t: Double): View {
        val q = orientationAt(t)
        val z = zoomAt(t)
        val p = posAt(t)
        return View(q, p + centering(die, q, z), camAt(z, die.close), joltAt(t))
    }

    companion object {
        private val DOWN = Jolt(0, -1)
        private val UP_JOLT = Jolt(0, 1)

        /**
         * Un axe de culbute franchement couché : un dé qui tourne sur la
         * verticale ne culbute pas, il pivote, et la face du dessus ne change
         * jamais.
         */
        private fun tumbleAxis(rnd: Random): Vec3 = Vec3(
            rnd.nextDouble() * 2 - 1,
            (rnd.nextDouble() * 2 - 1) * 0.35,
            rnd.nextDouble() * 2 - 1,
        ).normalized()

        /** Le plan d'un jet partant de l'orientation [from] et finissant sur [value]. */
        fun make(die: Die, from: Quat, value: Int, rnd: Random = Random.Default): Roll {
            val segs = mutableListOf<Seg>()
            var q = from

            for (k in 0 until CONTACT.size - 1) {
                val t0 = CONTACT[k]
                val t1 = CONTACT[k + 1]
                val axis = tumbleAxis(rnd)
                val rate = RATE0 * RATE_KEEP.pow(k)
                segs += Seg(t0, t1, axis, rate, if (k == 0) SegKind.RAMP else SegKind.SPIN, q)
                q = Quat.axis(axis, rate * (t1 - t0)) * q
            }

            /* Le roulé final : plus de rebond, une vitesse qui tombe à zéro au
               moment de la pose. C'est cette trajectoire-là que la mise en place
               vient corriger. */
            val rate = RATE0 * RATE_KEEP.pow(CONTACT.size - 1)
            segs += Seg(T_BRAKE, T_LAND, tumbleAxis(rnd), rate, SegKind.DECAY, q)

            val hops = PEAK.mapIndexed { k, peak -> Hop(CONTACT[k], CONTACT[k + 1], peak) }

            val a = rnd.nextDouble() * PI * 2
            val twist = rnd.nextInt(die.spin)
            return Roll(
                die = die,
                value = value,
                twist = twist,
                qEnd = die.restQuat(value, twist),
                segs = segs,
                hops = hops,
                bumps = doubleArrayOf(CONTACT[1], CONTACT[2]),
                drift = Vec3(cos(a) * DRIFT, 0.0, sin(a) * DRIFT),
                rock = Vec3(cos(a + 1.2), 0.0, sin(a + 1.2)).normalized(),
            )
        }

        /** La vue d'un dé posé : gros plan, face lue au milieu, rien qui bouge. */
        fun resting(die: Die, q: Quat): View =
            View(q, centering(die, q, 1.0), camAt(1.0, die.close), null)
    }
}

/**
 * Le décalage qui amène le centre de la face lue sur celui du hublot.
 *
 * La caméra vise le centre du dé, ce qui suffit aux solides réguliers : le
 * centre d'un carré ou d'un triangle équilatéral est sur l'axe de sa normale, il
 * tombe donc pile au milieu quand la face est en haut. Le cerf-volant d'un d10
 * n'a pas cette politesse — son centre est décalé de près d'une demi-unité de dé
 * sur le côté, soit cinq cellules au gros plan, et le chiffre partait se coller
 * dans un coin du hublot.
 *
 * Le décalage est pesé par l'avancement de la pose, [posed] — le même que la
 * caméra, et pas un réglage voisin : nul en vol, où c'est le solide entier qu'on
 * regarde, entier une fois posé, où c'est la face. Deux progressions différentes
 * pour un seul mouvement, c'est une face qui arrive au milieu du hublot pendant
 * que le cadrage bouge encore.
 */
private fun centering(die: Die, q: Quat, z: Double): Vec3 {
    val w = posed(z)
    if (w <= 0) return Vec3(0.0, 0.0, 0.0)
    val m = q.toMatrix()
    return m.toWorld(die.faces[die.topIndex(m)].c) * -w
}

/* ------------------------------ l'écoute ---------------------------------- */

/** L'étape lisible, pour l'affichage et les retours haptiques. */
enum class Stage { REST, TOSS, TUMBLE, BRAKE, READ }

fun stageAt(t: Double?): Stage = when {
    t == null || t >= T_END -> Stage.REST
    t < T_TOSS -> Stage.TOSS
    t < T_BRAKE -> Stage.TUMBLE
    t < T_LAND -> Stage.BRAKE
    else -> Stage.READ
}

/**
 * Fenêtre morte du départ. On vient de jeter : la caméra recule encore et le dé
 * n'est pas retombé une seule fois. Une secousse ici n'est pas une intention,
 * c'est la fin du geste précédent — le poignet qui revient. La compter ferait du
 * toy un bouton qui se déclenche deux fois.
 */
const val DEAD_HEAD = T_TOSS

/**
 * Fenêtre morte de l'arrivée. Le dé est en train de choisir sa face. Relancer
 * là, c'est effacer un résultat qui n'a jamais été montré : on aurait un toy
 * qu'on peut secouer sans fin sans jamais rien lire, ce qui est exactement ce
 * qu'un dé n'est pas.
 */
const val DEAD_TAIL = 0.45

enum class Verdict { OK, TOO_EARLY, TOO_LATE, READING }

/**
 * Le jet est-il relançable ? `null` = dé posé.
 *
 * Deux états acceptent : **posé** et **au milieu du vol**. Le milieu du vol
 * compte parce qu'un dé encore en l'air n'a pas de résultat à effacer — une
 * seconde secousse le renvoie en l'air, ce qui est précisément ce qu'une main
 * fait quand le jet lui déplaît.
 */
fun verdict(t: Double?): Verdict = when {
    t == null -> Verdict.OK
    t < DEAD_HEAD -> Verdict.TOO_EARLY
    t < T_LAND - DEAD_TAIL -> Verdict.OK
    t < T_LAND -> Verdict.TOO_LATE
    t < T_END -> Verdict.READING
    else -> Verdict.OK
}
