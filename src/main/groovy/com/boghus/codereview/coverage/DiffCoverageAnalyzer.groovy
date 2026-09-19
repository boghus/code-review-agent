package com.boghus.codereview.coverage

import groovy.xml.XmlSlurper

/**
 * Calculates line coverage for executable lines changed since a git base.
 *
 * JaCoCo identifies source files by package + local source-file name, while
 * git diffs contain repository-relative paths. Matching is therefore done by
 * suffix instead of requiring the two path formats to be identical.
 */
class DiffCoverageAnalyzer {

    static CoverageResult analyze(String baseSha, File reportFile, File projectDir) {
        if (!baseSha?.trim()) {
            throw new IllegalArgumentException('A base commit is required')
        }
        if (!reportFile.isFile()) {
            throw new IllegalArgumentException("JaCoCo report not found: \${reportFile}")
        }

        String diff = runGit(projectDir, ['diff', baseSha, '--unified=0'])
        Map<String, Set<Integer>> changedLines = parseChangedLines(diff)
        if (changedLines.isEmpty()) {
            return new CoverageResult([])
        }

        Map<String, Map<Integer, Boolean>> coverage = parseCoverage(reportFile)
        List<FileCoverage> files = []

        changedLines.each { String path, Set<Integer> lines ->
            String coveragePath = coverage.keySet().find { String key ->
                path == key || path.endsWith('/' + key)
            }
            if (coveragePath == null) {
                return
            }

            Map<Integer, Boolean> fileCoverage = coverage[coveragePath]
            List<Integer> executableLines = lines.findAll { fileCoverage.containsKey(it) }.sort()
            if (executableLines.isEmpty()) {
                return
            }

            int covered = executableLines.count { fileCoverage[it] }
            files << new FileCoverage(path, covered, executableLines.size(), executableLines.findAll { !fileCoverage[it] })
        }

        return new CoverageResult(files)
    }

    static void writeReport(CoverageResult result, File outputFile) {
        outputFile.parentFile?.mkdirs()

        int analyzedLines = result.analyzedLines
        int coveredLines = result.coveredLines
        int uncoveredLines = result.uncoveredLines
        BigDecimal percentage = analyzedLines == 0
            ? 0
            : (coveredLines * 100.0G / analyzedLines).setScale(2, BigDecimal.ROUND_HALF_UP)

        List<String> output = [
            "analyzedLines: \${analyzedLines}",
            "coveredLines: \${coveredLines}",
            "uncoveredLines: \${uncoveredLines}",
            "coveragePercentage: \${percentage}"
        ]

        result.files.each { FileCoverage file ->
            output << "\${file.coveredLines}/\${file.analyzedLines} - \${file.path}"
        }

        outputFile.text = output.join(System.lineSeparator()) + System.lineSeparator()
    }

    private static Map<String, Set<Integer>> parseChangedLines(String diff) {
        Map<String, Set<Integer>> result = [:].withDefault { new LinkedHashSet<Integer>() }
        String currentFile
        int newLine = 0

        diff.readLines().each { String line ->
            if (line.startsWith('diff --git ')) {
                Matcher matcher = line =~ /^diff --git a\/(.+) b\/(.+)$/
                currentFile = matcher.matches() ? matcher.group(2) : null
                newLine = 0
            } else if (currentFile && line.startsWith('@@')) {
                Matcher matcher = line =~ /^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/
                if (matcher.matches()) {
                    newLine = matcher.group(1).toInteger()
                }
            } else if (currentFile && line.startsWith('+') && !line.startsWith('+++')) {
                result[currentFile] << newLine
                newLine++
            } else if (currentFile && !line.startsWith('-') && !line.startsWith('\')) {
                newLine++
            }
        }

        result.findAll { String path, Set<Integer> lines -> !lines.isEmpty() }
    }

    private static Map<String, Map<Integer, Boolean>> parseCoverage(File reportFile) {
        def report = new XmlSlurper().parse(reportFile)
        Map<String, Map<Integer, Boolean>> result = [:]

        report.package.each { pkg ->
            String packagePath = pkg.@name.text().replace('.', '/').replaceAll('/+$', '')
            pkg.sourcefile.each { source ->
                String sourceName = source.@name.text()
                String key = packagePath ? "\${packagePath}/\${sourceName}" : sourceName
                Map<Integer, Boolean> lines = [:]
                source.line.each { line ->
                    int number = line.@nr.toInteger()
                    int coveredInstructions = line.@ci.toInteger()
                    lines[number] = coveredInstructions > 0
                }
                result[key] = lines
            }
        }

        result
    }

    private static String runGit(File projectDir, List<String> arguments) {
        List<String> command = ['git'] + arguments
        Process process = new ProcessBuilder(command)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        String output = process.inputStream.text
        int exitCode = process.waitFor()
        if (exitCode != 0) {
            throw new IllegalStateException("Git command failed (\${exitCode}): \${command.join(' ')}\n\${output}")
        }
        output
    }

    static class CoverageResult {
        final List<FileCoverage> files

        CoverageResult(List<FileCoverage> files) {
            this.files = files.asImmutable()
        }

        int getAnalyzedLines() {
            files.sum { it.analyzedLines } ?: 0
        }

        int getCoveredLines() {
            files.sum { it.coveredLines } ?: 0
        }

        int getUncoveredLines() {
            files.sum { it.uncoveredLines } ?: 0
        }
    }

    static class FileCoverage {
        final String path
        final int coveredLines
        final int analyzedLines
        final List<Integer> uncoveredLineNumbers

        FileCoverage(String path, int coveredLines, int analyzedLines, List<Integer> uncoveredLineNumbers) {
            this.path = path
            this.coveredLines = coveredLines
            this.analyzedLines = analyzedLines
            this.uncoveredLineNumbers = uncoveredLineNumbers.asImmutable()
        }

        int getUncoveredLines() {
            analyzedLines - coveredLines
        }
    }
}
