package com.boghus.minimax.client

import com.boghus.minimax.exception.MiniMaxException
import com.boghus.minimax.model.ChatRequest
import com.boghus.minimax.model.ChatResponse
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@CompileStatic
class MiniMaxClient {
    static final String DEFAULT_BASE_URL = 'https://api.minimax.io/v1'

    private final HttpClient httpClient
    private final String apiKey
    private final String baseUrl
    private final Duration timeout

    public MiniMaxClient(String apiKey, String baseUrl = DEFAULT_BASE_URL, Duration timeout = Duration.ofSeconds(60)) {
        if (!apiKey?.trim()) throw new IllegalArgumentException('MiniMax API key must not be blank.')
        if (!baseUrl?.trim()) throw new IllegalArgumentException('MiniMax base URL must not be blank.')
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException('MiniMax timeout must be positive.')
        }
        this.apiKey = apiKey
        this.baseUrl = baseUrl.replaceAll('/+$', '')
        this.timeout = timeout
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build()
    }

    public ChatResponse chat(ChatRequest request) {
        if (request == null) throw new IllegalArgumentException('Chat request must not be null.')

        String payload = JsonOutput.toJson([
            model: request.model,
            messages: request.messages.collect { [role: it.role, content: it.content] },
            temperature: request.temperature,
            max_completion_tokens: request.maxCompletionTokens,
            stream: false
        ])

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl}/chat/completions"))
            .timeout(timeout)
            .header('Authorization', "Bearer ${apiKey}")
            .header('Content-Type', 'application/json')
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            return parseResponse(response.statusCode(), response.body(), request.model)
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt()
            throw new MiniMaxException('MiniMax request was interrupted.', 0, ex)
        } catch (IOException ex) {
            throw new MiniMaxException('MiniMax request could not be completed.', 0, ex)
        }
    }

    private static ChatResponse parseResponse(int statusCode, String body, String requestedModel) {
        Object parsed
        try {
            parsed = new JsonSlurper().parseText(body ?: '')
        } catch (RuntimeException ex) {
            throw new MiniMaxException("MiniMax returned invalid JSON (HTTP ${statusCode}).", statusCode, ex)
        }

        if (statusCode < 200 || statusCode >= 300) {
            String providerMessage = extractErrorMessage(parsed)
            throw new MiniMaxException("MiniMax request failed (HTTP ${statusCode})${providerMessage ? ": ${providerMessage}" : '.'}", statusCode)
        }

        if (!(parsed instanceof Map)) {
            throw new MiniMaxException('MiniMax returned an unexpected response format.', statusCode)
        }

        Map response = (Map) parsed
        Map baseResp = response.base_resp instanceof Map ? (Map) response.base_resp : [:]
        Number providerStatus = baseResp.status_code instanceof Number ? (Number) baseResp.status_code : 0
        if (providerStatus.intValue() != 0) {
            String message = baseResp.status_msg?.toString() ?: 'MiniMax returned an error.'
            throw new MiniMaxException(message, providerStatus.intValue())
        }

        List choices = response.choices instanceof List ? (List) response.choices : []
        Map firstChoice = choices ? (Map) choices[0] : [:]
        Map message = firstChoice.message instanceof Map ? (Map) firstChoice.message : [:]
        String content = message.content?.toString()?.trim()
        if (!content) {
            throw new MiniMaxException('MiniMax responded successfully but returned no content.', statusCode)
        }

        return new ChatResponse(content, response.model?.toString() ?: requestedModel)
    }

    private static String extractErrorMessage(Object parsed) {
        if (!(parsed instanceof Map)) return null
        Map body = (Map) parsed
        Map error = body.error instanceof Map ? (Map) body.error : [:]
        if (error.message) return error.message.toString()
        Map baseResp = body.base_resp instanceof Map ? (Map) body.base_resp : [:]
        return baseResp.status_msg?.toString()
    }
}
