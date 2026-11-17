package cx.aswin.boxlore.sync

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.catalog.sync.SyncResult
import cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator
import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

// Fakes shared by the CloudSyncTriggerCoordinator tests.

internal class TriggerTestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

internal class FakeTriggerSyncCoordinator : UserSyncCoordinator() {
    var syncNowCalls = 0
    var executePushCalls = 0
    var executePushDelayMs = 0L
    var syncNowResult: SyncResult = SyncResult.Success(0, 0, false, 0, 0, false, 12345L)
    var executePushResult: Result<PushBatchSummary> = Result.success(PushBatchSummary(0, 0, false, 77_000L))

    /** When set, each sync waits for it, so a test can act while a sync is still running. */
    var syncNowGate: CompletableDeferred<Unit>? = null

    override suspend fun syncNow(): SyncResult {
        syncNowCalls++
        syncNowGate?.await()
        return syncNowResult
    }

    /** Runs on each push, so a test can clear the dirty rows the way a real push does. */
    var onPush: (suspend () -> Unit)? = null

    override suspend fun executePush(token: String?): Result<PushBatchSummary> {
        executePushCalls++
        onPush?.invoke()
        if (executePushDelayMs > 0) {
            kotlinx.coroutines.delay(executePushDelayMs)
        }
        return executePushResult
    }
}

internal class FakeTriggerAuthRepository : AuthRepository {
    override val currentUser = MutableStateFlow<BoxLoreUser?>(null)
    override val currentUserId: String? get() = currentUser.value?.uid

    override suspend fun signInWithGoogle(idToken: String): Result<BoxLoreUser> = error("unused")
    override suspend fun signInWithEmailPassword(email: String, password: String): Result<BoxLoreUser> = error("unused")
    override suspend fun signUpWithEmailPassword(email: String, password: String): Result<BoxLoreUser> = error("unused")
    override suspend fun sendMagicLink(email: String): Result<Unit> = error("unused")
    override suspend fun signInWithEmailLink(email: String, emailLink: String): Result<BoxLoreUser> = error("unused")
    override fun isSignInWithEmailLink(link: String): Boolean = false
    override suspend fun sendPasswordReset(email: String): Result<Unit> = error("unused")
    override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)
    override suspend fun reloadUser(): Result<BoxLoreUser?> = Result.success(currentUser.value)
    override fun signOut() {
        currentUser.value = null
    }
    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    override suspend fun getIdToken(forceRefresh: Boolean): String? = "mock-token"
}
