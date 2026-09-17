package red.suns.haloglyph.core.look

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import red.suns.haloglyph.core.matrix.MatrixLook
import red.suns.haloglyph.core.matrix.MatrixSpec
import kotlin.math.ceil
import kotlin.math.min

/**
 * La Glyph Matrix, peinte sur un `Canvas` Android.
 *
 * **Un seul rendu pour les deux surfaces émulées** : l'aperçu Compose de l'app
 * et la bitmap du widget d'écran d'accueil passent tous les deux par ici. Elles
 * l'ont longtemps dessinée chacune de son côté, en lisant la même table de
 * couleurs — ce qui garantissait les couleurs et rien d'autre. Le halo, le
 * biseau du verre et la géométrie entière n'auraient pas survécu à ce partage-là.
 *
 * ## Ce qui est peint, et pourquoi
 *
 * Le rendu de [GlyphPortal](https://glyph.suns.red), au détail près — `sharp` par
 * défaut, `soft` si on le demande ([style]) :
 *
 * 1. le **champ**, un disque presque noir. C'est lui qui donne la silhouette,
 *    avant même qu'une LED s'allume ;
 * 2. les **LEDs éteintes**, carrées, en gris plein — du plastique, pas du blanc
 *    atténué ;
 * 3. les **LEDs allumées**, avec leur **halo**. C'est le halo qui porte
 *    l'intensité : l'opacité du carré part d'un plancher haut pour qu'une LED
 *    faible se lise comme allumée, et c'est le débordement lumineux qui dit
 *    ensuite si elle l'est un peu ou beaucoup ;
 * 4. le **biseau du verre**, un liseré à 97 % du rayon dont la brillance varie
 *    avec la direction — quatre lobes, le plus fort en haut à gauche. Relevé sur
 *    une photo du dos d'un Phone (3) par le portail, repris tel quel. Facultatif,
 *    voir [bevel].
 *
 * ## Ce qui rend ça tenable trente fois par seconde
 *
 * Les trois premiers étages sur quatre **ne changent jamais**. Le champ, les 489
 * LEDs éteintes et le biseau sont peints une fois dans une bitmap — le hublot au
 * repos — que chaque image se contente de reposer. Une image ne dessine donc que
 * ce qui est allumé, soit quelques dizaines de rectangles au lieu du millier
 * qu'un rendu naïf redemande à chaque fois.
 *
 * Le halo suit la même logique : il est pré-flouté dans [GLOW_LEVELS] petites
 * bitmaps, une par palier de luminosité, plutôt que recalculé par LED. Un flou
 * gaussien par LED allumée et par image, c'est ce qui mettait les widgets à
 * genoux.
 *
 * ## Une instance par surface
 *
 * Le peintre garde des bitmaps calées sur **une** taille de boîte : le donner à
 * deux surfaces de tailles différentes le ferait les reconstruire en boucle. Il
 * n'est pas non plus protégé contre les accès concurrents — chaque surface tient
 * le sien, et la seule qui ne soit pas sur le fil principal (la rafale d'un
 * widget) en a un pour elle.
 *
 * @param bevel peindre le reflet du verre.
 *
 * Il est là pour les surfaces qui **se font passer** pour l'objet : le widget
 * d'écran d'accueil, posé à même le fond d'écran au milieu des icônes, n'a que
 * ça pour ne pas se lire comme un rond noir. Dans l'app, l'aperçu est une
 * illustration sur une carte et non un appareil — le reflet y simule une lumière
 * qui n'a rien à voir avec l'écran qu'on regarde, et une colonne de vignettes
 * portant toutes le même éclat en haut à gauche se lit pour ce qu'elle est, un
 * décalque.
 *
 * @param style le rendu des LEDs — voir [MatrixLook.LedStyle]. Le peintre ne
 * décide de rien : il lit la table du style, y compris pour savoir s'il y a un
 * halo à préparer. En `soft` il n'y en a pas, et les bitmaps de halo ne sont même
 * pas construites.
 */
