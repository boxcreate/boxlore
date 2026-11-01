package cx.aswin.boxlore.feature.settings.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import cx.aswin.boxlore.core.support.ConversationResult
import cx.aswin.boxlore.core.support.ReplyResult
import cx.aswin.boxlore.core.support.SupportConversation
import cx.aswin.boxlore.core.support.SupportRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FeedbackThreadUiState(
    val conversation: SupportConversation? = null,
    val loading: Boolean = true,
    /** Not this phone's conversation (any more), or support isn't available. */
    val gone: Boolean = false,
    /** The last load failed; with a conversation on screen it stays, with a way to try again. */
    val loadFailed: Boolean = false,
    val loadingEarlier: Boolean = false,
    val replyOpen: Boolean = false,
    val reply: String = "",
    val sending: Boolean = false,
    val replyError: String? = null,
    val solving: Boolean = false,
) {
    val canReply: Boolean get() = conversation?.thread?.canReply == true
    val canSendReply: Boolean get() = reply.isNotBlank() && !sending && canReply

    /** Mark solved: an open conversation the team has answered. */
    val canSolve: Boolean get() = conversation?.let { c -> c.ending == null && c.thread.canReply && c.messages.any { it.fromTeam } } == true

    /** The newest message on screen: replies up to it count as read. */
    val newestShownAt: Long? get() = conversation?.messages?.maxOfOrNull { it.createdAt }
}

/** What a conversation screen needs from support; the factory wires [SupportRepository], tests pass fakes. */
class FeedbackThreadSupport(
    val load: suspend (id: String, before: String?) -> ConversationResult,
    val reply: suspend (id: String, body: String, requestId: String) -> ReplyResult,
    val markRead: suspend (id: String, upTo: Long?) -> Unit,
    val solve: suspend (id: String) -> Boolean,
    val photo: suspend (id: String) -> ByteArray?,
    /** The server's message limit. */
    val maxBody: () -> Int,
    /** Conversation ids reply pushes arrived for. */
    val pushes: Flow<String?> = emptyFlow(),
)

/** [RetryKeyStore] in the screen's saved state, so a reply retried after the process died keeps its key. */
internal class SavedStateRetryKeyStore(private val handle: SavedStateHandle) : RetryKeyStore {
    override fun load(): Pair<String, String>? {
        val fingerprint = handle.get<String>(KEY_FINGERPRINT) ?: return null
        val key = handle.get<String>(KEY_REQUEST) ?: return null
        return fingerprint to key
    }

    override fun save(fingerprint: String, key: String) {
        handle[KEY_FINGERPRINT] = fingerprint
        handle[KEY_REQUEST] = key
    }

    override fun clear() {
        handle.remove<String>(KEY_FINGERPRINT)
        handle.remove<String>(KEY_REQUEST)
    }

    private companion object {
        const val KEY_FINGERPRINT = "reply_fingerprint"
        const val KEY_REQUEST = "reply_request_id"
    }
}

