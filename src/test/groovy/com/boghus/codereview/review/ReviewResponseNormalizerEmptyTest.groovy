package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerEmptyTest {
    @Test
    void 'keeps empty response empty'() {
        assertThat(ReviewResponseNormalizer.normalize('')).isEmpty()
    }
}
