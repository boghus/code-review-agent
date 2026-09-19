package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class FindingTest {

    @Test
    void 'defines the evidence and verification contract used by the review prompt'() {
        assertThat(Finding.PROMPT_CONTRACT)
            .contains('severity: CRITICAL, HIGH, MEDIUM, or LOW')
            .contains('evidence:')
            .contains('verified: true only when')
            .contains('external or changing information')
            .contains('set verified to false')
            .contains('Do not present that external claim as a fact')
            .contains('Never invent evidence or sources')
    }
}
