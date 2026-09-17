package red.suns.haloglyph.sono.engine

import kotlin.math.pow

/**
 * L'échelle de la forme d'onde : **amplitude linéaire**, rapportée au plus fort
 * du moment.
 *
 * ## Pourquoi ce n'est pas une échelle en décibels
 *
 * C'est la différence de fond avec un enregistreur, et elle explique à elle seule
 * pourquoi sa forme d'onde est plate là où la nôtre était grise.
 *
 * Un enregistreur trace une **amplitude**, pas un niveau. Or l'amplitude est
 * linéaire : un bruit de pièce trente décibels sous la parole n'y occupe pas les
 * trois dixièmes de la hauteur, il en occupe un **trente-deuxième** — un pixel,
 * puis plus rien. La même scène sur une échelle en décibels remonte ce bruit à
 * mi-hauteur, parce que c'est précisément ce que fait un logarithme : il rend
 * visible ce qui est négligeable.
 *
 * Vingt décibels sous le plafond valent donc quelques pour cent de la hauteur
 * ici, trente décibels une fraction de rangée. Le silence est plat parce qu'il
 * est réellement minuscule, sans qu'on ait à le décréter.
 *
 * S'y ajoute une mise en forme ([SHAPE]) qui creuse le haut de l'échelle, pour
 * qu'un pic se détache de la parole qui l'entoure au lieu de partager le bord du
 * disque avec elle.
 *
 * ## Rapportée à quoi
 *
 * À un **plafond** qui suit le plus fort du moment : attaque immédiate, chute
 * lente ([FALL_DB_PER_S]). C'est le réglage de gain automatique de n'importe quel
 * enregistreur — ce qu'on écoute occupe la hauteur disponible, quelle que soit la
 * distance au micro.
 *
 * Deux garde-fous, et il en faut deux :
 *
 * - le plafond ne descend jamais à moins de [MIN_RANGE_DB] au-dessus du fond de
 *   scène. Sans lui, une pièce vide finirait par normaliser son propre bruit et
 *   le remonterait à pleine hauteur — l'erreur classique d'un gain automatique
 *   qu'on laisse sans butée ;
 * - sous [NoiseFloor.GATE_DB] au-dessus du fond, rien du tout. Ce qui ne sort pas
 *   du bruit de la pièce n'est pas du signal.
 */
class WaveScale {

    private val floor = NoiseFloor(1)

    /** Le plus fort niveau récent, en dB. `NaN` tant que rien n'a été mesuré. */
    private var ceiling = Double.NaN

    fun clear() {
        floor.clear()
        ceiling = Double.NaN
    }

    /** Le fond de scène et le plafond courants — exposés pour les tests. */
    fun floorDb(): Double = floor.floorOf(0)
    fun ceilingDb(): Double = ceiling

    /**
     * Ouvre une image : le plafond redescend de [FALL_DB_PER_S] × [dt].
     *
     * À appeler **avant** [update], qui le relèvera si l'image courante est plus
     * forte. Un son qui dure ne fait donc pas pomper l'affichage : il se
     * re-désigne plafond à chaque image.
     */
    fun decay(dt: Double) {
        if (!ceiling.isNaN()) ceiling -= FALL_DB_PER_S * dt
    }

    /** Verse la mesure de l'image courante. */
    fun update(db: Double, dt: Double) {
        floor.update(0, db, dt)
        if (!floor.isMeasurement(db)) return
        if (ceiling.isNaN() || db > ceiling) ceiling = db
    }

    /** Hauteur 0..1 à donner à [db] dans la colonne courante. */
    fun amplitude(db: Double): Float {
        val base = floor.floorOf(0)
        if (base.isNaN() || !floor.isMeasurement(db)) return 0f
        if (db <= base + NoiseFloor.GATE_DB) return 0f

        val top = maxOf(if (ceiling.isNaN()) base else ceiling, base + MIN_RANGE_DB)
        if (db >= top) return 1f
        // dB → rapport d'amplitude. Vingt, pas dix : une amplitude, pas une
        // puissance. Puis la mise en forme, qui creuse ce qui suit le sommet.
        return 10.0.pow((db - top) / 20.0).pow(SHAPE).toFloat()
    }

    companion object {
        /**
         * Exposant de mise en forme, appliqué au rapport d'amplitude.
         *
         * Le plafond se pose **sur la plus forte tranche récente**, donc quelque
         * chose est en permanence à pleine hauteur, et tout ce qui est à moins de
         * six décibels en dessous occupe encore la moitié de l'écran. Sur de la
         * parole, dont les crêtes successives tiennent dans une poignée de
         * décibels, ça fait une onde qui touche le haut du disque presque tout le
         * temps : le sommet ne se détache plus de ce qui l'entoure, faute de place
         * au-dessus.
         *
         * L'exposant creuse cet écart. À 1 on affiche l'amplitude brute, à 2 la
         * puissance ; entre les deux, six décibels sous le plafond valent un tiers
         * de la hauteur au lieu de la moitié, et un vrai pic a de nouveau où
         * aller. Au-delà de 2 l'onde se réduit à quelques aiguilles sur une ligne
         * plate — le défaut inverse.
         */
        const val SHAPE = 1.6

        /**
         * Chute du plafond, en dB par seconde.
         *
         * Assez lente pour qu'une phrase garde une référence stable d'un mot à
         * l'autre, assez rapide pour qu'après une porte claquée l'échelle
         * redescende en trois ou quatre secondes au lieu de laisser tout le reste
         * écrasé.
         */
        const val FALL_DB_PER_S = 10.0

        /**
         * Dynamique minimale entre le fond de scène et le plafond, en décibels.
         *
         * C'est la butée du gain automatique, et son réglage est un compromis
         * franc : trop bas, une pièce vide remonte son propre bruit ; trop haut,
         * un son modéré reste écrasé en bas de l'écran même quand il est le plus
         * fort de la scène. À trente, un bruit de fond qui oscille de dix
         * décibels au-dessus de son minimum plafonne à un dixième de la hauteur —
         * une rangée sur douze — pendant qu'une voix à vingt-huit décibels du fond
         * remplit l'écran.
         */
        const val MIN_RANGE_DB = 30.0
    }
}
