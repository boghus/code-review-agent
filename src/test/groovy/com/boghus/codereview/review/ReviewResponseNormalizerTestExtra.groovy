package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTestExtra {
    @Test
    void 'preserves content after header'() {
        String response = 'prefix\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .isEqualTo('## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK')
    }
}
