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
            .contains('could not complete')
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
    void 'safeRuntimeMessageForLog includes exception chain details'() {
        RuntimeException cause = new RuntimeException('HTTP request failed: connection reset')
        RuntimeException exception = new RuntimeException('Failed to execute HTTP request.', cause)

        String logSafe = GeminiAdapter.safeRuntimeMessageForLog(exception)

        assertThat(logSafe)
            .contains('RuntimeException: Failed to execute HTTP request.')
            .contains('RuntimeException: HTTP request failed: connection reset')
            .doesNotContain('`')
    }

    @Test
    void 'safeRuntimeMessageForLog handles null and limits the cause chain'() {
        assertThat(GeminiAdapter.safeRuntimeMessageForLog(null)).isEqualTo('unknown error')

        Throwable deepest = new RuntimeException('level 6')
        Throwable level5 = new RuntimeException('level 5', deepest)
        Throwable level4 = new RuntimeException('level 4', level5)
        Throwable level3 = new RuntimeException('level 3', level4)
        Throwable level2 = new RuntimeException('level 2', level3)
        Throwable level1 = new RuntimeException('level 1', level2)

        String logSafe = GeminiAdapter.safeRuntimeMessageForLog(level1)

        assertThat(logSafe)
            .contains('level 1')
            .contains('level 5')
            .doesNotContain('level 6')
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
