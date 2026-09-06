package red.suns.haloglyph.core.matrix

/**
 * Destination d'une frame.
 *
 * C'est la seule frontière entre un toy et le monde. Un moteur produit un état,
 * un renderer en fait des luminosités, un sink les emmène quelque part : la
 * matrice physique ([red.suns.haloglyph.core.glyph.GlyphSink]), un bitmap de
 * widget, un `Canvas` Compose, un test.
 *
 * L'interface est ici, en Kotlin pur, et n'a aucune idée de ce qu'est un
 * téléphone Nothing — c'est ce qui permet aux trois surfaces de partager le
 * même code de rendu, à la lettre.
 */
fun interface FrameSink {

    /**
     * @param brightness `spec.cellCount` entrées (625 sur le Phone (3)), ordre
     * ligne par ligne, valeurs 0..255. Le tableau appartient à l'appelant et
     * peut être réutilisé dès le retour : un sink qui le conserve doit le copier.
     */
    fun push(brightness: IntArray)
}

/**
 * Duplique chaque frame vers plusieurs sinks.
 *
 * Sert aux tests de parité (§13 : la même frame doit allumer exactement les
 * mêmes LEDs sur `GlyphSink` et sur un sink bitmap) et, plus tard, au protocole
 * miroir opt-in : un toy qui accepte d'être observé se contente d'ajouter un
 * second sink, sans rien changer à son rendu.
 */
class FanOutSink(private val sinks: List<FrameSink>) : FrameSink {

    constructor(vararg sinks: FrameSink) : this(sinks.toList())

    override fun push(brightness: IntArray) {
        for (sink in sinks) sink.push(brightness)
    }
}

/** Sink de test : conserve une copie de la dernière frame reçue. */
class RecordingSink(private val expectedSize: Int) : FrameSink {

    var last: IntArray? = null
        private set

    var frameCount: Int = 0
        private set

    override fun push(brightness: IntArray) {
        require(brightness.size == expectedSize) {
            "frame de ${brightness.size} valeurs, attendu $expectedSize"
        }
        last = brightness.copyOf()
        frameCount++
    }
}
