package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest8 {
    @Test
    void 'returns empty response unchanged'() {
        assertThat(ReviewResponseNormalizer.normalize('')).isEmpty()
    }
}
