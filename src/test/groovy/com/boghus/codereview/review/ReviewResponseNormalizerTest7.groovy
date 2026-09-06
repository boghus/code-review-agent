package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerTest7 {
    @Test
    void 'rejects invalid response'() {
        assertThatThrownBy { ReviewResponseNormalizer.normalize('invalid') }
            .isInstanceOf(IllegalArgumentException)
    }
}
