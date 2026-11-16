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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.plugin.PluginConfigPublisher;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.dataprepper.plugins.secrets.SecretsRefreshJob.SECRETS_ACCESS_DENIED;
import static org.opensearch.dataprepper.plugins.secrets.SecretsRefreshJob.SECRETS_LIMIT_EXCEEDED;
import static org.opensearch.dataprepper.plugins.secrets.SecretsRefreshJob.SECRETS_REFRESH_DURATION;
import static org.opensearch.dataprepper.plugins.secrets.SecretsRefreshJob.SECRETS_REFRESH_FAILURE;
import static org.opensearch.dataprepper.plugins.secrets.SecretsRefreshJob.SECRETS_REFRESH_SUCCESS;
import static org.opensearch.dataprepper.plugins.secrets.SecretsRefreshJob.SECRETS_SECRET_NOT_FOUND;
import static org.opensearch.dataprepper.plugins.secrets.SecretsRefreshJob.SECRET_CONFIG_ID_TAG;

@ExtendWith(MockitoExtension.class)
class SecretsRefreshJobTest {
    private static final String SECRET_CONFIG_ID = "test-secret-config";

    @Mock
    private SecretsSupplier secretsSupplier;

    @Mock
    private PluginConfigPublisher pluginConfigPublisher;

    @Mock
    private PluginMetrics pluginMetrics;

    @Mock
    private Timer refreshTimer;

    private Map<String, Counter> counters;

    private SecretsRefreshJob objectUnderTest;

    @BeforeEach
    void setUp() {
        counters = Map.of(
                SECRETS_REFRESH_SUCCESS, mock(Counter.class),
                SECRETS_REFRESH_FAILURE, mock(Counter.class),
                SECRETS_SECRET_NOT_FOUND, mock(Counter.class),
                SECRETS_LIMIT_EXCEEDED, mock(Counter.class),
                SECRETS_ACCESS_DENIED, mock(Counter.class));
        counters.forEach((name, counter) ->
                when(pluginMetrics.counterWithTags(name, SECRET_CONFIG_ID_TAG, SECRET_CONFIG_ID)).thenReturn(counter));
        when(pluginMetrics.timerWithTags(SECRETS_REFRESH_DURATION, SECRET_CONFIG_ID_TAG, SECRET_CONFIG_ID)).thenReturn(refreshTimer);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(refreshTimer).record(any(Runnable.class));
        objectUnderTest = new SecretsRefreshJob(SECRET_CONFIG_ID, secretsSupplier, pluginConfigPublisher, pluginMetrics);
    }

    @Test
    void run_when_refresh_succeeds_notifies_and_counts_success() {
        objectUnderTest.run();

        verify(secretsSupplier).refresh(SECRET_CONFIG_ID);
        verify(pluginConfigPublisher).notifyAllPluginConfigObservable();
        verify(counters.get(SECRETS_REFRESH_SUCCESS)).increment();
        verifyNoInteractions(counters.get(SECRETS_REFRESH_FAILURE));
    }

    @Test
    void run_when_refresh_throws_RuntimeException_counts_failure_only() {
        doThrow(new IllegalStateException("boom")).when(secretsSupplier).refresh(SECRET_CONFIG_ID);

        objectUnderTest.run();

        verify(counters.get(SECRETS_REFRESH_FAILURE)).increment();
        verify(pluginConfigPublisher, never()).notifyAllPluginConfigObservable();
        verifyNoInteractions(counters.get(SECRETS_SECRET_NOT_FOUND), counters.get(SECRETS_LIMIT_EXCEEDED),
                counters.get(SECRETS_ACCESS_DENIED));
    }

    @ParameterizedTest
    @EnumSource(SecretStoreException.Reason.class)
    void run_when_refresh_throws_SecretStoreException_counts_reason_and_failure(final SecretStoreException.Reason reason) {
        doThrow(new SecretStoreException(reason, "failed")).when(secretsSupplier).refresh(SECRET_CONFIG_ID);

        objectUnderTest.run();

        verify(counters.get(SECRETS_REFRESH_FAILURE)).increment();
        verify(counters.get(SECRETS_SECRET_NOT_FOUND), reason == SecretStoreException.Reason.NOT_FOUND
                ? times(1) : never()).increment();
        verify(counters.get(SECRETS_LIMIT_EXCEEDED), reason == SecretStoreException.Reason.LIMIT_EXCEEDED
                ? times(1) : never()).increment();
        verify(counters.get(SECRETS_ACCESS_DENIED), reason == SecretStoreException.Reason.ACCESS_DENIED
                ? times(1) : never()).increment();
        verify(pluginConfigPublisher, never()).notifyAllPluginConfigObservable();
    }
}
