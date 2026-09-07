package red.suns.haloglyph.dice.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Les quatre dés, comme **solides** : d6, d10, d12, d20.
 *
 * Rien ici n'est dessiné. Un dé est une liste de faces, chacune avec son plan,
 * son centre, ses marques et sa valeur — le rendu ne fait que couper des rayons
 * contre ces plans et tamponner ces marques. C'est ce qui permet d'ajouter un
 * solide sans toucher au rendu : un maillage de plus dans ce fichier, et le
 * reste suit.
 *
 * Chaque solide est décrit par ses **sommets et ses faces**, pas par ses plans.
 * Les plans, les centres, les rayons et les tangentes en sont dérivés : écrire
 * dix normales à la main pour un d10, c'est écrire dix occasions de se tromper
 * d'un signe, et un signe faux sur une normale fait un dé qu'on voit de
 * l'intérieur.
 *
 * Deux façons de marquer une face, et c'est celle des vrais dés : **le d6 porte
 * des pips, les autres un chiffre**. Personne ne compte huit points sur un
 * triangle, et un jeu de dés du commerce ne le demande à personne.
 */

/* --------------------------------- vecteurs -------------------------------- */

data class Vec3(val x: Double, val y: Double, val z: Double)

val UP = Vec3(0.0, 1.0, 0.0)

infix fun Vec3.dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

infix fun Vec3.cross(o: Vec3): Vec3 =
    Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

operator fun Vec3.plus(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)

operator fun Vec3.minus(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)

operator fun Vec3.times(k: Double): Vec3 = Vec3(x * k, y * k, z * k)

operator fun Vec3.unaryMinus(): Vec3 = Vec3(-x, -y, -z)

/**
 * Longueur, par la racine de la somme des carrés et **non par `hypot`**.
 *
 * `hypot` est le choix normal : il évite le débordement quand les composantes
 * sont énormes ou minuscules. Ici elles vivent dans [−2, 2], le débordement
 * n'existe pas, et `hypot` coûte une chose qui compte davantage : il n'est pas
 * reproductible d'une plateforme à l'autre. La norme lui demande d'être juste à
 * un ulp près, pas d'être exact — `Math.hypot(x, y, z)` en JS et
 * `hypot(hypot(x, y), z)` sur la JVM ne donnent pas les mêmes derniers bits.
 *
 * Ça s'est vu sur le prototype web, et exactement ici : les normales du
 * dodécaèdre différaient de 1e−16, ce qui a suffi à faire basculer **deux
 * cellules** de la pose de repos d'une face à sa voisine — une arête presque
 * verticale qui passait pile au centre de ces cellules. La multiplication et la
 * racine, elles, sont exactes au bit près dans IEEE 754 : même expression, même
 * ordre, même résultat partout.
 */
val Vec3.length: Double get() = sqrt(x * x + y * y + z * z)

fun Vec3.normalized(): Vec3 {
    val n = length
    return if (n == 0.0) this else Vec3(x / n, y / n, z / n)
}

/* ---------------------------------- pips ----------------------------------- */

/**
 * Les six motifs du d6, en fractions du rayon inscrit de la face — la
 * demi-arête, sur un carré.
 *
 * Le 6 écarte ses rangées à 0,56 plutôt que 0,52 : à trois rangées le pas est
 * plus serré qu'à deux, et sans cet écart les taches du gros plan se touchent.
 *
 * Tous les motifs sont **symétriques par rapport au centre** : à chaque pip
 * répond son opposé, le pip central étant son propre opposé. Ce n'est pas une
 * coïncidence de dessin, c'est ce qu'est un dé, et `SolidsTest` le vérifie.
 */
