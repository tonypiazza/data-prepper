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

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.opensearch.dataprepper.gcp.api.GcpConfig;
import org.opensearch.dataprepper.gcp.api.GcpCredentialsOptions;
import org.opensearch.dataprepper.plugins.secrets.SecretStoreConfiguration;

import java.util.Optional;

/**
 * Configuration for one GCP Secret Manager secret store under {@code extensions.gcp.secrets}.
 */
public class GcpSecretManagerConfiguration extends SecretStoreConfiguration {
    static final String FULL_NAME_PREFIX = "projects/";

    @JsonProperty("secret_id")
    @NotNull(message = "secret_id must not be null")
    @Pattern(regexp = "^(projects/[^/]+/secrets/)?[A-Za-z0-9_-]{1,255}$",
            message = "secret_id must be a secret ID of up to 255 letters, digits, hyphens and underscores, "
                    + "or a full name projects/<project>/secrets/<secret>")
    private String secretId;

    @JsonProperty("gcp")
    @Valid
    private GcpConfig gcpConfig;

    @JsonProperty("destroy_replaced_version")
    private boolean destroyReplacedVersion = true;

    public String getSecretId() {
        return secretId;
    }

    public GcpConfig getGcpConfig() {
        return gcpConfig;
    }

    @Override
    public boolean isDestroyReplacedVersion() {
        return destroyReplacedVersion;
    }

    /**
     * Resolves the full Secret Manager resource name of this store's secret.
     *
     * @param secretConfigId   the store name, used in error messages
     * @param defaultProjectId the project from {@code extensions.gcp} or Application Default Credentials
     * @return the secret name in the form {@code projects/<project>/secrets/<secret>}
     */
    public String resolveSecretName(final String secretConfigId, final Optional<String> defaultProjectId) {
        if (secretId.startsWith(FULL_NAME_PREFIX)) {
            return secretId;
        }
        final String projectId = Optional.ofNullable(gcpConfig)
                .map(GcpConfig::getProjectId)
                .or(() -> defaultProjectId)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Unable to determine the GCP project for gcp secrets configuration %s. Set gcp.project_id, "
                                + "set project_id under extensions.gcp, or use a full secret_id projects/<project>/secrets/<secret>.",
                        secretConfigId)));
        return FULL_NAME_PREFIX + projectId + "/secrets/" + secretId;
    }

    public GcpCredentialsOptions toCredentialsOptions() {
        return gcpConfig != null ? gcpConfig.toCredentialsOptions() : GcpCredentialsOptions.builder().build();
    }
}
