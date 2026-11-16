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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.model.plugin.ExtensionProvider;
import org.opensearch.dataprepper.model.plugin.PluginConfigValueTranslator;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class SecretsPluginConfigValueTranslatorExtensionProviderTest {
    @Mock
    private PluginConfigValueTranslator pluginConfigValueTranslator;

    @Mock
    private ExtensionProvider.Context context;

    @Test
    void supportedClass_is_PluginConfigValueTranslator() {
        assertThat(new SecretsPluginConfigValueTranslatorExtensionProvider(pluginConfigValueTranslator).supportedClass(),
                equalTo(PluginConfigValueTranslator.class));
    }

    @Test
    void provideInstance_returns_translator() {
        final Optional<PluginConfigValueTranslator> result =
                new SecretsPluginConfigValueTranslatorExtensionProvider(pluginConfigValueTranslator).provideInstance(context);

        assertThat(result, equalTo(Optional.of(pluginConfigValueTranslator)));
    }

    @Test
    void provideInstance_with_null_translator_returns_empty() {
        assertThat(new SecretsPluginConfigValueTranslatorExtensionProvider(null).provideInstance(context).isPresent(), is(false));
    }
}
