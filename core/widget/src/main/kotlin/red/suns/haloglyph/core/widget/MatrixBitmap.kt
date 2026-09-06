package red.suns.haloglyph.core.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import red.suns.haloglyph.core.matrix.MatrixSpec
import kotlin.math.max
import kotlin.math.min

/**
 * Rendu d'une frame en bitmap : la matrice, dessinée.
 *
 * C'est ce qui rend la promesse du produit vérifiable — mêmes moteurs, mêmes
 * renderers, même masque de 489 LEDs. Le détail qui fait toute la différence
 * entre « un widget carré de points » et « la Glyph Matrix » est le masque :
 * les cellules sans LED ne sont pas dessinées, du tout. Les coins restent vides,
 * et la silhouette circulaire apparaît d'elle-même.
 *
 * ### Le budget, qui n'est pas négociable
 *
 * Un widget communique par `RemoteViews`, transmises par binder. Au-delà d'environ
 * **500 Ko** par mise à jour, `TransactionTooLargeException` — et ce n'est pas le
 * widget qui tombe, c'est le **launcher**. D'où :
 *
 * - `RGB_565` (2 octets/pixel) et non `ARGB_8888` (4) : la matrice est
 *   monochrome, la moitié du budget suffit ;
 * - la taille réelle du widget, jamais une taille fixe généreuse ;
 * - [MAX_SIDE_PX] comme garde-fou dur, quoi que demande l'appelant.
 */
object MatrixBitmap {

    /**
     * Côté maximum autorisé. 480×480 en RGB_565 ≈ 460 Ko : sous le budget, avec
     * la marge de ce qui entoure la bitmap dans la transaction.
     */
    const val MAX_SIDE_PX = 480

    /** Côté minimum : en dessous, un point de la grille ferait moins d'un pixel. */
    const val MIN_SIDE_PX = 50

    data class Style(
        /** Diamètre d'un point rapporté au pas de la grille. */
        val dotRatio: Float = 0.80f,
        val background: Int = Color.BLACK,
        /** Couleur d'une LED à pleine luminosité. */
        val lit: Int = Color.WHITE,
        /**
         * Luminosité résiduelle d'une LED éteinte, 0..1. Une matrice réelle
         * éteinte laisse deviner ses points ; à 0 le widget a l'air cassé.
         */
        val floor: Float = 0.10f,
    )

    /** Taille effective retenue pour une taille demandée. */
    fun clampSide(requestedPx: Int): Int =
        requestedPx.coerceIn(MIN_SIDE_PX, MAX_SIDE_PX)

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
        style: Style = Style(),
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
        canvas.drawColor(style.background)

        val pitch = side.toFloat() / spec.size
        val radius = pitch * style.dotRatio / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val floorLevel = (style.floor.coerceIn(0f, 1f) * 255).toInt()

        for (index in spec.leds) {
            val level = max(floorLevel, min(255, brightness[index]))
            paint.color = blend(style.background, style.lit, level / 255f)
            val cx = (spec.xOf(index) + 0.5f) * pitch
            val cy = (spec.yOf(index) + 0.5f) * pitch
            canvas.drawCircle(cx, cy, radius, paint)
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
