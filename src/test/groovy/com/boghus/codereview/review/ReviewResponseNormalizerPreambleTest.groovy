package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerPreambleTest {
    @Test
    void 'removes provider preamble'() {
        String response = 'Preamble\n\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .isEqualTo('## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK')
    }
}