private val PIPS: Array<Array<DoubleArray>> = arrayOf(
    emptyArray(),
    arrayOf(doubleArrayOf(0.0, 0.0)),
    arrayOf(doubleArrayOf(-0.52, -0.52), doubleArrayOf(0.52, 0.52)),
    arrayOf(doubleArrayOf(-0.52, -0.52), doubleArrayOf(0.0, 0.0), doubleArrayOf(0.52, 0.52)),
    arrayOf(
        doubleArrayOf(-0.52, -0.52), doubleArrayOf(0.52, -0.52),
        doubleArrayOf(-0.52, 0.52), doubleArrayOf(0.52, 0.52),
    ),
    arrayOf(
        doubleArrayOf(-0.52, -0.52), doubleArrayOf(0.52, -0.52), doubleArrayOf(0.0, 0.0),
        doubleArrayOf(-0.52, 0.52), doubleArrayOf(0.52, 0.52),
    ),
    arrayOf(
        doubleArrayOf(-0.52, -0.56), doubleArrayOf(-0.52, 0.0), doubleArrayOf(-0.52, 0.56),
        doubleArrayOf(0.52, -0.56), doubleArrayOf(0.52, 0.0), doubleArrayOf(0.52, 0.56),
    ),
)

/* --------------------------------- le solide ------------------------------- */

/**
 * Un pip, en fractions du rayon inscrit de sa face — et **pas** un point du
 * solide.
 *
 * C'est un motif, pas une géométrie. Les pips sont tamponnés sur la trame, à
 * l'endroit et à l'échelle de la trame ; les décrire en 3D reviendrait à les
 * faire passer par la projection, qui les rendrait à leur tour à des positions
 * sub-cellulaires que l'arrondi casserait chacune dans son coin. Voir le
 * renderer, qui les pose sur un réseau de cellules entières.
 *
 * [u] va vers la droite, [v] vers le bas, comme l'écran.
 */
class PipSpot(val u: Double, val v: Double)

class Face(
    /** Normale unitaire, sortante. */
    val n: Vec3,
    /** Distance du centre du dé au plan de la face. */
    val d: Double,
    /** Centre visuel — barycentre des sommets. C'est là que se pose la marque. */
    val c: Vec3,
    /** Rayon inscrit dans la face, depuis [c]. Dit quelle taille de marque tient. */
    val inr: Double,
    /** La valeur portée par la face, 1..faceCount. */
    val value: Int,
    /** Le nombre imprimé, vide si la face porte des pips. Deux chiffres au dix. */
    val glyph: String,
    /** Motif de pips de la face. Vide sur une face chiffrée. */
    val pips: List<PipSpot>,
)

/**
 * Le jeu retenu : celui d'un joueur de rôle, moins les deux dés dont personne ne
 * se sert seul. Le d4 et le d8 y étaient au départ, et sont partis ensemble — le
 * premier ne peut pas se lire « la face du dessus » puisqu'un tétraèdre posé
 * présente un sommet en l'air, le second n'a jamais rien apporté que le d6 et le
 * d10 n'aient déjà.
 *
 * La clé est celle des préférences : elle est persistée, donc elle ne change
 * pas. Le nom de l'énumération, lui, peut être renommé sans casser personne.
 */
enum class DieId(val key: String) {
    D6("d6"),
    D10("d10"),
    D12("d12"),
    D20("d20"),
}

