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

import org.opensearch.dataprepper.model.plugin.ExtensionProvider;
import org.opensearch.dataprepper.model.plugin.PluginConfigValueTranslator;

import java.util.Optional;

/**
 * Provides a secrets {@link PluginConfigValueTranslator} to Data Prepper.
 */
public class SecretsPluginConfigValueTranslatorExtensionProvider implements ExtensionProvider<PluginConfigValueTranslator> {
    private final PluginConfigValueTranslator pluginConfigValueTranslator;

    public SecretsPluginConfigValueTranslatorExtensionProvider(final PluginConfigValueTranslator pluginConfigValueTranslator) {
        this.pluginConfigValueTranslator = pluginConfigValueTranslator;
    }

    @Override
    public Optional<PluginConfigValueTranslator> provideInstance(final Context context) {
        return Optional.ofNullable(pluginConfigValueTranslator);
    }

    @Override
    public Class<PluginConfigValueTranslator> supportedClass() {
        return PluginConfigValueTranslator.class;
    }
}
