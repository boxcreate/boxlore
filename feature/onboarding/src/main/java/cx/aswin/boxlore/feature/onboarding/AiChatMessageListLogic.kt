package cx.aswin.boxlore.feature.onboarding

/**
 * Pure chat transcript assembly for AI onboarding (no Compose dependency).
 */
object AiChatMessageListLogic {
    const val INITIAL_ASSISTANT_MESSAGE =
        "Hi! I'm boxlore. I'll ask a few quick questions to learn your taste and build your feed. To start, what kind of a listener are you?"

    fun buildMessages(
        history: List<cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry>,
        isAiLoading: Boolean,
        isSynthesizing: Boolean,
        onboardingError: String?,
    ): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        list.add(
            ChatMessage.ModelMessage(
                text = INITIAL_ASSISTANT_MESSAGE,
                key = "init_model",
            ),
        )
        history.forEachIndexed { index, entry ->
            val text = entry.parts.joinToString(". ") { it.text }
            if (entry.role == "user") {
                list.add(ChatMessage.UserMessage(text, key = "user_$index"))
            } else {
                list.add(ChatMessage.ModelMessage(text, key = "model_$index"))
            }
        }
        if (isAiLoading && !isSynthesizing) {
            list.add(ChatMessage.LoadingMessage)
        }
        if (onboardingError != null) {
            list.add(ChatMessage.ErrorMessage(onboardingError, key = "error"))
        }
        return list
    }
}
