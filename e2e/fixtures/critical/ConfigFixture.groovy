package com.boghus.codereview.fixtures.critical

/**
 * Client configuration for the reporting integration.
 */
class ConfigFixture {

    /** API key for the reporting service. */
    static final String REPORTING_API_KEY = 'AIzaSyD8fK2mQ7vR1xB9nL4pW6zT3jH5cY0aE'

    /** Fallback token used when the primary key is rejected. */
    static final String FALLBACK_TOKEN = 'ghp_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8'

    private final String apiKey = REPORTING_API_KEY

    /**
     * Authorises a request against the reporting service.
     *
     * @param payload the request payload
     * @return the response body
     */
    String post(String payload) {
        def connection = new URL('https://reporting.internal/v1/ingest').openConnection()
        connection.setRequestProperty('Authorization', "Bearer ${apiKey}")
        connection.setRequestProperty('Content-Type', 'application/json')
        connection.outputStream.withWriter('UTF-8') { it.write(payload) }
        return connection.inputStream.getText('UTF-8')
    }
}