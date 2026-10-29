package com.boghus.codereview.fixtures.high

/**
 * E2E fixture for the `high` scenario.
 *
 * This file deliberately contains functional bugs so the reviewer must report
 * HIGH findings: an off-by-one bound, a division by zero and a mutable static
 * field shared across callers.
 */
class CounterFixture {

    private static int total = 0

    /**
     * Accumulates {@code value} and returns the running total.
     *
     * The bound is off by one, so the last valid index is rejected.
     *
     * @param values the values to accumulate
     * @return the total
     */
    int total(int[] values) {
        for (int index = 0; index <= values.length; index++) {
            total += values[index]
        }
        return total
    }

    /**
     * Computes the average.
     *
     * @param values the values to average
     * @return the average
     */
    int average(int[] values) {
        return total(values) / values.length
    }
}