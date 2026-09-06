package com.boghus.codereview.github

import org.junit.jupiter.api.Test

import java.nio.file.Files

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class TrustedRulesLoaderTest {

    @Test
    void 'deletes temporary rules file after successful load'() {
        File tempDirectory = Files.createTempDirectory('cra-rules-test-').toFile()

        try {
            String rules = TrustedRulesLoader.load(
                'HEAD',
                '.github/code_review_rules.md',
                tempDirectory,
                new File('.').canonicalFile
            )

            assertThat(rules).isNotEmpty()
            assertThat(tempDirectory.listFiles()).isEmpty()
        } finally {
            tempDirectory.deleteDir()
        }
    }

    @Test
    void 'deletes temporary rules file when git show fails'() {
        File tempDirectory = Files.createTempDirectory('cra-rules-test-').toFile()
        File rulesFile = new File(tempDirectory, 'cra-rules-failure.md')
        rulesFile.createNewFile()

        try {
            assertThatThrownBy {
                TrustedRulesLoader.loadFromFile(
                    'missing-base-ref',
                    '.github/code_review_rules.md',
                    new File('.').canonicalFile,
                    rulesFile
                )
            }.isInstanceOf(IllegalStateException)

            assertThat(rulesFile).doesNotExist()
        } finally {
            tempDirectory.deleteDir()
        }
    }

    @Test
    void 'deletes temporary rules file when reading the file fails'() {
        File tempDirectory = Files.createTempDirectory('cra-rules-test-').toFile()
        File backingFile = new File(tempDirectory, 'cra-rules-read-failure.md')
        backingFile.createNewFile()
        File failingReadFile = new FailingReadFile(backingFile)

        try {
            assertThatThrownBy {
                TrustedRulesLoader.loadFromFile(
                    'HEAD',
                    '.github/code_review_rules.md',
                    new File('.').canonicalFile,
                    failingReadFile
                )
            }.isInstanceOf(RuntimeException)

            assertThat(backingFile).doesNotExist()
        } finally {
            tempDirectory.deleteDir()
        }
    }

    private static class FailingReadFile extends File {

        FailingReadFile(File file) {
            super(file.absolutePath)
        }

        @Override
        String getText(String charset) {
            throw new IOException('simulated rules-file read failure')
        }
    }
}
