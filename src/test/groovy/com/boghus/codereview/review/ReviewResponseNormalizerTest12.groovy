package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest12 {
    @Test
    void 'keeps header and body'() {
        String response = 'prefix\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .isEqualTo('## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK')
    }
}
