package red.suns.haloglyph.core.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import red.suns.haloglyph.core.matrix.MatrixLook
import red.suns.haloglyph.core.matrix.MatrixSpec
import kotlin.math.min

/**
 * Rendu d'une frame en bitmap : la matrice, dessinée pour un widget.
 *
 * Même géométrie et mêmes couleurs que l'aperçu Compose des réglages — les deux
 * lisent [MatrixLook]. C'est ce qui rend vérifiable la promesse du produit :
 * mêmes moteurs, mêmes renderers, même dessin. Des LEDs **carrées** au tiers de
 * gouttière, sur un champ circulaire ; le masque laisse les coins vides, et la
 * silhouette de la matrice apparaît d'elle-même.
 *
 * ### Le budget, qui n'est pas négociable
 *
 * Un widget communique par `RemoteViews`, transmises par binder. Au-delà d'environ
 * **500 Ko** par mise à jour, `TransactionTooLargeException` — et ce n'est pas le
 * widget qui tombe, c'est le **launcher**. D'où :
 *
 * - `RGB_565` (2 octets/pixel) et non `ARGB_8888` (4) : la matrice est
 *   monochrome, la moitié du budget suffit. Corollaire : pas de canal alpha, donc
 *   les opacités de [MatrixLook] sont mélangées à la main sur le champ ;
 * - la taille réelle du widget, jamais une taille fixe généreuse ;
 * - [MAX_SIDE_PX] comme garde-fou dur, quoi que demande l'appelant.
 */
object MatrixBitmap {

    /**
     * Côté maximum autorisé. 480×480 en RGB_565 ≈ 460 Ko : sous le budget, avec
     * la marge de ce qui entoure la bitmap dans la transaction.
     */
    const val MAX_SIDE_PX = 480

    /** Côté minimum : en dessous, une LED ferait moins d'un pixel. */
    const val MIN_SIDE_PX = 50

    /** Taille effective retenue pour une taille demandée. */
    fun clampSide(requestedPx: Int): Int = requestedPx.coerceIn(MIN_SIDE_PX, MAX_SIDE_PX)

    /** Poids en octets d'une bitmap RGB_565 de ce côté — pour vérifier le budget. */
    fun byteSize(sidePx: Int): Int = sidePx * sidePx * 2

    /**
     * @param brightness `spec.cellCount` valeurs 0..255.
     * @param reuse bitmap à réutiliser si sa taille correspond — un widget se
     * redessine souvent, et une allocation de 460 Ko par frame en rafale se
     * remarque.
     */
    fun render(
        brightness: IntArray,
        spec: MatrixSpec,
        requestedSidePx: Int,
        litArgb: Int = MatrixLook.LIT_ARGB,
        fieldArgb: Int = MatrixLook.FIELD_ARGB,
        reuse: Bitmap? = null,
    ): Bitmap {
        require(brightness.size == spec.cellCount) {
            "frame de ${brightness.size} valeurs, ${spec.cellCount} attendues"
        }
        val side = clampSide(requestedSidePx)
        val bitmap = if (reuse != null && !reuse.isRecycled &&
            reuse.width == side && reuse.height == side && reuse.isMutable
        ) {
            reuse
        } else {
            Bitmap.createBitmap(side, side, Bitmap.Config.RGB_565)
        }

        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Hors du disque, du noir franc : le launcher pose la bitmap sur le
        // fond d'écran, et un carré gris trahirait la silhouette.
        canvas.drawColor(Color.BLACK)
        paint.color = fieldArgb
        canvas.drawCircle(side / 2f, side / 2f, side / 2f, paint)

        val pitch = side.toFloat() / spec.size
        val led = MatrixLook.ledSize(pitch)
        val inset = MatrixLook.inset(pitch)

        // Pas d'alpha en RGB_565 : les deux niveaux sont mélangés au champ.
        val offColor = blend(fieldArgb, litArgb, MatrixLook.OFF_ALPHA)

        for (index in spec.leds) {
            val left = spec.xOf(index) * pitch + inset
            val top = spec.yOf(index) * pitch + inset
            val value = min(255, brightness[index])

            // `perceived` et non `value / 255f` : la frame porte des rapports
            // cycliques, l'écran compose en linéaire. Voir MatrixLook.perceived.
            paint.color = if (value > MatrixLook.MIN_VISIBLE) {
                blend(fieldArgb, litArgb, MatrixLook.perceived(value))
            } else {
                offColor
            }
            canvas.drawRect(left, top, left + led, top + led, paint)
        }
        return bitmap
    }

    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * r).toInt().coerceIn(0, 255)
        return Color.rgb(
            mix(Color.red(from), Color.red(to)),
            mix(Color.green(from), Color.green(to)),
            mix(Color.blue(from), Color.blue(to)),
        )
    }
}
