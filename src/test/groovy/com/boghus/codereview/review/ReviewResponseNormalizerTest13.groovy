package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest13 {
    @Test
    void 'does not alter content after header'() {
        assertThat(ReviewResponseNormalizer.normalize('x\n## 🤖 Code Review Agent by boghus\nbody'))
            .contains('body')
    }
}
