package com.boghus.codereview.provider

import java.util.function.BiFunction

/**
 * Factory that resolves an {@link AiProvider} from the configured provider name.
 *
 * The provider abstraction is intentionally minimal: name + review. Adding a
 * new adapter only requires registering it here.
 */
class AiProviderFactory {

    private static final Map<String, BiFunction<String, String, AiProvider>> REGISTRY = [
        (GeminiAdapter.PROVIDER_NAME): { String apiKey, String model -> new GeminiAdapter(apiKey, model) }
    ]

    static AiProvider create(String providerName, String apiKey, String model) {
        if (!providerName?.trim()) {
            throw new IllegalArgumentException('Provider name must not be blank.')
        }
        BiFunction<String, String, AiProvider> builder = REGISTRY[providerName]
        if (builder == null) {
            throw new IllegalArgumentException(
                "Unsupported provider '${providerName}'. Supported: ${REGISTRY.keySet().join(', ')}."
            )
        }
        return builder.apply(apiKey, model)
    }

    static Set<String> supportedProviders() {
        return REGISTRY.keySet() as Set<String>
    }
}
