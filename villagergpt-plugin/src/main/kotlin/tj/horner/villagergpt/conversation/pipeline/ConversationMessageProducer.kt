package io.github.enpici.villager.gpt.conversation.pipeline

import io.github.enpici.villager.gpt.conversation.VillagerConversation

interface ConversationMessageProducer {
    suspend fun produceNextMessage(conversation: VillagerConversation): String
}