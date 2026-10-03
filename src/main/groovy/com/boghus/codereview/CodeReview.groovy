package com.boghus.codereview

import com.boghus.codereview.github.ActionInputs
import com.boghus.codereview.github.TrustedRulesLoader
import com.boghus.codereview.output.ReviewReportWriter
import com.boghus.codereview.provider.AiProvider
import com.boghus.codereview.provider.AiProviderException
import com.boghus.codereview.provider.AiProviderFactory
import com.boghus.codereview.provider.GeminiAdapter
import com.boghus.codereview.provider.ReviewRequest
import com.boghus.codereview.provider.RuntimeErrorSanitizer
import com.boghus.codereview.review.DiffAnalyzer
import com.boghus.codereview.review.DiffBuilder
import com.boghus.codereview.review.DiffSizeGuard
import com.boghus.codereview.review.ReviewPromptBuilder
import com.boghus.codereview.review.ReviewResponseJsonParser
import com.boghus.codereview.review.ReviewTrace
import groovy.json.JsonException
import groovy.transform.CompileStatic

import java.util.LinkedHashMap
import java.util.Map

/**
 * Orchestrator. Reads inputs, builds the structured review request, calls the
 * AI provider and writes the resulting review report. Posting the comment is
 * delegated to the composite action steps (peter-evans).
 */
@CompileStatic
class CodeReview {

    static void main(String[] args) {
        long totalStartedAt = System.nanoTime()
        LinkedHashMap<String, Long> timings = new LinkedHashMap<String, Long>()
        ActionInputs inputs = ActionInputs.fromEnv()
        ReviewReportWriter writer = new ReviewReportWriter()
        boolean performanceOnly = Boolean.parseBoolean(System.getenv('CRA_PERFORMANCE_ONLY') ?: 'false')

        if (!performanceOnly && !inputs.apiKey?.trim()) {
            writer.writeMisconfigured(inputs.outputPath,
                'The `api-key` input was not provided. Map your repository secret to it, e.g. `api-key: ${{ secrets.MY_AI_KEY }}`.')
            println 'Code Review Agent: api-key missing, wrote misconfiguration report.'
            return
        }

        File diffFile = new File(inputs.diffPath)
        File repositoryDirectory = new File(System.getenv('GITHUB_WORKSPACE') ?: '.').canonicalFile
        println "Code Review Agent: repository workspace=${repositoryDirectory}"

        long stageStartedAt = System.nanoTime()
        try {
            DiffBuilder.build(
                diffFile,
                inputs.baseSha,
                inputs.headSha,
                DiffBuilder.DEFAULT_CONTEXT_LINES,
                repositoryDirectory
            )
            timings.put('diff_generation', elapsedMillis(stageStartedAt))
        } catch (Exception ex) {
            timings.put('diff_generation', elapsedMillis(stageStartedAt))
            writer.writeFailure(inputs.outputPath, "Failed to generate Git diff: ${ex.message}")
            println "Code Review Agent: ${RuntimeErrorSanitizer.sanitize(ex)}"
            return
        }

        if (!diffFile.exists() || !diffFile.text.trim()) {
            timings.put('total_internal', elapsedMillis(totalStartedAt))
            writer.writeEmpty(inputs.outputPath)
            println 'Code Review Agent: empty diff, wrote empty review.'
            return
        }

        String rules
        stageStartedAt = System.nanoTime()
        try {
            rules = TrustedRulesLoader.load(inputs.baseSha, inputs.rulesPath, repositoryDirectory)
            timings.put('trusted_rules', elapsedMillis(stageStartedAt))
        } catch (IllegalArgumentException ex) {
            timings.put('trusted_rules', elapsedMillis(stageStartedAt))
            writer.writeTrustedRulesFailure(
                inputs.outputPath,
                'The trusted review rules are invalid or do not point to a regular file in the pull request base revision.'
            )
            println "Code Review Agent: trusted rules validation failed: ${RuntimeErrorSanitizer.sanitize(ex)}"
            return
        } catch (IllegalStateException ex) {
            timings.put('trusted_rules', elapsedMillis(stageStartedAt))
            writer.writeTrustedRulesFailure(
                inputs.outputPath,
                'The trusted review rules could not be loaded from the pull request base revision.'
            )
            println "Code Review Agent: trusted rules could not be loaded: ${RuntimeErrorSanitizer.sanitize(ex)}"
            return
        }

        String diff = diffFile.getText('UTF-8')

        stageStartedAt = System.nanoTime()
        DiffSizeGuard sizeGuard = new DiffSizeGuard(inputs.maxDiffBytes, inputs.maxDiffLines)
        DiffSizeGuard.DiffSizeDecision size = sizeGuard.evaluate(diff)
        if (!size.acceptable) {
            timings.put('diff_analysis', elapsedMillis(stageStartedAt))
            writer.writeTooLarge(inputs.outputPath, size.reason, size.bytes, size.lines,
                inputs.maxDiffBytes, inputs.maxDiffLines)
            println "Code Review Agent: diff too large (${size.bytes}B/${size.lines}L), wrote warning."
            return
        }

        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)
        timings.put('diff_analysis', elapsedMillis(stageStartedAt))
        if (!analyzer.hasChanges()) {
            timings.put('total_internal', elapsedMillis(totalStartedAt))
            writer.writeEmpty(inputs.outputPath)
            println 'Code Review Agent: no code changes detected, wrote empty review.'
            return
        }