class Die internal constructor(
    val id: DieId,
    /** Nombre de faces — et donc plage du tirage. */
    val faceCount: Int,
    /**
     * Demi-champ de la caméra au gros plan, en unités de dé.
     *
     * Par dé et non partagé : à taille de solide égale, la face d'un icosaèdre
     * est deux fois plus petite que celle d'un cube, et un gros plan commun
     * révélerait un triangle perdu au milieu du hublot. Réglé pour que la face
     * déborde d'environ 10 % — ce qui dit « on est dessus » — et que le nombre
     * tienne dans le cercle inscrit de la face.
     */
    val close: Double,
    /**
     * Nombre de poses de repos équivalentes pour une face donnée, par rotation
     * autour de sa normale. Quatre pour un carré, trois pour un triangle, une
     * pour le cerf-volant d'un d10, qui n'a aucune symétrie de rotation.
     *
     * Ça ne sert qu'à varier la pose : la marque, elle, est tamponnée à l'écran
     * et reste droite quelle que soit la pose.
     */
    val spin: Int,
    val faces: List<Face>,
) {
    /**
     * L'indice de la face tournée vers le haut, dans une orientation déjà
     * matricée.
     *
     * Trois endroits en ont besoin et doivent tomber d'accord : la relecture du
     * résultat, le recentrage de la caméra, et le tampon de la marque dans le
     * renderer. C'est la même question posée trois fois — « quelle face le dé
     * montre-t-il ? » — et il n'y en a qu'une réponse écrite ici. Deux critères
     * voisins, l'un dans le rendu et l'autre dans la caméra, c'est un dé qui
     * écrit un nombre à côté du chiffre qu'il vise.
     */
    fun topIndex(m: Mat3): Int {
        var best = 0
        var bestUp = Double.NEGATIVE_INFINITY
        for (i in faces.indices) {
            val up = m.upComponent(faces[i].n)
            if (up > bestUp) {
                bestUp = up
                best = i
            }
        }
        return best
    }

    /** La face tournée vers le haut, relue de l'orientation et non du tirage. */
    fun topFace(q: Quat): Int = faces[topIndex(q.toMatrix())].value

    /**
     * Une orientation **posée** : la face qui porte [value] vers le haut,
     * tournée d'un cran [twist] autour de la verticale.
     *
     * Le cran ne change pas ce qu'on lit. Les marques sont tamponnées à l'écran,
     * donc toujours droites, et les six motifs de pips du d6 sont de toute façon
     * symétriques au quart de tour — deux rangées de trois valent deux colonnes
     * de trois sur n'importe quel dé du commerce. Ce qu'il change, ce sont les
     * faces latérales visibles pendant le vol, et c'est tout ce qu'on lui
     * demande : sans lui, le dé repart toujours dans la même pose et la culbute
     * se répète.
     *
     * « Vers le haut » veut dire : **la face qui regarde la caméra du gros
     * plan**. Le dé flotte dans le noir, il n'y a ni table ni dessous, et les
     * quatre solides retenus se lisent tous ainsi.
     */
    fun restQuat(value: Int, twist: Int): Quat {
        val f = faces.firstOrNull { it.value == value } ?: faces[0]
        return Quat.axis(UP, twist * 2 * PI / spin) * upright(f.n)
    }
}

/** La rotation qui amène la direction [n] du dé sur la verticale du monde. */
private fun upright(n: Vec3): Quat {
    val c = n dot UP
    if (c > 0.99999) return Quat.IDENTITY
    // Déjà à l'envers : n'importe quel axe horizontal fait l'affaire.
    if (c < -0.99999) return Quat.axis(Vec3(1.0, 0.0, 0.0), PI)
    return Quat.axis((n cross UP).normalized(), acos(c))
}

/* ------------------------------- les maillages ----------------------------- */

/**
 * Tous les solides sont ramenés au **même rayon circonscrit**, celui du cube.
 *
 * C'est la silhouette en vol qu'on égalise, pas la face : le cadrage large est
 * commun aux quatre dés, et un solide qui dépasserait se ferait rogner par la
 * découpe du hublot. La taille des faces, elle, se rattrape dé par dé au gros
 * plan — voir [Die.close].
 */
private val REACH = sqrt(3.0)

private class Mesh(
    val verts: List<Vec3>,
    /** Sommets de chaque face. L'ordre importe peu : la normale est réorientée. */
    val faces: List<IntArray>,
)

/** Cube : les huit coins de ±1. */
private fun cube(): Mesh = Mesh(
    verts = listOf(
        Vec3(-1.0, -1.0, -1.0), Vec3(1.0, -1.0, -1.0), Vec3(1.0, 1.0, -1.0), Vec3(-1.0, 1.0, -1.0),
        Vec3(-1.0, -1.0, 1.0), Vec3(1.0, -1.0, 1.0), Vec3(1.0, 1.0, 1.0), Vec3(-1.0, 1.0, 1.0),
    ),
    faces = listOf(
        intArrayOf(4, 5, 6, 7), // +Z
        intArrayOf(1, 0, 3, 2), // −Z
        intArrayOf(5, 1, 2, 6), // +X
        intArrayOf(0, 4, 7, 3), // −X
        intArrayOf(7, 6, 2, 3), // +Y
        intArrayOf(0, 1, 5, 4), // −Y
    ),
)

