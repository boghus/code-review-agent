package com.boghus.codereview.fixtures.clean

/**
 * E2E fixture for the `clean` scenario.
 *
 * This file is intentionally trivial. The scenario asserts that the reviewer
 * reports zero findings, so nothing here may look like a defect: no secrets,
 * no logic errors, no duplication and no missing tests worth reporting.
 */
class CleanFixture {

    /** Fixed greeting used by the fixture. */
    static final String GREETING = 'hello'

    /**
     * Returns a greeting.
     *
     * @param name the name to greet
     * @return the greeting for {@code name}
     */
    String greet(String name) {
        return "${GREETING}, ${name}"
    }
}