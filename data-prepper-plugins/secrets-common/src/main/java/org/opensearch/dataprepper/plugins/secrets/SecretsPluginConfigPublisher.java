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

import org.opensearch.dataprepper.model.plugin.PluginConfigObservable;
import org.opensearch.dataprepper.model.plugin.PluginConfigPublisher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notifies registered plugin configuration observables after secrets are refreshed.
 */
public class SecretsPluginConfigPublisher implements PluginConfigPublisher {
    private final Map<PluginConfigObservable, Boolean> pluginConfigObservables = new ConcurrentHashMap<>();

    @Override
    public boolean addPluginConfigObservable(final PluginConfigObservable pluginConfigObservable) {
        pluginConfigObservables.put(pluginConfigObservable, true);
        return true;
    }

    @Override
    public void notifyAllPluginConfigObservable() {
        pluginConfigObservables.keySet().forEach(PluginConfigObservable::update);
    }
}
