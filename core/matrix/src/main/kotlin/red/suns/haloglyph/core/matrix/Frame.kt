package red.suns.haloglyph.core.matrix

import kotlin.math.min

/**
 * Tampon de rendu d'une frame.
 *
 * Les luminosités sont en **flottant 0..1** pendant le dessin — les renderers
 * composent des couches (fond, contenu, anneau, flash) et travaillent en
 * fractions — puis converties en **0..255** au moment de sortir vers un
 * [FrameSink]. La conversion vers l'échelle du matériel (0..4095) est l'affaire
 * du `GlyphSink`, pas du renderer.
 *
 * Deux invariants tenus ici, une fois pour toutes les surfaces :
 *
 * - **le masque** : écrire hors du disque est un no-op silencieux, jamais une
 *   exception, jamais un pixel dans un coin ;
 * - **la composition par maximum** : une couche n'efface pas la précédente, elle
 *   ne peut que l'éclaircir. C'est le comportement des renderers d'origine, et
 *   c'est ce qui rend l'ordre de dessin peu sensible.
 */
class Frame(val spec: MatrixSpec) {

    /** Luminosités 0..1, ordre ligne par ligne. Accès direct pour les boucles chaudes. */
    val values: FloatArray = FloatArray(spec.cellCount)

    private val out = IntArray(spec.cellCount)

    fun clear() {
        values.fill(0f)
    }

    /** Écriture masquée et composée par maximum. Hors disque : ignoré. */
    fun set(x: Int, y: Int, brightness: Float) {
        if (!spec.isLed(x, y)) return
        val i = spec.index(x, y)
        if (brightness > values[i]) values[i] = brightness
    }

    /** Idem par index. Utilisé par les parcours de [MatrixSpec.leds] / [MatrixSpec.edgeRing]. */
    fun setAt(index: Int, brightness: Float) {
        if (!spec.isLed(index)) return
        if (brightness > values[index]) values[index] = brightness
    }

    /** Écriture *autoritaire* : remplace au lieu de composer. Pour les fondus et les effacements. */
    fun put(index: Int, brightness: Float) {
        if (spec.isLed(index)) values[index] = brightness
    }

    operator fun get(x: Int, y: Int): Float =
        if (spec.isLed(x, y)) values[spec.index(x, y)] else 0f

    /** Allume tout le disque à [brightness] (composition par maximum). */
    fun fill(brightness: Float) {
        for (i in spec.leds) if (brightness > values[i]) values[i] = brightness
    }

    /** Atténue toute la frame — le fondu avant une animation d'arrivée, par exemple. */
    fun dim(factor: Float) {
        for (i in spec.leds) values[i] *= factor
    }

    fun copyFrom(other: Frame) {
        require(other.spec === spec) { "frames de géométries différentes" }
        other.values.copyInto(values)
    }

    /**
     * Sortie vers un sink : 0..255, entiers.
     *
     * Le tableau retourné est **réutilisé d'une frame à l'autre** — c'est
     * délibéré, on rend 30 fois par seconde et on ne veut pas d'allocation dans
     * la boucle. Le contrat de [FrameSink.push] dit qu'un sink qui conserve la
     * frame doit la copier.
     */
    fun toBrightness(): IntArray {
        for (i in out.indices) {
            out[i] = if (spec.isLed(i)) (min(1f, values[i]) * 255f).toInt() else 0
        }
        return out
    }

    /** Rend la frame courante et la pousse. Raccourci de boucle. */
    fun pushTo(sink: FrameSink) {
        sink.push(toBrightness())
    }
}
