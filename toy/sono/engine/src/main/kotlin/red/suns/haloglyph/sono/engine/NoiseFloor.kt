package red.suns.haloglyph.sono.engine

import kotlin.math.exp

/**
 * Le plancher de bruit, suivi en continu et retranché de l'affichage.
 *
 * ## Le problème qu'il règle
 *
 * Une pièce vide n'est pas silencieuse. Ventilation, circulation, ordinateur,
 * plancher de bruit du micro lui-même : il y a toujours trente à cinquante
 * décibels de fond, ils sont **permanents**, et sur une échelle absolue ils
 * occupent un tiers de la hauteur du disque en permanence. Le spectre et l'onde
 * affichaient donc une grosse tache qui ne voulait rien dire, sur laquelle le son
 * qu'on voulait voir ne se détachait plus.
 *
 * Retrancher un seuil fixe ne marche pas : la scène change d'une pièce à l'autre,
 * et la calibration dBFS → dB SPL du pack est de toute façon une estimation (voir
 * [Calibration.K]) — ce qui veut dire que le seuil « qui va bien » chez soi est
 * faux ailleurs, et faux sur un autre exemplaire du téléphone.
 *
 * ## Comment il le règle
 *
 * En ne montrant plus un niveau **absolu** mais un écart au fond de la scène :
 * ce qui est allumé est ce qui sort de l'ordinaire, pas ce qui est fort. Un
 * enregistreur audio fait exactement ça, et c'est pour ça que sa forme d'onde est
 * plate dans le silence au lieu d'être une bande grise.
 *
 * Le plancher est une **statistique de minimum** :
 *
 * - il **descend vite** ([TAU_FALL]) vers tout niveau plus bas que lui. Une pause
 *   dans la parole, un blanc entre deux mesures, et il retrouve le fond réel en
 *   une demi-seconde ;
 * - il **monte lentement** ([RISE_DB_PER_S]), et jamais au-dessus du niveau
 *   courant. C'est ce qui lui permet de s'adapter à une pièce qui devient
 *   bruyante sans se laisser emporter par un son qui dure.
 *
 * Un bruit vraiment stationnaire — une ventilation — le voit donc converger
 * jusqu'à lui en quelques secondes, après quoi il ne produit plus rien à
 * l'écran. C'est le comportement recherché.
 *
 * ## Un plancher par canal
 *
 * Le bruit de fond n'est pas plat en fréquence : il est écrasé dans le grave.
 * Un plancher commun laisserait les trois premières colonnes du spectre allumées
 * en permanence et les autres mortes. Chaque bande suit donc le sien, ce qui
 * revient à afficher un spectre normalisé sur la scène — la façon dont un
 * analyseur se lit vraiment.
 *
 * ## Ce qui n'est pas une mesure
 *
 * Une référence qui monte de [RISE_DB_PER_S] met des **minutes** à remonter de
 * cent décibels. Lui laisser avaler une valeur aberrante, c'est donc la perdre
 * pour de bon, et un plancher perdu très bas met tout en butée : l'écran devient
 * blanc et le reste.
 *
 * Ça n'est pas une hypothèse. Au démarrage, le banc de bandes rend des zéros
 * exacts tant que sa fenêtre de FFT n'est pas pleine, et la crête large bande en
 * fait autant tant que le premier bloc du micro n'est pas arrivé — deux
 * situations qui durent quelques dizaines de millisecondes et qui suffisaient à
 * bloquer les deux modes à fond pendant plusieurs minutes.
 *
 * Tout ce qui est sous [silenceDb] est donc **ignoré** : le plancher ne bouge
 * pas, et la valeur ne s'affiche pas. Voir [SILENT_DB] pour le seuil.
 *
 * @param silenceDb en dessous, ce n'est pas une pièce calme mais une absence de
 * mesure.
 */
class NoiseFloor(channels: Int, private val silenceDb: Double = SILENT_DB) {

    /** `NaN` tant que le canal n'a rien vu : le premier niveau fait l'amorce. */
    private val floor = DoubleArray(channels) { Double.NaN }

    fun clear() = floor.fill(Double.NaN)

    /** Le plancher du canal [k] — exposé pour les tests et le diagnostic. */
    fun floorOf(k: Int): Double = floor[k]

