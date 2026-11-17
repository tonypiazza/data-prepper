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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.auth.oauth2.GoogleCredentials;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.plugins.secrets.CachingSecretsSupplier;
import org.opensearch.dataprepper.plugins.secrets.SecretStore;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "tests.gcp.secrets.live.secret_name", matches = ".+")
class GcpSecretManagerLiveIT {
    private static final String STORE = "live-store";
    private static final String KEY = "it_marker";

    @Test
    void write_back_then_refresh_reads_new_value() throws Exception {
        final String secretName = System.getProperty("tests.gcp.secrets.live.secret_name");
        final GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
        final GcpSecretManagerAccessor accessor = new GcpSecretManagerAccessor(credentials);
        final GcpSecretManagerConfiguration configuration = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .readValue("secret_id: " + secretName + "\n", GcpSecretManagerConfiguration.class);
        final PluginMetrics pluginMetrics = mock(PluginMetrics.class);
        when(pluginMetrics.counterWithTags(any(), any(), any())).thenReturn(mock(Counter.class));
        final CachingSecretsSupplier supplier = new CachingSecretsSupplier(
                Map.of(STORE, new SecretStore(secretName, accessor, configuration)), new ObjectMapper(), pluginMetrics);
        final String marker = UUID.randomUUID().toString();

        supplier.updateValue(STORE, KEY, marker);
        supplier.refresh(STORE);

        assertThat(supplier.retrieveValue(STORE, KEY), equalTo(marker));
    }
}