/**
 * Les valeurs du d6, posées à la main et pas déduites.
 *
 * C'est un dé occidental : faces opposées à sept, et avec le 1 en haut et le 2
 * vers soi, le 3 tombe à droite. Un appariement automatique donnerait aussi des
 * opposées à sept, mais une chiralité tirée de l'ordre des faces — donc un dé
 * juste une fois sur deux, sans qu'on sache laquelle.
 */
private val CUBE_VALUES = intArrayOf(2, 5, 3, 4, 1, 6)

private val PHI = (1 + sqrt(5.0)) / 2

/** Icosaèdre : trois rectangles d'or emboîtés, douze sommets. */
private fun icosaVerts(): List<Vec3> {
    val out = mutableListOf<Vec3>()
    for (s in intArrayOf(1, -1)) {
        for (t in intArrayOf(1, -1)) {
            out += Vec3(0.0, s.toDouble(), t * PHI)
            out += Vec3(s.toDouble(), t * PHI, 0.0)
            out += Vec3(t * PHI, 0.0, s.toDouble())
        }
    }
    return out
}

/**
 * Dodécaèdre : les huit coins d'un cube, plus trois rectangles d'or.
 *
 * L'ordre des coordonnées n'est pas indifférent. Les deux jeux de sommets
 * doivent être **duaux l'un de l'autre dans la même pose**, et pas seulement à
 * une rotation près : ici le grand côté du rectangle est en `y` là où
 * l'icosaèdre le met en `z`. Écrit dans l'autre sens — le plus naturel à taper —
 * les directions de faces tombaient sur des sommets, et [fromDual] ne trouvait
 * qu'un sommet par face au lieu de cinq. C'est exactement ce que son contrôle
 * est là pour dire.
 */
private fun dodecaVerts(): List<Vec3> {
    val out = mutableListOf<Vec3>()
    for (x in intArrayOf(1, -1)) {
        for (y in intArrayOf(1, -1)) {
            for (z in intArrayOf(1, -1)) out += Vec3(x.toDouble(), y.toDouble(), z.toDouble())
        }
    }
    val i = 1 / PHI
    for (s in intArrayOf(1, -1)) {
        for (t in intArrayOf(1, -1)) {
            out += Vec3(0.0, s * PHI, t * i)
            out += Vec3(s * PHI, t * i, 0.0)
            out += Vec3(t * i, 0.0, s * PHI)
        }
    }
    return out
}

/**
 * Le maillage d'un solide dont on connaît les sommets et les **directions de
 * faces**, sans lister une seule face à la main.
 *
 * Pour un dodécaèdre il faudrait douze pentagones, soit soixante indices écrits
 * dans le bon ordre ; pour un icosaèdre, vingt triangles. Personne ne relit ça,
 * et une seule permutation donne une face retournée qu'on ne verra qu'en jouant.
 *
 * La direction d'une face suffit à la retrouver : les sommets de la face sont
 * ceux qui vont **le plus loin** dans cette direction, et ils sont tous à la
 * même distance puisque le solide est régulier. Restent à les mettre en cercle,
 * ce que fait un tri par angle autour du centre de la face.
 *
 * Et les directions ne s'écrivent pas non plus : ce sont les sommets du
 * **dual**. Les faces d'un icosaèdre regardent vers les sommets d'un
 * dodécaèdre, et réciproquement — les deux listes de sommets se suffisent l'une
 * à l'autre.
 */
private fun fromDual(verts: List<Vec3>, dirs: List<Vec3>, expect: Int): Mesh {
    val faces = dirs.map { dir ->
        val n = dir.normalized()
        val far = verts.maxOf { it dot n }
        val on = verts.indices.filter { abs((verts[it] dot n) - far) < 1e-9 }
        require(on.size == expect) { "face à ${on.size} sommets, $expect attendus" }
        val c = centroid(verts, on)
        val u = (verts[on[0]] - c).normalized()
        val w = n cross u
        on.sortedBy { i ->
            val r = verts[i] - c
            atan2(r dot w, r dot u)
        }.toIntArray()
    }
    return Mesh(verts, faces)
}

