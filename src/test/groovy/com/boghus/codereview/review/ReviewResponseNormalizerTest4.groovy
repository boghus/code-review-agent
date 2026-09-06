package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest4 {
    @Test
    void 'trims normalized response'() {
        String response = 'prefix\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK\n'
        assertThat(ReviewResponseNormalizer.normalize(response)).doesNotEndWith('\n')
    }
}
