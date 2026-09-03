package com.boghus.codereview.provider

import java.util.function.BiFunction

/**
 * Factory that resolves an {@link AiProvider} from the configured provider name.
 *
 * The configuration boundary accepts a String, but provider resolution uses
 * {@link AiProviderType} internally so provider names are not magic strings
 * throughout the domain.
 */
class AiProviderFactory {

    private static final Map<AiProviderType, BiFunction<String, String, AiProvider>> REGISTRY = [
        (AiProviderType.GEMINI): { String apiKey, String model -> new GeminiAdapter(apiKey, model) }
    ]

    static AiProvider create(String providerName, String apiKey, String model) {
        return create(AiProviderType.fromConfigName(providerName), apiKey, model)
    }

    static AiProvider create(AiProviderType providerType, String apiKey, String model) {
        BiFunction<String, String, AiProvider> builder = REGISTRY[providerType]
        if (builder == null) {
            throw new IllegalArgumentException(
                "Unsupported provider '${providerType}'. Supported: ${supportedProviders().join(', ')}."
            )
        }
        return builder.apply(apiKey, model)
    }

    static Set<String> supportedProviders() {
        return REGISTRY.keySet()*.configName as Set<String>
    }
}
