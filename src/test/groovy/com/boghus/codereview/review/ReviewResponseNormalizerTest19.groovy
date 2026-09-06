package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest19 {
    @Test
    void 'returns empty response'() {
        assertThat(ReviewResponseNormalizer.normalize('')).isEmpty()
    }
}
