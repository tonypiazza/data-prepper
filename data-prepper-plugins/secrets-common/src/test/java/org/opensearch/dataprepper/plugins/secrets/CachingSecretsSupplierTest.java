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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.plugin.FailedToUpdatePluginConfigValueException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.dataprepper.plugins.secrets.CachingSecretsSupplier.SECRETS_VERSION_DESTROY_FAILURE;
import static org.opensearch.dataprepper.plugins.secrets.CachingSecretsSupplier.SECRET_CONFIG_ID_TAG;

@ExtendWith(MockitoExtension.class)
class CachingSecretsSupplierTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String SECRET_CONFIG_ID = "test-secret-config";
    private static final String SECRET_NAME = "projects/test-project/secrets/test-secret";
    private static final String VERSION_1 = SECRET_NAME + "/versions/1";
    private static final String VERSION_2 = SECRET_NAME + "/versions/2";
    private static final String VERSION_3 = SECRET_NAME + "/versions/3";
    private static final String JSON_PAYLOAD = "{\"username\":\"user\",\"password\":\"old\"}";

    @Mock
    private SecretStoreAccessor accessor;

    @Mock
    private SecretStoreConfiguration configuration;

    @Mock
    private PluginMetrics pluginMetrics;

    @Mock
    private Counter destroyFailureCounter;

    @BeforeEach
    void setUp() {
        when(pluginMetrics.counterWithTags(SECRETS_VERSION_DESTROY_FAILURE, SECRET_CONFIG_ID_TAG, SECRET_CONFIG_ID))
                .thenReturn(destroyFailureCounter);
    }

    private CachingSecretsSupplier createObjectUnderTest() {
        return createObjectUnderTest(OBJECT_MAPPER);
    }

    private CachingSecretsSupplier createObjectUnderTest(final ObjectMapper objectMapper) {
        return new CachingSecretsSupplier(
                Map.of(SECRET_CONFIG_ID, new SecretStore(SECRET_NAME, accessor, configuration)), objectMapper, pluginMetrics);
    }

    private static SecretVersion version(final String versionName, final String payload) {
        return new SecretVersion(versionName, payload.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonProcessingException jsonProcessingException() {
        return new JsonProcessingException("serialization failed") { };
    }

    @Test
    void retrieveValue_with_key_on_json_payload_returns_value() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));

        assertThat(createObjectUnderTest().retrieveValue(SECRET_CONFIG_ID, "username"), equalTo("user"));
    }

    @Test
    void retrieveValue_with_key_on_plain_string_payload_throws() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "plain-value"));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> objectUnderTest.retrieveValue(SECRET_CONFIG_ID, "username"));

        assertThat(exception.getMessage(), containsString("is not a valid JSON object"));
        assertThat(exception.getMessage(), containsString("without a key"));
    }

    @Test
    void retrieveValue_with_missing_key_throws() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.retrieveValue(SECRET_CONFIG_ID, "missing"));
    }

    @Test
    void retrieveValue_with_unknown_secretId_throws() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.retrieveValue("unknown", "username"));
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.retrieveValue("unknown"));
    }

    @Test
    void retrieveValue_without_key_on_plain_string_payload_returns_string() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "plain-value"));

        assertThat(createObjectUnderTest().retrieveValue(SECRET_CONFIG_ID), equalTo("plain-value"));
    }

    @Test
    void retrieveValue_without_key_on_json_payload_returns_json_string() throws JsonProcessingException {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));

        final Object result = createObjectUnderTest().retrieveValue(SECRET_CONFIG_ID);

        assertThat(OBJECT_MAPPER.readValue((String) result, MAP_TYPE), equalTo(OBJECT_MAPPER.readValue(JSON_PAYLOAD, MAP_TYPE)));
    }

    @Test
    void retrieveValue_without_key_when_serialization_fails_throws() throws JsonProcessingException {
        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("username", "user"));
        when(objectMapper.writeValueAsString(any())).thenThrow(jsonProcessingException());
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest(objectMapper);

        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.retrieveValue(SECRET_CONFIG_ID));
    }

    @Test
    void constructor_when_accessor_throws_SecretStoreException_rethrows_with_reason_and_store() {
        when(accessor.accessLatest(SECRET_NAME))
                .thenThrow(new SecretStoreException(SecretStoreException.Reason.NOT_FOUND, "not found"));

        final SecretStoreException exception = assertThrows(SecretStoreException.class, this::createObjectUnderTest);

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.NOT_FOUND));
        assertThat(exception.getMessage(), containsString(SECRET_CONFIG_ID));
        assertThat(exception.getMessage(), containsString(SECRET_NAME));
    }

    @Test
    void constructor_when_accessor_throws_RuntimeException_wraps_as_OTHER() {
        when(accessor.accessLatest(SECRET_NAME)).thenThrow(new IllegalStateException("boom"));

        final SecretStoreException exception = assertThrows(SecretStoreException.class, this::createObjectUnderTest);

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.OTHER));
        assertThat(exception.getMessage(), containsString(SECRET_CONFIG_ID));
    }

    @Test
    void constructor_with_skipValidationOnStart_does_not_read_secret() {
        when(configuration.isSkipValidationOnStart()).thenReturn(true);

        createObjectUnderTest();

        verify(accessor, never()).accessLatest(anyString());
    }

    @Test
    void retrieveValue_with_skipValidationOnStart_reads_once_on_first_use() {
        when(configuration.isSkipValidationOnStart()).thenReturn(true);
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID, "username"), equalTo("user"));
        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID, "password"), equalTo("old"));

        verify(accessor, times(1)).accessLatest(SECRET_NAME);
    }

    @Test
    void refresh_replaces_cached_value_with_latest() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "first"), version(VERSION_2, "second"));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        objectUnderTest.refresh(SECRET_CONFIG_ID);

        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID), equalTo("second"));
    }

    @Test
    void refresh_when_accessor_fails_keeps_cached_value() {
        when(accessor.accessLatest(SECRET_NAME))
                .thenReturn(version(VERSION_1, "first"))
                .thenThrow(new SecretStoreException(SecretStoreException.Reason.LIMIT_EXCEEDED, "slow down"));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.refresh(SECRET_CONFIG_ID));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.LIMIT_EXCEEDED));
        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID), equalTo("first"));
    }

    @Test
    void refresh_with_unknown_secretId_throws() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "first"));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.refresh("unknown"));
    }

    @Test
    void updateValue_with_key_adds_updated_json_and_destroys_replaced_version() throws JsonProcessingException {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        when(configuration.isDestroyReplacedVersion()).thenReturn(true);
        when(accessor.supportsVersionDestroy()).thenReturn(true);
        when(accessor.addVersion(eq(SECRET_NAME), any(byte[].class))).thenReturn(VERSION_2);
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        final String result = objectUnderTest.updateValue(SECRET_CONFIG_ID, "password", "new");

        assertThat(result, equalTo(VERSION_2));
        final ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(accessor).addVersion(eq(SECRET_NAME), payloadCaptor.capture());
        assertThat(OBJECT_MAPPER.readValue(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8), MAP_TYPE),
                equalTo(Map.of("username", "user", "password", "new")));
        verify(accessor).destroyVersion(VERSION_1);
        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID, "password"), equalTo("new"));
    }

    @Test
    void updateValue_without_key_on_plain_string_adds_new_value_and_skips_destroy_when_disabled() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "old-value"));
        when(configuration.isDestroyReplacedVersion()).thenReturn(false);
        when(accessor.addVersion(eq(SECRET_NAME), any(byte[].class))).thenReturn(VERSION_2);
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThat(objectUnderTest.updateValue(SECRET_CONFIG_ID, "new-value"), equalTo(VERSION_2));

        final ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(accessor).addVersion(eq(SECRET_NAME), payloadCaptor.capture());
        assertThat(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8), equalTo("new-value"));
        verify(accessor, never()).destroyVersion(anyString());
        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID), equalTo("new-value"));
    }

    @Test
    void updateValue_when_destroy_not_supported_does_not_destroy() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "old-value"));
        when(configuration.isDestroyReplacedVersion()).thenReturn(true);
        when(accessor.supportsVersionDestroy()).thenReturn(false);
        when(accessor.addVersion(eq(SECRET_NAME), any(byte[].class))).thenReturn(VERSION_2);

        createObjectUnderTest().updateValue(SECRET_CONFIG_ID, "new-value");

        verify(accessor, never()).destroyVersion(anyString());
    }

    @Test
    void updateValue_when_destroy_fails_returns_new_version_and_counts_failure() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "old-value"));
        when(configuration.isDestroyReplacedVersion()).thenReturn(true);
        when(accessor.supportsVersionDestroy()).thenReturn(true);
        when(accessor.addVersion(eq(SECRET_NAME), any(byte[].class))).thenReturn(VERSION_2);
        doThrow(new SecretStoreException(SecretStoreException.Reason.ACCESS_DENIED, "denied"))
                .when(accessor).destroyVersion(VERSION_1);
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThat(objectUnderTest.updateValue(SECRET_CONFIG_ID, "new-value"), equalTo(VERSION_2));

        verify(destroyFailureCounter).increment();
        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID), equalTo("new-value"));
    }

    @Test
    void updateValue_twice_destroys_each_replaced_version_in_order() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        when(configuration.isDestroyReplacedVersion()).thenReturn(true);
        when(accessor.supportsVersionDestroy()).thenReturn(true);
        when(accessor.addVersion(eq(SECRET_NAME), any(byte[].class))).thenReturn(VERSION_2, VERSION_3);
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        objectUnderTest.updateValue(SECRET_CONFIG_ID, "username", "new-access-token");
        objectUnderTest.updateValue(SECRET_CONFIG_ID, "password", "new-refresh-token");

        final InOrder inOrder = inOrder(accessor);
        inOrder.verify(accessor).destroyVersion(VERSION_1);
        inOrder.verify(accessor).destroyVersion(VERSION_2);
        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID, "username"), equalTo("new-access-token"));
        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID, "password"), equalTo("new-refresh-token"));
    }

    @Test
    void updateValue_with_null_key_on_json_secret_throws_and_does_not_write() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.updateValue(SECRET_CONFIG_ID, "new-value"));

        verify(accessor, never()).addVersion(anyString(), any(byte[].class));
    }

    @Test
    void updateValue_when_addVersion_fails_throws_and_keeps_cached_value() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "old-value"));
        when(accessor.addVersion(eq(SECRET_NAME), any(byte[].class)))
                .thenThrow(new SecretStoreException(SecretStoreException.Reason.OTHER, "failed"));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThrows(FailedToUpdatePluginConfigValueException.class,
                () -> objectUnderTest.updateValue(SECRET_CONFIG_ID, "new-value"));

        assertThat(objectUnderTest.retrieveValue(SECRET_CONFIG_ID), equalTo("old-value"));
        verify(accessor, never()).destroyVersion(anyString());
    }

    @Test
    void updateValue_with_skipValidationOnStart_reads_before_writing() {
        when(configuration.isSkipValidationOnStart()).thenReturn(true);
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        when(configuration.isDestroyReplacedVersion()).thenReturn(true);
        when(accessor.supportsVersionDestroy()).thenReturn(true);
        when(accessor.addVersion(eq(SECRET_NAME), any(byte[].class))).thenReturn(VERSION_2);

        createObjectUnderTest().updateValue(SECRET_CONFIG_ID, "password", "new");

        final InOrder inOrder = inOrder(accessor);
        inOrder.verify(accessor).accessLatest(SECRET_NAME);
        inOrder.verify(accessor).addVersion(eq(SECRET_NAME), any(byte[].class));
        inOrder.verify(accessor).destroyVersion(VERSION_1);
    }

    @Test
    void updateValue_on_json_secret_when_serialization_fails_throws() throws JsonProcessingException {
        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("username", "user"));
        when(objectMapper.writeValueAsString(any())).thenThrow(jsonProcessingException());
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, JSON_PAYLOAD));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest(objectMapper);

        assertThrows(IllegalArgumentException.class,
                () -> objectUnderTest.updateValue(SECRET_CONFIG_ID, "username", "new"));
        verify(accessor, never()).addVersion(anyString(), any(byte[].class));
    }

    @Test
    void updateValue_with_unknown_secretId_throws() {
        when(accessor.accessLatest(SECRET_NAME)).thenReturn(version(VERSION_1, "old-value"));
        final CachingSecretsSupplier objectUnderTest = createObjectUnderTest();

        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.updateValue("unknown", "new-value"));
        verifyNoInteractions(destroyFailureCounter);
    }
}
