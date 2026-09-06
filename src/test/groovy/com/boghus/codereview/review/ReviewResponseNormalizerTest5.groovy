package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest5 {
    @Test
    void 'keeps empty response unchanged'() {
        assertThat(ReviewResponseNormalizer.normalize('')).isEmpty()
    }
}