/**
 * Trapézoèdre pentagonal — le d10, dix cerfs-volants.
 *
 * Deux couronnes de cinq sommets décalées d'un dixième de tour, plus deux
 * pointes sur l'axe. Chaque face joint une pointe, deux sommets voisins de sa
 * couronne, et le sommet de l'autre couronne qui s'insère entre eux.
 *
 * La proportion n'est pas libre : pour que les quatre coins d'un cerf-volant
 * soient **coplanaires**, il faut `h/c = (K + sin2φ) / (K − sin2φ)`, avec
 * `φ = 36°` et `K = sin2φ·cosφ + (1−cos2φ)·sinφ` — soit environ 9,47. Le rayon
 * `r`, lui, reste libre : l'étirer radialement est une application linéaire, et
 * une application linéaire envoie un plan sur un plan. C'est ce qui fait des
 * trapézoèdres une famille à un paramètre, et `r = 10` donne la silhouette d'un
 * d10 du commerce — un peu plus large que haute.
 */
private fun trapezohedron(): Mesh {
    val p = PI / 5
    val s = sin(2 * p)
    val k = s * cos(p) + (1 - cos(2 * p)) * sin(p)
    val c = 1.0
    val h = c * (k + s) / (k - s)
    val r = 10.0

    val verts = mutableListOf(Vec3(0.0, h, 0.0), Vec3(0.0, -h, 0.0))
    for (i in 0 until 5) verts += Vec3(r * cos(2 * p * i), c, r * sin(2 * p * i))
    for (i in 0 until 5) verts += Vec3(r * cos(2 * p * i + p), -c, r * sin(2 * p * i + p))

    fun upper(i: Int) = 2 + i % 5
    fun lower(i: Int) = 7 + i % 5
    val faces = mutableListOf<IntArray>()
    for (i in 0 until 5) faces += intArrayOf(0, upper(i), lower(i), upper(i + 1))
    for (i in 0 until 5) faces += intArrayOf(1, lower(i), upper(i + 1), lower(i + 1))
    return Mesh(verts, faces)
}

/* -------------------------------- fabrication ------------------------------ */

/** Barycentre des sommets d'une face. */
private fun centroid(verts: List<Vec3>, f: List<Int>): Vec3 {
    var c = Vec3(0.0, 0.0, 0.0)
    for (i in f) c += verts[i]
    return c * (1.0 / f.size)
}

/** Rayon du cercle inscrit : la plus courte distance du centre à une arête. */
private fun inradius(verts: List<Vec3>, f: IntArray, c: Vec3): Double {
    var best = Double.POSITIVE_INFINITY
    for (i in f.indices) {
        val a = verts[f[i]]
        val b = verts[f[(i + 1) % f.size]]
        val e = b - a
        val len = e.length
        val gap = ((c - a) cross e).length / (if (len == 0.0) 1.0 else len)
        if (gap < best) best = gap
    }
    return best
}

/**
 * Apparie les faces opposées et distribue les valeurs pour que deux opposées
 * somment à `faceCount + 1`.
 *
 * Les quatre solides retenus ont tous leurs faces par paires parallèles, donc
 * l'appariement aboutit toujours. Une forme qui n'en aurait pas — un tétraèdre —
 * prendrait ses valeurs dans l'ordre, sans que rien ne casse.
 */
private fun assignValues(normals: List<Vec3>, faceCount: Int): IntArray {
    val value = IntArray(normals.size)
    var next = 1
    for (i in normals.indices) {
        if (value[i] != 0) continue
        value[i] = next
        val anti = normals.indices.firstOrNull { j ->
            j != i && value[j] == 0 && (normals[j] dot normals[i]) < -0.999
        }
        if (anti != null) value[anti] = faceCount + 1 - next
        next++
    }
    return value
}

