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

import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.plugin.PluginConfigPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Schedules periodic {@link SecretsRefreshJob}s for secret configurations that have refresh enabled.
 */
public class SecretsRefreshScheduler {
    static final long SHUTDOWN_TIMEOUT_SECONDS = 60L;
    static final long MAX_JITTER_SECONDS = 60L;
    private static final Logger LOG = LoggerFactory.getLogger(SecretsRefreshScheduler.class);

    private final ScheduledExecutorService scheduledExecutorService;
    private final LongSupplier jitterSecondsSupplier;

    /**
     * Creates a scheduler backed by a single-threaded executor.
     */
    public SecretsRefreshScheduler() {
        this(Executors.newSingleThreadScheduledExecutor(), () -> ThreadLocalRandom.current().nextLong(MAX_JITTER_SECONDS));
    }

    SecretsRefreshScheduler(final ScheduledExecutorService scheduledExecutorService, final LongSupplier jitterSecondsSupplier) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.jitterSecondsSupplier = jitterSecondsSupplier;
    }

    /**
     * Schedules a refresh job for every configuration that does not disable refresh. The first run is
     * delayed by the refresh interval plus up to a minute of jitter.
     *
     * @param configurations        the store configurations, keyed by secret configuration ID
     * @param secretsSupplier       the supplier to refresh
     * @param pluginConfigPublisher notified after each successful refresh
     * @param pluginMetrics         used for the refresh metrics
     */
    public void schedule(final Map<String, ? extends SecretStoreConfiguration> configurations,
                         final SecretsSupplier secretsSupplier,
                         final PluginConfigPublisher pluginConfigPublisher,
                         final PluginMetrics pluginMetrics) {
        configurations.forEach((secretConfigId, configuration) -> {
            if (configuration.isDisableRefresh()) {
                return;
            }
            final SecretsRefreshJob secretsRefreshJob =
                    new SecretsRefreshJob(secretConfigId, secretsSupplier, pluginConfigPublisher, pluginMetrics);
            final long period = configuration.getRefreshInterval().toSeconds();
            scheduledExecutorService.scheduleAtFixedRate(secretsRefreshJob, period + jitterSecondsSupplier.getAsLong(),
                    period, TimeUnit.SECONDS);
        });
    }

    /**
     * Stops scheduling refreshes, waiting up to a minute for a running refresh to finish.
     */
    public void shutdown() {
        LOG.info("Shutting down secrets refreshing tasks.");
        scheduledExecutorService.shutdown();
        try {
            if (!scheduledExecutorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("Secrets refreshing tasks did not terminate in time, forcing termination.");
                scheduledExecutorService.shutdownNow();
            }
        } catch (final InterruptedException e) {
            LOG.info("Interrupted while terminating the secrets refreshing tasks, forcing termination.");
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
