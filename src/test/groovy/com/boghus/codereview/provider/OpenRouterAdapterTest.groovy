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
        assertThat(payload.max_completion_tokens).isEqualTo(OpenRouterAdapter.MAX_OUTPUT_TOKENS)
        assertThat(payload).doesNotContainKey('max_tokens')
        assertThat(payload.response_format).isEqualTo([type: 'json_object'])
        assertThat(payload.provider).isEqualTo([require_parameters: true])
        assertThat(messages).hasSize(2)
        assertThat(messages[0].role).isEqualTo('system')
        assertThat(messages[0].content.toString()).isEqualTo('system rules\n\ndeveloper rules')
        assertThat(messages[1].role).isEqualTo('user')
        assertThat(messages[1].content.toString()).isEqualTo('review this diff\n\nuntrusted diff')
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
    void 'describes empty OpenRouter responses without exposing prompt or completion content'() {
        Map<String, Object> response = [
            id: 'gen-123',
            model: 'provider/model-free',
            choices: [[
                finish_reason: 'length',
                native_finish_reason: 'max_tokens',
                message: [role: 'assistant', content: null, refusal: null, tool_calls: []]
            ]]
        ]

        String diagnostics = OpenRouterAdapter.describeResponse(response, 'openrouter/free')

        assertThat(diagnostics).contains('requestId=gen-123')
        assertThat(diagnostics).contains('requestedModel=openrouter/free')
        assertThat(diagnostics).contains('returnedModel=provider/model-free')
        assertThat(diagnostics).contains('finishReason=length')
        assertThat(diagnostics).contains('nativeFinishReason=max_tokens')
        assertThat(diagnostics).contains('contentType=null')
        assertThat(diagnostics).doesNotContain('private prompt', 'secret completion')
    }

    @Test
    void 'includes token usage and reasoning tokens in empty response diagnostics'() {
        Map<String, Object> response = [
            usage: [
                prompt_tokens: 1200,
                completion_tokens: 8192,
                total_tokens: 9392,
                completion_tokens_details: [reasoning_tokens: 8192]
            ],
            choices: [[message: [content: ''], finish_reason: 'length']]
        ]

        String diagnostics = OpenRouterAdapter.describeResponse(response, 'openrouter/free')

        assertThat(diagnostics).contains('promptTokens=1200')
        assertThat(diagnostics).contains('completionTokens=8192')
        assertThat(diagnostics).contains('reasoningTokens=8192')
        assertThat(diagnostics).contains('totalTokens=9392')
    }

    @Test
    void 'handles missing token usage in response diagnostics'() {
        String diagnostics = OpenRouterAdapter.describeResponse([choices: []], 'openrouter/free')

        assertThat(diagnostics).contains('promptTokens=n/a')
        assertThat(diagnostics).contains('completionTokens=n/a')
        assertThat(diagnostics).contains('reasoningTokens=n/a')
        assertThat(diagnostics).contains('totalTokens=n/a')
    }

    @Test
    void 'describes provider errors returned inside successful response payloads'() {
        Map<String, Object> response = [
            id: 'gen-456',
            choices: [],
            error: [code: 502, message: 'upstream unavailable']
        ]

        String diagnostics = OpenRouterAdapter.describeResponse(response, 'openrouter/free')

        assertThat(diagnostics).contains('choiceCount=0')
        assertThat(diagnostics).contains('errorCode=502')
        assertThat(diagnostics).contains('errorMessage=upstream unavailable')
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

    @Test
    void 'classifies payment and rate limits as quota failures'() {
        assertThat(OpenRouterAdapter.translateStatus(402).category).isEqualTo(AiProviderErrorCategory.QUOTA)
        assertThat(OpenRouterAdapter.translateStatus(402).userMessage).contains('credits')
        assertThat(OpenRouterAdapter.translateStatus(429).category).isEqualTo(AiProviderErrorCategory.QUOTA)
    }

    @Test
    void 'classifies request timeouts as timeout failures'() {
        assertThat(OpenRouterAdapter.translateStatus(408).category).isEqualTo(AiProviderErrorCategory.TIMEOUT)
        assertThat(OpenRouterAdapter.translateStatus(504).category).isEqualTo(AiProviderErrorCategory.TIMEOUT)
    }
}
