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
import org.opensearch.dataprepper.model.plugin.PluginConfigPublisher;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class SecretsPluginConfigPublisherExtensionProviderTest {
    @Mock
    private PluginConfigPublisher pluginConfigPublisher;

    @Mock
    private ExtensionProvider.Context context;

    @Test
    void supportedClass_is_PluginConfigPublisher() {
        assertThat(new SecretsPluginConfigPublisherExtensionProvider(pluginConfigPublisher).supportedClass(),
                equalTo(PluginConfigPublisher.class));
    }

    @Test
    void provideInstance_returns_publisher() {
        assertThat(new SecretsPluginConfigPublisherExtensionProvider(pluginConfigPublisher).provideInstance(context),
                equalTo(Optional.of(pluginConfigPublisher)));
    }

    @Test
    void provideInstance_with_null_publisher_returns_empty() {
        assertThat(new SecretsPluginConfigPublisherExtensionProvider(null).provideInstance(context).isPresent(), is(false));
    }
}
