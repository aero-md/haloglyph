package red.suns.haloglyph.gforce.render

import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.matrix.drawLine
import red.suns.haloglyph.core.matrix.drawText
import red.suns.haloglyph.gforce.engine.GForceEngine
import red.suns.haloglyph.gforce.engine.GForceMode
import red.suns.haloglyph.gforce.engine.GForceScale
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Le cadran, dans ses deux états.
 *
 * ## Le miroir, encore
 *
 * La Glyph Matrix est **au dos du téléphone**. Dans une voiture, le téléphone
 * regarde le conducteur, donc la matrice regarde le pare-brise : ce qu'on lit en
 * roulant est le **hublot d'écran d'accueil**, et la matrice sert à qui se penche
 * par-dessus. Les deux montrent la même scène vue de deux côtés opposés, donc
 * [fromBack] nie l'abscisse — et il la nie pour **tout ce qui a une gauche** : la
 * course de la bille, mais aussi les deux pics latéraux, qui échangent de place.
 *
 * L'axe avant-arrière, lui, ne bouge pas : le haut du téléphone est le haut des
 * deux côtés.
 *
 * ## Rien qu'une bille et quatre traits
 *
 * Pas de cerne, pas de couronne de visée, pas d'anneau. Un accéléromètre n'a pas
 * de cible — il n'y a pas de « bonne » valeur à atteindre, donc rien à viser. Ce
 * qui reste est une **règle** : deux graduations par côté, à un tiers et deux
 * tiers du rayon, soit 0,5 g et 1,0 g. Voir [GForceScale].
 *
 * ## La virgule tient dans une gouttière
 *
 * Les quatre pics s'écrivent en [Fonts.R4], qui n'a pas de virgule et ne peut pas
 * en avoir — deux colonnes de plus par valeur et la ligne médiane déborderait. Elle
 * est donc dessinée ici, **d'un seul pixel**, posé une ligne sous la ligne de base
 * dans la gouttière qui sépare déjà les deux chiffres. Ça ne coûte aucune colonne,
 * et c'est exactement là qu'une virgule se pose.
 */
