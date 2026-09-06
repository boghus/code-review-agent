package com.boghus.codereview.review

import groovy.transform.CompileStatic

/**
 * Builds the final PR diff between a base and a head SHA using
 * {@code git diff --no-ext-diff --unified=80 BASE HEAD}.
 *
 * <p>Only the final state of the PR (BASE..HEAD) is part of the review
 * context. Intermediate commit history is intentionally excluded.</p>
 */
@CompileStatic
class DiffBuilder {

    static final int DEFAULT_CONTEXT_LINES = 80

    /**
     * Runs {@code git diff} between the given SHAs and writes the unified
     * diff to {@code output}. Both SHAs must already be reachable from the
     * working repository. The GitHub Actions checkout step guarantees this
     * for the PR context; tests can pass an explicit {@code workingDir}.
     *
     * @param output       file receiving the unified diff
     * @param baseSha      SHA at the PR base (inclusive)
     * @param headSha      SHA at the PR head (inclusive)
     * @param contextLines number of context lines (default 80)
     * @param workingDir   git working directory (default: GITHUB_WORKSPACE)
     * @return the number of lines written, or 0 if the diff was empty
     */
    static int build(File output, String baseSha, String headSha,
                     int contextLines = DEFAULT_CONTEXT_LINES,
                     File workingDir = defaultWorkingDirectory()) {
        if (!baseSha?.trim()) {
            throw new IllegalArgumentException('baseSha is required')
        }
        if (!headSha?.trim()) {
            throw new IllegalArgumentException('headSha is required')
        }
        if (baseSha.startsWith('-') || headSha.startsWith('-')) {
            throw new IllegalArgumentException('baseSha and headSha must not start with "-"')
        }
        if (contextLines <= 0) {
            throw new IllegalArgumentException('contextLines must be positive')
        }

        String unifiedFlag = "--unified=${contextLines}".toString()
        ProcessBuilder pb = new ProcessBuilder(
            'git', 'diff',
            '--no-ext-diff',
            unifiedFlag,
            baseSha,
            headSha
        )
        pb.directory(workingDir)
        Process process = pb.start()

        File parent = output.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        FileOutputStream outputStream = new FileOutputStream(output)
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream()
        process.waitForProcessOutput(outputStream, errorStream)
        int exit = process.exitValue()
        outputStream.close()

        if (exit != 0) {
            output.delete()
            String error = errorStream.toString('UTF-8').trim()
            throw new IllegalStateException(
                "git diff failed (exit=${exit}): ${error}"
            )
        }

        int lines = 0
        output.withInputStream { InputStream input ->
            byte[] buffer = new byte[8192]
            int read
            while ((read = input.read(buffer)) != -1) {
                for (int index = 0; index < read; index++) {
                    if (buffer[index] == (byte) 10) {
                        lines++
                    }
                }
            }
        }
        return lines
    }

    private static File defaultWorkingDirectory() {
        new File(System.getenv('GITHUB_WORKSPACE') ?: '.').canonicalFile
    }
}