    /**
     * Avance le plancher du canal [k] vers [db], [dt] secondes après le dernier
     * appel. Une valeur qui n'est pas une mesure ne le déplace pas.
     */
    fun update(k: Int, db: Double, dt: Double) {
        if (!isMeasurement(db)) return
        val current = floor[k]
        if (current.isNaN() || db < current) {
            // Descente : premier ordre, pour ne pas coller à un creux isolé.
            // Amorce comprise — un canal neuf part du niveau qu'il voit.
            floor[k] = if (current.isNaN()) db
            else current + (db - current) * (1.0 - exp(-dt / TAU_FALL))
        } else {
            // Montée : lente, et bornée par le niveau courant. Sans cette borne,
            // un son fort et long finirait par tirer le plancher au-dessus de
            // lui et l'écran s'éteindrait en pleine mesure.
            floor[k] = minOf(db, current + RISE_DB_PER_S * dt)
        }
    }

    /**
     * Position 0..1 de [db] sur l'échelle du canal [k] : zéro au plancher (plus
     * la marge de [GATE_DB]), un à [SPAN_DB] au-dessus.
     *
     * La marge n'est pas de la coquetterie. Le plancher suit le **minimum** d'un
     * signal qui fluctue de quelques décibels autour de son fond ; sans elle, la
     * moitié haute de cette fluctuation allumerait la première rangée en
     * permanence, ce qui est précisément le grésillement qu'on cherche à ne plus
     * voir.
     */
    fun position(k: Int, db: Double): Float {
        val base = floor[k]
        if (base.isNaN() || !isMeasurement(db)) return 0f
        return (((db - base - GATE_DB) / SPAN_DB).coerceIn(0.0, 1.0)).toFloat()
    }

    /** Public parce que [WaveScale] s'appuie dessus pour la même raison. */
    fun isMeasurement(db: Double): Boolean = db.isFinite() && db > silenceDb

    companion object {

        /**
         * Seuil en dessous duquel une valeur ne décrit plus du son.
         *
         * Zéro dB SPL est le seuil d'audition, et le bruit propre d'un MEMS de
         * téléphone se tient vingt-cinq à trente-cinq décibels au-dessus : rien de
         * ce qu'un micro rapporte vraiment ne passe par ici. Ce qui y passe est
         * arithmétique — un tampon de zéros, une FFT pas encore remplie, un flux
         * coupé — et sort au repli de `FLOOR_DBFS` plus la calibration, soit très
         * loin en dessous.
         *
         * Le seuil est donc large exprès. Il sépare « pas de signal » de
         * « signal faible », pas deux niveaux sonores.
         */
        const val SILENT_DB = 0.0
        /** Constante de descente vers un fond plus bas, en secondes. */
        const val TAU_FALL = 0.4

        /**
         * Vitesse de montée, en dB par seconde.
         *
         * Assez lent pour qu'une phrase entière ne déplace pas la référence —
         * huit secondes de parole continue la montent de cinq décibels — et assez
         * rapide pour qu'un changement de pièce soit rattrapé en une dizaine de
         * secondes.
         */
        const val RISE_DB_PER_S = 0.6

        /**
         * Marge au-dessus du plancher avant que quoi que ce soit s'allume.
         *
         * Elle couvre la **fluctuation de l'estimateur**, pas celle de la scène.
         * Le plancher suit un minimum ; une mesure qui oscille de quelques
         * décibels autour de son fond passe donc son temps au-dessus de lui, et
         * sans marge cette oscillation seule allume les premières rangées en
         * permanence. C'est ce qui restait de la tache de bruit une fois l'échelle
         * absolue abandonnée.
         *
         * Neuf décibels, et pas cinq : cinq étaient réglés sur un fond lissé, or
         * les bandes graves ne tiennent qu'un ou deux points de FFT et fluctuent
         * bien plus que ça. Le moyennage de [SonoEngine] ramène cette oscillation
         * autour de deux décibels ; la marge couvre ce qui dépasse.
         */
        const val GATE_DB = 9.0

        /**
         * Dynamique affichée, en décibels au-dessus du plancher.
         *
         * Une voix normale à un mètre sort d'une vingtaine de décibels du fond
         * d'une pièce calme : à 28 dB de plage, elle occupe les deux tiers de la
         * hauteur, ce qui est la bonne réponse — visible sans être en butée. Plus
         * large, parler ne soulevait que deux rangées sur douze et le disque
         * restait mort ; plus étroit, tout sature et l'onde redevient un pavé.
         */
        const val SPAN_DB = 28.0
    }
}
