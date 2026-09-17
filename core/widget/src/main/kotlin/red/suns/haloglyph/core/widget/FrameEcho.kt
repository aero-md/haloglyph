package red.suns.haloglyph.core.widget

/**
 * La dernière image réellement partie vers le launcher, gardée pour ne pas
 * envoyer deux fois la même.
 *
 * ## Pourquoi ça existe
 *
 * C'est la principale économie de batterie du hublot. Rendre une image coûte
 * quelques dizaines de microsecondes — les moteurs sont du Kotlin pur — mais la
 * **rasteriser** puis la faire traverser le binder coûte 260 Ko, vingt-cinq fois
 * par seconde. Or une animation passe l'essentiel de son temps à redessiner la
 * même chose : un compteur en boucle continue ne change qu'à la seconde, soit
 * vingt-quatre images sur vingt-cinq identiques à la précédente.
 *
 * ## Pourquoi c'est une classe et pas deux lignes
 *
 * Parce que les deux lignes étaient fausses, et d'une façon qui ne se voit pas
 * en les relisant. [red.suns.haloglyph.core.matrix.Frame.toBrightness] **réutilise
 * son tableau** d'une image à l'autre — c'est écrit dans son contrat, et c'est
 * délibéré : on ne veut pas d'allocation dans une boucle de rendu. Retenir la
 * référence revenait donc à comparer le tableau avec lui-même : toujours égal,
 * donc plus **aucune** image poussée après la première.
 *
 * Tous les hublots se figeaient, tous toys confondus, alors que les moteurs
 * tournaient parfaitement — les journaux montraient un micro qui captait et une
 * matrice qui se dessinait, pendant que l'écran d'accueil ne bougeait plus. Le
 * genre de panne qu'on cherche du mauvais côté pendant longtemps.
 *
 * D'où une classe, un nom, et un test qui rejoue exactement ce cas : on lui passe
 * deux fois le même tableau, modifié entre les deux.
 */
internal class FrameEcho {

    /**
     * Une **copie**, jamais la référence reçue. Sert aussi de tampon réutilisé :
     * une seule allocation pour toute la vie de l'animation.
     */
    private var last: IntArray? = null

    /**
     * @return `true` si [brightness] diffère de la dernière image acceptée — donc
     * s'il faut la pousser. Mémorise alors son contenu.
     */
    fun accept(brightness: IntArray): Boolean {
        val previous = last
        if (previous != null && previous.size == brightness.size &&
            previous.contentEquals(brightness)
        ) {
            return false
        }
        val buffer = if (previous != null && previous.size == brightness.size) {
            previous
        } else {
            IntArray(brightness.size)
        }
        brightness.copyInto(buffer)
        last = buffer
        return true
    }
}
