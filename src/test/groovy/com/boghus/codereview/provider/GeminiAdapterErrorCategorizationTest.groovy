package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

/**
 * Tests for the translation layer inside GeminiAdapter: SDK-specific
 * failures and empty responses must surface as {@link AiProviderException}
 * with a user-safe message and a stable category, so the orchestrator
 * never has to know about the underlying SDK.
 */
class GeminiAdapterErrorCategorizationTest {

    @Test
    void 'ApiException with 429 yields a quota-category provider exception'() {
        // We cannot easily inject a fake ApiException into review() without a
        // real Client, so we exercise the static categorization shim and
        // assert the same string shape that review() would produce.
        String category = GeminiAdapter.categorizeError(
            new com.google.genai.errors.ApiException(429, 'RESOURCE_EXHAUSTED', 'quota'),
            'gemini-2.5-flash'
        )
        assertThat(category)
            .contains('Quota exhausted')
            .contains('gemini-2.5-flash')
    }

    @Test
    void 'ApiException with 401 yields an auth-category provider exception'() {
        String category = GeminiAdapter.categorizeError(
            new com.google.genai.errors.ApiException(401, 'UNAUTHENTICATED', 'bad key'),
            'gemini-2.5-flash'
        )
        assertThat(category)
            .contains('Authentication failed')
            .contains('api-key')
    }

    @Test
    void 'ApiException with 403 yields an auth-category provider exception'() {
        String category = GeminiAdapter.categorizeError(
            new com.google.genai.errors.ApiException(403, 'PERMISSION_DENIED', 'forbidden'),
            'gemini-2.5-flash'
        )
        assertThat(category).contains('Authentication failed')
    }

    @Test
    void 'ApiException with unknown status yields a generic-category message'() {
        String category = GeminiAdapter.categorizeError(
            new com.google.genai.errors.ApiException(500, 'INTERNAL', 'very specific internal detail: account=foo'),
            'gemini-2.5-flash'
        )
        assertThat(category)
            .doesNotContain('account=foo')
            .doesNotContain('very specific internal detail')
            .contains('temporarily unavailable')
    }

    @Test
    void 'classifies timeout and network causes for fallback'() {
        assertThat(GeminiAdapter.categorizeRuntimeException(
            new RuntimeException(new java.net.SocketTimeoutException('timed out'))
        )).isEqualTo(AiProviderErrorCategory.TIMEOUT)
        assertThat(GeminiAdapter.categorizeRuntimeException(
            new RuntimeException(new java.io.IOException('connection reset'))
        )).isEqualTo(AiProviderErrorCategory.NETWORK)
        assertThat(GeminiAdapter.categorizeRuntimeException(
            new RuntimeException('unexpected')
        )).isEqualTo(AiProviderErrorCategory.UNKNOWN)
    }

    @Test
    void 'does not classify JSON processing failures as network errors'() {
        com.fasterxml.jackson.core.JsonProcessingException invalidJson =
            new com.fasterxml.jackson.core.JsonProcessingException('invalid JSON') {}
        assertThat(GeminiAdapter.categorizeRuntimeException(new RuntimeException(invalidJson)))
            .isEqualTo(AiProviderErrorCategory.UNKNOWN)
    }

    @Test
    void 'safeMessageForLog strips backticks but keeps detail'() {
        String logSafe = GeminiAdapter.safeMessageForLog(
            new com.google.genai.errors.ApiException(500, 'INTERNAL', 'oops `weird` formatting')
        )
        assertThat(logSafe)
            .contains("oops 'weird' formatting")
            .doesNotContain('`')
    }

    @Test
    void 'safeMessageForLog handles empty formatted message'() {
        // ApiException.getMessage() returns the formatted "code status. message"
        // string, not the raw input. Passing an empty inner message produces
        // a formatted string that still has content ("500 INTERNAL. "). After
        // backtick stripping the result must not contain backticks.
        String logSafe = GeminiAdapter.safeMessageForLog(
            new com.google.genai.errors.ApiException(500, 'INTERNAL', '')
        )
        assertThat(logSafe)
            .doesNotContain('`')
            .startsWith('500')
    }

    @Test
    void 'AiProviderException carries typed category, user message and cause'() {
        RuntimeException cause = new RuntimeException('inner')
        AiProviderException ex = new AiProviderException(
            AiProviderErrorCategory.QUOTA,
            'Quota exhausted',
            cause
        )
        assertThat(ex.category).isEqualTo(AiProviderErrorCategory.QUOTA)
        assertThat(ex.userMessage).isEqualTo('Quota exhausted')
        assertThat(ex.getCause()).isSameAs(cause)
    }
}
