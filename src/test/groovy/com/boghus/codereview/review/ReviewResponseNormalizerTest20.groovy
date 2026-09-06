package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest20 {
    @Test
    void 'trims normalized response'() {
        assertThat(ReviewResponseNormalizer.normalize('x\n## 🤖 Code Review Agent by boghus\nbody\n'))
            .isEqualTo('## 🤖 Code Review Agent by boghus\nbody')
    }
}
