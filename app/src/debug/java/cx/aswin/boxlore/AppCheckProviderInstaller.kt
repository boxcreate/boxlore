package cx.aswin.boxlore

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun installBuildAppCheckProvider(appCheck: FirebaseAppCheck) {
    seedDebugSecret(BuildConfig.BOXLORE_APPCHECK_DEBUG_SECRET)
    appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}

/**
 * Optional fixed debug secret from `BOXLORE_APPCHECK_DEBUG_SECRET` in local.properties, so every
 * debug install uses one secret registered in the Firebase console instead of a new random one.
 *
 * firebase-appcheck-debug 19.3.0 reads its secret from the SharedPreferences file
 * `com.google.firebase.appcheck.debug.store.<FirebaseApp persistence key>`, key
 * `com.google.firebase.appcheck.debug.DEBUG_SECRET` (`StorageHelper`), and generates one only when
 * it is empty. The provider reads it when the factory is installed, so it is written first.
 * Recheck these names when the SDK is upgraded. Without the property nothing changes.
 */
private fun seedDebugSecret(secret: String) {
    if (secret.isBlank()) return
    runCatching {
        val app = FirebaseApp.getInstance()
        app.applicationContext
            .getSharedPreferences("com.google.firebase.appcheck.debug.store.${app.persistenceKey}", Context.MODE_PRIVATE)
            .edit()
            .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", secret)
            .commit()
    }
}
