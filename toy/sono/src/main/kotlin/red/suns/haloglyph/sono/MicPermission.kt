package red.suns.haloglyph.sono

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * L'autorisation micro, et les trois états dans lesquels elle peut être.
 *
 * ## Où la demande est faite, et pourquoi pas ici
 *
 * Une permission d'exécution ne se demande **que depuis une activité** : un
 * service lié par Glyph Interface n'a pas d'écran où poser la question. Le toy
 * ne peut donc jamais réparer sa propre situation — c'est l'app qui demande, à
 * la première ouverture, et l'écran de réglages de Sono qui redonne la main
 * ensuite.
 *
 * ## Le refus définitif
 *
 * Après deux refus, Android ne montre plus la boîte de dialogue : la demande
 * revient « refusée » sans que rien n'apparaisse à l'écran. Un bouton
 * « Autoriser » qui ne ferait rien serait pire que pas de bouton du tout, d'où
 * [openAppSettings] — on envoie alors vers la page des autorisations de l'app,
 * qui est le seul endroit où le refus se défait.
 *
 * On ne peut pas distinguer « jamais demandé » de « refusé définitivement » avec
 * les seules API du système : les deux répondent `false` à
 * `shouldShowRequestPermissionRationale`. D'où [Asked], notre propre trace, et
 * la règle qui en découle — tant qu'on n'a jamais demandé, on demande.
 */
object MicPermission {

    const val NAME: String = Manifest.permission.RECORD_AUDIO

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED

    /**
     * Ouvre la page des autorisations de l'application.
     *
     * @return `false` si le système n'a pas d'écran à proposer — l'appelant ne
     * doit alors pas prétendre avoir fait quelque chose.
     */
    fun openAppSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /**
     * La trace de nos propres demandes.
     *
     * Vit dans les préférences du toy et pas dans celles de l'app : c'est Sono
     * qui a besoin du micro, et le jour où le module sort du pack, sa trace part
     * avec lui.
     */
    object Asked {
        private const val KEY = "mic_asked"

        fun get(context: Context): Boolean =
            SonoConfig.prefs(context).getBoolean(KEY, false)

        fun mark(context: Context) {
            SonoConfig.prefs(context).edit().putBoolean(KEY, true).apply()
        }
    }
}
