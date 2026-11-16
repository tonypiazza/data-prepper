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
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.plugin.PluginConfigPublisher;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.dataprepper.plugins.secrets.SecretsRefreshScheduler.SHUTDOWN_TIMEOUT_SECONDS;

@ExtendWith(MockitoExtension.class)
class SecretsRefreshSchedulerTest {
    private static final long JITTER_SECONDS = 7L;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private SecretStoreConfiguration enabledConfiguration;

    @Mock
    private SecretStoreConfiguration disabledConfiguration;

    @Mock
    private SecretsSupplier secretsSupplier;

    @Mock
    private PluginConfigPublisher pluginConfigPublisher;

    @Mock
    private PluginMetrics pluginMetrics;

    private SecretsRefreshScheduler createObjectUnderTest() {
        return new SecretsRefreshScheduler(scheduledExecutorService, () -> JITTER_SECONDS);
    }

    @Test
    void schedule_schedules_enabled_stores_with_jittered_initial_delay_and_skips_disabled() {
        when(enabledConfiguration.isDisableRefresh()).thenReturn(false);
        when(enabledConfiguration.getRefreshInterval()).thenReturn(Duration.ofHours(2));
        when(disabledConfiguration.isDisableRefresh()).thenReturn(true);

        createObjectUnderTest().schedule(Map.of("enabled", enabledConfiguration, "disabled", disabledConfiguration),
                secretsSupplier, pluginConfigPublisher, pluginMetrics);

        final long period = Duration.ofHours(2).toSeconds();
        verify(scheduledExecutorService).scheduleAtFixedRate(any(SecretsRefreshJob.class), eq(period + JITTER_SECONDS),
                eq(period), eq(TimeUnit.SECONDS));
    }

    @Test
    void shutdown_when_tasks_terminate_does_not_force() throws InterruptedException {
        when(scheduledExecutorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)).thenReturn(true);

        createObjectUnderTest().shutdown();

        verify(scheduledExecutorService).shutdown();
        verify(scheduledExecutorService, never()).shutdownNow();
    }

    @Test
    void shutdown_when_tasks_do_not_terminate_forces_shutdown() throws InterruptedException {
        when(scheduledExecutorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)).thenReturn(false);

        createObjectUnderTest().shutdown();

        verify(scheduledExecutorService).shutdownNow();
    }

    @Test
    void shutdown_when_interrupted_forces_shutdown_and_restores_interrupt() throws InterruptedException {
        when(scheduledExecutorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

        createObjectUnderTest().shutdown();

        verify(scheduledExecutorService).shutdownNow();
        assertThat(Thread.interrupted(), is(true));
    }

    @Test
    void default_constructor_schedules_and_shuts_down_a_real_executor() {
        when(enabledConfiguration.isDisableRefresh()).thenReturn(false);
        when(enabledConfiguration.getRefreshInterval()).thenReturn(Duration.ofHours(1));
        final SecretsRefreshScheduler objectUnderTest = new SecretsRefreshScheduler();

        objectUnderTest.schedule(Map.of("enabled", enabledConfiguration), secretsSupplier, pluginConfigPublisher, pluginMetrics);
        objectUnderTest.shutdown();
    }
}
