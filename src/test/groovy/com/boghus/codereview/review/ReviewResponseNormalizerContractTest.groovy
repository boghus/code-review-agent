package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerContractTest {
    @Test
    void 'normalization returns content beginning at the canonical header'() {
        String response = 'Provider preamble\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .startsWith(ReviewResponseNormalizer.REVIEW_HEADER)
    }
}
