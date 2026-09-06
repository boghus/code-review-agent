package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest15 {
    @Test
    void 'trims provider whitespace around normalized content'() {
        String response = '  prefix\n## 🤖 Code Review Agent by boghus\nbody  '
        assertThat(ReviewResponseNormalizer.normalize(response))
            .isEqualTo('## 🤖 Code Review Agent by boghus\nbody')
    }
}
