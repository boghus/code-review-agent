package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerIntegrationTest {
    @Test
    void 'removes provider preamble before publication'() {
        String response = 'Analysis before review\n\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK\n'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .startsWith('## 🤖 Code Review Agent by boghus')
            .doesNotContain('Analysis before review')
    }
}
