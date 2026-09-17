package red.suns.haloglyph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import red.suns.haloglyph.core.widget.MatrixWidgetProvider
import red.suns.haloglyph.core.widget.WidgetBurstService
import red.suns.haloglyph.core.widget.WidgetToy
import red.suns.haloglyph.dice.widget.DiceWidgetToy
import red.suns.haloglyph.gforce.widget.GForceWidgetToy
import red.suns.haloglyph.lapse.widget.LapseWidgetToy
import red.suns.haloglyph.plumb.widget.PlumbWidgetToy
import red.suns.haloglyph.sono.widget.SonoWidgetToy

/**
 * Les toys que le hublot d'écran d'accueil fait tourner, dans cet ordre.
 *
 * Assemblé ici et nulle part ailleurs, pour la même raison que [ToyCatalog] :
 * `app` est le seul module qui sache ce qu'il embarque. Il n'y a pas de registre
 * global, parce qu'il n'existe pas de catalogue commun à plusieurs apps.
 *
 * ## Un objet, et pas une liste fabriquée à la demande
 *
 * Un `AppWidgetProvider` est un receiver : le système en construit une instance
 * neuve à chaque diffusion, et il y en a une par image de repos. Une liste
 * reconstruite dans le getter reconstruirait donc aussi les trois renderers du
 * pack — et, pour Lapse, un moteur qui relit la configuration. Les instances
 * vivent ici, dans le processus, et survivent aux diffusions comme aux rafales.
 *
 * L'ordre est celui du hub, et il est aussi celui du double tap : Lapse, Dice,
 * Sono, Plumb, G-Forces, puis Lapse à nouveau.
 */
object HaloToys {
    val list: List<WidgetToy> by lazy {
        listOf(
            LapseWidgetToy(),
            DiceWidgetToy(),
            SonoWidgetToy(),
            PlumbWidgetToy(),
            GForceWidgetToy(),
        )
    }
}

/**
 * Le hublot, deux cellules sur deux.
 *
 * Il n'y a plus **qu'un** widget dans le pack, là où il y en avait un par toy et
 * par format. Un écran d'accueil ne veut pas choisir entre un dé et un compteur
 * au moment de poser le widget ; il veut un hublot, et décider après — d'où le
 * double tap, qui passe au toy suivant, et l'onglet de rotation, qui dit
 * lesquels comptent. Tout est réglé **par hublot posé** : deux hublots côte à
 * côte peuvent tourner sur deux toys différents.
 */
open class HaloWidget : MatrixWidgetProvider() {
    override val toys: List<WidgetToy> get() = HaloToys.list
    override val burstService: Class<out WidgetBurstService> get() = HaloBurstService::class.java
}

/**
 * Le même hublot, sur une cellule.
 *
 * Une sous-classe et non un réglage : le système attache une taille à un
 * **fournisseur**, pas à une instance posée. Deux formats veulent donc deux
 * receivers, et c'est tout ce que celui-ci apporte — mêmes toys, même rendu,
 * mêmes réglages, même rotation.
 *
 * Ce qui change vraiment est plus bas : la définition de la bitmap suit la
 * taille réellement allouée, donc un hublot d'une cellule tombe sur une cellule
 * de trois ou quatre pixels. C'est le format à regarder en premier quand on
 * doute d'une police — Lapse y écrit des chiffres, et une cellule de trois
 * pixels ne pardonne pas.
 */
class HaloWidgetSmall : HaloWidget()

/**
 * Le service qui anime les hublots. Toute la mécanique est dans
 * [WidgetBurstService] ; cette classe existe pour être **déclarée au manifeste**
 * de `app`, avec les types de premier plan que les toys embarqués réclament.
 *
 * C'est le bon endroit et le seul : `core:widget` ne sait pas que Sono est dans
 * le pack, donc il ne peut pas déclarer un service de type `microphone`. `app`,
 * lui, le sait — c'est exactement ce qu'il assemble.
 */
class HaloBurstService : WidgetBurstService()

/**
 * Le redémarrage du téléphone, et lui seul.
 *
 * Un hublot réglé en boucle continue doit repartir après un reboot, et il ne le
 * peut pas tout seul : le système diffuse bien `APPWIDGET_UPDATE` au démarrage,
 * mais **on n'a pas le droit d'y promouvoir un service**. L'exemption « le
 * service démarre par interaction avec un widget » vaut pour un tap, pas pour
 * une mise à jour qu'on n'a pas demandée — mesuré, pas supposé :
 *
 * ```
 * W MatrixWidget: ForegroundServiceStartNotAllowedException
 *     at MatrixWidgetProvider.promote(MatrixWidgetProvider.kt:243)
 *     at MatrixWidgetProvider.syncLoops(MatrixWidgetProvider.kt:155)
 *     at MatrixWidgetProvider.onUpdate(MatrixWidgetProvider.kt:106)
 * ```
 *
 * `BOOT_COMPLETED`, lui, **est** une exemption nommée. D'où ce receiver : trois
 * lignes et une permission normale, pour la seule fenêtre de la journée où l'on
 * puisse rendre à la boucle le service qui la protège.
 */
class HaloBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        HaloWidget().startLoops(context)
    }
}

/**
 * Les deux formats, ensemble.
 *
 * Un service change une préférence sans savoir lesquels sont posés, et le hub
 * les compte sans savoir non plus. Écrire la paire ici évite qu'un troisième
 * format — ou un format retiré — laisse derrière lui un appelant qui en réveille
 * un de moins qu'il n'existe.
 */
val HALO_WIDGETS: List<Class<out MatrixWidgetProvider>> =
    listOf(HaloWidget::class.java, HaloWidgetSmall::class.java)
