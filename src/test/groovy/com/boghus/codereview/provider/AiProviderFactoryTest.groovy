package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class AiProviderFactoryTest {

    @Test
    void 'creates a GeminiAdapter when provider is gemini'() {
        AiProvider provider = AiProviderFactory.create('gemini', 'k', 'm')

        assertThat(provider).isInstanceOf(GeminiAdapter)
        assertThat(provider.type()).isEqualTo(AiProviderType.GEMINI)
    }

    @Test
    void 'creates a GeminiAdapter from the provider enum'() {
        AiProvider provider = AiProviderFactory.create(AiProviderType.GEMINI, 'k', 'm')

        assertThat(provider).isInstanceOf(GeminiAdapter)
        assertThat(provider.type()).isEqualTo(AiProviderType.GEMINI)
    }

    @Test
    void 'accepts provider names case insensitively'() {
        AiProvider provider = AiProviderFactory.create(' GEMINI ', 'k', 'm')

        assertThat(provider.type()).isEqualTo(AiProviderType.GEMINI)
    }

    @Test
    void 'rejects unknown provider'() {
        assertThatThrownBy { AiProviderFactory.create('openai', 'k', 'm') }
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining('openai')
    }

    @Test
    void 'rejects blank provider name'() {
        assertThatThrownBy { AiProviderFactory.create('', 'k', 'm') }
            .isInstanceOf(IllegalArgumentException.class)
    }

    @Test
    void 'supportedProviders exposes registered names'() {
        assertThat(AiProviderFactory.supportedProviders()).contains('gemini')
    }
}
