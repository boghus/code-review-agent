package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest23 {
    @Test
    void 'normalizes provider preamble to canonical header'() {
        String response = 'prefix\n## 🤖 Code Review Agent by boghus\nbody'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .startsWith(ReviewResponseNormalizer.REVIEW_HEADER)
    }
}
