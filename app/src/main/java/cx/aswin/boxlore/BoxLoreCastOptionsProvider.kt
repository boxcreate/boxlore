package cx.aswin.boxlore

import android.content.Context
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Keeps Media3's session ownership while selecting boxlore's registered Cast receiver.
 *
 * The receiver id is public configuration, not a credential.
 */
class BoxLoreCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions
            .Builder()
            .setReceiverApplicationId(BuildConfig.BOXLORE_CAST_RECEIVER_ID)
            .setResumeSavedSession(true)
            .setEnableReconnectionService(true)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setRemoteToLocalEnabled(true)
            .setLaunchOptions(
                LaunchOptions
                    .Builder()
                    .setAndroidReceiverCompatible(false)
                    .build(),
            ).build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()
}
