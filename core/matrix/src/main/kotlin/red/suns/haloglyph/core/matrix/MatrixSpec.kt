package red.suns.haloglyph.core.matrix

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Géométrie d'une Glyph Matrix.
 *
 * La matrice **n'est pas un carré plein** : c'est un disque de LEDs inscrit dans
 * une grille carrée. Sur le Phone (3), 489 LEDs dans une grille 25×25 — les 136
 * cellules restantes n'existent pas physiquement.
 *
 * Le matériel ignore les valeurs poussées sur une cellule absente ; une émulation
 * qui les affiche, elle, ne ressemble plus à la matrice. Le masque est donc porté
 * ici, une fois, et appliqué par toutes les surfaces.
 *
 * L'appartenance est lue dans la **table d'allocation officielle** (celle du GDK,
 * cf. [PHONE3_ALLOCATION]) et non calculée à l'exécution : une seule source de
 * vérité, aucun flottant dans le chemin chaud, et une table partageable telle
 * quelle avec le port TypeScript du portail. `MatrixSpecTest` vérifie qu'elle
 * coïncide au pixel près avec le disque géométrique dont elle est issue.
 *
 * Les renderers reçoivent une `MatrixSpec` au lieu de lire une constante globale
 * `SIZE = 25`. Ça ne coûte rien aujourd'hui et ça évite de repasser sur quatre
 * renderers le jour où le Phone (4a) Pro (13×13, 137 LEDs) entre dans le
 * périmètre — V1 ne cible que le Phone (3).
 */
class MatrixSpec internal constructor(
    /** Nom lisible, pour les logs et l'UI de diagnostic. */
    val name: String,
    /** Côté de la grille carrée. */
    val size: Int,
    allocation: Array<String>,
) {
    /** Nombre de cellules de la grille — 625 sur le Phone (3), LEDs absentes comprises. */
    val cellCount: Int = size * size

    /** Centre du disque. Impair par construction : la LED centrale existe. */
    val centerX: Int = (size - 1) / 2
    val centerY: Int = (size - 1) / 2

    /** Rayon du disque, en cellules. */
    val radius: Float = size / 2f

    private val present = BooleanArray(cellCount)

    /** Distance de chaque cellule au centre. Utile aux renderers (ondes, dégradés). */
    val distance: FloatArray = FloatArray(cellCount)

    /** Indices des LEDs physiques, dans l'ordre ligne par ligne. */
    val leds: IntArray

    /** Nombre de LEDs physiques — 489 sur le Phone (3). */
    val ledCount: Int get() = leds.size

    /**
     * Distance au centre de la LED la plus excentrée.
     *
     * Un peu en deçà de [radius], parce que le masque teste le **centre** des
     * cellules. C'est de là que se déduit le cerne minimal d'un hublot dessiné :
     * le coin de cette LED est encore une demi-diagonale plus loin, et en deçà
     * de `maxDistance − size/2 + √2/2` largeurs de LED il sortirait de la
     * découpe. Voir `MatrixLook.RING`.
     */
    val maxDistance: Float

    /**
     * Contour du disque, trié dans le sens horaire depuis midi.
     *
     * Définition : une LED présente dont l'un des quatre voisins directs est
     * absent. C'est le support de l'anneau des secondes de Lapse — d'où le tri,
     * qui fait de l'anneau une liste parcourable, pas un ensemble.
     */
    val edgeRing: IntArray

    init {
        require(allocation.size == size) {
            "Table d'allocation de ${allocation.size} lignes pour une grille $size×$size"
        }
        val present0 = mutableListOf<Int>()
        for (y in 0 until size) {
            val row = allocation[y]
            require(row.length == size) { "Ligne $y : ${row.length} colonnes au lieu de $size" }
            for (x in 0 until size) {
                val i = y * size + x
                distance[i] = hypot((x - centerX).toFloat(), (y - centerY).toFloat())
                if (row[x] == '1') {
                    present[i] = true
                    present0 += i
                }
            }
        }
        leds = present0.toIntArray()
        maxDistance = leds.maxOf { distance[it] }

        edgeRing = clockwise(
            leds.filter { i ->
                val x = i % size
                val y = i / size
                !isLed(x - 1, y) || !isLed(x + 1, y) || !isLed(x, y - 1) || !isLed(x, y + 1)
            },
        )
    }

    /**
     * Les LEDs à au moins [minDistance] du centre, triées dans le sens horaire
     * depuis midi.
     *
     * L'anneau des secondes de Lapse est une **bande**, pas le contour strict :
     * à 12,5 de rayon, le contour ne fait que 68 LEDs, la bande à partir de 11,3
     * en fait 88. La différence se voit — l'épaisseur du trait, et la finesse du
     * pas sur 60 secondes. D'où ce paramètre plutôt qu'un [edgeRing] imposé.
     */
    fun ringBand(minDistance: Float): IntArray =
        clockwise(leds.filter { distance[it] >= minDistance })

    /** Tri angulaire commun : midi en premier, puis sens horaire. */
    private fun clockwise(indices: List<Int>): IntArray =
        indices.sortedBy { i ->
            val a = atan2((i % size - centerX).toDouble(), -(i / size - centerY).toDouble())
            if (a < 0) a + 2 * PI else a
        }.toIntArray()

    fun index(x: Int, y: Int): Int = y * size + x

    fun xOf(index: Int): Int = index % size

    fun yOf(index: Int): Int = index / size

    /** Vrai si la cellule porte une LED physique. Hors grille = faux, jamais d'exception. */
    fun isLed(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && present[y * size + x]

    fun isLed(index: Int): Boolean = index in 0 until cellCount && present[index]

    /** Nombre de LEDs par ligne, de haut en bas. Sert aux tests et au diagnostic. */
    fun ledsPerRow(): IntArray = IntArray(size) { y ->
        (0 until size).count { x -> present[y * size + x] }
    }

    override fun toString(): String = "$name ${size}×$size, $ledCount LEDs"

    companion object {
        /**
         * Nothing Phone (3) — 25×25, 489 LEDs. La seule matrice ciblée en V1.
         */
        val Phone3: MatrixSpec = MatrixSpec("Nothing Phone (3)", 25, PHONE3_ALLOCATION)
    }
}
