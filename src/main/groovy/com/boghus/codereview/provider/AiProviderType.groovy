package com.boghus.codereview.provider

import groovy.transform.CompileStatic

@CompileStatic
enum AiProviderType {
    GEMINI('gemini'),
    OPENROUTER('openrouter')

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
