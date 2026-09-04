package com.boghus.codereview.provider

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewRequestTest {

    @Test
    void 'preserves empty values when they are intentionally empty'() {
        ReviewRequest request = new ReviewRequest('', '', '', '')

        assertThat(request.systemInstructions).isEmpty()
        assertThat(request.developerInstructions).isEmpty()
        assertThat(request.prompt).isEmpty()
        assertThat(request.untrustedRepositoryContent).isEmpty()
    }

    @Test
    void 'rejects null fields at construction time'() {
        assertThatThrownBy {
            new ReviewRequest(null, 'developer', 'prompt', 'diff')
        }
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review request field 'systemInstructions' must not be null.")

        assertThatThrownBy {
            new ReviewRequest('system', null, 'prompt', 'diff')
        }
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review request field 'developerInstructions' must not be null.")

        assertThatThrownBy {
            new ReviewRequest('system', 'developer', null, 'diff')
        }
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review request field 'prompt' must not be null.")

        assertThatThrownBy {
            new ReviewRequest('system', 'developer', 'prompt', null)
        }
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review request field 'untrustedRepositoryContent' must not be null.")
    }
}
