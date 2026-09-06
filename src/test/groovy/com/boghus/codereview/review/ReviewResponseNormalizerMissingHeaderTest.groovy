package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerMissingHeaderTest {
    @Test
    void 'fails explicitly when header is missing'() {
        assertThatThrownBy { ReviewResponseNormalizer.normalize('random response') }
            .isInstanceOf(IllegalArgumentException)
    }
}
