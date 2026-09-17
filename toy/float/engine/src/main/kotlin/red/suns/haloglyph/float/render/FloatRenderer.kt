package red.suns.haloglyph.float.render

import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.matrix.drawCentered
import red.suns.haloglyph.core.matrix.drawLine
import red.suns.haloglyph.core.matrix.drawText
import red.suns.haloglyph.float.engine.Heading
import red.suns.haloglyph.float.engine.FloatEngine
import red.suns.haloglyph.float.engine.FloatMode
import red.suns.haloglyph.float.engine.FloatRange
import red.suns.haloglyph.float.engine.FloatScale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les deux instruments, sur le disque.
 *
 * ## Le miroir, et ce n'est pas un détail
 *
 * La Glyph Matrix est **au dos du téléphone** : on la regarde par derrière. Le
 * hublot d'écran d'accueil, lui, est sur l'écran, donc de face. Les deux
 * surfaces montrent la même scène **vue de deux côtés opposés**, et une image
 * identique serait donc fausse sur l'une des deux.
 *
 * Concrètement : lever le bord droit de l'écran fait monter ce bord-là, donc y
 * envoie la bulle. Sur l'écran ce bord est à droite ; au dos du téléphone il est
 * à gauche. Pour une boussole, c'est le sens de rotation de la rose entière qui
 * s'inverse.
 *
 * Tout tient donc dans [fromBack], et il ne s'applique qu'à **l'abscisse** :
 * retourner un plan autour de son axe vertical, c'est nier son x. C'est aussi
 * pour cette raison que le moteur rend des directions dans le repère de
 * l'appareil plutôt que des positions à l'écran — il ne sait pas d'où on le
 * regarde, et il n'a pas à le savoir.
 *
 * ## La boussole, elle, ne se retourne pas
 *
 * Et c'est le seul dessin du pack dans ce cas. Le niveau mesure une direction
 * **dans le repère de l'appareil** : le flanc droit de l'écran reste le flanc
 * droit de l'écran quelle que soit la pose, donc le miroir s'applique. La rose,
 * elle, montre une direction **du monde**, et pour regarder la matrice il faut
 * poser le téléphone écran en bas. Ce retournement-là est un second miroir, et
 * les deux s'annulent.
 *
 * Au concret, cap au nord : l'est est au flanc droit de l'écran, donc à gauche
 * de qui regarde le dos du téléphone — mais écran en bas, ce flanc droit pointe
 * l'ouest, et l'est retombe à droite. La rose est donc **identique** sur les deux
 * surfaces. Appliquer le miroir ici échangeait `e` et `w`, ce qui est exactement
 * ce qu'on ne pardonne pas à une boussole.
 *
 * ## Aucun anticrénelage, nulle part
 *
 * Une LED est allumée ou éteinte. Les demi-teintes ont existé ici — bords de
 * bulle, anneau adouci — et elles coûtaient plus qu'elles ne rapportaient : sur
 * vingt-cinq LEDs, un bord dégradé ne lisse rien, il **élargit** la forme d'une
 * cellule et brouille le seul repère dont l'œil dispose, qui est le bord franc.
 * Les seules nuances qui restent sont des **niveaux**, pas des bords : la visée
 * en retrait, le trait de pleine lumière. Chacune code quelque chose.
 *
 * ## La bulle est un **dessin**, pas un calcul
 *
 * Elle a été un disque tracé au rayon, arrondi aux cellules les plus proches, et
 * elle changeait donc de silhouette en se déplaçant : cinq cellules de large ici,
 * quatre là, un coin en plus selon la position sous-pixel. Sur vingt-cinq LEDs,
 * cette respiration se lit comme du bruit et non comme du mouvement.
 *
 * C'est maintenant un **motif fixe** ([BUBBLE]), posé sur la cellule la plus
 * proche. Elle a toujours exactement la même forme, où qu'elle soit — et elle a
 * le droit de **sortir**. À pleine échelle son centre atteint le cerne, donc la
 * moitié du motif tombe hors du masque et disparaît. Une bulle écrêtée au bord
 * mentait sur la fin de course : elle s'arrêtait entière, à deux cellules du
 * bord, comme si l'échelle s'y terminait. Ici elle sort du champ, ce que fait une
 * vraie bulle dans une fiole trop courte.
 *
 * ## Ce qui s'allume quand c'est droit
 *
 * Deux cercles ont coexisté : le cerne du disque, allumé en permanence, et une
 * couronne de visée qui passait de gris à pleine lumière. C'était deux fois la
 * même information, et aucune des deux n'était franche.
 *
 * Maintenant la **visée** (couronne à plat, tunnel à la verticale) est en retrait
 * et ne bouge jamais : c'est la cible, elle n'a pas d'état. Et le **cerne** est
 * tout ou rien — éteint tant qu'on cherche, plein d'un coup quand la bulle entre
 * dans la tolérance. Il n'y a rien à interpréter : le disque s'allume, ou pas.
 *
 * Ça retire aussi le négatif sous la bulle, qui n'a plus lieu d'être : une visée
 * en retrait passe **sous** la bulle toute seule, la composition par maximum s'en
 * charge, et le cerne est ailleurs.
 */
