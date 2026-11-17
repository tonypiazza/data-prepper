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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.plugin.FailedToUpdatePluginConfigValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link SecretsSupplier} that caches secrets read through a {@link SecretStoreAccessor}.
 */
public class CachingSecretsSupplier implements SecretsSupplier {
    static final String SECRETS_VERSION_DESTROY_FAILURE = "secretsVersionDestroyFailure";
    static final String SECRET_CONFIG_ID_TAG = "secretConfigId";
    private static final Logger LOG = LoggerFactory.getLogger(CachingSecretsSupplier.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() { };
    private static final CachedSecret NOT_LOADED = new CachedSecret(null, null);

    private final Map<String, SecretStore> secretStores;
    private final ObjectMapper objectMapper;
    private final Map<String, Counter> destroyFailureCounters;
    private final ConcurrentMap<String, CachedSecret> cachedSecrets = new ConcurrentHashMap<>();

    /**
     * Creates the supplier and reads every store not marked {@code skip_validation_on_start}.
     *
     * @param secretStores  the configured stores, keyed by secret configuration ID
     * @param objectMapper  used to parse and serialize JSON payloads
     * @param pluginMetrics used for the {@code secretsVersionDestroyFailure} counter
     * @throws SecretStoreException if a store cannot be read at startup
     */
    public CachingSecretsSupplier(final Map<String, SecretStore> secretStores,
                                  final ObjectMapper objectMapper,
                                  final PluginMetrics pluginMetrics) {
        this.secretStores = Map.copyOf(secretStores);
        this.objectMapper = objectMapper;
        this.destroyFailureCounters = this.secretStores.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), secretConfigId -> pluginMetrics.counterWithTags(
                        SECRETS_VERSION_DESTROY_FAILURE, SECRET_CONFIG_ID_TAG, secretConfigId)));
        this.secretStores.forEach((secretConfigId, secretStore) -> {
            if (secretStore.getConfiguration().isSkipValidationOnStart()) {
                LOG.info("Skipping secret retrieval on start for secret configuration {} (skip_validation_on_start=true).",
                        secretConfigId);
                cachedSecrets.put(secretConfigId, NOT_LOADED);
            } else {
                cachedSecrets.put(secretConfigId, load(secretConfigId, secretStore));
            }
        });
    }

    @Override
    public Object retrieveValue(final String secretId, final String key) {
        final Object value = getLoadedSecret(secretId).value;
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(String.format(
                    "The value under secretId: %s is not a valid JSON object. Reference it without a key to retrieve the whole value.",
                    secretId));
        }
        final Map<String, Object> keyValueMap = (Map<String, Object>) value;
        if (!keyValueMap.containsKey(key)) {
            throw new IllegalArgumentException(String.format(
                    "Unable to find the value of key: %s under secretId: %s", key, secretId));
        }
        return keyValueMap.get(key);
    }

    @Override
    public Object retrieveValue(final String secretId) {
        final Object value = getLoadedSecret(secretId).value;
        return value instanceof Map ? toJson(secretId, value) : value;
    }

    @Override
    public void refresh(final String secretId) {
        final SecretStore secretStore = getSecretStore(secretId);
        LOG.info("Retrieving latest secret for secret configuration {}.", secretId);
        cachedSecrets.compute(secretId, (id, current) -> load(id, secretStore));
        LOG.info("Finished retrieving latest secret for secret configuration {}.", secretId);
    }

    @Override
    public String updateValue(final String secretId, final Object newValueToSet) {
        return updateValue(secretId, null, newValueToSet);
    }

    @Override
    public String updateValue(final String secretId, final String keyToUpdate, final Object newValueToSet) {
        final SecretStore secretStore = getSecretStore(secretId);
        final CachedSecret updated = cachedSecrets.compute(secretId, (id, current) -> {
            final CachedSecret loaded = current == NOT_LOADED ? load(id, secretStore) : current;
            final Object updatedValue;
            final String payload;
            if (loaded.value instanceof Map) {
                if (keyToUpdate == null) {
                    throw new IllegalArgumentException(String.format(
                            "Key to update cannot be null for a key value based secret. secretId: %s", id));
                }
                final Map<String, Object> updatedMap = new LinkedHashMap<>((Map<String, Object>) loaded.value);
                updatedMap.put(keyToUpdate, newValueToSet);
                updatedValue = updatedMap;
                payload = toJson(id, updatedMap);
            } else {
                updatedValue = newValueToSet;
                payload = String.valueOf(newValueToSet);
            }
            final String newVersionName;
            try {
                newVersionName = secretStore.getAccessor()
                        .addVersion(secretStore.getSecretName(), payload.getBytes(StandardCharsets.UTF_8));
            } catch (final RuntimeException e) {
                throw new FailedToUpdatePluginConfigValueException(String.format(
                        "Failed to update the secret: %s to put a new value for the key: %s",
                        secretStore.getSecretName(), keyToUpdate), e);
            }
            LOG.info("Updated key: {} in the secret configuration {}. New version is {}.", keyToUpdate, id, newVersionName);
            destroyReplacedVersion(id, secretStore, loaded.versionName);
            return new CachedSecret(updatedValue, newVersionName);
        });
        return updated.versionName;
    }

    private void destroyReplacedVersion(final String secretConfigId, final SecretStore secretStore,
                                        final String replacedVersionName) {
        if (!secretStore.getConfiguration().isDestroyReplacedVersion() || !secretStore.getAccessor().supportsVersionDestroy()) {
            return;
        }
        try {
            secretStore.getAccessor().destroyVersion(replacedVersionName);
        } catch (final RuntimeException e) {
            destroyFailureCounters.get(secretConfigId).increment();
            LOG.warn("Unable to destroy replaced version {} of secret configuration {}.", replacedVersionName, secretConfigId, e);
        }
    }

    private CachedSecret getLoadedSecret(final String secretId) {
        final SecretStore secretStore = getSecretStore(secretId);
        return cachedSecrets.compute(secretId, (id, current) -> {
            if (current == NOT_LOADED) {
                LOG.info("Secret configuration {} was not loaded on start, loading now on first access.", id);
                return load(id, secretStore);
            }
            return current;
        });
    }

    private SecretStore getSecretStore(final String secretId) {
        final SecretStore secretStore = secretStores.get(secretId);
        if (secretStore == null) {
            throw new IllegalArgumentException(String.format("Unable to find secretId: %s", secretId));
        }
        return secretStore;
    }

    private CachedSecret load(final String secretConfigId, final SecretStore secretStore) {
        final SecretVersion secretVersion;
        try {
            secretVersion = secretStore.getAccessor().accessLatest(secretStore.getSecretName());
        } catch (final SecretStoreException e) {
            throw new SecretStoreException(e.getReason(), String.format(
                    "Unable to retrieve secret %s for secret configuration %s: %s",
                    secretStore.getSecretName(), secretConfigId, e.getMessage()), e);
        } catch (final RuntimeException e) {
            throw new SecretStoreException(SecretStoreException.Reason.OTHER, String.format(
                    "Unable to retrieve secret %s for secret configuration %s",
                    secretStore.getSecretName(), secretConfigId), e);
        }
        return new CachedSecret(parse(secretVersion.getPayload()), secretVersion.getVersionName());
    }

    private Object parse(final byte[] payload) {
        final String payloadString = new String(payload, StandardCharsets.UTF_8);
        try {
            return objectMapper.readValue(payloadString, MAP_TYPE_REFERENCE);
        } catch (final JsonProcessingException e) {
            return payloadString;
        }
    }

    private String toJson(final String secretId, final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException(String.format(
                    "Unable to read the value under secretId: %s as string.", secretId), e);
        }
    }

    private static final class CachedSecret {
        private final Object value;
        private final String versionName;

        private CachedSecret(final Object value, final String versionName) {
            this.value = value;
            this.versionName = versionName;
        }
    }
}
