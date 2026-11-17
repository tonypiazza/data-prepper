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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@code extensions.gcp.secrets} block: a map of store name to store configuration.
 */
public class GcpSecretPluginConfig {
    @JsonAnySetter
    @Valid
    private Map<String, GcpSecretManagerConfiguration> secretManagerConfigurationMap = new HashMap<>();

    public Map<String, GcpSecretManagerConfiguration> getSecretManagerConfigurationMap() {
        return Collections.unmodifiableMap(secretManagerConfigurationMap);
    }
}
