package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseNormalizerTest16 {
    @Test
    void 'handles null response'() {
        assertThat(ReviewResponseNormalizer.normalize(null)).isNull()
    }
}
