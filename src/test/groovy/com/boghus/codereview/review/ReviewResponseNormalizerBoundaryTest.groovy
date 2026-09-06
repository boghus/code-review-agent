package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerBoundaryTest {
    @Test
    void 'preserves review content after header'() {
        String response = 'x\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .isEqualTo('## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK')
    }
}
