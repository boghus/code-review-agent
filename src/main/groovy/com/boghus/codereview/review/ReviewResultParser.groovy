package com.boghus.codereview.review

import groovy.transform.CompileStatic

import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
class ReviewResultParser {

    private static final Pattern FINDING_HEADER =
        Pattern.compile('(?m)^###\\s+\\[(CRITICAL|HIGH|MEDIUM|LOW)]\\s+(.+?)\\s*$')

    private static final Pattern FIELD =
        Pattern.compile('^\\s*(?:-\\s*)?\\*\\*(File|Lines|Problem|Impact|Suggested fix|Evidence|Verification):\\*\\*\\s*(.*)$')

    private static final Pattern NO_FINDINGS =
        Pattern.compile('(?i)^(?:no findings(?: were (?:detected|found))?|no issues(?: were (?:detected|found))?|no problems(?: were (?:detected|found))?|sin hallazgos|no se detectaron problemas|no encontramos problemas)[.!]?\\s*

    ReviewResult parse(String markdown) {
        String source = markdown?.trim() ?: ''
        Matcher headerMatcher = FINDING_HEADER.matcher(source)
        List<Finding> findings = []

        String currentSeverity = null
        String currentTitle = null
        int currentEnd = -1

        while (headerMatcher.find()) {
            if (currentEnd >= 0) {
                String block = source.substring(currentEnd, headerMatcher.start()).trim()
                findings << parseFinding(currentSeverity, currentTitle, block)
            }

            currentSeverity = headerMatcher.group(1)
            currentTitle = headerMatcher.group(2).trim()
            currentEnd = headerMatcher.end()
        }

        if (currentEnd >= 0) {
            findings << parseFinding(currentSeverity, currentTitle, source.substring(currentEnd).trim())
        }

        if (!findings.isEmpty()) {
            return new ReviewResult(findings)
        }

        return new ReviewResult([], NO_FINDINGS.matcher(source).matches())
    }

    private static Finding parseFinding(String severity, String title, String block) {
        Map<String, String> fields = fields(block)

        String file = fields['File'] ?: 'Unknown file'
        String lines = fields['Lines']
        String problem = fields['Problem'] ?: 'No problem description provided.'
        String impact = fields['Impact'] ?: 'Impact not specified.'
        String suggestedFix = fields['Suggested fix'] ?: 'No suggested fix provided.'
        String evidence = fields['Evidence'] ?: 'Evidence was not provided.'
        boolean verified = 'Verified'.equalsIgnoreCase(fields['Verification']?.trim())

        return new Finding(severity, title, file, lines, problem, impact, suggestedFix, evidence, verified)
    }

    private static Map<String, String> fields(String block) {
        Map<String, String> values = [:]
        String currentField = null

        block.split('\\r?\\n').each { String line ->
            Matcher matcher = FIELD.matcher(line)
            if (matcher.matches()) {
                currentField = matcher.group(1)
                values[currentField] = matcher.group(2).trim()
            } else if (currentField && line.trim()) {
                values[currentField] = values[currentField] + '\\n' + line.trim()
            }
        }

        values
    }
}
)

    ReviewResult parse(String markdown) {
        String source = markdown?.trim() ?: ''
        Matcher headerMatcher = FINDING_HEADER.matcher(source)
        List<Finding> findings = []

        String currentSeverity = null
        String currentTitle = null
        int currentEnd = -1

        while (headerMatcher.find()) {
            if (currentEnd >= 0) {
                String block = source.substring(currentEnd, headerMatcher.start()).trim()
                findings << parseFinding(currentSeverity, currentTitle, block)
            }

            currentSeverity = headerMatcher.group(1)
            currentTitle = headerMatcher.group(2).trim()
            currentEnd = headerMatcher.end()
        }

        if (currentEnd >= 0) {
            findings << parseFinding(currentSeverity, currentTitle, source.substring(currentEnd).trim())
        }

        if (!findings.isEmpty()) {
            return new ReviewResult(findings)
        }

        return new ReviewResult([], NO_FINDINGS.matcher(source).matches())
    }

    private static Finding parseFinding(String severity, String title, String block) {
        Map<String, String> fields = fields(block)

        String file = fields['File'] ?: 'Unknown file'
        String lines = fields['Lines']
        String problem = fields['Problem'] ?: 'No problem description provided.'
        String impact = fields['Impact'] ?: 'Impact not specified.'
        String suggestedFix = fields['Suggested fix'] ?: 'No suggested fix provided.'
        String evidence = fields['Evidence'] ?: 'Evidence was not provided.'
        boolean verified = 'Verified'.equalsIgnoreCase(fields['Verification']?.trim())

        return new Finding(severity, title, file, lines, problem, impact, suggestedFix, evidence, verified)
    }

    private static Map<String, String> fields(String block) {
        Map<String, String> values = [:]
        String currentField = null

        block.split('\\r?\\n').each { String line ->
            Matcher matcher = FIELD.matcher(line)
            if (matcher.matches()) {
                currentField = matcher.group(1)
                values[currentField] = matcher.group(2).trim()
            } else if (currentField && line.trim()) {
                values[currentField] = values[currentField] + '\\n' + line.trim()
            }
        }

        values
    }
}
