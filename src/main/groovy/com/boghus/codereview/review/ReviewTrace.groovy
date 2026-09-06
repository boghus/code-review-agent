package com.boghus.codereview.review

import com.boghus.codereview.provider.ReviewRequest
import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashSet
import java.util.UUID

/**
 * Metadata-only trace for a single AI review request.
 *
 * No prompt, diff, rules content, credentials or provider response is logged.
 * Content is represented only by SHA-256 hashes and byte/line counts.
 */
@CompileStatic
class ReviewTrace {

    final String executionId
    final String repository
    final int pullRequest
    final String baseSha
    final String headSha
    final Set<String> changedFiles
    final int diffLines
    final int diffBytes
    final String diffSha256
    final String rulesSha256
    final String model
    final int maxOutputTokens
    final int promptBytes
    final String promptSha256

    ReviewTrace(
        String executionId,
        String repository,
        int pullRequest,
        String baseSha,
        String headSha,
        Set<String> changedFiles,
        int diffLines,
        int diffBytes,
        String diffSha256,
        String rulesSha256,
        String model,
        int maxOutputTokens,
        int promptBytes,
        String promptSha256
    ) {
        this.executionId = executionId
        this.repository = repository
        this.pullRequest = pullRequest
        this.baseSha = baseSha
        this.headSha = headSha
        this.changedFiles = changedFiles == null
            ? new LinkedHashSet<String>()
            : new LinkedHashSet<String>(changedFiles)
        this.diffLines = diffLines
        this.diffBytes = diffBytes
        this.diffSha256 = diffSha256
        this.rulesSha256 = rulesSha256
        this.model = model
        this.maxOutputTokens = maxOutputTokens
        this.promptBytes = promptBytes
        this.promptSha256 = promptSha256
    }

    static ReviewTrace create(
        String repository,
        int pullRequest,
        String baseSha,
        String headSha,
        DiffAnalyzer analyzer,
        String diff,
        String rules,
        ReviewRequest request,
        String model,
        int maxOutputTokens
    ) {
        String systemAndDeveloper = "${request.systemInstructions}\n\n${request.developerInstructions}"
        String providerPrompt = "${request.prompt}\n\n${request.untrustedRepositoryContent}"
        Set<String> touchedFiles = analyzer == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(analyzer.touchedFiles())

        return new ReviewTrace(
            UUID.randomUUID().toString(),
            repository ?: 'unknown',
            pullRequest,
            baseSha ?: 'unknown',
            headSha ?: 'unknown',
            touchedFiles,
            countLines(diff),
            bytes(diff),
            sha256(diff),
            sha256(rules ?: ''),
            model,
            maxOutputTokens,
            bytes(systemAndDeveloper) + bytes(providerPrompt),
            sha256("${systemAndDeveloper}\n\n${providerPrompt}")
        )
    }

    void log() {
        println "Code Review Agent trace: executionId=${executionId}, repository=${repository}, PR=#${pullRequest}, baseSha=${baseSha}, headSha=${headSha}, changedFiles=${changedFiles.size()}, files=${joinFiles(changedFiles)}, diffLines=${diffLines}, diffBytes=${diffBytes}, diffSha256=${diffSha256}, rulesSha256=${rulesSha256}, model=${model}, maxOutputTokens=${maxOutputTokens}, promptBytes=${promptBytes}, promptSha256=${promptSha256}"
    }

    private static String joinFiles(Set<String> files) {
        StringBuilder result = new StringBuilder()
        boolean first = true
        for (String file : files) {
            if (!first) {
                result.append(',')
            }
            result.append(file)
            first = false
        }
        return result.toString()
    }

    private static int countLines(String value) {
        if (!value) return 0
        return value.split('\\n', -1).length
    }

    private static int bytes(String value) {
        return (value ?: '').getBytes(StandardCharsets.UTF_8).length
    }

    private static String sha256(String value) {
        byte[] digest = MessageDigest.getInstance('SHA-256').digest((value ?: '').getBytes(StandardCharsets.UTF_8))
        StringBuilder result = new StringBuilder()
        for (byte valueByte : digest) {
            result.append(String.format('%02x', valueByte & 0xff))
        }
        return result.toString()
    }
}