class FeedbackThreadViewModel internal constructor(
    private val support: FeedbackThreadSupport,
    private val threadId: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val retryKeys = RetryKeys(store = SavedStateRetryKeyStore(saved))
    private val photos = PhotoCache(support.photo)
    private var readUpTo = 0L
    private val _uiState = MutableStateFlow(
        FeedbackThreadUiState(reply = saved[KEY_REPLY] ?: "", replyOpen = saved[KEY_REPLY_OPEN] ?: false),
    )
    val uiState: StateFlow<FeedbackThreadUiState> = _uiState.asStateFlow()

    init {
        // A reply to this conversation (or a push that doesn't say which) brings it up to date while it's open.
        viewModelScope.launch { support.pushes.collect { id -> if (id == null || id == threadId) reload() } }
    }

    /** Each time the conversation shows: load it fresh (the view model may be kept from an earlier visit). */
    fun activate() = reload()

    fun reload() {
        _uiState.update { it.copy(loading = it.conversation == null, loadFailed = false) }
        viewModelScope.launch {
            when (val result = support.load(threadId, null)) {
                is ConversationResult.Loaded -> _uiState.update { it.copy(conversation = result.conversation, loading = false, gone = false, loadFailed = false) }
                ConversationResult.Gone -> _uiState.update { it.copy(conversation = null, loading = false, gone = true, replyOpen = false) }
                ConversationResult.Failed -> _uiState.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    /** The conversation is on screen: the team's replies up to the newest shown message count as read. */
    fun onSeen() {
        val state = _uiState.value
        val upTo = state.newestShownAt ?: return
        if (state.conversation?.thread?.unread != true || upTo <= readUpTo) return
        readUpTo = upTo
        viewModelScope.launch { runCatching { support.markRead(threadId, upTo) } }
    }

    fun loadEarlier() {
        val current = _uiState.value.conversation ?: return
        val before = current.earlier ?: return
        if (_uiState.value.loadingEarlier) return
        _uiState.update { it.copy(loadingEarlier = true) }
        viewModelScope.launch {
            val page = (support.load(threadId, before) as? ConversationResult.Loaded)?.conversation
            _uiState.update { state ->
                val now = state.conversation
                if (page == null || now == null) {
                    state.copy(loadingEarlier = false)
                } else {
                    state.copy(conversation = now.copy(messages = page.messages + now.messages, earlier = page.earlier), loadingEarlier = false)
                }
            }
        }
    }

    fun onReplyOpen(open: Boolean) {
        if (_uiState.value.sending) return
        _uiState.update { it.copy(replyOpen = open && it.canReply, replyError = null) }
        saved[KEY_REPLY_OPEN] = _uiState.value.replyOpen
    }

    fun onReplyChanged(text: String) {
        if (_uiState.value.sending) return
        _uiState.update { it.copy(reply = limitedMessage(it.reply, text, support.maxBody()), replyError = null) }
        saved[KEY_REPLY] = _uiState.value.reply
    }

    fun sendReply() {
        val state = _uiState.value
        val body = state.reply.trim()
        if (!state.canSendReply || body.isBlank()) return
        val requestId = retryKeys.keyFor(body)
        _uiState.update { it.copy(sending = true, replyError = null) }
        viewModelScope.launch {
            when (val result = support.reply(threadId, body, requestId)) {
                is ReplyResult.Sent -> {
                    retryKeys.clear()
                    saved.remove<String>(KEY_REPLY)
                    saved[KEY_REPLY_OPEN] = false
                    _uiState.update { it.copy(sending = false, reply = "", replyOpen = false) }
                    reload()
                }
                is ReplyResult.Closed -> {
                    saved[KEY_REPLY_OPEN] = false
                    // The server's answer says how it ended; show that now rather than wait for a reload.
                    _uiState.update { s ->
                        val c = s.conversation
                        s.copy(
                            sending = false,
                            replyOpen = false,
                            conversation = c?.copy(ending = result.ending ?: c.ending, thread = c.thread.copy(canReply = false)),
                        )
                    }
                    reload()
                }
                is ReplyResult.Refused -> _uiState.update { it.copy(sending = false, replyError = result.message) }
                ReplyResult.Failed -> _uiState.update {
                    it.copy(sending = false, replyError = "Couldn't send your reply. Check your connection and try again.")
                }
            }
        }
    }

    fun solve() {
        if (!_uiState.value.canSolve || _uiState.value.solving) return
        _uiState.update { it.copy(solving = true) }
        viewModelScope.launch {
            support.solve(threadId)
            _uiState.update { it.copy(solving = false) }
            reload()
        }
    }

    /** A photo's bytes: fetched once while it's kept, and fetched again later if it failed. */
    suspend fun photo(id: String): ByteArray? = photos.get(id)

    class Factory(private val repository: SupportRepository, private val threadId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = FeedbackThreadViewModel(
            support = FeedbackThreadSupport(
                load = repository::conversation,
                reply = { id, body, key -> repository.reply(id, body, key) },
                markRead = { id, upTo -> repository.markRead(id, upTo) },
                solve = repository::solve,
                photo = repository::photo,
                maxBody = { repository.meta.value.maxBody },
                pushes = repository.pushedReplies,
            ),
            threadId = threadId,
            saved = extras.createSavedStateHandle(),
        ) as T
    }

    private companion object {
        const val KEY_REPLY = "reply"
        const val KEY_REPLY_OPEN = "reply_open"
    }
}

/**
 * The newest [MAX_PHOTOS] photos' bytes. One fetch per photo at a time; a failed fetch isn't
 * kept, so the next look tries again.
 */
internal class PhotoCache(private val fetch: suspend (String) -> ByteArray?) {
    private val lock = Mutex()
    private val bytes = object : LinkedHashMap<String, ByteArray>(MAX_PHOTOS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > MAX_PHOTOS
    }
    private val inFlight = mutableMapOf<String, CompletableDeferred<ByteArray?>>()

    suspend fun get(id: String): ByteArray? {
        var mine: CompletableDeferred<ByteArray?>? = null
        val waiting = lock.withLock {
            bytes[id]?.let { return it }
            inFlight[id] ?: CompletableDeferred<ByteArray?>().also {
                inFlight[id] = it
                mine = it
            }
        }
        val owned = mine ?: return waiting.await()
        val result = runCatching { fetch(id) }.getOrNull()
        lock.withLock {
            if (result != null) bytes[id] = result
            inFlight.remove(id)
        }
        owned.complete(result)
        return result
    }

    private companion object {
        const val MAX_PHOTOS = 12
    }
}
