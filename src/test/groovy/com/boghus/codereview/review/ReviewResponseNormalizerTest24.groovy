package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest24 {
    @Test
    void 'handles empty response'() {
        assertThat(ReviewResponseNormalizer.normalize('')).isEmpty()
    }
}
