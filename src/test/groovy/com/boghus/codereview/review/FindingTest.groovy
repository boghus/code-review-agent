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
    @Test
    void 'stores finding evidence and verification state'() {
        Finding finding = new Finding(
            'MEDIUM',
            'External claim',
            'src/main.groovy',
            '10-12',
            'The claim cannot be verified from the diff.',
            'It may produce a false positive.',
            'Verify the claim before acting.',
            'No verified external source was supplied.',
            false
        )

        assertThat(finding.severity).isEqualTo('MEDIUM')
        assertThat(finding.evidence).contains('No verified external source')
        assertThat(finding.verified).isFalse()
    }

}

