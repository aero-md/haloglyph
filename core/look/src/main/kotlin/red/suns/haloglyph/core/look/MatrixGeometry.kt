package red.suns.haloglyph.core.look

import red.suns.haloglyph.core.matrix.MatrixLook
import red.suns.haloglyph.core.matrix.MatrixSpec
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Le placement d'un hublot dessiné dans une boîte carrée de [boxPx] pixels.
 *
 * ## Pourquoi tout est en entiers
 *
 * Une cellule occupe un nombre **entier** de pixels. À pas fractionnaire, une
 * colonne sur *n* gagne un pixel de gouttière et la trame devient irrégulière —
 * défaut discret sur un aperçu de 250 px, franc sur une vignette de 44 dp où la
 * cellule fait quatre pixels. C'est la même règle que le portail applique à ses
 * canvas, et pour la même raison.
 *
 * Le disque obtenu est donc en général un peu **plus petit** que la boîte : le
 * mou reste autour, la boîte garde la taille qu'on lui a demandée, et rien ne
 * bouge quand la densité de l'écran change.
 */
class MatrixGeometry(val spec: MatrixSpec, val boxPx: Int) {

    /** Pixels par LED. */
    val cell: Int

    /** Cerne, de chaque côté, en pixels. */
    val ring: Int

    /** Côté du champ de LEDs. */
    val field: Int

    /** Diamètre du hublot, cerne compris. */
    val disc: Int

    /** Côté d'une LED, et sa marge dans la cellule. */
    val led: Int
    val pad: Int

    /** Coin haut-gauche du disque dans la boîte. */
    val discOrigin: Int

    init {
        // Le plus grand pas qui laisse au moins un pixel de cerne, puis on
        // redescend tant que le cerne **visé** ne rentre pas. `ringFor` arrondit,
        // donc la bonne cellule ne se calcule pas d'un trait ; deux tours en
        // pratique.
        var c = max(1, (boxPx - 2) / spec.size)
        while (c > MIN_CELL && discFor(spec, c) > boxPx) c--

        cell = c
        field = spec.size * c

        // Ce qui reste autour du champ, une fois la cellule arrêtée. Le cerne le
        // prend, dans les deux sens :
        //
        // - **trop peu** — sous MIN_CELL la cellule ne descend plus, et une
        //   vignette minuscule doit sacrifier son cerne plutôt que déborder ;
        // - **trop** — la cellule étant entière, il reste presque toujours du
        //   mou, et un cerne figé à sa cote décorative laisserait le hublot
        //   flotter dans son emplacement. Sur un widget, qui *est* le hublot,
        //   ça se voit tout de suite. Le cerne absorbe donc le mou jusqu'à
        //   [RING_MAX], au-delà duquel on aurait un anneau noir au lieu d'un
        //   liseré.
        val available = max(1, (boxPx - field) / 2)
        val wanted = ringFor(spec, c)
        ring = if (available < wanted) available
        else min(available, (RING_MAX * c).roundToInt())
        disc = field + 2 * ring
        discOrigin = (boxPx - disc) / 2

        // Au moins 1 px d'écart — deux LEDs jointives ne se distinguent plus — et
        // une marge **plancher** plutôt que la moitié exacte : centrer imposerait
        // une marge à la demie dès que l'écart est impair, donc un bord
        // anticrénelé à chaque cellule. Décaler la trame entière d'un demi-pixel
        // ne se voit pas ; un bord flou, si.
        led = min(max(1, c - 1), max(2, (c * MatrixLook.DUTY).roundToInt()))
        pad = (c - led) / 2
    }

    /** Coin gauche d'une colonne, en pixels, dans un repère calé sur le disque. */
    fun leftOf(x: Int): Int = ring + x * cell + pad

    /** Coin haut d'une ligne, dans le même repère. */
    fun topOf(y: Int): Int = ring + y * cell + pad

    companion object {
        /** En dessous, LED et écart ne se distinguent plus. */
        const val MIN_CELL = 3

        /**
         * Cerne maximal, en largeurs de LED : jusqu'où il a le droit de grossir
         * pour absorber le mou de la quantification.
         *
         * 2,6 contre 1,6 visés, soit un pas de LED de marge. Au-delà, le liseré
         * du biseau se détache du champ et le hublot se lit comme un anneau noir
         * avec une matrice au milieu.
         */
        const val RING_MAX = 2.6f

        /**
         * Le cerne visé pour un pas de [cell] pixels.
         *
         * Le décoratif de [MatrixLook.RING], jamais sous la borne géométrique —
         * ce qu'il faut pour qu'aucun **coin** de LED ne sorte de la découpe. Le
         * masque teste le centre des cellules, donc la LED la plus excentrée est
         * à `maxDistance` du centre et son coin une demi-diagonale plus loin.
         */
        fun ringFor(spec: MatrixSpec, cell: Int): Int {
            val floorRing = spec.maxDistance - spec.size / 2f + sqrt(0.5f)
            return max(
                max(1, ceil(floorRing * cell).toInt()),
                (MatrixLook.RING * cell).roundToInt(),
            )
        }

        /** Diamètre du hublot pour un pas de [cell] pixels, cerne visé compris. */
        fun discFor(spec: MatrixSpec, cell: Int): Int =
            spec.size * cell + 2 * ringFor(spec, cell)
    }
}
