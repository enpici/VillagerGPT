package io.github.enpici.villager.gpt.conversation.pipeline.producers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LocalModelResponseParserTest {
    @Test
    fun `openAiChatContent extracts assistant message content`() {
        val response = """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Hola, soy un aldeano."
                  },
                  "finish_reason": "stop"
                }
              ]
            }
        """.trimIndent()

        val content = LocalModelResponseParser.openAiChatContent(response)

        assertEquals("Hola, soy un aldeano.", content)
    }

    @Test
    fun `openAiChatContent rejects responses without content`() {
        val response = """{"choices":[{"message":{"role":"assistant"}}]}"""

        assertThrows(LocalProviderHttpException::class.java) {
            LocalModelResponseParser.openAiChatContent(response)
        }
    }
}
