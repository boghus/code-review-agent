package com.boghus.codereview.fixtures.idempotency

/**
 * E2E fixture for the `idempotency` scenario.
 *
 * The scenario re-runs the action against this pull request through a
 * synchronize event and asserts that the existing comment is updated in place
 * instead of a second comment being published. This file therefore stays
 * trivial: the scenario checks comment identity, not findings.
 */
class IdempotencyFixture {

    /** Fixed label used by the fixture. */
    static final String LABEL = 'idempotency'

    /**
     * Returns the label.
     *
     * @return the label
     */
    String label() {
        return LABEL
    }
}
