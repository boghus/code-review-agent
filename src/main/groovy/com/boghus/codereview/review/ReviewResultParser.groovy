package com.boghus.codereview.review

import groovy.transform.CompileStatic

import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
class ReviewResultParser {

    private static final Pattern FINDING_HEADER =
        Pattern.compile('(?m)^###\\s+\\[(CRITICAL|HIGH|MEDIUM|LOW)]\\s+(.+?)\\s*$')

    ReviewResult parse(String markdown) {
        String source = markdown ?: ''
        Matcher matcher = FINDING_HEADER.matcher(source)
        List<Finding> findings = []

        while (matcher.find()) {
            int start = matcher.start()
            int end = matcher.end()
            int next = matcher.find() ? matcher.start() : source.length()
            matcher = FINDING_HEADER.matcher(source)
            matcher.find(start)
            String severity = matcher.group(1)
            String title = matcher.group(2).trim()

            String block = source.substring(end, next).trim()
            findings << parseFinding(severity, title, block)
        }

        return new ReviewResult(findings)
    }

    private static Finding parseFinding(String severity, String title, String block) {
        String file = value(block, 'File') ?: 'Unknown file'
        String lines = value(block, 'Lines')
        String problem = value(block, 'Problem') ?: 'No problem description provided.'
        String impact = value(block, 'Impact') ?: 'Impact not specified.'
        String suggestedFix = value(block, 'Suggested fix') ?: 'No suggested fix provided.'
        String evidence = value(block, 'Evidence') ?: 'Evidence was not provided.'
        boolean verified = !'Unverified'.equalsIgnoreCase(value(block, 'Verification'))

        return new Finding(severity, title, file, lines, problem, impact, suggestedFix, evidence, verified)
    }

    private static String value(String block, String label) {
        Matcher matcher = Pattern.compile("(?ms)^\\s*\\*\\*${Pattern.quote(label)}:\\*\\*\\s*(.+?)(?=\\n\\s*\\*\\*[^*]+:\\*\\*|\\z)").matcher(block)
        return matcher.find() ? matcher.group(1).trim() : null
    }
}
