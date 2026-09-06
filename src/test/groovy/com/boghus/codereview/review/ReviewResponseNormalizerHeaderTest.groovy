package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerHeaderTest {
    @Test
    void 'strips provider preamble'() {
        String result = ReviewResponseNormalizer.normalize('Preamble\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK')
        assertThat(result).startsWith(ReviewResponseNormalizer.REVIEW_HEADER)
    }
}
