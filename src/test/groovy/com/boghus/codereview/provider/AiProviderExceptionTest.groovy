package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class AiProviderExceptionTest {

    @Test
    void 'exposes typed category and userMessage'() {
        Throwable cause = new RuntimeException('boom')
        AiProviderException ex = new AiProviderException(
            AiProviderErrorCategory.QUOTA,
            'Quota exhausted for **gemini**.',
            cause
        )

        assertThat(ex.category).isEqualTo(AiProviderErrorCategory.QUOTA)
        assertThat(ex.userMessage).isEqualTo('Quota exhausted for **gemini**.')
        assertThat(ex.cause).isSameAs(cause)
        assertThat(ex.message).isEqualTo('Quota exhausted for **gemini**.')
    }

    @Test
    void 'supports all provider error categories'() {
        assertThat(AiProviderErrorCategory.values()).containsExactly(
            AiProviderErrorCategory.AUTHENTICATION,
            AiProviderErrorCategory.QUOTA,
            AiProviderErrorCategory.TIMEOUT,
            AiProviderErrorCategory.NETWORK,
            AiProviderErrorCategory.INVALID_REQUEST,
            AiProviderErrorCategory.UNKNOWN
        )
    }
}
