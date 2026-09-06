package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest26 {
    @Test
    void 'keeps response beginning with canonical header'() {
        String response = '## 🤖 Code Review Agent by boghus\nbody'
        assertThat(ReviewResponseNormalizer.normalize(response)).isEqualTo(response)
    }
}
