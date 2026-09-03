package com.boghus.codereview.provider

import groovy.transform.CompileStatic

/**
 * Supported AI provider types.
 *
 * The configuration-facing name is kept at the boundary while the domain
 * uses this enum to avoid provider magic strings internally.
 */
@CompileStatic
enum AiProviderType {
    GEMINI('gemini')

    final String configName

    AiProviderType(String configName) {
        this.configName = configName
    }

    static AiProviderType fromConfigName(String value) {
        if (!value?.trim()) {
            throw new IllegalArgumentException('Provider name must not be blank.')
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
        for (AiProviderType type : values()) {
            if (type.configName == normalized) {
                return type
            }
        }
        throw new IllegalArgumentException(
            "Unsupported provider '${value}'. Supported: ${values()*.configName.join(', ')}."
        )
    }
}
