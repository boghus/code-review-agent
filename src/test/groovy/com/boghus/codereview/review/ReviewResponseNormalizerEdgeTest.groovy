package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerEdgeTest {
    @Test
    void 'accepts empty response without throwing'() {
        assertThat(ReviewResponseNormalizer.normalize('')).isEmpty()
    }
}
