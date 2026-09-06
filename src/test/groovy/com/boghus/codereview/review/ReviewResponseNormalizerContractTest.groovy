package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerContractTest {
    @Test
    void 'normalized content starts at canonical review header'() {
        String response = 'Provider preamble\n\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .startsWith(ReviewResponseNormalizer.REVIEW_HEADER)
    }
}
