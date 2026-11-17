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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.plugins.secrets.CachingSecretsSupplier;
import org.opensearch.dataprepper.plugins.secrets.SecretStore;
import org.opensearch.dataprepper.plugins.secrets.SecretStoreException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "tests.gcp.secrets.emulator.endpoint", matches = ".+")
class GcpSecretManagerEmulatorIT {
    private static final String PROJECT = "floci-local";
    private static final String STORE = "emulator-store";
    private static final ObjectMapper JSON = new ObjectMapper();

    private URI endpoint;
    private HttpClient httpClient;
    private GcpSecretManagerAccessor accessor;
    private String secretName;

    @BeforeEach
    void setUp() throws Exception {
        final String configured = System.getProperty("tests.gcp.secrets.emulator.endpoint");
        endpoint = URI.create(configured.endsWith("/") ? configured : configured + "/");
        httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        final GoogleCredentials credentials =
                GoogleCredentials.create(new AccessToken("emulator-token", new Date(System.currentTimeMillis() + 3_600_000L)));
        accessor = new GcpSecretManagerAccessor(credentials, httpClient, endpoint, JSON, Thread::sleep);
        final String secretId = "it-" + UUID.randomUUID();
        secretName = "projects/" + PROJECT + "/secrets/" + secretId;
        final HttpResponse<String> created = httpClient.send(HttpRequest.newBuilder(
                        endpoint.resolve("v1/projects/" + PROJECT + "/secrets?secretId=" + secretId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"replication\":{\"automatic\":{}}}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(created.body(), created.statusCode(), equalTo(200));
    }

    private CachingSecretsSupplier createSupplier() throws Exception {
        final GcpSecretManagerConfiguration configuration = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .readValue("secret_id: " + secretName + "\n", GcpSecretManagerConfiguration.class);
        final PluginMetrics pluginMetrics = mock(PluginMetrics.class);
        when(pluginMetrics.counterWithTags(any(), any(), any())).thenReturn(mock(Counter.class));
        return new CachingSecretsSupplier(Map.of(STORE, new SecretStore(secretName, accessor, configuration)), JSON, pluginMetrics);
    }

    private String versionState(final String versionName) throws Exception {
        final HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(endpoint.resolve("v1/" + versionName))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        final JsonNode body = JSON.readTree(response.body());
        return body.path("state").asText();
    }

    @Test
    void read_write_back_and_destroy_round_trip() throws Exception {
        final String firstVersion = accessor.addVersion(secretName,
                "{\"username\":\"user\",\"password\":\"old\"}".getBytes(StandardCharsets.UTF_8));
        final CachingSecretsSupplier supplier = createSupplier();

        assertThat(supplier.retrieveValue(STORE, "password"), equalTo("old"));

        final String secondVersion = supplier.updateValue(STORE, "password", "new");

        assertThat(secondVersion, not(equalTo(firstVersion)));
        assertThat(versionState(firstVersion), equalTo("DESTROYED"));
        supplier.refresh(STORE);
        assertThat(supplier.retrieveValue(STORE, "password"), equalTo("new"));
        assertThat(supplier.retrieveValue(STORE, "username"), equalTo("user"));
    }

    @Test
    void accessLatest_of_missing_secret_reports_NOT_FOUND() {
        final SecretStoreException exception = assertThrows(SecretStoreException.class,
                () -> accessor.accessLatest("projects/" + PROJECT + "/secrets/does-not-exist-" + UUID.randomUUID()));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.NOT_FOUND));
    }
}
