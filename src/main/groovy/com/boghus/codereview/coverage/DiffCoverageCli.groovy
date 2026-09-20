package com.boghus.codereview.coverage

import com.boghus.codereview.review.DiffAnalyzer
import groovy.transform.CompileStatic

@CompileStatic
class DiffCoverageCli {

    static void main(String[] args) {
        String baseSha = option(args, '--diffSha')
        String reportPath = option(args, '--report')

        if (!baseSha?.trim() || !reportPath?.trim()) {
            System.err.println('Usage: DiffCoverageCli --diffSha=<base-sha> --report=<jacoco-xml>')
            System.exit(2)
        }

        File reportFile = new File(reportPath)
        if (!reportFile.isFile()) {
            writeResult(reportFile, '❌ JaCoCo XML report was not found.')
            System.exit(2)
        }

        String workspace = System.getenv('GITHUB_WORKSPACE') ?: '.'
        String diff = gitDiff(workspace, baseSha)
        DiffCoverageAnalyzer analyzer = new DiffCoverageAnalyzer()
        DiffCoverageResult result = analyzer.analyze(DiffAnalyzer.parse(diff), reportFile.getText('UTF-8'))

        String output = render(result)
        File outputFile = new File('build/reports/coverage/report.txt')
        writeResult(outputFile, output)

        if (result.status == DiffCoverageStatus.MAPPING_ERROR) {
            System.exit(1)
        }
    }

    private static String gitDiff(String workspace, String baseSha) {
        Process process = new ProcessBuilder('git', '-C', workspace, 'diff', '--unified=0', baseSha, 'HEAD', '--')
            .redirectErrorStream(true)
            .start()
        String output = process.inputStream.getText('UTF-8')
        int exitCode = process.waitFor()
        if (exitCode != 0) {
            throw new IllegalStateException('Unable to calculate Git diff: ' + output.trim())
        }
        output
    }

    private static String option(String[] args, String name) {
        String prefix = name + '='
        String value = args.find { String arg -> arg.startsWith(prefix) }
        value ? value.substring(prefix.length()) : null
    }

    private static String render(DiffCoverageResult result) {
        switch (result.status) {
            case DiffCoverageStatus.COVERAGE_AVAILABLE:
                return '''Changed executable lines: %d
Covered: %d
Missed: %d
Coverage: %s%%'''.formatted(
                    result.executableLines,
                    result.coveredLines,
                    result.missedLines,
                    result.coveragePercentage()
                )
            case DiffCoverageStatus.NO_EXECUTABLE_CHANGES:
                return 'ℹ️ ' + result.message
            case DiffCoverageStatus.MAPPING_ERROR:
                return '❌ ' + result.message
            default:
                return '❌ Unknown diff coverage status.'
        }
    }

    private static void writeResult(File file, String content) {
        file.parentFile.mkdirs()
        file.setText(content + System.lineSeparator(), 'UTF-8')
        println content
    }
}
