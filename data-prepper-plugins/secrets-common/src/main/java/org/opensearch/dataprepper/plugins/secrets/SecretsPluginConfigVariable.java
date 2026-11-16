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

import org.opensearch.dataprepper.model.plugin.PluginConfigVariable;

/**
 * A {@link PluginConfigVariable} whose value comes from, and is written back to, a {@link SecretsSupplier}.
 */
public class SecretsPluginConfigVariable implements PluginConfigVariable {
    private final SecretsSupplier secretsSupplier;
    private final String secretId;
    private final String secretKey;
    private Object secretValue;

    public SecretsPluginConfigVariable(final SecretsSupplier secretsSupplier,
                                       final String secretId,
                                       final String secretKey,
                                       final Object secretValue) {
        this.secretsSupplier = secretsSupplier;
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.secretValue = secretValue;
    }

    @Override
    public Object getValue() {
        return secretValue;
    }

    @Override
    public void setValue(final Object updatedValue) {
        secretsSupplier.updateValue(secretId, secretKey, updatedValue);
        this.secretValue = updatedValue;
    }

    @Override
    public void refresh() {
        secretsSupplier.refresh(secretId);
        this.secretValue = secretKey != null
                ? secretsSupplier.retrieveValue(secretId, secretKey)
                : secretsSupplier.retrieveValue(secretId);
    }

    @Override
    public boolean isUpdatable() {
        return true;
    }
}
