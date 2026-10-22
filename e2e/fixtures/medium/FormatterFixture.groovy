package com.boghus.codereview.fixtures.medium

/**
 * E2E fixture for the `medium` scenario.
 *
 * This file deliberately duplicates logic and omits tests so the reviewer must
 * report MEDIUM findings about maintainability rather than correctness.
 */
class FormatterFixture {

    /**
     * Formats a label.
     *
     * Duplicated in {@code labelB}; the two copies must stay in sync.
     *
     * @param value the value to format
     * @return the formatted label
     */
    String labelA(String value) {
        if (value == null || value.isEmpty()) {
            return 'n/a'
        }
        return "[${value}]"
    }

    /**
     * Formats a label.
     *
     * Identical to {@code labelA} with no shared helper.
     *
     * @param value the value to format
     * @return the formatted label
     */
    String labelB(String value) {
        if (value == null || value.isEmpty()) {
            return 'n/a'
        }
        return "[${value}]"
    }
}