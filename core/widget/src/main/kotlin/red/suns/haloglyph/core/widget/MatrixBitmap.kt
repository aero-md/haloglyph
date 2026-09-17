package red.suns.haloglyph.core.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import red.suns.haloglyph.core.look.MatrixPainter
import red.suns.haloglyph.core.matrix.MatrixSpec

/**
 * Rendu d'une frame en bitmap : la matrice, dessinée pour un widget.
 *
 * Le dessin est celui de [MatrixPainter], donc **exactement** celui de l'aperçu
 * de l'app — même hublot, mêmes LEDs carrées, même halo, même biseau de verre.
 * C'est ce qui rend vérifiable la promesse du produit : mêmes moteurs, mêmes
 * renderers, même dessin. Ce fichier ne décide plus que du support.
 *
 * ### Un widget rond, et donc transparent
 *
 * Le widget n'a **aucun fond** : il *est* le hublot. Hors du disque la bitmap
 * est transparente, et le fond d'écran passe au travers — d'où `ARGB_8888`, qui
 * n'était pas nécessaire tant que les coins étaient peints en noir.
 *
 * ### Le budget, qui n'est pas négociable
 *
 * Un widget communique par `RemoteViews`, transmises par binder. Au-delà
 * d'environ **500 Ko** par mise à jour, `TransactionTooLargeException` — et ce
 * n'est pas le widget qui tombe, c'est le **launcher**. Le passage en ARGB_8888
 * double le poids du pixel : [MAX_SIDE_PX] est descendu de 480 à 288 quand le
 * widget est devenu rond, ce qui ne coûtait rien puisqu'il fait deux cellules et
 * n'a jamais eu de quoi afficher 480 pixels.
 *
 * C'est aussi, et surtout, ce qui décide de la fluidité : chaque image d'une
 * animation traverse le binder **en entier**. D'où le second palier, de 288 à
 * 256 — voir [MAX_SIDE_PX].
 */
object MatrixBitmap {

    /**
     * Côté maximum autorisé. 256 donne un hublot de 255 px, soit ≈ 260 Ko en
     * ARGB_8888 : sous le budget, avec la marge de ce qui entoure la bitmap dans
     * la transaction.
     *
     * ### Pourquoi 256 et pas 288
     *
     * La rafale rendait plus petit que le repos, pour alléger ses transactions.
     * C'était une fausse économie : la cellule est **entière**, donc changer de
     * définition change le pas de la trame, la part du halo, la largeur du cerne
     * — bref les proportions du hublot, qui se voyaient sauter à chaque tap.
     * Aucun réglage de la définition de rafale ne règle ça ; seule une définition
     * unique le règle, parce que la géométrie devient alors la même par
     * construction.
     *
     * Il fallait donc que **la définition de repos soit tenable en rafale**.
     * 288 pesait 318 Ko par image, seize fois par seconde ; 256 en pèse 260, et
     * la différence ne se voit pas sur une bitmap que l'`ImageView` agrandit de
     * toute façon.
     */
    const val MAX_SIDE_PX = 256

    /** Côté minimum : en dessous, une LED ferait moins d'un pixel. */
    const val MIN_SIDE_PX = 50

    /** Taille effective retenue pour une taille demandée. */
    fun clampSide(requestedPx: Int): Int = requestedPx.coerceIn(MIN_SIDE_PX, MAX_SIDE_PX)

    /** Poids en octets d'une bitmap ARGB_8888 de ce côté — pour vérifier le budget. */
    fun byteSize(sidePx: Int): Int = sidePx * sidePx * 4

    /**
     * @param brightness `spec.cellCount` valeurs 0..255.
     * @param painter le peintre du fournisseur — il garde en cache le hublot au
     * repos et les halos, qui ne dépendent que de la taille. En créer un par
     * image reviendrait à refaire ce cache trente fois par seconde.
     * @param reuse bitmap à réutiliser si sa taille correspond. Un widget se
     * redessine souvent, et un quart de mégaoctet jeté par image en rafale se
     * remarque — c'est même ce qui se remarquait le plus.
     */
    fun render(
        brightness: IntArray,
        spec: MatrixSpec,
        requestedSidePx: Int,
        painter: MatrixPainter,
        reuse: Bitmap? = null,
    ): Bitmap {
        require(brightness.size == spec.cellCount) {
            "frame de ${brightness.size} valeurs, ${spec.cellCount} attendues"
        }
        val side = clampSide(requestedSidePx)
        // La bitmap fait exactement le diamètre du hublot, et non la taille
        // demandée : elle *est* le hublot, sans marge transparente autour. Comme
        // l'`ImageView` la remet à l'échelle, une marge variable d'une définition
        // à l'autre faisait changer le widget de taille — voir
        // `MatrixPainter.paintDisc`.
        val disc = painter.discSize(side)
        val bitmap = if (reuse != null && !reuse.isRecycled &&
            reuse.width == disc && reuse.height == disc && reuse.isMutable
        ) {
            reuse
        } else {
            Bitmap.createBitmap(disc, disc, Bitmap.Config.ARGB_8888)
        }

        val canvas = Canvas(bitmap)
        // Une bitmap réutilisée porte encore l'image précédente : sans effacement
        // franc, le halo de la frame d'avant resterait sous celui de la nouvelle.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        painter.paintDisc(canvas, brightness, side)
        return bitmap
    }
}