class MatrixPainter(
    private val spec: MatrixSpec = MatrixSpec.Phone3,
    private val litArgb: Int = MatrixLook.LIT_ARGB,
    private val bevel: Boolean = true,
    private val style: MatrixLook.LedStyle = MatrixLook.LedStyle.SHARP,
) {
    private var geometry: MatrixGeometry? = null
    private var backdrop: Bitmap? = null

    /** Le halo pré-flouté, un par palier. `null` quand la cellule est trop petite. */
    private var glow: Array<Bitmap?> = emptyArray()
    private var glowPad = 0

    /** Indices des LEDs allumées de l'image courante — réutilisé, jamais réalloué. */
    private val lit = IntArray(spec.cellCount)

    private val blitPaint = Paint()
    private val glowPaint = Paint().apply { isFilterBitmap = false }

    /**
     * Anticrénelé **seulement** si les coins sont arrondis. Un carré franc tombe
     * sur des pixels entiers : l'adoucir ne ferait que rendre la trame floue.
     *
     * `also` et non `apply` : dans un `apply` sur un `Paint`, `style` désigne le
     * `Paint.style` du receveur, pas le nôtre. Le compilateur le dit, mais
     * seulement parce que les deux n'ont aucun membre en commun.
     */
    private val ledPaint = Paint().also { it.isAntiAlias = style.corner > 0f }

    /**
     * Diamètre du hublot pour une boîte de [boxPx] pixels.
     *
     * La cellule est entière, donc le disque tombe presque toujours sous la
     * boîte. Un appelant qui veut une surface **exactement** de la taille du
     * hublot — le widget, dont la bitmap *est* le hublot — alloue ce nombre puis
     * appelle [paintDisc] avec le `boxPx` d'origine.
     */
    fun discSize(boxPx: Int): Int = geometryFor(boxPx).disc

    /**
     * Peint [brightness] (0..255, `spec.cellCount` valeurs) dans une boîte carrée
     * de [boxPx] pixels, hublot centré.
     *
     * Ce qui est hors du disque n'est pas touché : le widget compte dessus pour
     * être rond sur le fond d'écran, et l'aperçu pour se poser sur la carte sans
     * y découper un carré noir.
     */
    fun paint(canvas: Canvas, brightness: IntArray, boxPx: Int) =
        draw(canvas, brightness, boxPx, centered = true)

    /**
     * Le même hublot, mais posé au coin : la surface fait exactement
     * [discSize] pixels et ne porte que lui.
     *
     * C'est ce qu'il faut au widget. Sa bitmap est remise à l'échelle par
     * l'`ImageView`, donc **la part du disque dans la bitmap décide de sa taille
     * apparente** : un hublot occupant 98 % d'une bitmap de repos et 95 % d'une
     * bitmap de rafale rétrécissait à vue d'œil à chaque tap. Ici la proportion
     * vaut 100 % à toutes les définitions, et le widget ne bouge plus.
     */
    fun paintDisc(canvas: Canvas, brightness: IntArray, boxPx: Int) =
        draw(canvas, brightness, boxPx, centered = false)

    private fun draw(canvas: Canvas, brightness: IntArray, boxPx: Int, centered: Boolean) {
        if (boxPx <= 0) return
        val g = geometryFor(boxPx)
        val back = backdrop ?: return

        val origin = if (centered) g.discOrigin.toFloat() else 0f
        canvas.drawBitmap(back, origin, origin, blitPaint)

        var n = 0
        for (i in spec.leds) {
            if (brightness[i] > MatrixLook.MIN_VISIBLE) lit[n++] = i
        }
        if (n == 0) return

        val save = canvas.save()
        canvas.translate(origin, origin)

        // Les halos d'abord, tous, puis les carrés : sinon le halo d'une LED
        // vient poser un voile sur le carré de sa voisine, déjà peint. En soft il
        // n'y a pas de halo — la table du style le dit, et `glow` est vide.
        for (k in 0 until n) {
            val i = lit[k]
            val duty = min(255, brightness[i]) / 255f
            val sprite = glow.getOrNull(glowLevel(duty)) ?: continue
            glowPaint.alpha = (255f * MatrixLook.HALO_ALPHA * duty).toInt().coerceIn(0, 255)
            canvas.drawBitmap(
                sprite,
                (g.leftOf(spec.xOf(i)) - glowPad).toFloat(),
                (g.topOf(spec.yOf(i)) - glowPad).toFloat(),
                glowPaint,
            )
        }

        for (k in 0 until n) {
            val i = lit[k]
            ledPaint.color = withAlpha(litArgb, style.alphaOf(brightness[i]))
            led(canvas, g.leftOf(spec.xOf(i)), g.topOf(spec.yOf(i)), g.led, ledPaint)
        }

        canvas.restoreToCount(save)
    }

    /**
     * Une LED : un carré, aux coins adoucis si le style le demande.
     *
     * L'arrondi est en part du côté, donc il suit la LED d'une taille
     * d'affichage à l'autre. Sous le demi-pixel il n'y a plus de congé à faire —
     * sur une vignette de 44 dp, une LED fait trois pixels — et un `drawRoundRect`
     * y coûterait le passage en anticrénelé pour un coin invisible.
     */
    private fun led(canvas: Canvas, left: Int, top: Int, side: Int, paint: Paint) {
        val x = left.toFloat()
        val y = top.toFloat()
        val s = side.toFloat()
        val r = s * style.corner
        if (r < MIN_CORNER) canvas.drawRect(x, y, x + s, y + s, paint)
        else canvas.drawRoundRect(x, y, x + s, y + s, r, r, paint)
    }

    /** Libère les bitmaps. Appelé quand la surface disparaît. */
    fun release() {
        backdrop?.recycle()
        backdrop = null
        for (b in glow) b?.recycle()
        glow = emptyArray()
        geometry = null
    }

    // ---------- ce qui ne change pas d'une image à l'autre ----------

    private fun geometryFor(boxPx: Int): MatrixGeometry {
        geometry?.let { if (it.boxPx == boxPx) return it }
        release()
        val g = MatrixGeometry(spec, boxPx)
        geometry = g
        backdrop = paintBackdrop(g)
        buildGlow(g)
        return g
    }

    /**
     * Le hublot au repos : champ, LEDs éteintes, biseau. Transparent au-delà du
     * disque — c'est cette transparence qui fait le widget rond.
     */
    private fun paintBackdrop(g: MatrixGeometry): Bitmap {
        val bitmap = Bitmap.createBitmap(g.disc, g.disc, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = g.disc / 2f

        val field = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = style.fieldArgb }
        canvas.drawCircle(c, c, c, field)

        val off = Paint().also {
            it.color = style.offArgb
            it.isAntiAlias = style.corner > 0f
        }
        for (i in spec.leds) {
            led(canvas, g.leftOf(spec.xOf(i)), g.topOf(spec.yOf(i)), g.led, off)
        }

        if (bevel) paintBevel(canvas, g)
        return bitmap
    }

    /**
     * Le biseau du verre.
     *
     * Deux dégradés, chacun disant une moitié du relevé fait sur la photo du dos :
     *
     * - le **balayage** ([BEVEL_SHEEN]) porte la brillance par direction, une
     *   valeur tous les 15°. Ce n'est pas un éclairage directionnel — il a quatre
     *   lobes, un fort en haut à gauche, un secondaire à midi, deux faibles en bas
     *   à droite — ce qu'aucun dégradé linéaire ne rend ;
     * - le **radial** ([BEVEL_STOPS]) place l'anneau et lui donne son épaisseur.
     *   Le profil mesuré monte à partir de 95 % du rayon, culmine à 97,4 % et
     *   retombe à 99,7 % : une gaussienne, pas une bande franche.
     *
     * Le second sert de masque au premier, d'où le calque et le `DST_IN`. Le
     * relevé donne du gris neutre à ±2 près sur les trois canaux : blanc pur à
     * opacité variable suffit.
     */
    private fun paintBevel(canvas: Canvas, g: MatrixGeometry) {
        val c = g.disc / 2f
        val r = g.disc / 2f
        if (r < 3f) return

        val layer = canvas.saveLayer(0f, 0f, g.disc.toFloat(), g.disc.toFloat(), null)

        // CSS place l'origine du dégradé conique à midi ; `SweepGradient` la met à
        // 3 h. Six crans de 15° séparent les deux repères.
        val colors = IntArray(BEVEL_SHEEN.size + 1)
        val stops = FloatArray(colors.size)
        val n = BEVEL_SHEEN.size
        for (k in 0 until n) {
            colors[k] = withAlpha(Color.WHITE, BEVEL_SHEEN[(k + 6) % n])
            stops[k] = k.toFloat() / n
        }
        colors[n] = colors[0]
        stops[n] = 1f

        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(c, c, colors, stops)
        }
        canvas.drawCircle(c, c, r, sheen)

        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                c, c, r,
                IntArray(BEVEL_STOPS.size) { withAlpha(Color.BLACK, BEVEL_STOPS[it].second) },
                FloatArray(BEVEL_STOPS.size) { BEVEL_STOPS[it].first },
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawCircle(c, c, r, mask)

        canvas.restoreToCount(layer)
    }

    /**
     * Les halos pré-floutés, un par palier de luminosité.
     *
     * Le flou est bien un flou gaussien — pas un dégradé radial qui y
     * ressemblerait : une LED est **carrée**, et son débordement garde la forme du
     * carré tant qu'il reste étroit. C'est cette différence qui fait qu'une
     * matrice émulée ressemble à des diodes plutôt qu'à un semis de points.
     *
     * Peints sur une bitmap, donc sur un canvas logiciel, où `BlurMaskFilter` est
     * toujours disponible — ce qui n'est pas garanti sur un canvas accéléré, et
     * c'est une raison de plus de les préparer une fois.
     */
    private fun buildGlow(g: MatrixGeometry) {
        // Le style peut n'en vouloir aucun : pas de bitmaps, pas de boucle de
        // halo au dessin, rien à libérer.
        if (style.halo <= 0f) {
            glow = emptyArray()
            glowPad = 0
            return
        }

        val maxRadius = g.cell * style.halo
        glowPad = ceil(maxRadius * 2.5f).toInt().coerceAtLeast(1)
        val side = g.led + 2 * glowPad

        glow = Array(GLOW_LEVELS) { level ->
            val duty = (level + 1f) / GLOW_LEVELS
            val radius = maxRadius * duty
            // Sous le demi-pixel il n'y a plus de flou à faire, et
            // `BlurMaskFilter` refuse un rayon nul.
            if (radius < MIN_BLUR) return@Array null

            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = litArgb
                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
            Canvas(bitmap).drawRect(
                glowPad.toFloat(),
                glowPad.toFloat(),
                (glowPad + g.led).toFloat(),
                (glowPad + g.led).toFloat(),
                paint,
            )
            bitmap
        }
    }

    private fun glowLevel(duty: Float): Int =
        (duty * GLOW_LEVELS).toInt().coerceIn(0, GLOW_LEVELS - 1)

    private companion object {

        /**
         * Paliers de halo. Six suffisent : le halo est diffus par nature, et un
         * palier de plus ne se distingue de son voisin sur aucune des deux
         * surfaces. Chacun coûte une bitmap de la taille d'une cellule.
         */
        const val GLOW_LEVELS = 6

        /** Rayon de flou en dessous duquel il n'y a plus rien à flouter. */
        const val MIN_BLUR = 0.5f

        /** Rayon de congé en dessous duquel il n'y a plus rien à arrondir. */
        const val MIN_CORNER = 0.5f

        /**
         * Brillance du liseré par direction, tous les 15° depuis midi, sens
         * horaire. Relevé sur la photo du dos d'un Phone (3) — voir `ToyPreview`
         * dans le portail, d'où ces vingt-quatre nombres sont recopiés.
         */
        val BEVEL_SHEEN = floatArrayOf(
            0.47f, 0.18f, 0.02f, 0.00f, 0.01f, 0.06f, // 0° → 75°
            0.13f, 0.19f, 0.22f, 0.12f, 0.00f, 0.00f, // 90° → 165°
            0.20f, 0.00f, 0.00f, 0.01f, 0.25f, 0.41f, // 180° → 255°
            0.37f, 0.61f, 0.78f, 0.52f, 0.22f, 0.21f, // 270° → 345°
        )

        /** Épaisseur du liseré : rayon relatif, opacité. */
        val BEVEL_STOPS = arrayOf(
            0.000f to 0f,
            0.950f to 0f,
            0.964f to 0.64f,
            0.974f to 1f,
            0.985f to 0.64f,
            0.997f to 0f,
            1.000f to 0f,
        )

        fun withAlpha(argb: Int, alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
            return (a shl 24) or (argb and 0x00FFFFFF)
        }
    }
}