class FloatRenderer(
    private val spec: MatrixSpec = MatrixSpec.Phone3,
    private val fromBack: Boolean = true,
) {

    /** `-1` au dos du téléphone, `+1` sur un écran. Voir l'en-tête. */
    private val mirror: Float = if (fromBack) -1f else 1f

    /** Le cerne du disque : le signal de mise à niveau, et rien d'autre. */
    private val rim: IntArray = spec.edgeRing

    /**
     * Le rayon que le **centre** de la bulle peut atteindre.
     *
     * C'est le rayon du disque, sans retenue : la bulle a le droit d'être coupée
     * en deux par le bord. Voir l'en-tête — retrancher son étendue ici faisait
     * finir l'échelle avant le bord.
     */
    private val usableRadius: Float = spec.maxDistance

    /** La couronne de visée. Son rayon est la tolérance, voir [FloatScale]. */
    private val targetRadius: Float = usableRadius * FloatScale.TARGET_FRACTION

    /**
     * Où se posent les deux repères du tunnel, en cellules depuis le centre.
     *
     * **Une cellule de plus que la demi-bulle**, et pas un pixel de plus : les deux
     * repères encadrent la bulle centrée en la laissant tout juste passer. C'est la
     * même chose que dit le cerne, mais dessinée là où l'œil regarde — on voit la
     * bulle se caler entre les deux marques, et le disque s'allume au même instant.
     *
     * Ils ont été bien plus larges, au rayon de la couronne plus la demi-bulle,
     * quand « de niveau » valait un demi-degré. À cette écartée-là ils ne
     * disaient plus rien : la bulle flottait entre eux sur toute la plage utile.
     */
    private val gate: Int = BUBBLE_HALF + 1

    /**
     * Une image de l'instrument.
     *
     * @param elapsedSeconds base de temps de la surface, pour le seul clignotement
     * qui reste : celui qui dit que le magnétomètre n'est pas calibré.
     */
    fun render(
        frame: Frame,
        reading: FloatEngine.Reading,
        mode: FloatMode,
        range: FloatRange,
        elapsedSeconds: Double,
    ) {
        when (mode) {
            FloatMode.NIVEAU -> renderLevel(frame, reading, range)
            FloatMode.BOUSSOLE -> renderCompass(frame, reading, elapsedSeconds)
        }
    }

    /**
     * Ce que montre un hublot **qui ne mesure pas**, et comment on le dit.
     *
     * Deux étages : `TAP` en haut, la **marque de l'instrument** en dessous. Rien
     * de ce qui est dessiné n'est une mesure — ce serait la seule façon de mentir,
     * puisque aucun capteur n'écoute entre deux rafales.
     *
     * ## La marque suit le mode, maintenant
     *
     * Les deux instruments ont partagé la même image, au motif qu'un hublot au
     * repos dit **quel toy** on va réveiller et pas ce qu'il affichera. C'était
     * vrai tant qu'il n'y avait qu'une question à poser ; il y en a deux. Un
     * hublot posé sur Float est tantôt un niveau, tantôt une boussole, le tap
     * change d'instrument, et on ne savait pas lequel on allait réveiller avant de
     * l'avoir réveillé — donc trop tard.
     *
     * La rose pour la boussole, la fiole pour le niveau : neuf cellules chacune,
     * même place, même taille. Ce qui change est le dessin, pas la mise en page.
     */
    fun renderIdle(frame: Frame, mode: FloatMode) {
        val badge = if (mode == FloatMode.BOUSSOLE) ROSE else VIAL
        // Le **bloc entier** se centre, mot compris — centrer la marque seule
        // poussait le mot au-dessus d'elle, donc tout l'ensemble au-dessus du
        // milieu du disque. L'arrondi va vers le bas.
        val top = (spec.size - Fonts.F4.height - TAP_GAP - badge.size + 1) / 2
        frame.drawLine(Fonts.F4, TAP, top - TAP_RISE, 1f)
        frame.badge(badge, top + Fonts.F4.height + TAP_GAP + BADGE_DROP)
    }

    /**
     * La vignette d'identité, au milieu du disque.
     *
     * Un hublot au repos affichait `TAP` et rien d'autre, et le pack en compte
     * maintenant assez pour que ce soit un problème : trois toys s'y ressemblaient
     * au pixel près, et on ne savait pas lequel on allait réveiller. La marque du
     * toy prend donc la place de la mesure — c'est la même grammaire que Sono, qui
     * montrait déjà son onde entre ses deux mots.
     *
     * Le mot, lui, est **le même partout** : même police, même taille, même ligne.
     * Ce qui distingue les hublots doit être ce qu'ils font, pas la façon dont ils
     * écrivent « tape-moi ».
     */
    private fun Frame.badge(sprite: Array<String>, y0: Int) {
        val x0 = spec.centerX - sprite[0].length / 2
        for (row in sprite.indices) {
            val line = sprite[row]
            for (col in line.indices) {
                if (line[col] == '1') set(x0 + col, y0 + row, 1f)
            }
        }
    }

    // ---------------------------------------------------------------- niveau

    private fun renderLevel(frame: Frame, reading: FloatEngine.Reading, range: FloatRange) {
        // Rien reçu du capteur : la visée est là, la bulle n'y est pas. Même parti
        // pris que le repos d'un hublot — ne pas dessiner une mesure qu'on n'a pas.
        if (!reading.hasTilt) {
            frame.ring(AIM)
            return
        }

        val r = FloatScale.radiusOf(reading.tiltDegrees, range.degrees, usableRadius)
        if (reading.vertical) frame.tunnel(reading.alongX) else frame.ring(AIM)

        // La même formule sert aux deux régimes. À la verticale, le moteur annule
        // l'une des deux directions — c'est ce que veut dire « un seul axe » — et
        // la bulle tombe donc d'elle-même sur la ligne du tunnel.
        frame.bubble(
            (spec.centerX + mirror * reading.dirX * r).roundToInt(),
            (spec.centerY - reading.dirY * r).roundToInt(),
            1f,
        )

        // Le seul état de l'instrument, et il est franc : le disque s'allume.
        if (reading.level) frame.paint(rim, LOCK)
    }

    /**
     * Le tunnel du régime vertical : deux bords, deux repères, un seul axe.
     *
     * La bulle n'y a qu'une dimension de liberté — voir `FloatEngine`, qui ne
     * mesure qu'un aplomb quand le téléphone est debout. Les bords ne servent pas
     * qu'à décorer : ils disent **que la course est droite**, ce qu'une bulle seule
     * sur un disque noir ne dirait pas, et ils se tournent avec le téléphone.
     */
    private fun Frame.tunnel(alongX: Boolean) {
        for (k in 0 until spec.size) {
            if (alongX) {
                set(k, spec.centerY - TUNNEL_HALF, WALL)
                set(k, spec.centerY + TUNNEL_HALF, WALL)
            } else {
                set(spec.centerX - TUNNEL_HALF, k, WALL)
                set(spec.centerX + TUNNEL_HALF, k, WALL)
            }
        }
        for (t in -TUNNEL_HALF..TUNNEL_HALF) {
            if (alongX) {
                set(spec.centerX - gate, spec.centerY + t, AIM)
                set(spec.centerX + gate, spec.centerY + t, AIM)
            } else {
                set(spec.centerX + t, spec.centerY - gate, AIM)
                set(spec.centerX + t, spec.centerY + gate, AIM)
            }
        }
    }

    // -------------------------------------------------------------- boussole

    /**
     * La boussole : **quatre lettres qui tournent, une flèche qui ne tourne pas**.
     *
     * ## Ce qui est parti, et ce que ça rend
     *
     * **Le cap chiffré.** Trois chiffres au milieu du disque donnaient une précision
     * que l'instrument n'a pas — le nord magnétique non corrigé de la déclinaison se
     * trompe déjà de plusieurs degrés — et ils occupaient le centre pour une valeur
     * qu'on ne lit jamais au degré près. On ne consulte pas une boussole pour savoir
     * qu'on est à 214°, mais pour savoir où est le sud.
     *
     * **Les intercardinaux.** Huit repères sur un disque de vingt-cinq LEDs, c'est
     * une couronne de points où l'œil ne distingue plus lequel est lequel.
     *
     * **Et les repères abstraits eux-mêmes.** Croix pleine, losange creux, point :
     * il fallait apprendre la convention avant de pouvoir lire l'instrument. Les
     * lettres, elles, n'ont rien à apprendre — un `n` au bord du disque dit où est
     * le nord à quelqu'un qui n'a jamais ouvert le toy.
     *
     * ## Les lettres *sont* la rose
     *
     * Quatre bas de casse en 4×5 arrondie, posés au cerne à [LETTER_RADIUS] du
     * centre, qui orbitent avec le cap et restent droits. Le disque libéré par les
     * chiffres est ce qui leur donne la place d'être lisibles.
     *
     * ## L'aiguille est la seule chose fixe
     *
     * Elle traverse le centre et pointe midi : c'est le téléphone, pas le monde.
     * Tout ce qui tourne tourne autour d'elle.
     *
     * Sa tête est **ouverte** — deux traits qui s'écartent, et rien entre eux. Elle
     * a été pleine, et une pointe pleine à cet endroit-là fait une tache de neuf
     * cellules au milieu du disque : l'œil s'y accroche au lieu de chercher le `n`,
     * et elle empâte la zone que les lettres traversent en tournant. Ouverte, elle
     * dit la même direction en trois cellules.
     */
    private fun renderCompass(frame: Frame, reading: FloatEngine.Reading, elapsedSeconds: Double) {
        if (!reading.hasHeading) {
            // Pas de rose : on ne sait pas où est le nord, et en dessiner un serait
            // la seule façon de mentir. L'aiguille, elle, ne prétend rien — elle dit
            // juste où pointe le téléphone.
            frame.needle(NO_FIX)
            return
        }

        // Magnétomètre non calibré : l'instrument bat au lieu d'afficher fermement
        // un cap dont il n'est pas sûr. Le geste qui corrige — le huit dans l'air
        // — ne se dessine pas sur vingt-cinq LEDs, alors on se contente de ne pas
        // mentir.
        val trust = if (reading.headingTrusted || blink(elapsedSeconds, TRUST_BLINK)) 1f else DIMMED

        frame.rose(reading.heading, trust)
        frame.needle(trust)
    }

    /**
     * Les quatre lettres, à leur place sur le cerne.
     *
     * Chacune est **posée droite** sur la cellule la plus proche de son rayon : une
     * lettre inclinée n'existe pas sur une grille de vingt-cinq cellules, et une
     * lettre qui se redessinerait à chaque degré respirerait comme du bruit. Ce qui
     * tourne est leur **position**, pas leur dessin — la même règle que les repères
     * qu'elles remplacent.
     */
    private fun Frame.rose(heading: Float, trust: Float) {
        for (point in Points.ALL) {
            // **Pas de miroir ici**, et c'est voulu : voir l'en-tête. Le
            // retournement du téléphone qui rend la matrice visible est déjà le
            // second miroir, et les deux s'annulent.
            val angle = Heading.delta(heading, point.bearing) * DEG
            val ax = sin(angle)
            val ay = -cos(angle)

            // La lettre **rentre** plutôt que de se faire amputer. Au rayon nominal
            // elle touche le cerne, ce qui est le but ; mais le disque n'a pas la
            // même largeur selon l'angle, et une boîte de cinq sur cinq y perd un
            // coin dans les diagonales. On la remonte alors d'un dixième de cellule
            // à la fois jusqu'à ce qu'elle tienne entière.
            //
            // Amputer aurait été pire qu'un léger retrait : un `s` sans son pied ne
            // se lit plus, et la rose se mettrait à respirer en tournant.
            var radius = LETTER_RADIUS
            while (radius > LETTER_RADIUS - LETTER_GIVE && !fits(point, ax, ay, radius)) {
                radius -= LETTER_STEP
            }
            stamp(point, ax, ay, radius, trust)
        }
    }

    /** La lettre tient-elle entière dans le masque, à ce rayon ? */
    private fun fits(point: Point, ax: Float, ay: Float, radius: Float): Boolean {
        val cx = (spec.centerX + ax * radius).roundToInt()
        val cy = (spec.centerY + ay * radius).roundToInt()
        for (row in point.glyph.indices) {
            val line = point.glyph[row]
            for (col in line.indices) {
                if (line[col] == '1' && !spec.isLed(cx + point.dx + col, cy + point.dy + row)) {
                    return false
                }
            }
        }
        return true
    }

    private fun Frame.stamp(point: Point, ax: Float, ay: Float, radius: Float, brightness: Float) {
        val cx = (spec.centerX + ax * radius).roundToInt()
        val cy = (spec.centerY + ay * radius).roundToInt()
        for (row in point.glyph.indices) {
            val line = point.glyph[row]
            for (col in line.indices) {
                if (line[col] == '1') set(cx + point.dx + col, cy + point.dy + row, brightness)
            }
        }
    }

    /**
     * L'aiguille : une pointe **évidée** sur un fût, centrée sur le disque.
     *
     * Elle est la seule chose de l'instrument qui ne tourne pas, et elle occupe
     * l'axe autour duquel tout le reste tourne. Sa tête est ouverte — voir
     * [renderCompass] : pleine, elle faisait une tache de neuf cellules au milieu du
     * disque, là où les lettres passent.
     */
    private fun Frame.needle(brightness: Float) {
        val x0 = spec.centerX - NEEDLE[0].length / 2
        val y0 = spec.centerY - NEEDLE.size / 2
        for (row in NEEDLE.indices) {
            val line = NEEDLE[row]
            for (col in line.indices) {
                if (line[col] == '1') set(x0 + col, y0 + row, brightness)
            }
        }
    }

    // ------------------------------------------------------------ primitives

    private fun Frame.paint(cells: IntArray, brightness: Float) {
        for (i in cells) setAt(i, brightness)
    }

    /** La bulle : le motif [BUBBLE], posé sur la cellule ([bx], [by]). */
    private fun Frame.bubble(bx: Int, by: Int, brightness: Float) {
        for (row in BUBBLE.indices) {
            val line = BUBBLE[row]
            for (col in line.indices) {
                if (line[col] == '1') set(bx + col - BUBBLE_HALF, by + row - BUBBLE_HALF, brightness)
            }
        }
    }

    /** La couronne de visée : une cellule d'épaisseur, bords francs. */
    private fun Frame.ring(brightness: Float) {
        for (i in spec.leds) {
            if (abs(spec.distance[i] - targetRadius) <= RING_HALF) setAt(i, brightness)
        }
    }

    private companion object {
        /** Degrés vers radians, pour les tracés angulaires. */
        const val DEG = (PI / 180).toFloat()

        /**
         * La bulle, dessinée une fois pour toutes.
         *
         * Cinq cellules de côté, les quatre coins retirés — assez ronde pour être
         * une bulle, assez franche pour ne pas scintiller. Voir l'en-tête : le
         * disque calculé qu'elle remplace changeait de silhouette en se déplaçant.
         */
        val BUBBLE = arrayOf(
            "01110",
            "11111",
            "11111",
            "11111",
            "01110",
        )

        /** Du centre du motif à son bord, en cellules. */
        const val BUBBLE_HALF = 2

        /** Demi-épaisseur de la couronne de visée : une cellule, pas deux. */
        const val RING_HALF = 0.55f

        /** Demi-largeur du tunnel vertical : une cellule de jeu autour de la bulle. */
        const val TUNNEL_HALF = 3

        /** La visée — couronne ou tunnel. Elle ne change jamais de valeur. */
        const val AIM = 0.24f

        /** Les bords du tunnel : le rail, pas la cible. */
        const val WALL = 0.10f

        /** Le cerne, quand c'est droit. Tout ou rien. */
        const val LOCK = 1f

        /**
         * Où se posent les quatre lettres, en cellules depuis le centre.
         *
         * Assez loin pour qu'elles soient au cerne et pas dans le champ de
         * l'aiguille, assez près pour qu'une boîte de cinq sur cinq tienne dans le
         * disque quel que soit l'angle — c'est le `w`, large de cinq, qui fixe la
         * borne.
         */
        const val LETTER_RADIUS = 10.4f

        /** De combien une lettre a le droit de rentrer pour tenir entière, et par quel pas. */
        const val LETTER_GIVE = 3f
        const val LETTER_STEP = 0.1f

        /**
         * L'aiguille : pointe évidée, puis fût jusqu'au bas du disque.
         *
         * Neuf lignes, centrées sur le milieu du disque, donc la pointe monte à
         * quatre lignes au-dessus du centre et le fût descend d'autant. Les deux
         * traits de la tête s'écartent d'une cellule par ligne et s'arrêtent là :
         * rien ne les referme, et c'est ce qui la distingue d'un triangle.
         */
        val NEEDLE = arrayOf(
            "00100",
            "01010",
            "10001",
            "00100",
            "00100",
            "00100",
            "00100",
            "00100",
            "00100",
        )

        /** L'aiguille seule, quand il n'y a pas de cap : elle ne prétend rien. */
        const val NO_FIX = 0.45f

        /**
         * Ce qu'affiche un hublot au repos, et **où**.
         *
         * Même mot, même police 3×4, et toujours la même place par rapport à la
         * marque : quatre lignes, dont la dernière une ligne au-dessus du haut du
         * badge. Un hublot ne se distingue pas par sa façon d'écrire « tape-moi ».
         */
        const val TAP = "TAP"

        /** Ligne vide entre le mot et la marque. Une suffit, deux les séparent. */
        const val TAP_GAP = 1

        /**
         * De combien la marque descend sous le bloc centré, en lignes.
         *
         * Le mot reste où le centrage le pose ; la marque, elle, descend de deux
         * lignes. C'est le disque qui le demande : un bloc parfaitement centré
         * laisse la marque au milieu, là où elle se lit comme la mesure qu'elle
         * n'est pas. Deux lignes plus bas, le mot et l'image se séparent
         * franchement et le hublot se lit d'un coup d'œil.
         */
        const val BADGE_DROP = 2

        /**
         * De combien le mot monte au-dessus du bloc centré, en lignes.
         *
         * Une, et une seule : le disque se rétrécit vite en haut, et un mot de
         * onze colonnes n'y tient plus très loin. Ce qu'on achète, c'est de l'air
         * entre le mot et la marque — celle-ci étant déjà descendue de deux
         * lignes, les deux étages se lisent séparément au lieu de se toucher.
         */
        const val TAP_RISE = 1

        /**
         * La marque de la boussole : quatre flèches creuses qui se rejoignent.
         *
         * Neuf cellules de côté — la moitié de ce qu'occupaient les deux
         * instruments côte à côte, et le double de lisibilité.
         */
        val ROSE = arrayOf(
            "000010000",
            "000111000",
            "000101000",
            "011010110",
            "110111011",
            "011010110",
            "000101000",
            "000111000",
            "000010000",
        )

        /**
         * La marque du niveau : la fiole vue de dessus, bulle dedans.
         *
         * Même boîte que [ROSE], à la cellule près : passer d'un instrument à
         * l'autre ne déplace rien, ça ne change que le dessin.
         */
        val VIAL = arrayOf(
            "001111100",
            "010000010",
            "100111001",
            "101111101",
            "101111101",
            "101111101",
            "100111001",
            "010000010",
            "001111100",
        )

        /** Ce à quoi retombe une rose dont le magnétomètre n'est pas calibré. */
        const val DIMMED = 0.3f

        /** Période du seul clignotement qui reste, en secondes. */
        const val TRUST_BLINK = 1.4
    }
}

