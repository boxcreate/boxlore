package cx.aswin.boxlore.updates

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class PlayUpdateSource(context: Context) : UpdateSource {
    private val manager = AppUpdateManagerFactory.create(context)

    override suspend fun lookup(): UpdateLookup = suspendCancellableCoroutine { continuation ->
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (!continuation.isActive) return@addOnSuccessListener
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE || info.installStatus() == InstallStatus.DOWNLOADED
            val allowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) || info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) || info.installStatus() == InstallStatus.DOWNLOADED
            val offer = if (available && allowed) UpdateOffer(info.availableVersionCode().toLong(), info.availableVersionCode().toString()) else null
            continuation.resume(UpdateLookup(offer, incompatible = available && !allowed))
        }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}
