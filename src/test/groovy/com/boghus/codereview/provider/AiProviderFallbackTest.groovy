package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class AiProviderFallbackTest {

    static class FakeProvider implements AiProvider {
        final AiProviderType providerType
        final String response
        final AiProviderException failure
        int calls

        FakeProvider(AiProviderType providerType, String response = 'ok', AiProviderException failure = null) {
            this.providerType = providerType
            this.response = response
            this.failure = failure
        }

        @Override
        AiProviderType type() { providerType }

        @Override
        AiProviderCapabilities capabilities() { AiProviderCapabilities.PROMPT_ONLY }

        @Override
        String review(ReviewRequest request) {
            calls++
            if (failure != null) throw failure
            return response
        }
    }

    private static ReviewRequest request() {
        new ReviewRequest('system', 'developer', 'prompt', 'repository content')
    }

    @Test
    void 'uses primary provider when it succeeds'() {
        FakeProvider primary = new FakeProvider(AiProviderType.GEMINI, 'primary response')
        FakeProvider backup = new FakeProvider(AiProviderType.OPENROUTER, 'backup response')

        AiProviderFallback.ReviewResult result = AiProviderFallback.review(primary, backup, request())

        assertThat(result.response).isEqualTo('primary response')
        assertThat(result.provider).isSameAs(primary)
        assertThat(result.fallbackUsed).isFalse()
        assertThat(primary.calls).isEqualTo(1)
        assertThat(backup.calls).isZero()
    }

    @Test
    void 'uses backup when primary fails due to a retryable availability category'() {
        for (AiProviderErrorCategory category : [
            AiProviderErrorCategory.QUOTA,
            AiProviderErrorCategory.TIMEOUT,
            AiProviderErrorCategory.NETWORK,
            AiProviderErrorCategory.UNAVAILABLE
        ]) {
            FakeProvider primary = new FakeProvider(
                AiProviderType.GEMINI,
                'unused',
                new AiProviderException(category, 'temporary failure')
            )
            FakeProvider backup = new FakeProvider(AiProviderType.OPENROUTER, 'backup response')

            AiProviderFallback.ReviewResult result = AiProviderFallback.review(primary, backup, request())

            assertThat(result.response).isEqualTo('backup response')
            assertThat(result.provider).isSameAs(backup)
            assertThat(result.fallbackUsed).isTrue()
            assertThat(primary.calls).isEqualTo(1)
            assertThat(backup.calls).isEqualTo(1)
        }
    }

    @Test
    void 'does not use backup for authentication invalid request or unknown failures'() {
        for (AiProviderErrorCategory category : [
            AiProviderErrorCategory.AUTHENTICATION,
            AiProviderErrorCategory.INVALID_REQUEST,
            AiProviderErrorCategory.UNKNOWN
        ]) {
            FakeProvider primary = new FakeProvider(
                AiProviderType.GEMINI,
                'unused',
                new AiProviderException(category, 'non-retryable')
            )
            FakeProvider backup = new FakeProvider(AiProviderType.OPENROUTER, 'backup response')

            assertThatThrownBy { AiProviderFallback.review(primary, backup, request()) }
                .isInstanceOf(AiProviderException.class)
            assertThat(backup.calls).isZero()
        }
    }

    @Test
    void 'does not use backup when it is not configured'() {
        FakeProvider primary = new FakeProvider(
            AiProviderType.GEMINI,
            'unused',
            new AiProviderException(AiProviderErrorCategory.TIMEOUT, 'timed out')
        )

        assertThatThrownBy { AiProviderFallback.review(primary, null, request()) }
            .isInstanceOf(AiProviderException.class)
        assertThat(primary.calls).isEqualTo(1)
    }

    @Test
    void 'keeps primary failure as suppressed context when backup also fails'() {
        AiProviderException primaryFailure = new AiProviderException(AiProviderErrorCategory.NETWORK, 'primary down')
        AiProviderException backupFailure = new AiProviderException(AiProviderErrorCategory.UNAVAILABLE, 'backup down')
        FakeProvider primary = new FakeProvider(AiProviderType.GEMINI, 'unused', primaryFailure)
        FakeProvider backup = new FakeProvider(AiProviderType.OPENROUTER, 'unused', backupFailure)

        assertThatThrownBy { AiProviderFallback.review(primary, backup, request()) }
            .isSameAs(backupFailure)
        assertThat(backupFailure.suppressed).contains(primaryFailure)
    }
}
