package com.boghus.codereview.provider

/**
 * Supported instruction-channel configurations for a provider/model adapter.
 *
 * Each configuration is explicit, preventing invalid combinations of channel
 * flags and making provider capabilities easy to compare and extend.
 */
enum AiProviderCapabilities {
    /** All trusted instructions and repository content must use one prompt channel. */
    PROMPT_ONLY(true, false, false),

    /** Use a native system channel; developer instructions remain trusted system content. */
    SYSTEM_PROMPT(true, true, false),

    /** Use native system and developer channels; repository content remains user content. */
    SYSTEM_DEVELOPER_PROMPT(true, true, true)

    final boolean prompt
    final boolean system
    final boolean developer

    AiProviderCapabilities(boolean prompt, boolean system, boolean developer) {
        this.prompt = prompt
        this.system = system
        this.developer = developer
    }
}
