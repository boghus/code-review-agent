package com.boghus.codereview

import com.boghus.codereview.github.ActionInputs
import com.boghus.codereview.output.ReviewReportWriter
import com.boghus.codereview.provider.AiProvider
import com.boghus.codereview.provider.AiProviderException
import com.boghus.codereview.provider.AiProviderFactory
import com.boghus.codereview.review.DiffAnalyzer
import com.boghus.codereview.review.DiffSizeGuard
import com.boghus.codereview.review.ReviewPromptBuilder
import groovy.transform.CompileStatic

/**
 * Orchestrator. Reads inputs, builds the prompt, calls the AI provider and
 * writes the resulting markdown. Posting the comment is delegated to the
 * composite action steps (peter-evans).
 *
 * <p>The orchestrator is provider-agnostic: it only knows about
 * {@link AiProvider} and {@link AiProviderException}. Adapters are
 * responsible for translating their own SDK-specific failures into
 * {@link AiProviderException} with a safe user-facing message.</p>
 *
 * <p>The orchestrator never throws on AI-side failures. It writes a failure
 * report and exits 0 so the PR is never blocked by an unavailable provider.</p>
 */
@CompileStatic
class CodeReview {

    static void main(String[] args) {
        ActionInputs inputs = ActionInputs.fromEnv()
        ReviewReportWriter writer = new ReviewReportWriter(inputs.actionRef, inputs.actionSha)

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
            println "Code Review Agent: ${ex.message}"
            return
        }

        String prompt = new ReviewPromptBuilder().build(rules, diff)

        try {
            String text = provider.review(prompt)
            writer.write(inputs.outputPath, text)
            println "Code Review Agent: review written to ${inputs.outputPath} using ${provider.name()}/${inputs.model}."
        } catch (AiProviderException ex) {
            writer.writeFailure(inputs.outputPath, ex.userMessage)
            println "Code Review Agent: ${provider.name()} failure [${ex.category}]: ${ex.cause?.message ?: ex.message}"
        } catch (Exception ex) {
            String userMessage = "The AI provider (**${provider.name()}**, model `${inputs.model}`) failed unexpectedly. Check the workflow log for the technical error and retry."
            writer.writeFailure(inputs.outputPath, userMessage)
            String safe = ex.message?.replace('`', "'") ?: 'unknown error'
            println "Code Review Agent: unexpected failure: ${safe}"
        }
    }
}
