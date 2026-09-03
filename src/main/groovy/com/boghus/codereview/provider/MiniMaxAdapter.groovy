package com.boghus.codereview.provider

import com.boghus.minimax.client.MiniMaxClient
import com.boghus.minimax.exception.MiniMaxException
import com.boghus.minimax.model.ChatRequest
import com.boghus.minimax.model.Message
import groovy.transform.CompileStatic

/**
 * MiniMax implementation of the provider-agnostic AI contract.
 *
 * Uses the small local MiniMax Java client and the OpenAI-compatible Chat API.
 */
@CompileStatic
class MiniMaxAdapter implements AiProvider {
    private final MiniMaxClient client
    private final String model

    MiniMaxAdapter(String apiKey, String model) {
        if (!apiKey?.trim()) throw new IllegalArgumentException('MiniMax API key must not be blank.')
        if (!model?.trim()) throw new IllegalArgumentException('MiniMax model must not be blank.')
        this.client = new MiniMaxClient(apiKey)
        this.model = model
    }

    @Override
    AiProviderType type() {
        return AiProviderType.MINIMAX
    }

    @Override
    String review(String prompt) {
        try {
            return client.chat(
                new ChatRequest(
                    model,
                    [new Message('user', prompt)]
                )
            ).content
        } catch (MiniMaxException ex) {
            throw translate(ex, model)
        } catch (RuntimeException ex) {
            throw new AiProviderException(
                AiProviderErrorCategory.UNKNOWN,
                "MiniMax could not complete the review with **${model}**. Check the workflow log for the technical error and retry.",
                ex
            )
        }
    }

    static AiProviderException translate(MiniMaxException exception, String model) {
        AiProviderErrorCategory category
        String message
        switch (exception.statusCode) {
            case 401:
            case 403:
            case 2049:
                category = AiProviderErrorCategory.AUTHENTICATION
                message = "Authentication failed for **${model}**. Check that the API key provided to the `api-key` input is valid and has access to this model."
                break
            case 429:
                category = AiProviderErrorCategory.QUOTA
                message = "Quota exhausted for **${model}**. Reduce PR size or wait for the provider quota window to reset."
                break
            default:
                category = AiProviderErrorCategory.UNKNOWN
                message = "MiniMax could not complete the review with **${model}**. Check the workflow log for the technical error and retry when the provider is reachable again."
        }
        return new AiProviderException(category, message, exception)
    }
}
