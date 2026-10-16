package cx.aswin.boxlore.core.network.session

import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 2027-01-15T08:00:00Z in milliseconds; tests start every clock here. */
internal const val BASE_MS = 1_800_000_000_000L
internal const val BASE_S = BASE_MS / 1_000L
internal const val DAY_S = 86_400L

/** Builds a `bls1` token whose payload the app can read (the signature is never checked client-side). */
internal fun sessionToken(
    sid: String = "sid-1",
    att: String = "appcheck",
    iat: Long = BASE_S,
    exp: Long = iat + DAY_S,
    attExp: Long = if (att == "appcheck") iat + 30 * DAY_S else exp,
    deviceKey: Boolean = true,
    tag: String = "a",
): String {
    val dpk = if (deviceKey) ",\"dpk\":\"device-pk\"" else ""
    val payload = """{"sid":"$sid","iid":"install-1","plat":"android","att":"$att","iat":$iat,"exp":$exp,"attExp":$attExp$dpk}"""
    return "bls1.k1.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.sig-$tag"
}

internal class FakeStore(initial: StoredApiSession = StoredApiSession()) : ApiSessionStore {
    @Volatile var state: StoredApiSession = initial
    val saves = AtomicInteger()

    override suspend fun load(): StoredApiSession = state

    override suspend fun save(state: StoredApiSession) {
        this.state = state
        saves.incrementAndGet()
    }
}

internal class FakeDeviceKey(var available: Boolean = true) : DeviceKeySigner {
    val signed = CopyOnWriteArrayList<String>()

    override fun publicKey(): String? = if (available) "device-pk" else null

    override fun sign(message: String): String? {
        if (!available) return null
        signed += message
        return "signed-${message.hashCode()}"
    }

    fun signatureOf(message: String) = "signed-${message.hashCode()}"
}

internal class FakeExchanger(var handler: suspend (SessionExchangeRequest) -> SessionExchangeResult) : SessionExchanger {
    val requests = CopyOnWriteArrayList<SessionExchangeRequest>()

    val calls: Int get() = requests.size

    override suspend fun exchange(request: SessionExchangeRequest): SessionExchangeResult {
        requests += request
        return handler(request)
    }
}

internal fun issued(token: String, attestation: ApiAttestation = ApiAttestation.fromWire(ApiSessionToken.parse(token)?.attestation?.wire)) =
    SessionExchangeResult.Issued(token, attestation, attestation == ApiAttestation.None, reason = if (attestation == ApiAttestation.None) "appcheck:invalid" else null)

internal class RecordingListener : ApiSessionListener {
    val exchanges = CopyOnWriteArrayList<ApiSessionExchangeEvent>()
    val renewals = CopyOnWriteArrayList<ApiSessionRenewOutcome>()
    val stepUps = CopyOnWriteArrayList<Pair<ApiSessionStepUpOutcome, String?>>()

    override fun onExchange(event: ApiSessionExchangeEvent) {
        exchanges += event
    }

    override fun onRenewal(outcome: ApiSessionRenewOutcome, attestation: ApiAttestation) {
        renewals += outcome
    }

    override fun onStepUp(outcome: ApiSessionStepUpOutcome, reason: String?, durationMs: Long) {
        stepUps += outcome to reason
    }
}

internal class FakeClock(start: Long = BASE_MS) : () -> Long {
    private val now = AtomicLong(start)

    override fun invoke(): Long = now.get()

    fun set(ms: Long) = now.set(ms)

    fun advanceSeconds(seconds: Long) {
        now.addAndGet(seconds * 1_000L)
    }
}

/** Polls a condition that settles on another thread (persistence runs asynchronously). */
internal fun eventually(timeoutMs: Long = 2_000L, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "Condition not met within ${timeoutMs}ms" }
        Thread.sleep(10)
    }
}
