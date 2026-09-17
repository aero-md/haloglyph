package red.suns.haloglyph.sono.engine

/**
 * L'enveloppe récente du son : une valeur par tranche de temps, la plus récente
 * en tête.
 *
 * C'est ce qui fait défiler le visualiseur de droite à gauche. Le renderer lit
 * la tranche d'âge 0 dans la colonne la plus à droite et remonte le temps vers
 * la gauche ; à chaque bascule de tranche, tout le dessin glisse d'une colonne.
 *
 * ## Une tranche, et pas une image
 *
 * Le temps est découpé en tranches de [columnSeconds], ce qui met [columns] ×
 * [columnSeconds] à l'écran. Le découpage est indépendant de la cadence de
 * rendu : c'est ce qui fait que l'onde défile à la même vitesse sur la matrice,
 * dans un aperçu et sur un écran à 120 Hz.
 *
 * Quand une tranche reçoit plusieurs instantanés, l'agrégation est un
 * **maximum**, pas une moyenne : une moyenne dilue un transitoire dans le
 * silence qui l'entoure et la forme d'onde d'une batterie devient un aplat ; le
 * maximum garde la frappe, qui est précisément ce qu'on est venu voir. Quand
 * elle n'en reçoit aucun, elle reporte la précédente — voir [commit].
 *
 * ## Alimenté en permanence
 *
 * Le toy le remplit à chaque image, quel que soit le mode affiché. Ça coûte une
 * comparaison et ça évite qu'arriver sur le visualiseur ouvre sur un écran vide
 * qui met une seconde à se peupler — l'histoire est déjà là quand on la demande.
 */
class Waveform(
    val columns: Int,
    private val columnSeconds: Double,
) {
    /** Tampon circulaire, un niveau 0..1 par tranche. */
    private val cells = FloatArray(columns) { EMPTY }

    /** Index de la tranche la plus récente. */
    private var head = 0

    /** Tranche en cours d'agrégation, pas encore poussée dans le tampon. */
    private var pending = EMPTY

    /**
     * Origine du temps, et numéro de la tranche courante compté **depuis elle**.
     *
     * Et non une échéance qu'on avancerait de `columnSeconds` à chaque bascule :
     * cinq additions de 0,2 ne font pas 1,0 en flottant, si bien qu'un saut d'une
     * seconde ne ferait défiler que quatre colonnes sur cinq. Une division ne
     * dérive pas.
     */
    private var originAt = Double.NaN
    private var slice = 0L

    /** Nombre de tranches réellement écrites — sert à ne pas dessiner du vide. */
    var filled = 0
        private set

    fun clear() {
        cells.fill(EMPTY)
        head = 0
        filled = 0
        pending = EMPTY
        originAt = Double.NaN
        slice = 0L
    }

    /**
     * Verse un niveau 0..1 dans la tranche courante, et bascule de tranche quand
     * elle est pleine.
     */
    fun push(level: Float, now: Double) {
        // Une horloge qui recule n'est pas une hypothèse théorique : les aperçus
        // de l'app comptent depuis l'ouverture de leur écran, donc repartent de
        // zéro. Sans ce recalage, la tranche en cours attendrait de rattraper son
        // ancienne échéance et le visualiseur se figerait.
        if (originAt.isNaN() || now < originAt) {
            originAt = now
            slice = 0L
        }

        // `+ TOLERANCE` avant la troncature : 0,6 / 0,2 vaut 2,999 999 999 999 999 6
        // en double, et une tranche pile à l'échéance serait perdue. La tolérance
        // vaut un millionième de tranche — une fraction de microseconde, soit très
        // au-dessus de l'erreur de représentation et très en dessous de tout ce
        // qui se voit.
        val target = ((now - originAt) / columnSeconds + TOLERANCE).toLong()
        // Une image sautée peut valoir plusieurs tranches — GC, veille, service
        // suspendu — et les colonnes manquantes doivent défiler quand même, sinon
        // le temps de l'image ne serait plus celui du son. Au-delà d'un écran
        // entier il n'y a plus rien à faire défiler : tout ce qui restait est
        // périmé, et on n'itère pas dix mille fois pour l'effacer colonne à
        // colonne.
        if (target - slice >= columns) {
            wipe()
            commit()
        } else {
            var k = slice
            while (k < target) {
                commit()
                k++
            }
        }
        slice = target

        // Le niveau est versé **après** la bascule, jamais avant : un échantillon
        // appartient à la tranche que son horodatage désigne, pas à celle qui
        // était ouverte quand il est arrivé. Dans l'autre ordre, une valeur
        // tombant pile sur une échéance se retrouvait dans la tranche qui se
        // ferme, décalée d'un cran vers le passé.
        if (level > pending) pending = level
    }

    private fun wipe() {
        cells.fill(EMPTY)
        head = 0
        filled = 0
    }

    /**
     * Ferme la tranche courante et en ouvre une neuve.
     *
     * Une tranche qui n'a **rien reçu** ne devient pas un trou : elle reprend la
     * valeur de la précédente. À une colonne par image, la gigue de la boucle de
     * rendu fait régulièrement passer deux tranches d'un coup, et sans ce report
     * une colonne sur deux s'éteignait — une onde en peigne au lieu d'une onde.
     * Reporter la dernière valeur connue est ce que fait n'importe quel traceur
     * devant un échantillon manquant.
     */
    private fun commit() {
        val carried = if (pending > EMPTY) pending else cells[head]
        head = (head + 1) % columns
        cells[head] = carried
        pending = EMPTY
        if (filled < columns) filled++
    }

    /**
     * Niveau de la tranche [age] colonnes en arrière — 0 = la plus récente.
     * Hors histoire disponible : [EMPTY], que le renderer traduit par « rien à
     * dessiner ».
     */
    fun at(age: Int): Float {
        if (age !in 0 until columns || age >= filled) return EMPTY
        return cells[Math.floorMod(head - age, columns)]
    }

    companion object {
        /** Valeur d'une tranche jamais écrite. Sous tout niveau affichable. */
        const val EMPTY = -1f

        /** Marge de troncature, en fraction de tranche. Voir [push]. */
        private const val TOLERANCE = 1e-6
    }
}
