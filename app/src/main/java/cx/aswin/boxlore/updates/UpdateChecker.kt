package cx.aswin.boxlore.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface UpdateSource {
    suspend fun lookup(): UpdateLookup
}

interface UpdateCheckStore {
    var checkedAt: Long
    var attemptedAt: Long
    var cachedManifest: UpdateManifest?
    var cachedPlayVersion: Long
    var upcoming: String
}

/** No automatic prompts or downloads. Failed checks retain the last verified offer. */
class UpdateChecker(
    private val source: UpdateSource,
    private val store: UpdateCheckStore,
    private val installedVersion: Long,
    private val sdkInt: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(initialState())
    val state = mutableState.asStateFlow()

    private fun initialState(): UpdateCheckState {
        val cached = store.cachedManifest
        val offer = if (cached != null && cached.versionCode > installedVersion && cached.minSdk <= sdkInt) {
            UpdateOffer(cached.versionCode, cached.versionName, cached)
        } else if (cached == null && store.cachedPlayVersion > installedVersion) {
            UpdateOffer(store.cachedPlayVersion, store.cachedPlayVersion.toString())
        } else {
            return UpdateCheckState(upcoming = store.upcoming)
        }
        return UpdateCheckState(UpdateCheckStatus.AVAILABLE, offer, store.upcoming)
    }

    suspend fun check(force: Boolean = false) {
        // Joining an already-running check never issues a duplicate request.
        if (!mutex.tryLock()) {
            mutex.withLock { }
            return
        }
        try {
            val time = now()
            if (shouldSkipCheck(force, time)) return
            store.attemptedAt = time
            mutableState.value = mutableState.value.copy(status = UpdateCheckStatus.CHECKING)
            val result = source.lookup()
            result.offer?.manifest?.validate()
            val incompatible = result.incompatible || (result.offer?.manifest?.minSdk ?: 0) > sdkInt
            val offer = result.offer?.takeIf { it.versionCode > installedVersion && !incompatible }
            require(result.upcoming.length <= 6_000)
            store.upcoming = result.upcoming
            cacheOffer(offer)
            store.checkedAt = now()
            mutableState.value = UpdateCheckState(
                status = when {
                    offer != null -> UpdateCheckStatus.AVAILABLE
                    incompatible -> UpdateCheckStatus.INCOMPATIBLE
                    else -> UpdateCheckStatus.CURRENT
                },
                offer = offer,
                upcoming = result.upcoming,
            )
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(status = UpdateCheckStatus.IDLE)
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(status = UpdateCheckStatus.FAILED)
        } finally {
            mutex.unlock()
        }
    }

    private fun shouldSkipCheck(force: Boolean, time: Long): Boolean {
        if (force || mutableState.value.status == UpdateCheckStatus.IDLE) return false
        return recent(store.checkedAt, time, CHECK_INTERVAL) || recent(store.attemptedAt, time, RETRY_INTERVAL)
    }

    private fun cacheOffer(offer: UpdateOffer?) {
        store.cachedManifest = offer?.manifest
        store.cachedPlayVersion = if (offer?.manifest == null) offer?.versionCode ?: 0 else 0
    }

    private fun recent(previous: Long, time: Long, interval: Long): Boolean = previous > 0 && time >= previous && time - previous < interval

    companion object {
        const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
        const val RETRY_INTERVAL = 15 * 60 * 1000L
    }
}
