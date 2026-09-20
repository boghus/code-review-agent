package com.boghus.codereview.coverage

import com.boghus.codereview.review.DiffAnalyzer
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class DiffCoverageAnalyzerTest {

    private final DiffCoverageAnalyzer analyzer = new DiffCoverageAnalyzer()

    @Test
    void 'maps Groovy source path and reports covered changed lines'() {
        DiffCoverageResult result = analyzer.analyze(
            [new ChangedSourceLine('src/main/groovy/com/boghus/codereview/Foo.groovy', 10)],
            jacoco('com/boghus/codereview', 'Foo.groovy', 10, 0, 3)
        )

        assertThat(result.status).isEqualTo(DiffCoverageStatus.COVERAGE_AVAILABLE)
        assertThat(result.changedLines).isEqualTo(1)
        assertThat(result.executableLines).isEqualTo(1)
        assertThat(result.coveredLines).isEqualTo(1)
        assertThat(result.missedLines).isZero()
        assertThat(result.coveragePercentage()).isEqualByComparingTo('100.00')
    }

    @Test
    void 'reports missed executable Groovy lines'() {
        DiffCoverageResult result = analyzer.analyze(
            [new ChangedSourceLine('src/main/groovy/com/boghus/codereview/Foo.groovy', 10)],
            jacoco('com/boghus/codereview', 'Foo.groovy', 10, 3, 0)
        )

        assertThat(result.status).isEqualTo(DiffCoverageStatus.COVERAGE_AVAILABLE)
        assertThat(result.executableLines).isEqualTo(1)
        assertThat(result.coveredLines).isZero()
        assertThat(result.missedLines).isEqualTo(1)
        assertThat(result.coveragePercentage()).isEqualByComparingTo('0.00')
    }

    @Test
    void 'distinguishes non executable changes from mapping failures'() {
        DiffCoverageResult nonExecutable = analyzer.analyze(
            [new ChangedSourceLine('src/main/groovy/com/boghus/codereview/Foo.groovy', 10)],
            jacoco('com/boghus/codereview', 'Foo.groovy', 10, 0, 0)
        )

        DiffCoverageResult mappingFailure = analyzer.analyze(
            [new ChangedSourceLine('src/main/groovy/com/boghus/codereview/Missing.groovy', 10)],
            jacoco('com/boghus/codereview', 'Foo.groovy', 10, 0, 3)
        )

        assertThat(nonExecutable.status).isEqualTo(DiffCoverageStatus.NO_EXECUTABLE_CHANGES)
        assertThat(mappingFailure.status).isEqualTo(DiffCoverageStatus.MAPPING_ERROR)
    }

    @Test
    void 'deduplicates changed lines and aggregates multiple files'() {
        List<ChangedSourceLine> changes = [
            new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 10),
            new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 10),
            new ChangedSourceLine('src/main/groovy/com/foo/Bar.groovy', 20)
        ]

        String xml = '''<report>
            <package name="com/foo">
                <sourcefile name="Foo.groovy">
                    <line nr="10" mi="0" ci="2"/>
                </sourcefile>
                <sourcefile name="Bar.groovy">
                    <line nr="20" mi="1" ci="0"/>
                </sourcefile>
            </package>
        </report>'''

        DiffCoverageResult result = analyzer.analyze(changes, xml)

        assertThat(result.changedLines).isEqualTo(2)
        assertThat(result.executableLines).isEqualTo(2)
        assertThat(result.coveredLines).isEqualTo(1)
        assertThat(result.missedLines).isEqualTo(1)
    }

    @Test
    void 'uses the existing DiffAnalyzer as the source of changed lines'() {
        String diff = '''diff --git a/src/main/groovy/com/foo/Foo.groovy b/src/main/groovy/com/foo/Foo.groovy
--- a/src/main/groovy/com/foo/Foo.groovy
+++ b/src/main/groovy/com/foo/Foo.groovy
@@ -1 +1,2 @@
 class Foo {}
+int added = 1
'''

        DiffCoverageResult result = analyzer.analyze(
            DiffAnalyzer.parse(diff),
            jacoco('com/foo', 'Foo.groovy', 2, 0, 1)
        )

        assertThat(result.status).isEqualTo(DiffCoverageStatus.COVERAGE_AVAILABLE)
        assertThat(result.coveredLines).isEqualTo(1)
    }

    @Test
    void 'returns no executable changes for empty input'() {
        DiffCoverageResult result = analyzer.analyze([], '<report/>')

        assertThat(result.status).isEqualTo(DiffCoverageStatus.NO_EXECUTABLE_CHANGES)
        assertThat(result.executableLines).isZero()
    }

    @Test
    void 'ignores changed lines that JaCoCo does not report as executable lines'() {
        DiffCoverageResult result = analyzer.analyze(
            [new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 20)],
            jacoco('com/foo', 'Foo.groovy', 10, 0, 1)
        )

        assertThat(result.status).isEqualTo(DiffCoverageStatus.NO_EXECUTABLE_CHANGES)
        assertThat(result.changedLines).isEqualTo(1)
        assertThat(result.executableLines).isZero()
    }

    @Test
    void 'calculates coverage using only JaCoCo executable lines from mixed changes'() {
        List<ChangedSourceLine> changes = [
            new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 10),
            new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 11),
            new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 12)
        ]

        String xml = '''<report>
            <package name="com/foo">
                <sourcefile name="Foo.groovy">
                    <line nr="10" mi="0" ci="1"/>
                    <line nr="12" mi="1" ci="0"/>
                </sourcefile>
            </package>
        </report>'''

        DiffCoverageResult result = analyzer.analyze(changes, xml)

        assertThat(result.status).isEqualTo(DiffCoverageStatus.COVERAGE_AVAILABLE)
        assertThat(result.changedLines).isEqualTo(3)
        assertThat(result.executableLines).isEqualTo(2)
        assertThat(result.coveredLines).isEqualTo(1)
        assertThat(result.missedLines).isEqualTo(1)
        assertThat(result.coveragePercentage()).isEqualByComparingTo('50.00')
    }

    @Test
    void 'supports JaCoCo XML with a doctype declaration'() {
        String xml = '''<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
<report>
    <package name="com/foo">
        <sourcefile name="Foo.groovy">
            <line nr="10" mi="0" ci="2" mb="0" cb="0"/>
        </sourcefile>
    </package>
</report>'''

        DiffCoverageResult result = analyzer.analyze(
            [new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 10)],
            xml
        )

        assertThat(result.status).isEqualTo(DiffCoverageStatus.COVERAGE_AVAILABLE)
        assertThat(result.coveredLines).isEqualTo(1)
        assertThat(result.coveragePercentage()).isEqualByComparingTo('100.00')
    }

    @Test
    void 'calculates fractional coverage with two decimal places'() {
        List<ChangedSourceLine> changes = [
            new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 10),
            new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 20),
            new ChangedSourceLine('src/main/groovy/com/foo/Foo.groovy', 30)
        ]

        String xml = '''<report>
            <package name="com/foo">
                <sourcefile name="Foo.groovy">
                    <line nr="10" mi="0" ci="1"/>
                    <line nr="20" mi="1" ci="0"/>
                    <line nr="30" mi="1" ci="0"/>
                </sourcefile>
            </package>
        </report>'''

        DiffCoverageResult result = analyzer.analyze(changes, xml)

        assertThat(result.status).isEqualTo(DiffCoverageStatus.COVERAGE_AVAILABLE)
        assertThat(result.executableLines).isEqualTo(3)
        assertThat(result.coveredLines).isEqualTo(1)
        assertThat(result.missedLines).isEqualTo(2)
        assertThat(result.coveragePercentage()).isEqualByComparingTo('33.33')
    }

    private static String jacoco(String packageName, String sourceFile, int line, int missed, int covered) {
        '<report><package name="' + packageName + '"><sourcefile name="' + sourceFile +
            '"><line nr="' + line + '" mi="' + missed + '" ci="' + covered +
            '" mb="0" cb="0"/></sourcefile></package></report>'
    }
}
