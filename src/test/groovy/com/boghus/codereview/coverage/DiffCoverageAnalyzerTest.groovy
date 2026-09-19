package com.boghus.codereview.coverage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

class DiffCoverageAnalyzerTest {

    @TempDir
    Path tempDir

    @Test
    void 'analyzes changed Groovy lines from JaCoCo report'() {
        File repo = tempDir.toFile()
        run(repo, 'git init')
        run(repo, 'git config user.email test@example.com')
        run(repo, 'git config user.name Test')

        File source = new File(repo, 'src/main/groovy/com/example/Sample.groovy')
        source.parentFile.mkdirs()
        source.text = 'class Sample {\n    String value() { "base" }\n}\n'
        run(repo, 'git add .')
        run(repo, 'git commit -m base')
        String baseSha = run(repo, 'git rev-parse HEAD').trim()

        source.text = 'class Sample {\n    String value() { "changed" }\n}\n'

        File report = new File(repo, 'jacoco.xml')
        report.text = '''<report name="test">
  <package name="com/example">
    <sourcefile name="Sample.groovy">
      <line nr="2" mi="0" ci="1"/>
    </sourcefile>
  </package>
</report>
'''

        DiffCoverageAnalyzer.CoverageResult result =
            DiffCoverageAnalyzer.analyze(baseSha, report, repo)

        assertThat(result.analyzedLines).isEqualTo(1)
        assertThat(result.coveredLines).isEqualTo(1)
        assertThat(result.uncoveredLines).isZero()
        assertThat(result.files[0].path).isEqualTo('src/main/groovy/com/example/Sample.groovy')
    }

    @Test
    void 'ignores changed non-executable lines'() {
        File repo = tempDir.toFile()
        run(repo, 'git init')
        run(repo, 'git config user.email test@example.com')
        run(repo, 'git config user.name Test')

        File source = new File(repo, 'src/main/groovy/com/example/Sample.groovy')
        source.parentFile.mkdirs()
        source.text = 'class Sample {\n    String value() { "base" }\n}\n'
        run(repo, 'git add .')
        run(repo, 'git commit -m base')
        String baseSha = run(repo, 'git rev-parse HEAD').trim()

        source.text = 'class Sample {\n    String value() { "changed" }\n}\n'

        File report = new File(repo, 'jacoco.xml')
        report.text = '''<report name="test">
  <package name="com/example">
    <sourcefile name="Sample.groovy">
      <line nr="1" mi="0" ci="1"/>
    </sourcefile>
  </package>
</report>
'''

        DiffCoverageAnalyzer.CoverageResult result =
            DiffCoverageAnalyzer.analyze(baseSha, report, repo)

        assertThat(result.analyzedLines).isZero()
        assertThat(result.files).isEmpty()
    }

    private static String run(File directory, String command) {
        Process process = new ProcessBuilder(['bash', '-c', command])
            .directory(directory)
            .redirectErrorStream(true)
            .start()
        String output = process.inputStream.text
        int exitCode = process.waitFor()
        assertThat(exitCode).as("Command failed: \${command}\n\${output}").isZero()
        output
    }
}
