package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest11 {
    @Test
    void 'returns null unchanged'() {
        assertThat(ReviewResponseNormalizer.normalize(null)).isNull()
    }
}
