package red.suns.haloglyph.dice.render

import red.suns.haloglyph.core.matrix.Font
import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.dice.engine.Die
import red.suns.haloglyph.dice.engine.Mat3
import red.suns.haloglyph.dice.engine.Vec3
import red.suns.haloglyph.dice.engine.View
import red.suns.haloglyph.dice.engine.cross
import red.suns.haloglyph.dice.engine.dot
import red.suns.haloglyph.dice.engine.plus
import red.suns.haloglyph.dice.engine.revealAt
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le rendu d'un dé, quel que soit son solide.
 *
 * **Un convexe, lancé de rayons, une cellule à la fois.** Pour chaque LED du
 * hublot on tire un rayon parallèle dans la scène et on le rogne contre les
 * plans des faces : ce qui revient, c'est la face par laquelle il est entré. Six
 * cent vingt-cinq rayons contre vingt plans au plus, à trente images par
 * seconde, c'est quelques milliers de multiplications — la matrice est si petite
 * que la méthode la plus directe est aussi la moins chère.
 *
 * Le rognage contre des demi-espaces est la même chose que le test de tranches
 * d'un cube, écrit sans supposer que les faces vont par paires parallèles. C'est
 * ce qui fait tourner le cube et le trapézoèdre dans exactement le même code, et
 * ce qui a fait entrer le dodécaèdre et l'icosaèdre sans y toucher une ligne :
 * un solide de plus est un maillage de plus dans `Solids.kt`, et rien ne change
 * ici.
 *
 * Ce que ça achète par ailleurs : la grille n'apparaît nulle part dans le tracé.
 * Le même code rend le dé sur les 25 × 25 du (3) et sur les 13 × 13 du (4a) Pro,
 * à la seule condition que le hublot soit rond.
 *
 * Trois niveaux : **ce qui porte l'information est plein**. La marque est à
 * fond, c'est elle qu'on lit. L'arête est à moitié moins, c'est la carcasse. Le
 * corps des faces est un lavis de quelques pour-cent, juste de quoi que le
 * solide soit un solide et non un grillage — et il varie avec l'éclairage, ce
 * qui fait que la face du dessus, celle qui porte le résultat, est toujours la
 * plus claire.
 */
class DiceRenderer(private val spec: MatrixSpec = MatrixSpec.Phone3) {

    /** Luminosités de travail, grille entière, hublot compris. */
    private val g = FloatArray(spec.cellCount)

    /** Face touchée par cellule, ou −1 pour le vide. */
    private val hit = IntArray(spec.cellCount)

