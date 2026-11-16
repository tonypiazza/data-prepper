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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.plugin.PluginConfigPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refreshes one secret configuration and notifies plugin configuration observables.
 */
public class SecretsRefreshJob implements Runnable {
    static final String SECRETS_REFRESH_SUCCESS = "secretsRefreshSuccess";
    static final String SECRETS_REFRESH_FAILURE = "secretsRefreshFailure";
    static final String SECRETS_REFRESH_DURATION = "secretsRefreshDuration";
    static final String SECRETS_SECRET_NOT_FOUND = "secretsSecretNotFound";
    static final String SECRETS_LIMIT_EXCEEDED = "secretsLimitExceeded";
    static final String SECRETS_ACCESS_DENIED = "secretsAccessDenied";
    static final String SECRET_CONFIG_ID_TAG = "secretConfigId";
    private static final Logger LOG = LoggerFactory.getLogger(SecretsRefreshJob.class);

    private final String secretConfigId;
    private final SecretsSupplier secretsSupplier;
    private final PluginConfigPublisher pluginConfigPublisher;
    private final Counter refreshSuccessCounter;
    private final Counter refreshFailureCounter;
    private final Timer refreshTimer;
    private final Counter secretNotFoundCounter;
    private final Counter limitExceededCounter;
    private final Counter accessDeniedCounter;

    public SecretsRefreshJob(final String secretConfigId,
                             final SecretsSupplier secretsSupplier,
                             final PluginConfigPublisher pluginConfigPublisher,
                             final PluginMetrics pluginMetrics) {
        this.secretConfigId = secretConfigId;
        this.secretsSupplier = secretsSupplier;
        this.pluginConfigPublisher = pluginConfigPublisher;
        this.refreshSuccessCounter = pluginMetrics.counterWithTags(SECRETS_REFRESH_SUCCESS, SECRET_CONFIG_ID_TAG, secretConfigId);
        this.refreshFailureCounter = pluginMetrics.counterWithTags(SECRETS_REFRESH_FAILURE, SECRET_CONFIG_ID_TAG, secretConfigId);
        this.refreshTimer = pluginMetrics.timerWithTags(SECRETS_REFRESH_DURATION, SECRET_CONFIG_ID_TAG, secretConfigId);
        this.secretNotFoundCounter = pluginMetrics.counterWithTags(SECRETS_SECRET_NOT_FOUND, SECRET_CONFIG_ID_TAG, secretConfigId);
        this.limitExceededCounter = pluginMetrics.counterWithTags(SECRETS_LIMIT_EXCEEDED, SECRET_CONFIG_ID_TAG, secretConfigId);
        this.accessDeniedCounter = pluginMetrics.counterWithTags(SECRETS_ACCESS_DENIED, SECRET_CONFIG_ID_TAG, secretConfigId);
    }

    @Override
    public void run() {
        refreshTimer.record(() -> {
            try {
                secretsSupplier.refresh(secretConfigId);
                refreshSuccessCounter.increment();
                pluginConfigPublisher.notifyAllPluginConfigObservable();
            } catch (final Exception e) {
                if (e instanceof SecretStoreException) {
                    countReason(((SecretStoreException) e).getReason());
                }
                LOG.error("Failed to refresh secrets for secret configuration {}.", secretConfigId, e);
                refreshFailureCounter.increment();
            }
        });
    }

    private void countReason(final SecretStoreException.Reason reason) {
        if (reason == SecretStoreException.Reason.NOT_FOUND) {
            secretNotFoundCounter.increment();
        } else if (reason == SecretStoreException.Reason.LIMIT_EXCEEDED) {
            limitExceededCounter.increment();
        } else if (reason == SecretStoreException.Reason.ACCESS_DENIED) {
            accessDeniedCounter.increment();
        }
    }
}