class GForceRenderer(
    private val spec: MatrixSpec = MatrixSpec.Phone3,
    private val fromBack: Boolean = true,
) {

    /** `-1` au dos du téléphone, `+1` sur un écran. Voir l'en-tête. */
    private val mirror: Float = if (fromBack) -1f else 1f

    /** Le rayon que le **centre** de la bille peut atteindre : celui du disque. */
    private val usableRadius: Float = spec.maxDistance

    fun render(frame: Frame, reading: GForceEngine.Reading, mode: GForceMode) {
        when (mode) {
            GForceMode.VIF -> renderLive(frame, reading)
            GForceMode.PICS -> renderPeaks(frame, reading)
        }
    }

    /**
     * Le repos d'un hublot : la **marque du toy**, et `TAP` en bas.
     *
     * Aucun capteur n'écoute entre deux rafales, donc rien de ce qui est dessiné
     * n'est une mesure — une bille figée serait un cadran qui montre le virage
     * d'hier sans rien pour le dire. Ce qui reste est l'identité : la bille entre
     * ses quatre graduations, sans échelle et sans position, qui dit **quel** toy on
     * s'apprête à réveiller.
     *
     * Le mot est celui de tous les autres hublots du pack — même police, même
     * taille, même ligne. Voir [TAP_Y].
     */
    fun renderIdle(frame: Frame) {
        // C'est le **bloc entier** qui se centre, mot compris : centrer la marque
        // seule poussait le mot au-dessus d'elle, donc tout l'ensemble au-dessus du
        // milieu du disque. L'arrondi va vers le bas.
        val top = (spec.size - Fonts.F4.height - TAP_GAP - BADGE.size + 1) / 2
        frame.drawLine(Fonts.F4, TAP, top, 1f)
        // La marque descend de deux lignes sous le bloc centré — le mot, lui, ne
        // bouge pas. Même règle que chez Plumb : au milieu pile, une image se lit
        // comme la mesure qu'elle n'est pas.
        frame.badge(top + Fonts.F4.height + TAP_GAP + BADGE_DROP)
    }

    private fun Frame.badge(y0: Int) {
        val x0 = spec.centerX - BADGE[0].length / 2
        for (row in BADGE.indices) {
            val line = BADGE[row]
            for (col in line.indices) {
                if (line[col] == '1') set(x0 + col, y0 + row, 1f)
            }
        }
    }

    // ------------------------------------------------------------------- vif

    private fun renderLive(frame: Frame, reading: GForceEngine.Reading) {
        frame.graduations()
        if (!reading.hasFix) return

        // La course est bornée **en norme** et non axe par axe : écrêter chaque
        // composante séparément laisserait la bille atteindre les coins du carré,
        // donc sortir du disque par la diagonale pour une poussée que l'échelle
        // annonce comme pleine.
        var dx = mirror * reading.towardRight / GForceScale.FULL_SCALE
        var dy = reading.towardFront / GForceScale.FULL_SCALE
        val reach = hypot(dx, dy)
        if (reach > 1f) {
            dx /= reach
            dy /= reach
        }

        frame.ball(
            (spec.centerX + dx * usableRadius).roundToInt(),
            (spec.centerY - dy * usableRadius).roundToInt(),
        )
    }

    /**
     * La règle : deux traits par côté, perpendiculaires au rayon.
     *
     * Ils sont posés sur les axes et nulle part ailleurs. Un cercle de graduations
     * complet aurait dit la même chose en occupant tout le disque ; quatre paires
     * de traits suffisent à donner l'échelle, et laissent la bille seule au milieu.
     */
    private fun Frame.graduations() {
        for (k in 1..GForceScale.TICKS) {
            val d = (usableRadius * k / (GForceScale.TICKS + 1)).roundToInt()
            for (t in -TICK_HALF..TICK_HALF) {
                set(spec.centerX + t, spec.centerY - d, AIM)
                set(spec.centerX + t, spec.centerY + d, AIM)
                set(spec.centerX - d, spec.centerY + t, AIM)
                set(spec.centerX + d, spec.centerY + t, AIM)
            }
        }
    }

    // ------------------------------------------------------------------ pics

    /**
     * Les quatre pics, en croix, autour d'un centre **vide**.
     *
     * Chaque nombre est **là où la bille était** quand il a été pris : le freinage
     * en haut, l'accélération en bas, les virages sur les côtés. Le cadran ne
     * change donc pas de langue d'un mode à l'autre — c'est la même carte, figée.
     *
     * Il y a eu un `G` au milieu, et il est parti. Il ne disait rien que le toy ne
     * dise déjà : son nom dans Glyph Interface, son unité dans les réglages, et
     * quatre nombres entre 0 et 2 qui ne peuvent pas être autre chose que des g. Un
     * ornement qui répète l'évidence coûte le centre du cadran — c'est-à-dire
     * l'endroit où l'œil se pose d'abord, et où la croix respire maintenant.
     */
    private fun renderPeaks(frame: Frame, reading: GForceEngine.Reading) {
        // Le miroir échange les deux latéraux : sur la matrice, ce qui a jeté le
        // corps à droite s'affiche à gauche, exactement comme la bille.
        val onLeft = if (fromBack) reading.right else reading.left
        val onRight = if (fromBack) reading.left else reading.right

        frame.value(reading.front, AXIS_X, TOP_Y)
        frame.value(reading.rear, AXIS_X, BOTTOM_Y)
        frame.value(onLeft, LEFT_X, MID_Y)
        frame.value(onRight, RIGHT_X, MID_Y)
    }

    /**
     * Une valeur : deux chiffres, et la virgule d'un pixel sous la gouttière.
     *
     * [x0] est le coin gauche du premier chiffre. La gouttière tombe donc quatre
     * colonnes plus loin — la largeur d'un chiffre — et la ligne de la virgule une
     * ligne sous la dernière du glyphe.
     */
    private fun Frame.value(g: Float, x0: Int, y0: Int) {
        drawText(Fonts.R4, GForceScale.format(g), x0, y0, VALUE)
        set(x0 + Fonts.R4.charWidth('0'), y0 + Fonts.R4.height, VALUE)
    }

    /** La bille : le motif [BALL], posé sur la cellule ([bx], [by]). */
    private fun Frame.ball(bx: Int, by: Int) {
        for (row in BALL.indices) {
            val line = BALL[row]
            for (col in line.indices) {
                if (line[col] == '1') set(bx + col - BALL_HALF, by + row - BALL_HALF, 1f)
            }
        }
    }

    private companion object {
        /**
         * La bille, dessinée une fois pour toutes — le motif de la bulle de Plumb.
         *
         * Même raison là-bas qu'ici : un disque calculé au rayon change de
         * silhouette en se déplaçant, et sur vingt-cinq LEDs cette respiration se
         * lit comme du bruit et non comme du mouvement.
         *
         * Ce n'est plus une bulle pour autant. Une bulle remonte vers le haut d'une
         * fiole, donc à contresens de l'accélération ; celle-ci part **où le corps
         * est jeté**, comme une bille posée sur une plaque. C'est la lecture qu'un
         * conducteur a déjà : on freine, ça part en avant.
         */
        val BALL = arrayOf(
            "01110",
            "11111",
            "11111",
            "11111",
            "01110",
        )

        const val BALL_HALF = 2

        /** Demi-longueur d'une graduation, en cellules. */
        const val TICK_HALF = 2

        /** Les graduations : une règle, pas une cible. */
        const val AIM = 0.24f

        const val VALUE = 0.9f

        /**
         * Ce qu'affiche un hublot au repos, et **où**.
         *
         * Même mot, même police 3×4, et toujours la même place par rapport à la
         * marque : quatre lignes, dont la dernière une ligne au-dessus du haut du
         * badge. Un hublot ne se distingue pas par sa façon d'écrire « tape-moi » —
         * il se distingue par sa marque, juste en dessous.
         */
        const val TAP = "TAP"

        /** Ligne vide entre le mot et la marque. Une suffit, deux les séparent. */
        const val TAP_GAP = 1

        /** De combien la marque descend sous le bloc centré, en lignes. */
        const val BADGE_DROP = 2

        /**
         * La marque de G-Forces : le cadran vif en miniature.
         *
         * Onze cellules de côté, centrées sur le disque : la bille au milieu, et
         * **deux graduations de chaque côté** aux mêmes places relatives que sur le
         * vrai cadran — à trois et à cinq cellules du centre, sur les quatre axes.
         *
         * Sans échelle et sans position : c'est une identité, pas une mesure. Un
         * hublot qui montrerait une bille figée afficherait le virage d'hier sans
         * rien pour le dire.
         */
        val BADGE = arrayOf(
            "00001110000",
            "00000000000",
            "00001110000",
            "00000000000",
            "10100100101",
            "10101110101",
            "10100100101",
            "00000000000",
            "00001110000",
            "00000000000",
            "00001110000",
        )

        /**
         * La croix, au pixel.
         *
         * Une valeur fait neuf colonnes — deux chiffres de quatre, une gouttière.
         * Les deux latérales se posent donc à une colonne de chaque bord, ce qui
         * laisse cinq colonnes de vide au milieu depuis que le `G` est parti : assez
         * pour que les deux nombres ne se lisent pas comme un seul de quatre
         * chiffres.
         *
         * En hauteur, chaque valeur occupe six lignes — cinq de chiffres, une de
         * virgule — et les trois blocs laissent une ligne vide entre eux.
         */
        const val LEFT_X = 1
        const val RIGHT_X = 15
        const val AXIS_X = 8

        const val TOP_Y = 2
        const val MID_Y = 10
        const val BOTTOM_Y = 17
    }
}