    /**
     * Une image du dé [die] dans la vue [view].
     *
     * La frame est supposée effacée : on n'y écrit que ce qui est allumé, et le
     * masque du disque est celui de [Frame].
     */
    fun render(frame: Frame, die: Die, view: View) {
        val size = spec.size
        g.fill(0f)
        hit.fill(-1)

        /* Base de la caméra dans le monde. `ex` est tiré du seul azimut, ce qui
           la garde orthogonale à `ez` même à l'aplomb — un produit vectoriel
           avec la verticale dégénérerait pile dans la pose de repos. */
        val ce = cos(view.cam.elev)
        val se = sin(view.cam.elev)
        val cy = cos(view.cam.yaw)
        val sy = sin(view.cam.yaw)
        val ez = Vec3(sy * ce, se, cy * ce)
        val ex = Vec3(cy, 0.0, -sy)
        val ey = ez cross ex

        /* Tout est ramené une fois par image dans le repère du dé : le rayon
           d'une cellule y est une combinaison linéaire de trois vecteurs
           constants. */
        val m = view.q.toMatrix()
        val a = m.toLocal(ex)
        val b = m.toLocal(ey)
        val c = m.toLocal(ez)
        val o = m.toLocal(Vec3(
            CAM_D * ez.x - view.pos.x,
            CAM_D * ez.y - view.pos.y,
            CAM_D * ez.z - view.pos.z,
        ))
        val light = m.toLocal(LIGHT)

        /** Unités de dé par cellule. */
        val k = view.cam.half / spec.radius

        /* Par face et une fois par image : la fonction du plan le long du rayon
           est affine en (wx, wy), et sa pente le long du rayon est constante
           puisque la projection est parallèle. La boucle par LED n'a donc qu'une
           division. */
        val faceCount = die.faces.size
        val q0 = DoubleArray(faceCount)
        val qx = DoubleArray(faceCount)
        val qy = DoubleArray(faceCount)
        val qd = DoubleArray(faceCount)
        val fill = FloatArray(faceCount)
        for (f in 0 until faceCount) {
            val n = die.faces[f].n
            q0[f] = (n dot o) - die.faces[f].d
            qx[f] = n dot a
            qy[f] = n dot b
            // Incidence : le rayon va vers `−c`, donc une face vue de face a `n·c > 0`.
            qd[f] = -(n dot c)
            val lam = (n dot light).coerceAtLeast(0.0)
            fill[f] = (FILL_MIN + FILL_SPAN * lam).toFloat()
        }

        /**
         * La face qui portera la marque : celle du **dessus**, la même que relit
         * le résultat et que vise le recentrage.
         *
         * Un dé physique montre les marques de toutes ses faces visibles, et il
         * a deux cents fois cette résolution pour le faire. Ici un d10 posé
         * montre cinq cerfs-volants à la fois : cinq chiffres pleins dans un
         * hublot de vingt-cinq LEDs, dont un seul est le résultat. **Un dé, un
         * nombre.**
         *
         * Le critère a d'abord été « la face la plus de face », ce qui
         * paraissait revenir au même puisqu'on ne montre une marque qu'à
         * l'aplomb. Ça n'y revient pas au *début* du rapprochement : la caméra
         * est encore à mi-élévation, et sur un icosaèdre la face la plus de face
         * y est encore une face latérale. Mesuré sur 4 000 jets, le maximum
         * changeait de face jusqu'à `z = 0,64` — donc à 93 % de luminosité, donc
         * bien visible : le 20 s'affichait une ou deux images sur le flanc, à
         * cinq cellules du centre, puis sautait à sa place. Avec la face du
         * dessus, plus une seule bascule sur 12 000 jets, et pour cause : le dé
         * est posé, elle ne peut plus changer.
         */
        val best = die.topIndex(m)

        /* La grille entière, hublot compris : le masque est appliqué à
           l'écriture finale. Une passe restreinte au disque ferait croire à la
           détection d'arêtes que le dé s'arrête au bord du hublot, et
           dessinerait un arc vif sur la découpe. */
        for (y in 0 until size) {
            val wy = -(y - spec.centerY) * k
            for (x in 0 until size) {
                val wx = (x - spec.centerX) * k

                /* Rognage du rayon par les demi-espaces : on garde le plus
                   tardif des instants d'entrée et le plus précoce des instants
                   de sortie. La face d'entrée est celle qui a fixé le premier. */
                var lo = Double.NEGATIVE_INFINITY
                var hi = Double.POSITIVE_INFINITY
                var entry = -1
                var outside = false

                for (f in 0 until faceCount) {
                    val num = q0[f] + wx * qx[f] + wy * qy[f]
                    val den = qd[f]
                    if (den > -EPS && den < EPS) {
                        // Rayon parallèle à la face : soit dedans pour toujours,
                        // soit dehors.
                        if (num > 0) {
                            outside = true
                            break
                        }
                        continue
                    }
                    val t = -num / den
                    if (den < 0) {
                        if (t > lo) {
                            lo = t
                            entry = f
                        }
                    } else if (t < hi) hi = t
                }

                if (outside || entry < 0 || lo > hi) continue

                val i = y * size + x
                hit[i] = entry
                g[i] = fill[entry]
            }
        }

        /* Les arêtes ne sont pas tracées, elles sont **trouvées** : une cellule
           dont une voisine touche une autre face, ou le vide, est une arête. Ça
           donne une carcasse d'exactement une cellule quelle que soit la grille
           et quel que soit l'angle — un trait d'épaisseur constante en unités de
           dé aurait fondu sur les faces vues de biais, pile là où le solide a
           besoin de son contour. */
        for (y in 0 until size) {
            for (x in 0 until size) {
                val i = y * size + x
                val f = hit[i]
                if (f < 0) continue
                val edge = (x > 0 && hit[i - 1] != f) ||
                    (x < size - 1 && hit[i + 1] != f) ||
                    (y > 0 && hit[i - size] != f) ||
                    (y < size - 1 && hit[i + size] != f)
                if (edge && g[i] < EDGE) g[i] = EDGE
            }
        }

        /* Les marques, en dernier et par-dessus les arêtes : ce sont elles qu'on
           lit. Le point d'ancrage est projeté à l'écran, puis la marque est
           tamponnée sur la grille — d'où sa taille constante.

           Le tampon n'écrit que sur les cellules de **sa** face, ce qui est
           l'occlusion exacte et gratuite : la passe de rayons a laissé
           l'identité de la face dans chaque cellule. Une marque qui déborde
           d'une arête est donc rognée par l'arête, au lieu de baver sur la face
           voisine ou dans le vide. */
        val lit = revealAt(view.cam.z).toFloat()
        if (lit > 0f) {
            val face = die.faces[best]
            if (face.pips.isNotEmpty()) {
                for (p in face.pips) {
                    project(p, m, view.pos, ex, ey, k)
                    block(px, py, PIP_PX, PIP_PX, best, lit)
                }
            } else {
                /* Place disponible pour un nombre, en cellules : le rayon
                   inscrit de la face, mesuré dans le cadrage du gros plan et non
                   dans celui de l'image courante — c'est ce qui empêche le corps
                   de changer en cours de révélation. Voir [STEPS]. */
                val room = face.inr * spec.radius / die.close
                val step = STEPS.firstOrNull {
                    it.font.height * it.scale / 2.0 <= room &&
                        it.font.textWidth(face.glyph) * it.scale / 2.0 <= room
                }
                if (step != null) {
                    project(face.c, m, view.pos, ex, ey, k)
                    text(step.font, step.scale, face.glyph, px, py, best, lit)
                }
            }
        }

        /* Sortie : le masque du disque et le tressaut d'impact, en une passe. */
        val jx = view.jolt?.dx ?: 0
        val jy = view.jolt?.dy ?: 0
        if (jx == 0 && jy == 0) {
            for (i in spec.leds) frame.setAt(i, g[i])
            return
        }
        for (i in spec.leds) {
            val sx = i % size - jx
            val sy = i / size - jy
            if (sx < 0 || sy < 0 || sx >= size || sy >= size) continue
            frame.setAt(i, g[sy * size + sx])
        }
    }

