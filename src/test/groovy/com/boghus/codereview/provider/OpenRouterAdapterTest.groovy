package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class OpenRouterAdapterTest {
    @Test
    void 'builds OpenAI-compatible chat messages with trusted instructions separated from repository content'() {
        ReviewRequest request = new ReviewRequest('system rules', 'developer rules', 'review this diff', 'untrusted diff')
        Map<String, Object> payload = OpenRouterAdapter.buildPayload(request, 'openrouter/free')
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.messages

        assertThat(payload.model).isEqualTo('openrouter/free')
        assertThat(payload.temperature).isEqualTo(0.1)
        assertThat(payload.max_tokens).isEqualTo(OpenRouterAdapter.MAX_OUTPUT_TOKENS)
        assertThat(messages).hasSize(2)
        assertThat(messages[0].role).isEqualTo('system')
        assertThat(messages[0].content).isEqualTo('system rules\n\ndeveloper rules')
        assertThat(messages[1].role).isEqualTo('user')
        assertThat(messages[1].content).isEqualTo('review this diff\n\nuntrusted diff')
    }

    @Test
    void 'extracts assistant content from chat completions response'() {
        Map<String, Object> response = [choices: [[message: [role: 'assistant', content: '{"findings":[]}']]]]
        assertThat(OpenRouterAdapter.extractContent(response)).isEqualTo('{"findings":[]}')
    }

    @Test
    void 'returns null for malformed or empty chat completions response'() {
        assertThat(OpenRouterAdapter.extractContent([choices: []])).isNull()
        assertThat(OpenRouterAdapter.extractContent([choices: [[message: [content: null]]]])).isNull()
        assertThat(OpenRouterAdapter.extractContent('not-json')).isNull()
    }

    @Test
    void 'rejects blank API key and model'() {
        assertThatThrownBy { new OpenRouterAdapter(' ', 'openrouter/free') }
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining('API key')
        assertThatThrownBy { new OpenRouterAdapter('key', ' ') }
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining('model')
    }
}
