package cx.aswin.boxlore.core.support

/** One conversation in the list; every label comes from the server. */
data class SupportThread(
    val id: String,
    val category: String,
    val categoryLabel: String,
    val title: String,
    val status: SupportStatus,
    val updatedAt: Long,
    val unread: Boolean,
    val canReply: Boolean,
)

/** [key] is new_reply, replied, waiting, solved or closed; anything newer shows like waiting. */
data class SupportStatus(val key: String, val label: String, val showTime: Boolean)

data class SupportMessage(
    val id: String,
    val fromTeam: Boolean,
    val author: String,
    val body: String,
    val photoIds: List<String>,
    val diagnostics: Boolean,
    val createdAt: Long,
)

/** How a conversation ended; [actionKind] is reply or new, with the server's [actionLabel]. */
data class SupportEnding(val kind: String, val title: String, val body: String, val at: Long, val actionKind: String?, val actionLabel: String?)

/** One page of a conversation: messages oldest first, and [earlier] to load the page before. */
data class SupportConversation(
    val thread: SupportThread,
    val messages: List<SupportMessage>,
    val earlier: String?,
    val ending: SupportEnding?,
)

sealed interface ConversationResult {
    data class Loaded(val conversation: SupportConversation) : ConversationResult

    /** Not this phone's (or account's) conversation, or support isn't available. */
    data object Gone : ConversationResult

    data object Failed : ConversationResult
}

sealed interface ReplyResult {
    data class Sent(val thread: SupportThread?, val message: SupportMessage?) : ReplyResult

    /** The team closed the conversation; [ending] says what to do instead. */
    data class Closed(val ending: SupportEnding?) : ReplyResult

    /** Today's limit, or similar; [message] comes from the server. */
    data class Refused(val message: String) : ReplyResult

    data object Failed : ReplyResult
}

/**
 * What this phone remembers about support between launches: whether it has conversations
 * (so Feedback opens on the list and the app checks for replies at all), whether a reply is
 * waiting (set by a push, so Home shows it without asking the server), and the push topics it is
 * subscribed to. The app backs it with its own preferences file.
 */
interface SupportLocalStore {
    var hasConversations: Boolean
    var replyWaiting: Boolean
    var subscribedTopics: Set<String>
    var lastCheckedAt: Long

    /** For tests and before the app wires a real one. */
    class InMemory : SupportLocalStore {
        override var hasConversations = false
        override var replyWaiting = false
        override var subscribedTopics: Set<String> = emptySet()
        override var lastCheckedAt = 0L
    }
}

/** Which push topics to join and leave to match the server's list. */
data class PushTopicPlan(val subscribe: Set<String>, val unsubscribe: Set<String>) {
    val isEmpty: Boolean get() = subscribe.isEmpty() && unsubscribe.isEmpty()

    companion object {
        fun between(subscribed: Set<String>, wanted: Collection<String>): PushTopicPlan {
            val target = wanted.toSet()
            return PushTopicPlan(subscribe = target - subscribed, unsubscribe = subscribed - target)
        }
    }
}

/**
 * Keeps this phone's reply-push subscriptions in line with the server's list. Use it only with a
 * `meta` from a successful refresh: an offline `meta` has no topics and would leave them all.
 * A phone without conversations needs none.
 */
class SupportPushTopics(private val local: SupportLocalStore) {
    fun planFor(meta: SupportMeta, hasConversations: Boolean): PushTopicPlan =
        PushTopicPlan.between(local.subscribedTopics, if (hasConversations) meta.pushTopics else emptyList())

    /** Records what Firebase confirmed. */
    fun joined(topic: String) {
        local.subscribedTopics = local.subscribedTopics + topic
    }

    fun left(topic: String) {
        local.subscribedTopics = local.subscribedTopics - topic
    }
}