    /* Sortie de [project], en coordonnées de cellule. Deux champs plutôt qu'une
       paire allouée : la projection tourne jusqu'à six fois par image. */
    private var px = 0.0
    private var py = 0.0

    /** Un point du repère du dé, en coordonnées de cellule. */
    private fun project(
        p: Vec3,
        m: Mat3,
        pos: Vec3,
        ex: Vec3,
        ey: Vec3,
        k: Double,
    ) {
        val w = m.toWorld(p) + pos
        // Projection parallèle, puis le calage de la boucle par cellule, à l'envers.
        px = spec.centerX + (w dot ex) / k
        py = spec.centerY - (w dot ey) / k
    }

    /** Un rectangle plein centré, rogné à la face [f]. */
    private fun block(sx: Double, sy: Double, w: Int, h: Int, f: Int, b: Float) {
        val x0 = (sx - w / 2.0).roundToInt()
        val y0 = (sy - h / 2.0).roundToInt()
        for (dy in 0 until h) {
            val yy = y0 + dy
            if (yy < 0 || yy >= spec.size) continue
            for (dx in 0 until w) {
                val xx = x0 + dx
                if (xx < 0 || xx >= spec.size) continue
                val j = yy * spec.size + xx
                if (hit[j] == f && g[j] < b) g[j] = b
            }
        }
    }

    /**
     * Un nombre centré, chaque pixel de police dilaté en carré de [scale].
     *
     * Un pixel de blanc entre deux chiffres, dilaté comme le reste — c'est ce
     * que mesure `textWidth`, et le centrage en dépend. Le dix est le seul
     * nombre à deux chiffres du lot, et il ne tient qu'à l'échelle 1 : cette
     * cellule d'écart est donc bien une cellule, et le `10` fait onze de large.
     */
    private fun text(font: Font, scale: Int, s: String, sx: Double, sy: Double, f: Int, b: Float) {
        val h = font.height * scale
        val y0 = (sy - h / 2.0).roundToInt()
        var x0 = (sx - font.textWidth(s) * scale / 2.0).roundToInt()

        for (ch in s) {
            val rows = font.glyphs[ch] ?: continue
            for (r in 0 until font.height) {
                val row = rows[r]
                for (col in row.indices) {
                    if (row[col] != '1') continue
                    for (dy in 0 until scale) {
                        val yy = y0 + r * scale + dy
                        if (yy < 0 || yy >= spec.size) continue
                        for (dx in 0 until scale) {
                            val xx = x0 + col * scale + dx
                            if (xx < 0 || xx >= spec.size) continue
                            val j = yy * spec.size + xx
                            if (hit[j] == f && g[j] < b) g[j] = b
                        }
                    }
                }
            }
            x0 += (rows[0].length + 1) * scale
        }
    }

