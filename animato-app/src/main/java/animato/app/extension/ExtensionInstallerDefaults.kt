package animato.app.extension

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.base.BasePreferences.ExtensionInstaller
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Extensions install inside the app, with no system install screen, unless somebody chose otherwise.
 *
 * ## What it replaces
 *
 * Mihon's default installer hands each downloaded extension to Android's package installer, which
 * means a system dialog per extension, an *unknown sources* permission to grant first, and — on a
 * television, where that permission is often buried or missing — an install that simply does not
 * happen. Installing five extensions was five dialogs.
 *
 * Mihon already has the other way, the *private* installer: the APK is checked — its package,
 * that it is an extension, that it is signed, and on an update that the signature matches and the
 * version does not go backwards — then kept in the app's own storage and loaded from there. The
 * extensions themselves are no different; only where they live is. Mihon ships it switched off in
 * release builds, and the class that holds that default is Mihon's to keep.
 *
 * ## Why a seed and not a new default
 *
 * The same answer as the NSFW defaults beside it: on a launch where nobody has ever chosen an
 * installer, the choice is made for them, once. `isSet` is the difference between a default and a
 * decision, so somebody who picked the system installer or Shizuku keeps it, and this never runs
 * over that.
 *
 * Extensions already installed the old way keep working where they are. The loader reads both
 * places, and their next update simply lands privately.
 */
object ExtensionInstallerDefaults {

    fun seedPrivateByDefault() {
        val installer = Injekt.get<BasePreferences>().extensionInstaller
        if (!installer.isSet()) {
            installer.set(ExtensionInstaller.PRIVATE)
        }
    }
}
