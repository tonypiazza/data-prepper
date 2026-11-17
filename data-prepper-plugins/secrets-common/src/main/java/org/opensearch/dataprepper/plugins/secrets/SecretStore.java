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

import java.util.Objects;

/**
 * One configured secret store: the cloud secret, how to reach it, and its settings.
 */
public final class SecretStore {
    private final String secretName;
    private final SecretStoreAccessor accessor;
    private final SecretStoreConfiguration configuration;

    /**
     * @param secretName    the cloud resource name of the secret
     * @param accessor      the accessor for the secret store
     * @param configuration the store settings
     */
    public SecretStore(final String secretName,
                       final SecretStoreAccessor accessor,
                       final SecretStoreConfiguration configuration) {
        this.secretName = Objects.requireNonNull(secretName, "secretName must not be null");
        this.accessor = Objects.requireNonNull(accessor, "accessor must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * @return the cloud resource name of the secret
     */
    public String getSecretName() {
        return secretName;
    }

    /**
     * @return the accessor for the secret store
     */
    public SecretStoreAccessor getAccessor() {
        return accessor;
    }

    /**
     * @return the store settings
     */
    public SecretStoreConfiguration getConfiguration() {
        return configuration;
    }
}