    private class Step(val font: Font, val scale: Int)

    private companion object {

        /**
         * Carcasse et lavis du corps — les deux niveaux qui **ne portent pas**
         * l'information, et qui doivent donc s'effacer devant celui qui la
         * porte.
         *
         * Ces valeurs ont été baissées deux fois, et la première ne s'est pas
         * vue : la consigne envoyée à une LED est un rapport cyclique, mais
         * l'œil la lit en gamma. Enlever un tiers du rapport cyclique n'enlève
         * qu'un dixième de luminosité apparente — 0,55 et 0,38 se perçoivent à
         * 76 % et 64 %, ce qui explique très bien qu'on ne voie aucune
         * différence.
         *
         * Les niveaux sont donc posés dans l'espace perceptuel, puis ramenés en
         * rapport cyclique par `v^2,2` :
         *
         *     marque   1,00  →  perçue 100 %
         *     arête    0,18  →  perçue  46 %
         *     lavis    0,03 – 0,08  →  perçu 20 – 32 %
         *
         * L'arête à mi-hauteur perçue laisse enfin le rapport de deux qu'on
         * croyait avoir depuis le début, et la silhouette ne perd rien : c'est
         * un contour d'une cellule, il n'a jamais eu besoin d'être vif pour se
         * voir. Le lavis, lui, ne descend pas plus bas : sous huit sur 255, la
         * LED s'éteint pour de bon et le solide redevient un grillage.
         */
        const val EDGE = 0.18f

        /** Lavis du corps, de la face à contre-jour à la face éclairée. */
        const val FILL_MIN = 0.03
        const val FILL_SPAN = 0.05

        /**
         * Côté d'un pip, **en cellules d'écran**, et c'est un parti pris contre
         * la perspective.
         *
         * Un pip n'est pas peint sur la face : son centre est projeté, puis un
         * carré de 3 × 3 est tamponné sur la grille. Il garde donc la même
         * taille et reste aligné sur les LEDs quelle que soit l'inclinaison de
         * la face, et pendant que le rapprochement fait grandir le solide sous
         * lui.
         *
         * C'est faux, et c'est exactement ce qu'on veut. La version juste — un
         * disque mesuré dans le dé, projeté avec le reste — donnait un pip d'une
         * à quatre cellules selon là où il tombait entre les centres : trois
         * pips côte à côte n'avaient pas la même taille, et un 6 se lisait comme
         * un motif irrégulier plutôt que comme six points. Sur vingt-cinq LEDs
         * de côté, la quantification fait plus de dégâts que l'entorse à la
         * géométrie. Les LEDs ne sont pas un rendu, elles sont une trame : un
         * point y a une taille, pas une distance.
         *
         * Impair, nécessairement : un carré centré sur une cellule a un côté
         * impair.
         */
        const val PIP_PX = 3

        /**
         * Les nombres suivent la même règle que les pips : tamponnés à l'écran,
         * donc droits et à l'échelle de la trame, jamais déformés par
         * l'inclinaison de leur face. Un 8 penché sur un icosaèdre qui culbute
         * serait un 8 illisible.
         *
         * Trois tailles et pas un continuum, parce qu'une trame n'a pas de
         * demi-cellule : un chiffre ne peut grandir que par doublement de son
         * pixel. La plus grande qui tient dans le cercle inscrit de la face est
         * retenue.
         *
         * Le test porte sur la **hauteur et la largeur**, et il fallait les deux
         * dès que le dix s'est écrit à deux chiffres : `10` dilaté deux fois
         * fait vingt-deux cellules de large, soit plus que le hublot entier.
         * C'est ce qui choisit tout seul la police non dilatée pour le dix, et
         * laisse les chiffres seuls en grand.
         */
        val STEPS = listOf(Step(Fonts.F5, 2), Step(Fonts.F5, 1), Step(Fonts.F3, 1))

        /**
         * La lumière, dans le monde et non dans le repère du dé : elle vient
         * d'en haut, un peu de la gauche, un peu de l'avant. C'est ce qui fait
         * que le lavis des faces change *pendant* la culbute au lieu d'être
         * collé au solide, et que la face du dessus se détache une fois le dé
         * posé.
         */
        val LIGHT = Vec3(-0.35, 0.92, 0.2)

        /** Distance du plan de tirage. Projection parallèle : seul son signe compte. */
        const val CAM_D = 6.0

        /** Un plan rasant : au-delà, le rayon est parallèle à la face. */
        const val EPS = 1e-9
    }
}
