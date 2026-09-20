package com.boghus.codereview.coverage

import com.boghus.codereview.review.DiffAnalyzer
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper

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

        List<JaCoCoSourceLine> jacocoLines = parseJaCoCo(jacocoXml)
        List<ChangedSourceLine> covered = []
        List<ChangedSourceLine> missed = []
        int executableLines = 0
        int mappedLines = 0

        uniqueChanges.each { ChangedSourceLine changed ->
            JaCoCoSourceLine jacocoLine = jacocoLines.find { JaCoCoSourceLine candidate ->
                pathMapper.matches(changed.path, candidate.sourcePath) &&
                    changed.lineNumber == candidate.lineNumber
            }

            if (jacocoLine == null) {
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

        BigDecimal percentage = (covered.size() * 100G)
            .divide(executableLines as BigDecimal, 2, BigDecimal.ROUND_HALF_UP)

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

    private List<JaCoCoSourceLine> parseJaCoCo(String jacocoXml) {
        if (!jacocoXml?.trim()) {
            return []
        }

        def root = new XmlSlurper(false, false).parseText(jacocoXml)
        List<JaCoCoSourceLine> lines = []

        root.package.each { packageNode ->
            String packagePath = packageNode.@name.text()
            packageNode.sourcefile.each { sourceNode ->
                String sourceFile = sourceNode.@name.text()
                String sourcePath = packagePath ? packagePath + '/' + sourceFile : sourceFile

                sourceNode.line.each { lineNode ->
                    int lineNumber = Integer.parseInt(lineNode.@nr.text())
                    int missedInstructions = Integer.parseInt(lineNode.@mi.text())
                    int coveredInstructions = Integer.parseInt(lineNode.@ci.text())
                    lines << new JaCoCoSourceLine(
                        sourcePath,
                        lineNumber,
                        missedInstructions + coveredInstructions > 0,
                        coveredInstructions > 0
                    )
                }
            }
        }

        lines
    }
}
