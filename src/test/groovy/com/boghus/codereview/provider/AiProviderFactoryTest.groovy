package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class AiProviderFactoryTest {

    @Test
    void 'creates a GeminiAdapter when provider is gemini'() {
        AiProvider provider = AiProviderFactory.create('gemini', 'k', 'm')

        assertThat(provider).isInstanceOf(GeminiAdapter)
        assertThat(provider.name()).isEqualTo('gemini')
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
