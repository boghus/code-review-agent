package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest21 {
    @Test
    void 'keeps canonical header when already present'() {
        String response = '## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK'
        assertThat(ReviewResponseNormalizer.normalize(response)).isEqualTo(response)
    }
}
