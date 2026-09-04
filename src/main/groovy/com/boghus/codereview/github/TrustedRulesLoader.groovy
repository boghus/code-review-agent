package com.boghus.codereview.github

import groovy.transform.CompileStatic

/**
 * Loads review rules from the pull request base Git tree.
 *
 * rules-path is untrusted input and is deliberately treated as data. Git is
 * invoked directly through ProcessBuilder; no shell is involved. Only a
 * regular blob from BASE_SHA can become trusted review instructions.
 */
@CompileStatic
class TrustedRulesLoader {

    static String load(String baseSha, String rulesPath, File tempDirectory) {
        validate(rulesPath)

        String entryType = git(baseSha, ['ls-tree', baseSha, '--', rulesPath], null)
            .readLines()
            .findResult { String line ->
                List<String> fields = line.split(/\s+/, 4) as List<String>
                fields.size() == 4 && fields[3] == rulesPath ? fields[1] : null
            }

        if (entryType != 'blob') {
            throw new IllegalArgumentException("rules-path must point to a regular file in base ref '${baseSha}': ${rulesPath}")
        }

        File rulesFile = File.createTempFile('cra-rules-', '.md', tempDirectory)
        rulesFile.withOutputStream { OutputStream output ->
            Process process = new ProcessBuilder('git', 'show', "${baseSha}:${rulesPath}")
                .redirectErrorStream(false)
                .start()
            process.inputStream.transferTo(output)
            String error = process.errorStream.getText('UTF-8')
            int exitCode = process.waitFor()
            if (exitCode != 0) {
                rulesFile.delete()
                throw new IllegalStateException("Unable to read trusted rules from base ref '${baseSha}': ${error.trim()}")
            }
        }
        rulesFile.setReadable(true, true)
        return rulesFile.text
    }

    static void validate(String rulesPath) {
        if (!rulesPath?.trim() || rulesPath.startsWith('/') || rulesPath.startsWith('-')) {
            throw new IllegalArgumentException("rules-path must be a non-empty repository-relative path and must not start with '-': '${rulesPath}'")
        }

        rulesPath.split('/', -1).each { String component ->
            if (!component || component == '.' || component == '..') {
                throw new IllegalArgumentException("rules-path contains an unsafe path component: '${rulesPath}'")
            }
        }
    }

    private static String git(String baseSha, List<String> arguments, File workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder(['git'] + arguments)
        if (workingDirectory != null) {
            builder.directory(workingDirectory)
        }
        Process process = builder.start()
        String output = process.inputStream.getText('UTF-8')
        String error = process.errorStream.getText('UTF-8')
        int exitCode = process.waitFor()
        if (exitCode != 0) {
            throw new IllegalStateException("Git command failed: ${error.trim()}")
        }
        output
    }
}
