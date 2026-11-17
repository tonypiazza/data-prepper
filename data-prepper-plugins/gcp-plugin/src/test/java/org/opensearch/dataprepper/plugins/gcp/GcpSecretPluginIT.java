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

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.gcp.api.GcpCredentialsOptions;
import org.opensearch.dataprepper.gcp.api.GcpCredentialsSupplier;
import org.opensearch.dataprepper.model.plugin.ExtensionPoints;
import org.opensearch.dataprepper.model.plugin.ExtensionProvider;
import org.opensearch.dataprepper.model.plugin.PluginConfigPublisher;
import org.opensearch.dataprepper.model.plugin.PluginConfigValueTranslator;
import org.opensearch.dataprepper.plugins.secrets.SecretStoreAccessor;
import org.opensearch.dataprepper.plugins.secrets.SecretVersion;
import org.opensearch.dataprepper.plugins.secrets.SecretsPluginConfigPublisherExtensionProvider;
import org.opensearch.dataprepper.plugins.secrets.SecretsPluginConfigValueTranslatorExtensionProvider;
import org.opensearch.dataprepper.plugins.secrets.SecretsRefreshScheduler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GcpSecretPluginIT {
    private static final String STORE_A = "store-a";
    private static final String STORE_B = "store-b";

    @Mock
    private ExtensionPoints extensionPoints;

    @Mock
    private ExtensionProvider.Context context;

    @Mock
    private GcpCredentialsSupplier credentialsSupplier;

    @Mock
    private GoogleCredentials googleCredentials;

    @Mock
    private GcpSecretPluginConfig pluginConfig;

    @Mock
    private GcpSecretManagerConfiguration configurationA;

    @Mock
    private GcpSecretManagerConfiguration configurationB;

    @Mock
    private SecretsRefreshScheduler scheduler;

    private final List<SecretStoreAccessor> createdAccessors = new ArrayList<>();

    private GcpSecretPlugin createObjectUnderTest(final GcpSecretPluginConfig config) {
        return new GcpSecretPlugin(config, credentials -> {
            final SecretStoreAccessor accessor = mock(SecretStoreAccessor.class);
            when(accessor.accessLatest(any())).thenAnswer(invocation -> new SecretVersion(
                    invocation.getArgument(0) + "/versions/1",
                    "{\"password\":\"from-gcp\"}".getBytes(StandardCharsets.UTF_8)));
            createdAccessors.add(accessor);
            return accessor;
        }, () -> scheduler);
    }

    private List<ExtensionProvider> capturedProviders() {
        final ArgumentCaptor<ExtensionProvider> captor = ArgumentCaptor.forClass(ExtensionProvider.class);
        verify(extensionPoints, times(2)).addExtensionProvider(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void apply_with_config_registers_working_translator_and_publisher_and_schedules_refresh() {
        final GcpCredentialsOptions sharedOptions = GcpCredentialsOptions.builder().build();
        when(extensionPoints.getExtensionProvider(GcpCredentialsSupplier.class)).thenReturn(credentialsSupplier);
        when(credentialsSupplier.getCredentials(sharedOptions)).thenReturn(googleCredentials);
        when(credentialsSupplier.getDefaultProjectId()).thenReturn(Optional.of("default-project"));
        when(pluginConfig.getSecretManagerConfigurationMap())
                .thenReturn(Map.of(STORE_A, configurationA, STORE_B, configurationB));
        when(configurationA.toCredentialsOptions()).thenReturn(sharedOptions);
        when(configurationB.toCredentialsOptions()).thenReturn(sharedOptions);
        when(configurationA.resolveSecretName(STORE_A, Optional.of("default-project"))).thenReturn("projects/p/secrets/a");
        when(configurationB.resolveSecretName(STORE_B, Optional.of("default-project"))).thenReturn("projects/p/secrets/b");

        final GcpSecretPlugin objectUnderTest = createObjectUnderTest(pluginConfig);
        objectUnderTest.apply(extensionPoints);

        final List<ExtensionProvider> providers = capturedProviders();
        assertThat(providers.get(0), instanceOf(SecretsPluginConfigValueTranslatorExtensionProvider.class));
        assertThat(providers.get(1), instanceOf(SecretsPluginConfigPublisherExtensionProvider.class));
        final PluginConfigValueTranslator translator =
                (PluginConfigValueTranslator) providers.get(0).provideInstance(context).orElseThrow();
        assertThat(translator.getPrefix(), equalTo(GcpSecretPlugin.GCP_SECRETS_PREFIX));
        assertThat(translator.translate(STORE_A + ":password"), equalTo("from-gcp"));
        assertThat(providers.get(1).provideInstance(context).orElseThrow(), instanceOf(PluginConfigPublisher.class));
        assertThat(createdAccessors.size(), equalTo(1));
        verify(scheduler).schedule(eq(Map.of(STORE_A, configurationA, STORE_B, configurationB)), any(), any(), any());

        objectUnderTest.shutdown();

        verify(scheduler).shutdown();
        verify(createdAccessors.get(0)).close();
    }

    @Test
    void apply_with_different_credentials_creates_one_accessor_per_credentials() {
        final GcpCredentialsOptions optionsA = GcpCredentialsOptions.builder().build();
        final GcpCredentialsOptions optionsB = GcpCredentialsOptions.builder().withProjectId("other-project").build();
        when(extensionPoints.getExtensionProvider(GcpCredentialsSupplier.class)).thenReturn(credentialsSupplier);
        when(credentialsSupplier.getCredentials(any())).thenReturn(googleCredentials);
        when(credentialsSupplier.getDefaultProjectId()).thenReturn(Optional.empty());
        when(pluginConfig.getSecretManagerConfigurationMap())
                .thenReturn(Map.of(STORE_A, configurationA, STORE_B, configurationB));
        when(configurationA.toCredentialsOptions()).thenReturn(optionsA);
        when(configurationB.toCredentialsOptions()).thenReturn(optionsB);
        when(configurationA.resolveSecretName(STORE_A, Optional.empty())).thenReturn("projects/p/secrets/a");
        when(configurationB.resolveSecretName(STORE_B, Optional.empty())).thenReturn("projects/q/secrets/b");

        createObjectUnderTest(pluginConfig).apply(extensionPoints);

        assertThat(createdAccessors.size(), equalTo(2));
    }

    @Test
    void apply_without_config_registers_empty_providers_and_does_not_touch_gcp() {
        final GcpSecretPlugin objectUnderTest = createObjectUnderTest(null);

        objectUnderTest.apply(extensionPoints);

        final List<ExtensionProvider> providers = capturedProviders();
        assertThat(providers.get(0).provideInstance(context).isPresent(), is(false));
        assertThat(providers.get(1).provideInstance(context).isPresent(), is(false));
        verify(extensionPoints, times(0)).getExtensionProvider(GcpCredentialsSupplier.class);
        objectUnderTest.shutdown();
        verifyNoInteractions(scheduler);
    }

    @Test
    void public_constructor_creates_plugin() {
        final GcpSecretPlugin objectUnderTest = new GcpSecretPlugin(null);

        objectUnderTest.apply(extensionPoints);

        assertThat(capturedProviders().size(), equalTo(2));
    }
}
