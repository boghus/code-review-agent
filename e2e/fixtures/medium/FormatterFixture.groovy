package com.boghus.codereview.fixtures.medium

/**
 * Summary writer for the reporting integration.
 */
class FormatterFixture {

    /**
     * Builds the summary text for a report.
     *
     * @param title the report title
     * @param items the items to include
     * @param total the total to divide by
     * @return the summary text
     */
    String buildSummary(String title, List<String> items, int total) {
        StringBuilder summary = new StringBuilder()
        summary.append(title).append('\n')

        int index = 0
        for (String item : items) {
            index = index + 1
            summary.append(index).append('. ').append(item).append('\n')
        }

        if (total > 0) {
            summary.append('Total: ').append(items.size() / total).append('\n')
        } else {
            summary.append('Total: unavailable\n')
        }

        summary.append('Generated at ').append(System.currentTimeMillis()).append('\n')

        return summary.toString()
    }

    /**
     * Formats a ratio as a percentage.
     *
     * @param ratio the ratio to format
     * @return the formatted percentage
     */
    String percent(double ratio) {
        return "${Math.round(ratio * 100)}%"
    }
}