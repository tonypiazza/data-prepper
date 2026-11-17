/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 */

package org.opensearch.dataprepper.plugins.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.GoogleCredentials;
import org.opensearch.dataprepper.plugins.secrets.SecretStoreAccessor;
import org.opensearch.dataprepper.plugins.secrets.SecretStoreException;
import org.opensearch.dataprepper.plugins.secrets.SecretVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

/**
 * A {@link SecretStoreAccessor} for GCP Secret Manager using its v1 REST API.
 */
class GcpSecretManagerAccessor implements SecretStoreAccessor {
    static final URI DEFAULT_ENDPOINT = URI.create("https://secretmanager.googleapis.com/");
    static final int MAX_ATTEMPTS = 3;
    static final long BASE_BACKOFF_MILLIS = 500L;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String NO_ERROR_MESSAGE = "no error message";
    private static final Logger LOG = LoggerFactory.getLogger(GcpSecretManagerAccessor.class);

    private final GoogleCredentials credentials;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;

    GcpSecretManagerAccessor(final GoogleCredentials credentials) {
        this(credentials, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), DEFAULT_ENDPOINT,
                new ObjectMapper(), Thread::sleep);
    }

    GcpSecretManagerAccessor(final GoogleCredentials credentials,
                             final HttpClient httpClient,
                             final URI endpoint,
                             final ObjectMapper objectMapper,
                             final Sleeper sleeper) {
        this.credentials = credentials;
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
    }

    @Override
    public SecretVersion accessLatest(final String secretName) {
        final JsonNode response = send("GET", secretName + "/versions/latest:access", null);
        final String versionName = response.path("name").asText();
        final JsonNode payload = response.path("payload");
        final byte[] data;
        try {
            data = Base64.getDecoder().decode(payload.path("data").asText());
        } catch (final IllegalArgumentException e) {
            throw new SecretStoreException(SecretStoreException.Reason.OTHER, String.format(
                    "GCP Secret Manager returned a payload that is not valid base64 for secret %s", secretName), e);
        }
        final JsonNode checksum = payload.get("dataCrc32c");
        if (checksum != null && checksum.asLong() != crc32c(data)) {
            throw new SecretStoreException(SecretStoreException.Reason.OTHER, String.format(
                    "Checksum mismatch for secret %s version %s", secretName, versionName));
        }
        return new SecretVersion(versionName, data);
    }

    @Override
    public String addVersion(final String secretName, final byte[] payload) {
        final ObjectNode body = objectMapper.createObjectNode();
        final ObjectNode payloadNode = body.putObject("payload");
        payloadNode.put("data", Base64.getEncoder().encodeToString(payload));
        payloadNode.put("dataCrc32c", Long.toString(crc32c(payload)));
        return send("POST", secretName + ":addVersion", body.toString()).path("name").asText();
    }

    @Override
    public boolean supportsVersionDestroy() {
        return true;
    }

    @Override
    public void destroyVersion(final String versionName) {
        send("POST", versionName + ":destroy", "{}");
    }

    @Override
    public void close() {
    }

    private JsonNode send(final String method, final String path, final String body) {
        final URI uri = endpoint.resolve("v1/" + path);
        SecretStoreException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                backoff(attempt);
            }
            try {
                final HttpResponse<String> response =
                        httpClient.send(buildRequest(uri, body), HttpResponse.BodyHandlers.ofString());
                final int status = response.statusCode();
                if (status / 100 == 2) {
                    return parse(response.body(), method, path);
                }
                lastFailure = new SecretStoreException(toReason(status), String.format(
                        "GCP Secret Manager request %s %s returned HTTP %d: %s",
                        method, path, status, errorMessage(response.body())));
                if (!isRetryable(status)) {
                    throw lastFailure;
                }
            } catch (final IOException e) {
                lastFailure = new SecretStoreException(SecretStoreException.Reason.OTHER, String.format(
                        "GCP Secret Manager request %s %s failed", method, path), e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SecretStoreException(SecretStoreException.Reason.OTHER, String.format(
                        "Interrupted during GCP Secret Manager request %s %s", method, path), e);
            }
            LOG.warn("GCP Secret Manager request {} {} failed on attempt {} of {}.", method, path, attempt, MAX_ATTEMPTS);
        }
        throw lastFailure;
    }

    private HttpRequest buildRequest(final URI uri, final String body) throws IOException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        for (final Map.Entry<String, List<String>> header : credentials.getRequestMetadata(uri).entrySet()) {
            for (final String value : header.getValue()) {
                builder.header(header.getKey(), value);
            }
        }
        if (body == null) {
            return builder.GET().build();
        }
        return builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private void backoff(final int attempt) {
        try {
            sleeper.sleep(BASE_BACKOFF_MILLIS * (1L << (attempt - 2)));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecretStoreException(SecretStoreException.Reason.OTHER,
                    "Interrupted while retrying a GCP Secret Manager request", e);
        }
    }

    private JsonNode parse(final String body, final String method, final String path) {
        try {
            return objectMapper.readTree(body);
        } catch (final JsonProcessingException e) {
            throw new SecretStoreException(SecretStoreException.Reason.OTHER, String.format(
                    "GCP Secret Manager returned an invalid response for %s %s", method, path), e);
        }
    }

    private String errorMessage(final String body) {
        try {
            final JsonNode message = objectMapper.readTree(body).path("error").path("message");
            return message.isTextual() ? message.asText() : NO_ERROR_MESSAGE;
        } catch (final JsonProcessingException e) {
            return NO_ERROR_MESSAGE;
        }
    }

    private static SecretStoreException.Reason toReason(final int status) {
        if (status == 404) {
            return SecretStoreException.Reason.NOT_FOUND;
        }
        if (status == 429) {
            return SecretStoreException.Reason.LIMIT_EXCEEDED;
        }
        if (status == 401 || status == 403) {
            return SecretStoreException.Reason.ACCESS_DENIED;
        }
        return SecretStoreException.Reason.OTHER;
    }

    private static boolean isRetryable(final int status) {
        return status == 429 || status >= 500;
    }

    private static long crc32c(final byte[] data) {
        final CRC32C crc32c = new CRC32C();
        crc32c.update(data);
        return crc32c.getValue();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
