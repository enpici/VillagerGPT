package io.github.enpici.villager.gpt.conversation.pipeline

import io.github.enpici.villager.gpt.conversation.VillagerConversation

interface ConversationMessageProcessor {
    fun processMessage(message: String, conversation: VillagerConversation): Collection<ConversationMessageAction>?
}