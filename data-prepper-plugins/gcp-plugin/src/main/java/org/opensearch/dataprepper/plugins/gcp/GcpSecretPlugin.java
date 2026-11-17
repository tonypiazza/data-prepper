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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.opensearch.dataprepper.gcp.api.GcpCredentialsOptions;
import org.opensearch.dataprepper.gcp.api.GcpCredentialsSupplier;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperExtensionPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.annotations.ExtensionDependsOn;
import org.opensearch.dataprepper.model.plugin.ExtensionPlugin;
import org.opensearch.dataprepper.model.plugin.ExtensionPoints;
import org.opensearch.dataprepper.model.plugin.PluginConfigPublisher;
import org.opensearch.dataprepper.model.plugin.PluginConfigValueTranslator;
import org.opensearch.dataprepper.plugins.secrets.CachingSecretsSupplier;
import org.opensearch.dataprepper.plugins.secrets.SecretStore;
import org.opensearch.dataprepper.plugins.secrets.SecretStoreAccessor;
import org.opensearch.dataprepper.plugins.secrets.SecretsPluginConfigPublisher;
import org.opensearch.dataprepper.plugins.secrets.SecretsPluginConfigPublisherExtensionProvider;
import org.opensearch.dataprepper.plugins.secrets.SecretsPluginConfigValueTranslator;
import org.opensearch.dataprepper.plugins.secrets.SecretsPluginConfigValueTranslatorExtensionProvider;
import org.opensearch.dataprepper.plugins.secrets.SecretsRefreshScheduler;
import org.opensearch.dataprepper.plugins.secrets.SecretsSupplier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides the {@code gcp_secrets} plugin configuration translator backed by GCP Secret Manager.
 */
@DataPrepperExtensionPlugin(modelType = GcpSecretPluginConfig.class, rootKeyJsonPath = "/gcp/secrets",
        allowInPipelineConfigurations = true)
@ExtensionDependsOn(dependentClasses = {GcpCredentialsSupplier.class})
public class GcpSecretPlugin implements ExtensionPlugin {
    static final String GCP_SECRETS_PREFIX = "gcp_secrets";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GcpSecretPluginConfig gcpSecretPluginConfig;
    private final Function<GoogleCredentials, SecretStoreAccessor> accessorFactory;
    private final Supplier<SecretsRefreshScheduler> schedulerFactory;
    private final List<SecretStoreAccessor> accessors = new ArrayList<>();
    private SecretsRefreshScheduler secretsRefreshScheduler;

    @DataPrepperPluginConstructor
    public GcpSecretPlugin(final GcpSecretPluginConfig gcpSecretPluginConfig) {
        this(gcpSecretPluginConfig, GcpSecretManagerAccessor::new, SecretsRefreshScheduler::new);
    }

    GcpSecretPlugin(final GcpSecretPluginConfig gcpSecretPluginConfig,
                    final Function<GoogleCredentials, SecretStoreAccessor> accessorFactory,
                    final Supplier<SecretsRefreshScheduler> schedulerFactory) {
        this.gcpSecretPluginConfig = gcpSecretPluginConfig;
        this.accessorFactory = accessorFactory;
        this.schedulerFactory = schedulerFactory;
    }

    @Override
    public void apply(final ExtensionPoints extensionPoints) {
        PluginConfigValueTranslator pluginConfigValueTranslator = null;
        PluginConfigPublisher pluginConfigPublisher = null;
        if (gcpSecretPluginConfig != null) {
            final GcpCredentialsSupplier credentialsSupplier = extensionPoints.getExtensionProvider(GcpCredentialsSupplier.class);
            final PluginMetrics pluginMetrics = PluginMetrics.fromNames("secrets", "gcp");
            final SecretsSupplier secretsSupplier =
                    new CachingSecretsSupplier(createSecretStores(credentialsSupplier), OBJECT_MAPPER, pluginMetrics);
            pluginConfigPublisher = new SecretsPluginConfigPublisher();
            pluginConfigValueTranslator = new SecretsPluginConfigValueTranslator(GCP_SECRETS_PREFIX, secretsSupplier);
            secretsRefreshScheduler = schedulerFactory.get();
            secretsRefreshScheduler.schedule(gcpSecretPluginConfig.getSecretManagerConfigurationMap(),
                    secretsSupplier, pluginConfigPublisher, pluginMetrics);
        }
        extensionPoints.addExtensionProvider(new SecretsPluginConfigValueTranslatorExtensionProvider(pluginConfigValueTranslator));
        extensionPoints.addExtensionProvider(new SecretsPluginConfigPublisherExtensionProvider(pluginConfigPublisher));
    }

    @Override
    public void shutdown() {
        if (secretsRefreshScheduler != null) {
            secretsRefreshScheduler.shutdown();
        }
        accessors.forEach(SecretStoreAccessor::close);
    }

    private Map<String, SecretStore> createSecretStores(final GcpCredentialsSupplier credentialsSupplier) {
        final Map<GcpCredentialsOptions, SecretStoreAccessor> accessorsByOptions = new HashMap<>();
        final Optional<String> defaultProjectId = credentialsSupplier.getDefaultProjectId();
        final Map<String, SecretStore> secretStores = new HashMap<>();
        gcpSecretPluginConfig.getSecretManagerConfigurationMap().forEach((secretConfigId, configuration) -> {
            final SecretStoreAccessor accessor = accessorsByOptions.computeIfAbsent(configuration.toCredentialsOptions(),
                    options -> accessorFactory.apply(credentialsSupplier.getCredentials(options)));
            secretStores.put(secretConfigId, new SecretStore(
                    configuration.resolveSecretName(secretConfigId, defaultProjectId), accessor, configuration));
        });
        accessors.addAll(accessorsByOptions.values());
        return secretStores;
    }
}
