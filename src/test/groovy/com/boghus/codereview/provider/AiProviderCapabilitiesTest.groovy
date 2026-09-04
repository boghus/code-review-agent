package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class AiProviderCapabilitiesTest {

    @Test
    void 'prompt-only capability disables stronger channels'() {
        AiProviderCapabilities capabilities = AiProviderCapabilities.PROMPT_ONLY

        assertThat(capabilities.prompt).isTrue()
        assertThat(capabilities.system).isFalse()
        assertThat(capabilities.developer).isFalse()
    }

    @Test
    void 'system prompt capability enables system instructions without developer channel'() {
        AiProviderCapabilities capabilities = AiProviderCapabilities.SYSTEM_PROMPT

        assertThat(capabilities.prompt).isTrue()
        assertThat(capabilities.system).isTrue()
        assertThat(capabilities.developer).isFalse()
    }

    @Test
    void 'system developer prompt capability enables all channels'() {
        AiProviderCapabilities capabilities = AiProviderCapabilities.SYSTEM_DEVELOPER_PROMPT

        assertThat(capabilities.prompt).isTrue()
        assertThat(capabilities.system).isTrue()
        assertThat(capabilities.developer).isTrue()
    }
}
