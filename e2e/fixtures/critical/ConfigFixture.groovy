package com.boghus.codereview.fixtures.critical

/**
 * E2E fixture for the `critical` scenario.
 *
 * This file deliberately hardcodes an API key so the reviewer must report a
 * CRITICAL finding. The value is a non-functional placeholder, not a real
 * credential: it only needs to match the shape the rules reject.
 */
class ConfigFixture {

    /** Intentionally hardcoded so the scenario can detect the finding. */
    static final String API_KEY = 'AIzaSyFIXTUREPLACEHOLDER000000'

    /** Intentionally hardcoded so the scenario can detect a second finding. */
    static final String BACKUP_TOKEN = 'ghp_FIXTUREPLACEHOLDER0000000000'

    /**
     * Returns the configured key.
     *
     * @return the hardcoded key
     */
    String key() {
        return API_KEY
    }
}