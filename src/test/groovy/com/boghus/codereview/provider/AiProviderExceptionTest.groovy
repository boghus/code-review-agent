package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class AiProviderExceptionTest {

    @Test
    void 'exposes category and userMessage'() {
        Throwable cause = new RuntimeException('boom')
        AiProviderException ex = new AiProviderException(
            AiProviderException.CATEGORY_QUOTA,
            'Quota exhausted for **gemini**.',
            cause
        )

        assertThat(ex.category).isEqualTo(AiProviderException.CATEGORY_QUOTA)
        assertThat(ex.userMessage).isEqualTo('Quota exhausted for **gemini**.')
        assertThat(ex.cause).isSameAs(cause)
        assertThat(ex.message).isEqualTo('Quota exhausted for **gemini**.')
    }

    @Test
    void 'category constants are stable strings'() {
        // Other modules (or future log shippers) may switch on these.
        assertThat(AiProviderException.CATEGORY_QUOTA).isEqualTo('quota')
        assertThat(AiProviderException.CATEGORY_AUTH).isEqualTo('auth')
        assertThat(AiProviderException.CATEGORY_UNKNOWN).isEqualTo('unknown')
    }
}