/**
 * Un battement **carré** : allumé une demi-période, éteint l'autre.
 *
 * Un fondu sinusoïdal traversait toute la plage de luminosité, donc toutes les
 * demi-teintes qu'on vient de bannir du reste du dessin. Un clignotement franc
 * dit la même chose et ne dit qu'elle.
 */
private fun blink(elapsedSeconds: Double, periodSeconds: Double): Boolean =
    (elapsedSeconds % periodSeconds) < periodSeconds / 2

/**
 * Les quatre directions de la rose, et leur dessin.
 *
 * Il n'y a plus de motif abstrait à choisir : la lettre **est** le repère. C'est ce
 * qui a retiré la dernière convention à apprendre — croix pleine pour le nord,
 * losange creux pour les autres — au profit de quatre caractères que tout le monde
 * lit déjà.
 *
 * ## Quatre dessins, et pas une police
 *
 * Les glyphes ne sont pas tirés de `Fonts` et n'ont pas la même boîte : `n` fait
 * trois colonnes sur quatre lignes, `e` quatre sur cinq, `s` trois sur cinq, `w`
 * cinq sur quatre. C'est délibéré — chaque lettre est dessinée pour **sa** place au
 * cerne, où le disque n'offre pas la même largeur en haut qu'au flanc, et une
 * boîte unique aurait obligé la plus large à rentrer là où la plus étroite tient.
 *
 * [dx] et [dy] placent la boîte par rapport au point d'ancrage qui tourne, parce
 * qu'un centrage arithmétique ne rend pas la même chose sur une largeur paire et
 * sur une impaire.
 */
private class Point(
    val bearing: Float,
    val glyph: Array<String>,
    val dx: Int,
    val dy: Int,
)

private object Points {

    val ALL: List<Point> = listOf(
        Point(0f, arrayOf("110", "101", "101", "101"), -1, -2),
        Point(90f, arrayOf("0110", "1001", "1111", "1000", "0110"), -1, -2),
        Point(180f, arrayOf("011", "100", "111", "001", "110"), -1, -2),
        Point(270f, arrayOf("10001", "10101", "10101", "01010"), -2, -1),
    )
}
