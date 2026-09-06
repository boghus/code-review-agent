package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest25 {
    @Test
    void 'normalizes preamble and trims response'() {
        assertThat(ReviewResponseNormalizer.normalize('prefix\n## 🤖 Code Review Agent by boghus\nbody\n'))
            .isEqualTo('## 🤖 Code Review Agent by boghus\nbody')
    }
}
