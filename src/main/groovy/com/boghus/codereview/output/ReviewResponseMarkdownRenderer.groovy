package com.boghus.codereview.output

import com.boghus.codereview.review.ReviewFinding
import com.boghus.codereview.review.ReviewLanguage
import com.boghus.codereview.review.ReviewResponse
import com.boghus.codereview.review.ReviewSeverity
import groovy.transform.CompileStatic

@CompileStatic
class ReviewResponseMarkdownRenderer {

    String render(ReviewResponse response, ReviewLanguage language) {
        boolean spanish = language == ReviewLanguage.SPANISH
        StringBuilder output = new StringBuilder('## 🤖 Code Review Agent\n\n')

        if (response.findings.isEmpty()) {
            output.append(spanish ? '**Sin hallazgos.**\n' : '**No findings.**\n')
            output.append(spanish
                ? 'No encontramos problemas que requieran cambios en este PR.'
                : 'No issues requiring changes were found in this PR.')
            output.append('\n\n**')
            output.append(spanish ? 'Resumen' : 'Summary')
            output.append('**\n')
            output.append(response.summary.trim())
            output.append('\n\n')
            output.append(renderCounts(response, language))
            return output.toString()
        }

        output.append('**')
        output.append(spanish ? 'Resumen' : 'Summary')
        output.append('**\n')
        output.append(response.summary.trim())
        output.append('\n\n')
        output.append(spanish ? 'Encontramos **' : 'Found **')
        output.append(response.findings.size())
        output.append(response.findings.size() == 1
            ? (spanish ? ' hallazgo' : ' finding')
            : (spanish ? ' hallazgos' : ' findings'))
        output.append(spanish
            ? '** que conviene revisar.\n\n'
            : '** that should be reviewed.\n\n')

        response.findings.eachWithIndex { ReviewFinding finding, int index ->
            output.append(renderFinding(finding, language))
            if (index < response.findings.size() - 1) {
                output.append('\n---\n\n')
            }
        }

        output.append('\n\n')
        output.append(renderCounts(response, language))
        return output.toString()
    }

    private static String renderFinding(ReviewFinding finding, ReviewLanguage language) {
        boolean spanish = language == ReviewLanguage.SPANISH
        StringBuilder output = new StringBuilder()
        output.append(severityIcon(finding.severity))
        output.append(' **')
        output.append(finding.severity.name())
        output.append(' — ')
        output.append(finding.title)
        output.append('**\n\n')

        if (finding.file) {
            output.append('`')
            output.append(finding.file)
            if (finding.line != null) {
                output.append(':')
                output.append(finding.line)
            }
            output.append('`\n\n')
        } else if (finding.line != null) {
            output.append(spanish ? 'Línea `' : 'Line `')
            output.append(finding.line)
            output.append('`\n\n')
        }

        output.append('**')
        output.append(spanish ? 'Qué encontramos' : 'What we found')
        output.append('**\n')
        output.append(finding.description)

        if (finding.impact) {
            output.append('\n\n**')
            output.append(spanish ? 'Impacto' : 'Impact')
            output.append('**\n')
            output.append(finding.impact)
        }

        if (finding.recommendation) {
            output.append('\n\n**')
            output.append(spanish ? 'Qué hacer' : 'What to do')
            output.append('**\n')
            output.append(finding.recommendation)
        }

        output.toString()
    }

    private static String renderCounts(ReviewResponse response, ReviewLanguage language) {
        String counts = ReviewSeverity.values().collect { ReviewSeverity severity ->
            int count = response.findings.findAll { ReviewFinding finding ->
                finding.severity == severity
            }.size()
            countLabel(severity, count)
        }.join(' · ')

        "**${language == ReviewLanguage.SPANISH ? 'Resumen de severidad' : 'Severity summary'}:** ${counts}"
    }

    private static String countLabel(ReviewSeverity severity, int count) {
        "${severityIcon(severity)} ${count} ${severity.name()}"
    }

    private static String severityIcon(ReviewSeverity severity) {
        switch (severity) {
            case ReviewSeverity.CRITICAL:
                return '🔴'
            case ReviewSeverity.HIGH:
                return '🟠'
            case ReviewSeverity.MEDIUM:
                return '🟡'
            case ReviewSeverity.LOW:
                return '🔵'
            default:
                return '•'
        }
    }
}
