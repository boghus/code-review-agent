package com.boghus.codereview.coverage

import com.boghus.codereview.review.DiffAnalyzer
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper
import groovy.util.slurpersupport.GPathResult

@CompileStatic
class DiffCoverageAnalyzer {

    private final SourcePathMapper pathMapper

    DiffCoverageAnalyzer(SourcePathMapper pathMapper = new SourcePathMapper()) {
        this.pathMapper = pathMapper
    }

    DiffCoverageResult analyze(DiffAnalyzer diffAnalyzer, String jacocoXml) {
        List<ChangedSourceLine> changedLines = diffAnalyzer.addedLines().collect {
            new ChangedSourceLine(it.file, it.line)
        }
        analyze(changedLines, jacocoXml)
    }

    DiffCoverageResult analyze(List<ChangedSourceLine> changedLines, String jacocoXml) {
        List<ChangedSourceLine> uniqueChanges = changedLines?.unique {
            ChangedSourceLine line -> line.path + ':' + line.lineNumber
        } ?: []

        if (uniqueChanges.isEmpty()) {
            return new DiffCoverageResult(
                DiffCoverageStatus.NO_EXECUTABLE_CHANGES,
                0, 0, 0, 0, [], [],
                'No added source lines were available for coverage analysis.'
            )
        }

        ParsedJaCoCo parsed
        try {
            parsed = parseJaCoCo(jacocoXml)
        } catch (Exception ex) {
            return new DiffCoverageResult(
                DiffCoverageStatus.MAPPING_ERROR,
                uniqueChanges.size(),
                0, 0, 0, [], [],
                'JaCoCo XML could not be parsed.'
            )
        }

        List<ChangedSourceLine> covered = []
        List<ChangedSourceLine> missed = []
        int executableLines = 0
        int mappedLines = 0

        uniqueChanges.each { ChangedSourceLine changed ->
            JaCoCoSourceLine jacocoLine = parsed.lines.find { JaCoCoSourceLine candidate ->
                pathMapper.matches(changed.path, candidate.sourcePath) &&
                    changed.lineNumber == candidate.lineNumber
            }

            if (jacocoLine == null) {
                boolean sourceFileExists = parsed.sourcePaths.any { String sourcePath ->
                    pathMapper.matches(changed.path, sourcePath)
                }
                if (sourceFileExists) {
                    mappedLines++
                }
                return
            }

            mappedLines++
            if (!jacocoLine.executable) {
                return
            }

            executableLines++
            if (jacocoLine.covered) {
                covered << changed
            } else {
                missed << changed
            }
        }

        if (mappedLines == 0) {
            return new DiffCoverageResult(
                DiffCoverageStatus.MAPPING_ERROR,
                uniqueChanges.size(), 0, 0, 0, [], [],
                'Changed source lines could not be mapped to JaCoCo source data.'
            )
        }

        if (executableLines == 0) {
            return new DiffCoverageResult(
                DiffCoverageStatus.NO_EXECUTABLE_CHANGES,
                uniqueChanges.size(), 0, 0, 0, [], [],
                'No executable changed lines were found in the JaCoCo report.'
            )
        }

        BigDecimal percentage = BigDecimal.valueOf(covered.size() * 100L)
            .divide(BigDecimal.valueOf(executableLines as long), 2, BigDecimal.ROUND_HALF_UP)

        new DiffCoverageResult(
            DiffCoverageStatus.COVERAGE_AVAILABLE,
            uniqueChanges.size(),
            executableLines,
            covered.size(),
            missed.size(),
            covered,
            missed,
            'Diff coverage: ' + percentage + '%'
        )
    }

    private ParsedJaCoCo parseJaCoCo(String jacocoXml) {
        if (!jacocoXml?.trim()) {
            throw new IllegalArgumentException('JaCoCo XML is empty.')
        }

        GPathResult root = new XmlSlurper(false, false).parseText(jacocoXml)
        List<JaCoCoSourceLine> lines = []
        Set<String> sourcePaths = new LinkedHashSet<>()

        root.children().findAll { Object node -> node instanceof GPathResult && ((GPathResult) node).name() == 'package' }.each { Object packageObject ->
            GPathResult packageNode = (GPathResult) packageObject
            String packagePath = packageNode.attributes().get('name')?.toString() ?: ''

            packageNode.children().findAll { Object node ->
                node instanceof GPathResult && ((GPathResult) node).name() == 'sourcefile'
            }.each { Object sourceObject ->
                GPathResult sourceNode = (GPathResult) sourceObject
                String sourceFile = sourceNode.attributes().get('name')?.toString() ?: ''
                String sourcePath = packagePath ? packagePath + '/' + sourceFile : sourceFile
                sourcePaths << sourcePath

                sourceNode.children().findAll { Object node ->
                    node instanceof GPathResult && ((GPathResult) node).name() == 'line'
                }.each { Object lineObject ->
                    GPathResult lineNode = (GPathResult) lineObject
                    int lineNumber = Integer.parseInt(lineNode.attributes().get('nr').toString())
                    int missedInstructions = Integer.parseInt(lineNode.attributes().get('mi').toString())
                    int coveredInstructions = Integer.parseInt(lineNode.attributes().get('ci').toString())
                    lines << new JaCoCoSourceLine(
                        sourcePath,
                        lineNumber,
                        missedInstructions + coveredInstructions > 0,
                        coveredInstructions > 0
                    )
                }
            }
        }

        new ParsedJaCoCo(lines, sourcePaths)
    }

    private static class ParsedJaCoCo {
        final List<JaCoCoSourceLine> lines
        final Set<String> sourcePaths

        ParsedJaCoCo(List<JaCoCoSourceLine> lines, Set<String> sourcePaths) {
            this.lines = lines
            this.sourcePaths = sourcePaths
        }
    }
}
