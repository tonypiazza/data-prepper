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

/**
 * The cloud-specific operations a secrets provider must implement. Every method throws
 * {@link SecretStoreException} on failure.
 */
public interface SecretStoreAccessor {
    /**
     * Reads the latest version of a secret.
     *
     * @param secretName the cloud resource name of the secret
     * @return the latest version
     */
    SecretVersion accessLatest(String secretName);

    /**
     * Adds a new version of a secret.
     *
     * @param secretName the cloud resource name of the secret
     * @param payload    the new secret value
     * @return the name of the new version
     */
    String addVersion(String secretName, byte[] payload);

    /**
     * @return true if {@link #destroyVersion(String)} is supported by this secret store
     */
    boolean supportsVersionDestroy();

    /**
     * Permanently destroys one version of a secret.
     *
     * @param versionName the name of the version to destroy
     */
    void destroyVersion(String versionName);

    /**
     * Releases any resources held by this accessor.
     */
    void close();
}
