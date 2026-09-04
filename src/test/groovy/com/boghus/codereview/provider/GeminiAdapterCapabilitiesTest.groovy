package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class GeminiAdapterCapabilitiesTest {

    @Test
    void 'Gemini exposes the system prompt capability without a separate developer channel'() {
        GeminiAdapter adapter = new GeminiAdapter('test-key', 'test-model')

        assertThat(adapter.capabilities()).isEqualTo(AiProviderCapabilities.SYSTEM_PROMPT)
    }
}