        stageStartedAt = System.nanoTime()
        ReviewPromptBuilder promptBuilder = new ReviewPromptBuilder()
        ReviewRequest request = promptBuilder.buildRequest(rules, diff, inputs.language)
        timings.put('prompt_building', elapsedMillis(stageStartedAt))

        stageStartedAt = System.nanoTime()
        ReviewTrace trace = ReviewTrace.create(
            inputs.repository,
            inputs.pullRequest,
            inputs.baseSha,
            inputs.headSha,
            analyzer,
            diff,
            rules,
            request,
            inputs.model,
            performanceOnly ? 0 : GeminiAdapter.MAX_OUTPUT_TOKENS
        )
        timings.put('trace_building', elapsedMillis(stageStartedAt))
        trace.log()

        if (performanceOnly) {
            timings.put('total_internal', elapsedMillis(totalStartedAt))
            writePerformanceReport(inputs.outputPath, timings)
            println 'Code Review Agent performance-only run completed.'
            return
        }

        AiProvider provider
        stageStartedAt = System.nanoTime()
        try {
            provider = AiProviderFactory.create(inputs.provider, inputs.apiKey, inputs.model)
            timings.put('provider_creation', elapsedMillis(stageStartedAt))
        } catch (IllegalArgumentException ex) {
            timings.put('provider_creation', elapsedMillis(stageStartedAt))
            writer.writeMisconfigured(inputs.outputPath, ex.message)
            println "Code Review Agent: ${RuntimeErrorSanitizer.sanitize(ex)}"
            return
        }

        stageStartedAt = System.nanoTime()
        try {
            String text = provider.review(request)
            timings.put('provider_review', elapsedMillis(stageStartedAt))

            stageStartedAt = System.nanoTime()
            writer.writeAiGenerated(
                inputs.outputPath,
                ReviewResponseJsonParser.parse(text),
                inputs.language
            )
            timings.put('response_processing', elapsedMillis(stageStartedAt))
            timings.put('total_internal', elapsedMillis(totalStartedAt))
            println "Code Review Agent: review written to ${inputs.outputPath} using ${provider.type().configName}/${inputs.model}."
        } catch (AiProviderException ex) {
            timings.put('provider_review', elapsedMillis(stageStartedAt))
            timings.put('total_internal', elapsedMillis(totalStartedAt))
            writer.writeFailure(inputs.outputPath, ex.userMessage)
            println "Code Review Agent: ${provider.type().configName} failure [${ex.category}]: ${RuntimeErrorSanitizer.sanitize(ex.cause ?: ex)}"
        } catch (JsonException ex) {
            timings.put('response_processing', elapsedMillis(stageStartedAt))
            timings.put('total_internal', elapsedMillis(totalStartedAt))
            writer.writeFailure(
                inputs.outputPath,
                'The AI provider returned malformed JSON.'
            )
            println "Code Review Agent: malformed JSON review response: ${RuntimeErrorSanitizer.sanitize(ex)}"
        } catch (IllegalArgumentException ex) {
            timings.put('response_processing', elapsedMillis(stageStartedAt))
            timings.put('total_internal', elapsedMillis(totalStartedAt))
            writer.writeFailure(
                inputs.outputPath,
                'The AI provider returned JSON that does not match the review contract.'
            )
            println "Code Review Agent: invalid JSON review response: ${RuntimeErrorSanitizer.sanitize(ex)}"
        } catch (Exception ex) {
            timings.put('response_processing', elapsedMillis(stageStartedAt))
            timings.put('total_internal', elapsedMillis(totalStartedAt))
            String userMessage = "The AI provider (**${provider.type().configName}**, model `${inputs.model}`) failed unexpectedly. Check the workflow log for the technical error and retry."
            writer.writeFailure(inputs.outputPath, userMessage)
            println "Code Review Agent: unexpected failure: ${RuntimeErrorSanitizer.sanitize(ex)}"
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (long) ((System.nanoTime() - startedAt) / 1_000_000L)
    }

    private static void writePerformanceReport(String outputPath, LinkedHashMap<String, Long> timings) {
        StringBuilder report = new StringBuilder()
        report.append('<!-- code-review-agent-by-boghus -->\n')
        report.append('## Code Review Agent performance measurement\n\n')
        report.append('This QA run measures internal preparation time without calling the AI provider.\n\n')
        report.append('| Stage | Duration |\n| --- | ---: |\n')
        for (Map.Entry<String, Long> entry : timings.entrySet()) {
            report.append('| ').append(entry.key).append(' | ').append(entry.value).append(' ms |\n')
        }
        new File(outputPath).setText(report.toString(), 'UTF-8')
    }
}
