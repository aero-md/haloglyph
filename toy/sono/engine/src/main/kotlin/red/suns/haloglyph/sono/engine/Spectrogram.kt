package red.suns.haloglyph.sono.engine

/**
 * L'histoire récente du spectre : une ligne par tranche de temps.
 *
 * ## Pourquoi une tranche, et pas une image
 *
 * L'affichage produit trente images par seconde et la matrice a vingt-cinq
 * lignes : une ligne par image donnerait moins d'une seconde d'histoire, qui
 * défilerait trop vite pour qu'on y lise quoi que ce soit. On agrège donc par
 * tranches de [rowSeconds], ce qui met [rows] × [rowSeconds] ≈ 5 s à l'écran —
 * assez pour voir passer une phrase, un passage de véhicule, une mesure de
 * musique.
 *
 * L'agrégation est un **maximum**, pas une moyenne. Sur 200 ms, une moyenne
 * dilue un transitoire dans le silence qui l'entoure et le spectrogramme d'une
 * batterie devient un aplat ; le maximum garde la frappe, qui est précisément ce
 * qu'on est venu voir.
 *
 * ## Alimenté en permanence
 *
 * Le toy le remplit à chaque image, quel que soit le mode affiché. Ça coûte 25
 * comparaisons et ça évite qu'arriver sur le mode spectrogramme ouvre sur un
 * écran vide qui met cinq secondes à se peupler — l'histoire est déjà là quand
 * on la demande.
 */
class Spectrogram(
    private val bands: Int,
    val rows: Int,
    private val rowSeconds: Double,
) {
    /** Tampon circulaire, `rows` lignes de `bands` valeurs, en dB SPL de bande. */
    private val cells = DoubleArray(rows * bands) { EMPTY }

    /** Index de la ligne la plus récente. */
    private var head = 0

    /** Tranche en cours d'agrégation, pas encore poussée dans le tampon. */
    private val pending = DoubleArray(bands) { EMPTY }

    /**
     * Origine du temps, et numéro de la tranche courante comptés **depuis elle**.
     *
     * Et non une échéance qu'on avancerait de `rowSeconds` à chaque bascule :
     * cinq additions de 0,2 ne font pas 1,0 en flottant, si bien qu'un saut d'une
     * seconde ne faisait défiler que quatre lignes sur cinq. Une division ne
     * dérive pas.
     */
    private var originAt = Double.NaN
    private var slice = 0L

    /** Nombre de lignes réellement écrites — sert à ne pas dessiner du vide. */
    var filled = 0
        private set

    fun clear() {
        clearRows()
        pending.fill(EMPTY)
        originAt = Double.NaN
        slice = 0L
    }

    /**
     * Verse un instantané de bandes dans la tranche courante, et bascule de
     * tranche quand elle est pleine.
     *
     * [bandsDb] est le tableau réutilisé du moteur : on ne le garde pas, on en
     * prend le maximum cellule par cellule.
     */
    fun push(bandsDb: DoubleArray, now: Double) {
        // Une horloge qui recule n'est pas une hypothèse théorique : les aperçus
        // de l'app comptent depuis l'ouverture de leur écran, donc repartent de
        // zéro. Sans ce recalage, la tranche en cours attendrait de rattraper
        // son ancienne échéance et le spectrogramme se figerait.
        if (originAt.isNaN() || now < originAt) {
            originAt = now
            slice = 0L
        }
        for (k in 0 until minOf(bands, bandsDb.size)) {
            if (bandsDb[k] > pending[k]) pending[k] = bandsDb[k]
        }

        // `+ TOLERANCE` avant la troncature : 0,6 / 0,2 vaut 2,999 999 999 999 999 6
        // en double, et une tranche pile à l'échéance serait perdue. La tolérance
        // vaut un millionième de tranche — deux dixièmes de microseconde, soit
        // très au-dessus de l'erreur de représentation et très en dessous de
        // tout ce qui se voit.
        val target = ((now - originAt) / rowSeconds + TOLERANCE).toLong()
        // Une image sautée peut valoir plusieurs tranches — GC, veille, service
        // suspendu — et les lignes manquantes doivent défiler quand même, sinon
        // le temps de l'image ne serait plus celui du son. Au-delà d'un écran
        // entier il n'y a plus rien à faire défiler : tout ce qui restait est
        // périmé, et on n'itère pas dix mille fois pour l'effacer ligne à ligne.
        if (target - slice >= rows) {
            clearRows()
            commit()
        } else {
            var k = slice
            while (k < target) {
                commit()
                k++
            }
        }
        slice = target
    }

    private fun clearRows() {
        cells.fill(EMPTY)
        head = 0
        filled = 0
    }

    private fun commit() {
        head = (head + 1) % rows
        val base = head * bands
        for (k in 0 until bands) {
            cells[base + k] = pending[k]
            pending[k] = EMPTY
        }
        if (filled < rows) filled++
    }

    /**
     * Niveau de la bande [band], [age] lignes en arrière — 0 = la plus récente.
     * Hors histoire disponible : [EMPTY], que le renderer traduit par « éteint ».
     */
    fun at(age: Int, band: Int): Double {
        if (age !in 0 until rows || age >= filled || band !in 0 until bands) return EMPTY
        val row = Math.floorMod(head - age, rows)
        return cells[row * bands + band]
    }

    companion object {
        /** Valeur d'une cellule jamais écrite. Sous tout plancher affichable. */
        const val EMPTY = -1000.0

        /** Marge de troncature, en fraction de tranche. Voir [push]. */
        private const val TOLERANCE = 1e-6
    }
}
