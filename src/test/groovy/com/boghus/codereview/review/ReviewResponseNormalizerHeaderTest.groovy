package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerHeaderTest {
    @Test
    void 'normalizes content from canonical header'() {
        String response = 'Preamble\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .startsWith(ReviewResponseNormalizer.REVIEW_HEADER)
    }
}