private fun build(
    id: DieId,
    mesh: Mesh,
    close: Double,
    spin: Int,
    values: IntArray? = null,
): Die {
    val count = mesh.faces.size

    // Mise à l'échelle par le rayon circonscrit, avant tout le reste.
    val scale = REACH / mesh.verts.maxOf { it.length }
    val verts = mesh.verts.map { it * scale }

    class Raw(val n: Vec3, val d: Double, val c: Vec3, val inr: Double, val f: IntArray)

    val raw = mesh.faces.map { f ->
        val c = centroid(verts, f.toList())
        var n = ((verts[f[1]] - verts[f[0]]) cross (verts[f[2]] - verts[f[0]])).normalized()
        // Le centre du dé est à l'intérieur : une normale sortante regarde du
        // même côté que n'importe quel sommet de sa face.
        if ((n dot verts[f[0]]) < 0) n = -n
        Raw(n, n dot verts[f[0]], c, inradius(verts, f, c), f)
    }

    val value = values ?: assignValues(raw.map { it.n }, count)

    val faces = raw.mapIndexed { i, r ->
        val v = value[i]
        /* Les pips ne concernent que le d6, et ne sont qu'un motif : aucune base
           tangente à construire, aucun point à placer dans le solide. Le rayon
           inscrit d'une face carrée vaut sa demi-arête, donc les fractions de
           [PIPS] sont déjà à la bonne unité. */
        val pips = if (count != 6) emptyList() else PIPS[v].map { PipSpot(it[0], it[1]) }
        Face(
            n = r.n,
            d = r.d,
            c = r.c,
            inr = r.inr,
            value = v,
            /* Le dix s'écrit `10`, et pas `0` comme sur un dé du commerce.
               La convention du `0` marche sur un objet qu'on tient : les neuf
               autres faces sont là pour dire de quoi il s'agit. Ici on ne voit
               **qu'une face à la fois**, en gros plan, et un `0` seul annonce
               alors une valeur que le dé ne peut pas faire. Le prix est payé en
               taille : deux chiffres ne tiennent dans le cerf-volant qu'à la
               police non dilatée, donc le dix s'affiche plus petit que le sept.
               C'est la lisibilité du nombre qui tranche, pas l'uniformité du
               dessin. */
            glyph = if (count == 6) "" else v.toString(),
            pips = pips,
        )
    }

    return Die(id, count, close, spin, faces)
}

/* -------------------------------------------------------------------------- */

/**
 * Le jeu complet, construit une fois au chargement de la classe.
 *
 * Les [Die.close] sont réglés dé par dé, et le critère n'est pas seulement « la
 * face remplit le hublot » : c'est **le nombre en grand qui décide**. Le rendu
 * ne dispose que de trois tailles de glyphe — voir `STEPS` dans le renderer — et
 * le passage de l'une à l'autre se joue sur une comparaison. Réglé au ras du
 * seuil, un solide retombe d'un cheveu sur la petite taille et révèle un 1 de
 * sept cellules au milieu d'un hublot de vingt-cinq. Chaque valeur garde donc
 * une marge nette au-dessus du seuil, quitte à zoomer plus fort et à laisser les
 * coins de la face sortir du hublot — ce qui dit de toute façon qu'on est
 * dessus.
 *
 * Le d6 est le seul dont le réglage ne dépende pas d'un nombre, puisqu'il porte
 * des pips : 1,30 lui fait un carré qui déborde d'un dixième, et c'est le
 * cadrage sur lequel tout le reste a été réglé.
 */
object Dice {

    val ALL: List<Die> = listOf(
        build(DieId.D6, cube(), 1.3, 4, CUBE_VALUES),
        build(DieId.D10, trapezohedron(), 1.06, 1),
        build(DieId.D12, fromDual(dodecaVerts(), icosaVerts(), 5), 1.24, 5),
        build(DieId.D20, fromDual(icosaVerts(), dodecaVerts(), 3), 0.9, 3),
    )

    /**
     * Le d6, et par son identité plutôt que par son rang : c'est le dé dont tout
     * le monde connaît les faces, pas celui qui se trouve en tête de liste.
     */
    val DEFAULT: Die = ALL.first { it.id == DieId.D6 }

    fun byKey(key: String?): Die = ALL.firstOrNull { it.id.key == key } ?: DEFAULT

    /** Le solide suivant, en rotation. C'est ce que fait l'appui long. */
    fun next(die: Die): Die = ALL[(ALL.indexOf(die) + 1) % ALL.size]
}
