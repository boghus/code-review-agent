package com.boghus.codereview

import com.boghus.codereview.github.ActionInputs
import com.boghus.codereview.output.ReviewReportWriter
import com.boghus.codereview.provider.AiProvider
import com.boghus.codereview.provider.AiProviderException
import com.boghus.codereview.provider.AiProviderFactory
import com.boghus.codereview.provider.ReviewRequest
import com.boghus.codereview.provider.RuntimeErrorSanitizer
import com.boghus.codereview.review.DiffAnalyzer
import com.boghus.codereview.review.DiffSizeGuard
import com.boghus.codereview.review.ReviewPromptBuilder
import groovy.transform.CompileStatic

/**
 * Orchestrator. Reads inputs, builds the structured review request, calls the
 * AI provider and writes the resulting markdown. Posting the comment is
 * delegated to the composite action steps (peter-evans).
 *
 * <p>The orchestrator is provider-agnostic: it only knows about
 * {@link AiProvider} and {@link AiProviderException}. Adapters are
 * responsible for mapping trusted instructions and untrusted repository
 * content to their provider's available channels.</p>
 *
 * <p>The orchestrator never throws on AI-side failures. It writes a failure
 * report and exits 0 so the PR is never blocked by an unavailable provider.</p>
 */
@CompileStatic
class CodeReview {

    static void main(String[] args) {
        ActionInputs inputs = ActionInputs.fromEnv()
        ReviewReportWriter writer = new ReviewReportWriter()

        if (!inputs.apiKey?.trim()) {
            writer.writeMisconfigured(inputs.outputPath,
                'The `api-key` input was not provided. Map your repository secret to it, e.g. `api-key: ${{ secrets.MY_AI_KEY }}`.')
            println 'Code Review Agent: api-key missing, wrote misconfiguration report.'
            return
        }

        File diffFile = new File(inputs.diffPath)
        if (!diffFile.exists() || !diffFile.text.trim()) {
            writer.writeEmpty(inputs.outputPath)
            println 'Code Review Agent: empty diff, wrote empty review.'
            return
        }

        String rules = new File(inputs.rulesPath).exists() ? new File(inputs.rulesPath).getText('UTF-8') : ''
        String diff = diffFile.getText('UTF-8')

        DiffSizeGuard sizeGuard = new DiffSizeGuard(inputs.maxDiffBytes, inputs.maxDiffLines)
        DiffSizeGuard.DiffSizeDecision size = sizeGuard.evaluate(diff)
        if (!size.acceptable) {
            writer.writeTooLarge(inputs.outputPath, size.reason, size.bytes, size.lines,
                inputs.maxDiffBytes, inputs.maxDiffLines)
            println "Code Review Agent: diff too large (${size.bytes}B/${size.lines}L), wrote warning."
            return
        }

        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)
        if (!analyzer.hasChanges()) {
            writer.writeEmpty(inputs.outputPath)
            println 'Code Review Agent: no code changes detected, wrote empty review.'
            return
        }

        AiProvider provider
        try {
            provider = AiProviderFactory.create(inputs.provider, inputs.apiKey, inputs.model)
        } catch (IllegalArgumentException ex) {
            writer.writeMisconfigured(inputs.outputPath, ex.message)
            println "Code Review Agent: ${RuntimeErrorSanitizer.sanitize(ex)}"
            return
        }

        ReviewPromptBuilder promptBuilder = new ReviewPromptBuilder()
        ReviewRequest request = promptBuilder.buildRequest(rules, diff, inputs.language)

        try {
            String text = provider.review(request)
            writer.writeAiGenerated(inputs.outputPath, text)
            println "Code Review Agent: review written to ${inputs.outputPath} using ${provider.type().configName}/${inputs.model}."
        } catch (AiProviderException ex) {
            writer.writeFailure(inputs.outputPath, ex.userMessage)
            println "Code Review Agent: ${provider.type().configName} failure [${ex.category}]: ${RuntimeErrorSanitizer.sanitize(ex.cause ?: ex)}"
        } catch (Exception ex) {
            String userMessage = "The AI provider (**${provider.type().configName}**, model `${inputs.model}`) failed unexpectedly. Check the workflow log for the technical error and retry."
            writer.writeFailure(inputs.outputPath, userMessage)
            println "Code Review Agent: unexpected failure: ${RuntimeErrorSanitizer.sanitize(ex)}"
        }
    }
}
