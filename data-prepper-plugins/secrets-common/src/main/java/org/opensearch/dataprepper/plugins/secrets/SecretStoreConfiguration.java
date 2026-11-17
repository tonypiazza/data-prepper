/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 */

package org.opensearch.dataprepper.plugins.secrets;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;

import java.time.Duration;

/**
 * The settings every cloud secret store configuration shares.
 */
public class SecretStoreConfiguration {
    @JsonProperty("refresh_interval")
    @NotNull(message = "refresh_interval must not be null")
    @DurationMin(hours = 1L, message = "Refresh interval must be at least 1 hour.")
    private Duration refreshInterval = Duration.ofHours(1L);

    @JsonProperty("disable_refresh")
    private boolean disableRefresh = false;

    @JsonProperty("skip_validation_on_start")
    private boolean skipValidationOnStart = false;

    /**
     * @return how often the secret is refreshed; defaults to one hour
     */
    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @return true if periodic refresh is disabled
     */
    public boolean isDisableRefresh() {
        return disableRefresh;
    }

    /**
     * @return true if the secret is read on first use rather than at startup
     */
    public boolean isSkipValidationOnStart() {
        return skipValidationOnStart;
    }

    /**
     * @return true if the version replaced by a write-back should be destroyed
     */
    public boolean isDestroyReplacedVersion() {
        return false;
    }
}
