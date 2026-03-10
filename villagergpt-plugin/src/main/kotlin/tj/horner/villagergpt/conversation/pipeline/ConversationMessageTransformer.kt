package io.github.enpici.villager.gpt.conversation.pipeline

import io.github.enpici.villager.gpt.conversation.VillagerConversation

interface ConversationMessageTransformer {
    fun transformMessage(message: String, conversation: VillagerConversation): String
}