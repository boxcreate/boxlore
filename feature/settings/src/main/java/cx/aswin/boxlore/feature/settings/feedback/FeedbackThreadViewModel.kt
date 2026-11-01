package cx.aswin.boxlore.feature.settings.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.support.ConversationResult
import cx.aswin.boxlore.core.support.ReplyResult
import cx.aswin.boxlore.core.support.SupportConversation
import cx.aswin.boxlore.core.support.SupportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedbackThreadUiState(
    val conversation: SupportConversation? = null,
    val loading: Boolean = true,
    /** Not this phone's conversation (any more), or support isn't available. */
    val gone: Boolean = false,
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
}

/** What a conversation screen needs from support; the factory wires [SupportRepository], tests pass fakes. */
class FeedbackThreadSupport(
    val load: suspend (id: String, before: String?) -> ConversationResult,
    val reply: suspend (id: String, body: String, requestId: String) -> ReplyResult,
    val markRead: suspend (id: String) -> Unit,
    val solve: suspend (id: String) -> Boolean,
    val photo: suspend (id: String) -> ByteArray?,
    /** The server's message limit. */
    val maxBody: () -> Int,
)

class FeedbackThreadViewModel(private val support: FeedbackThreadSupport, private val threadId: String) : ViewModel() {
    private val retryKeys = RetryKeys()
    private val photoCache = mutableMapOf<String, ByteArray?>()
    private val _uiState = MutableStateFlow(FeedbackThreadUiState())
    val uiState: StateFlow<FeedbackThreadUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    /** Loads the newest page; an unread team reply is marked read once it is on screen. */
    fun reload() {
        _uiState.update { it.copy(loading = it.conversation == null, loadFailed = false) }
        viewModelScope.launch {
            when (val result = support.load(threadId, null)) {
                is ConversationResult.Loaded -> {
                    _uiState.update { it.copy(conversation = result.conversation, loading = false, gone = false, loadFailed = false) }
                    if (result.conversation.thread.unread) runCatching { support.markRead(threadId) }
                }
                ConversationResult.Gone -> _uiState.update { it.copy(loading = false, gone = true) }
                ConversationResult.Failed -> _uiState.update { it.copy(loading = false, loadFailed = true) }
            }
        }
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
    }

    fun onReplyChanged(text: String) {
        if (_uiState.value.sending) return
        _uiState.update { it.copy(reply = limitedMessage(it.reply, text, support.maxBody()), replyError = null) }
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
                    _uiState.update { it.copy(sending = false, reply = "", replyOpen = false) }
                    reload()
                }
                is ReplyResult.Closed -> {
                    _uiState.update { it.copy(sending = false, replyOpen = false) }
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

    /** A photo's bytes, fetched once. */
    suspend fun photo(id: String): ByteArray? {
        if (photoCache.containsKey(id)) return photoCache[id]
        return support.photo(id).also { photoCache[id] = it }
    }

    class Factory(private val repository: SupportRepository, private val threadId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedbackThreadViewModel(
            support = FeedbackThreadSupport(
                load = repository::conversation,
                reply = { id, body, key -> repository.reply(id, body, key) },
                markRead = { id -> repository.markRead(id) },
                solve = repository::solve,
                photo = repository::photo,
                maxBody = { repository.meta.value.maxBody },
            ),
            threadId = threadId,
        ) as T
    }

}
