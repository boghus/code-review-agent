package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class AiProviderTypeTest {

    @Test
    void 'resolves provider from configuration name'() {
        assertThat(AiProviderType.fromConfigName('gemini')).isEqualTo(AiProviderType.GEMINI)
    }

    @Test
    void 'resolves provider names case insensitively and trims whitespace'() {
        assertThat(AiProviderType.fromConfigName(' GEMINI ')).isEqualTo(AiProviderType.GEMINI)
    }

    @Test
    void 'rejects blank provider name'() {
        assertThatThrownBy { AiProviderType.fromConfigName('') }
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining('must not be blank')
    }

    @Test
    void 'rejects unsupported provider name'() {
        assertThatThrownBy { AiProviderType.fromConfigName('openai') }
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining('openai')
            .hasMessageContaining('gemini')
    }
}
