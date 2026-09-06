package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class RuntimeErrorSanitizerTest {

    @Test
    void 'preserves useful diagnostic context'() {
        String result = RuntimeErrorSanitizer.sanitize(
            new RuntimeException('Gemini request failed with HTTP 429 while processing review')
        )

        assertThat(result)
            .contains('RuntimeException')
            .contains('HTTP 429')
            .contains('processing review')
    }

    @Test
    void 'redacts common credential values'() {
        String result = RuntimeErrorSanitizer.sanitize(
            new RuntimeException('request failed apiKey=secret123 token=token456 password="hunter2" secret=topsecret')
        )

        assertThat(result)
            .contains('apiKey=[REDACTED]')
            .contains('token=[REDACTED]')
            .contains('password=[REDACTED]')
            .contains('secret=[REDACTED]')
            .doesNotContain('secret123')
            .doesNotContain('token456')
            .doesNotContain('hunter2')
            .doesNotContain('topsecret')
    }

    @Test
    void 'redacts bearer authorization values'() {
        String result = RuntimeErrorSanitizer.sanitize(
            new RuntimeException('HTTP 401 Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.secret.signature')
        )

        assertThat(result)
            .contains('Authorization: Bearer [REDACTED]')
            .doesNotContain('eyJhbGciOiJIUzI1NiJ9')
    }

    @Test
    void 'redacts prompt values'() {
        String result = RuntimeErrorSanitizer.sanitize(
            new RuntimeException('provider failed prompt=Review this private repository content: customer data')
        )

        assertThat(result)
            .contains('prompt [REDACTED]')
            .doesNotContain('Review this private repository content')
            .doesNotContain('customer data')
    }

    @Test
    void 'redacts sensitive values in nested causes'() {
        Throwable cause = new IllegalStateException('provider token=inner-secret')
        Throwable exception = new RuntimeException('review failed', cause)

        String result = RuntimeErrorSanitizer.sanitize(exception)

        assertThat(result)
            .contains('RuntimeException: review failed')
            .contains('IllegalStateException: provider token=[REDACTED]')
            .doesNotContain('inner-secret')
    }

    @Test
    void 'handles null and empty messages safely'() {
        assertThat(RuntimeErrorSanitizer.sanitize((Throwable) null)).isEqualTo('unknown error')
        assertThat(RuntimeErrorSanitizer.sanitize('')).isEmpty()
    }
}
