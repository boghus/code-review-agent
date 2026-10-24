package com.boghus.codereview.provider

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * OpenRouter implementation using its OpenAI-compatible Chat Completions API.
 * Uses the JDK HTTP client to avoid adding another SDK dependency.
 */
class OpenRouterAdapter implements AiProvider {
    static final String API_URL = 'https://openrouter.ai/api/v1/chat/completions'
    static final int MAX_OUTPUT_TOKENS = 8192

    private final HttpClient client
    private final String apiKey
    private final String model

    OpenRouterAdapter(String apiKey, String model) {
        if (!apiKey?.trim()) {
            throw new IllegalArgumentException('OpenRouter API key must not be blank.')
        }
        if (!model?.trim()) {
            throw new IllegalArgumentException('OpenRouter model must not be blank.')
        }
        this.apiKey = apiKey.trim()
        this.model = model.trim()
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
    }

    @Override
    AiProviderType type() {
        return AiProviderType.OPENROUTER
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

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .timeout(Duration.ofSeconds(180))
            .header('Authorization', "Bearer ${apiKey}")
            .header('Content-Type', 'application/json')
            .header('HTTP-Referer', 'https://github.com/boghus/code-review-agent')
            .header('X-OpenRouter-Title', 'Code Review Agent by boghus')
            .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(buildPayload(request, model))))
            .build()

        try {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            int status = response.statusCode()
            if (status < 200 || status >= 300) {
                throw translateStatus(status)
            }

            Object parsed
            try {
                parsed = new JsonSlurper().parseText(response.body())
            } catch (Exception ex) {
                throw new AiProviderException(
                    AiProviderErrorCategory.UNKNOWN,
                    'OpenRouter returned an unreadable response. Check the workflow log and retry.',
                    ex
                )
            }

            String content = extractContent(parsed)
            if (!content?.trim()) {
                throw new AiProviderException(
                    AiProviderErrorCategory.UNKNOWN,
                    "OpenRouter responded successfully but returned no content for model '${model}'.",
                    null
                )
            }
            return content.trim()
        } catch (AiProviderException ex) {
            throw ex
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt()
            throw new AiProviderException(
                AiProviderErrorCategory.NETWORK,
                'The OpenRouter request was interrupted before the review completed.',
                ex
            )
        } catch (IOException ex) {
            throw new AiProviderException(
                AiProviderErrorCategory.NETWORK,
                'OpenRouter could not be reached. Check the workflow log and retry.',
                ex
            )
        } catch (RuntimeException ex) {
            throw new AiProviderException(
                AiProviderErrorCategory.UNKNOWN,
                "OpenRouter could not complete the review with model '${model}'. Check the workflow log and retry.",
                ex
            )
        }
    }

    static Map<String, Object> buildPayload(ReviewRequest request, String model) {
        return [
            model      : model,
            temperature: 0.1,
            max_tokens : MAX_OUTPUT_TOKENS,
            messages   : [
                [role: 'system', content: "${request.systemInstructions}\n\n${request.developerInstructions}"],
                [role: 'user', content: "${request.prompt}\n\n${request.untrustedRepositoryContent}"]
            ]
        ] as Map<String, Object>
    }

    static String extractContent(Object response) {
        if (!(response instanceof Map)) return null
        Object choices = ((Map) response).get('choices')
        if (!(choices instanceof List) || ((List) choices).isEmpty()) return null
        Object firstChoice = ((List) choices).get(0)
        if (!(firstChoice instanceof Map)) return null
        Object message = ((Map) firstChoice).get('message')
        if (!(message instanceof Map)) return null
        Object content = ((Map) message).get('content')
        return content instanceof String ? (String) content : null
    }

    private static AiProviderException translateStatus(int status) {
        if (status == 401 || status == 403) {
            return new AiProviderException(
                AiProviderErrorCategory.AUTHENTICATION,
                'OpenRouter authentication failed. Verify the API key configured in the api-key input.',
                null
            )
        }
        if (status == 429) {
            return new AiProviderException(
                AiProviderErrorCategory.QUOTA,
                'OpenRouter rate limit or quota reached. Wait for the limit to reset or choose another available model.',
                null
            )
        }
        if (status == 400 || status == 404 || status == 422) {
            return new AiProviderException(
                AiProviderErrorCategory.INVALID_REQUEST,
                "OpenRouter rejected the request (HTTP ${status}). Verify the model identifier and request configuration.",
                null
            )
        }
        if (status == 408 || status == 504) {
            return new AiProviderException(
                AiProviderErrorCategory.TIMEOUT,
                'OpenRouter did not complete the request in time. Retry when the service is available.',
                null
            )
        }
        return new AiProviderException(
            AiProviderErrorCategory.UNKNOWN,
            "OpenRouter could not complete the review (HTTP ${status}). Check the workflow log and retry.",
            null
        )
    }
}
