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

import java.util.Arrays;
import java.util.Objects;

/**
 * One version of a secret read from a cloud secret store.
 */
public final class SecretVersion {
    private final String versionName;
    private final byte[] payload;

    /**
     * @param versionName the cloud resource name of the version
     * @param payload     the secret value; copied
     */
    public SecretVersion(final String versionName, final byte[] payload) {
        this.versionName = Objects.requireNonNull(versionName, "versionName must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    /**
     * @return the cloud resource name of the version
     */
    public String getVersionName() {
        return versionName;
    }

    /**
     * @return a copy of the secret value
     */
    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public String toString() {
        return "SecretVersion{versionName='" + versionName + "'}";
    }
}
