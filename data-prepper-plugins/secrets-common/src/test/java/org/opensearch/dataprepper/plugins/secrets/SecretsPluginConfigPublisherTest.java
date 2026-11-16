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
import org.opensearch.dataprepper.model.plugin.PluginConfigObservable;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecretsPluginConfigPublisherTest {
    @Mock
    private PluginConfigObservable pluginConfigObservable;

    @Test
    void notifyAllPluginConfigObservable_updates_added_observables() {
        final SecretsPluginConfigPublisher objectUnderTest = new SecretsPluginConfigPublisher();

        assertThat(objectUnderTest.addPluginConfigObservable(pluginConfigObservable), is(true));
        objectUnderTest.notifyAllPluginConfigObservable();

        verify(pluginConfigObservable).update();
    }
}
