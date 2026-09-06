package com.boghus.codereview.provider

import com.google.genai.Client
import com.google.genai.errors.ApiException
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import com.google.genai.types.Part
import groovy.transform.CompileStatic

import java.util.regex.Matcher

/**
 * Gemini implementation of {@link AiProvider}.
 *
 * Gemini exposes a system-instruction channel but not a separate developer
 * channel through GenerateContentConfig. Trusted system and developer
 * instructions are therefore combined into systemInstruction, while the
 * prompt and untrusted repository content remain in the user content.
 */
@CompileStatic
class GeminiAdapter implements AiProvider {

    static final int MAX_OUTPUT_TOKENS = 8192

    private final Client client
    private final String model

    GeminiAdapter(String apiKey, String model) {
        if (!apiKey?.trim()) {
            throw new IllegalArgumentException('Gemini API key must not be blank.')
        }
        if (!model?.trim()) {
            throw new IllegalArgumentException('Gemini model must not be blank.')
        }
        this.model = model
        this.client = Client.builder()
            .apiKey(apiKey)
            .httpOptions(
                HttpOptions.builder()
                    .timeout(60_000)
                    .retryOptions(
                        HttpRetryOptions.builder()
                            .attempts(3)
                            .httpStatusCodes(408, 429, 500, 502, 503, 504)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    @Override
    AiProviderType type() {
        return AiProviderType.GEMINI
    }

    @Override
    AiProviderCapabilities capabilities() {
        return AiProviderCapabilities.SYSTEM_PROMPT
    }

    @Override
    String review(ReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException('Review request must not be null.')
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
            .systemInstruction(buildSystemInstruction(request))
            .temperature(0.1f)
            .maxOutputTokens(MAX_OUTPUT_TOKENS)
            .build()

        try {
            GenerateContentResponse response = client.models.generateContent(model, buildUserContent(request), config)
            String text = response.text()?.trim()
            if (!text) {
                throw new AiProviderException(
                    AiProviderErrorCategory.UNKNOWN,
                    'Gemini responded successfully but returned no content for the review.',
                    null
                )
            }
            return text
        } catch (ApiException ex) {
            throw translate(ex)
        } catch (RuntimeException ex) {
            throw new AiProviderException(
                AiProviderErrorCategory.UNKNOWN,
                "Gemini could not complete the review with **${model}**. Check the workflow log for the technical error and retry.",
                ex
            )
        }
    }

    static Content buildSystemInstruction(ReviewRequest request) {
        return Content.fromParts(
            Part.fromText("""${request.systemInstructions}

${request.developerInstructions}""")
        )
    }

    static String buildUserContent(ReviewRequest request) {
        return """${request.prompt}

${request.untrustedRepositoryContent}"""
    }

    /**
     * Maps a Gemini SDK exception into an {@link AiProviderException},
     * preserving the user-safe category for the PR comment while keeping
     * the original cause for the runner log.
     */
    private static AiProviderException translate(ApiException ex) {
        AiProviderErrorCategory category
        String message
        if (isQuotaError(ex)) {
            category = AiProviderErrorCategory.QUOTA
            message = "Quota exhausted for **${ex instanceof ApiException ? safeModelFor(ex) : 'gemini'}**. The request will not be retried again automatically. Reduce PR size or wait for the quota window to reset."
        } else if (isAuthError(ex)) {
            category = AiProviderErrorCategory.AUTHENTICATION
            message = "Authentication failed for **${safeModelFor(ex)}**. Check that the API key provided to the `api-key` input is valid and has access to this model."
        } else {
            category = AiProviderErrorCategory.UNKNOWN
            message = "Gemini could not complete the review with **${safeModelFor(ex)}**. Check the workflow log for the technical error and retry when the provider is reachable again."
        }
        return new AiProviderException(category, message, ex)
    }

    private static String safeModelFor(ApiException ex) {
        return 'gemini'
    }

    static String categorizeError(ApiException exception, String model) {
        AiProviderException translated = translate(exception)
        return translated.userMessage.replace('**gemini**', "**${model}**")
    }

    static String safeMessageForLog(ApiException exception) {
        String msg = exception.message
        if (!msg) return 'unknown error'
        return msg.replace('`', "'")
    }

    private static boolean isQuotaError(ApiException exception) {
        return extractHttpStatus(exception) == 429
    }

    private static boolean isAuthError(ApiException exception) {
        int status = extractHttpStatus(exception)
        return status == 401 || status == 403
    }

    private static int extractHttpStatus(ApiException exception) {
        String message = exception.message
        if (!message) return 0
        Matcher matcher = (message =~ /^\s*(\d{3})\s/)
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1))
            } catch (NumberFormatException ignored) {
                return 0
            }
        }
        return 0
    }
}
