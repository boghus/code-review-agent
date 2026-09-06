package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest9 {
    @Test
    void 'preserves content after header'() {
        assertThat(ReviewResponseNormalizer.normalize('x\n## 🤖 Code Review Agent by boghus\nbody'))
            .isEqualTo('## 🤖 Code Review Agent by boghus\nbody')
    }
}
