package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest17 {
    @Test
    void 'normalizes header occurrence'() {
        String response = 'prefix\n## 🤖 Code Review Agent by boghus\nbody'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .startsWith('## 🤖 Code Review Agent by boghus')
    }
}
