package com.jaysay.coursetable.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderConfigTest {
    @Test fun presetsHaveStableChatCompletionsDefaults() {
        assertEquals(listOf("openai", "deepseek", "zhipu", "aliyun", "custom"), AiProviderPresets.all.map { it.id })
        assertEquals("https://api.openai.com/v1/chat/completions", AiProviderPresets.byId("openai").url)
        assertEquals("deepseek-flash", AiProviderPresets.byId("deepseek").defaultModel)
        assertEquals("glm-4.5", AiProviderPresets.byId("zhipu").defaultModel)
        assertEquals("qwen-plus", AiProviderPresets.byId("aliyun").defaultModel)
    }

    @Test fun configRoundTripsProviderAndModelIsolation() {
        val config = AiProviderConfig(selectedId = "custom", models = mapOf("openai" to "model-a", "deepseek" to "model-b"),
            customUrl = "https://api.example.com/v1/chat/completions", customModel = "custom-model")
        val decoded = AiProviderConfig.decode(config.encode())
        assertEquals("custom", decoded.selectedId)
        assertEquals("model-a", decoded.modelFor("openai"))
        assertEquals("model-b", decoded.modelFor("deepseek"))
        assertEquals("custom-model", decoded.modelFor("custom"))
        assertEquals(config.customUrl, decoded.urlFor("custom"))
        assertFalse(config.encode().contains("apiKey"))
    }

    @Test fun probeBodyContainsNoWorkbookOrSecret() {
        val body = AiChatCompletionsClient.buildConnectionTestRequestBody("special\"model")
        assertTrue(body.contains("special\\\"model"))
        assertTrue(body.contains("Reply with OK."))
        assertFalse(body.contains("course"))
        assertFalse(body.contains("secret"))
    }

    @Test fun probeResponseClassificationRequiresNonEmptyValidContent() {
        val valid = """{"choices":[{"finish_reason":"stop","message":{"content":"OK"}}]}""".toByteArray()
        val empty = """{"choices":[{"finish_reason":"stop","message":{"content":" "}}]}""".toByteArray()
        assertNull(AiChatCompletionsClient.classifyConnectionResponse(200, valid))
        assertEquals(AiConnectionFailure.RESPONSE, AiChatCompletionsClient.classifyConnectionResponse(200, empty))
        assertEquals(AiConnectionFailure.AUTH, AiChatCompletionsClient.classifyConnectionResponse(401, byteArrayOf()))
        assertEquals(AiConnectionFailure.MODEL_OR_URL, AiChatCompletionsClient.classifyConnectionResponse(404, byteArrayOf()))
        assertEquals(AiConnectionFailure.CONFIG, AiChatCompletionsClient.classifyConnectionResponse(400, byteArrayOf()))
        assertEquals(AiConnectionFailure.RATE_LIMIT, AiChatCompletionsClient.classifyConnectionResponse(429, byteArrayOf()))
        assertEquals(AiConnectionFailure.RESPONSE, AiChatCompletionsClient.classifyConnectionResponse(200, "not json".toByteArray()))
    }
}
