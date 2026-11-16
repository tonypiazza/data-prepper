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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.model.plugin.PluginConfigVariable;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretsPluginConfigValueTranslatorTest {
    private static final String PREFIX = "gcp_secrets";

    @Mock
    private SecretsSupplier secretsSupplier;

    private SecretsPluginConfigValueTranslator createObjectUnderTest() {
        return new SecretsPluginConfigValueTranslator(PREFIX, secretsSupplier);
    }

    @Test
    void getPrefix_returns_constructor_prefix() {
        assertThat(createObjectUnderTest().getPrefix(), equalTo(PREFIX));
    }

    @Test
    void translate_with_key_retrieves_key_value() {
        when(secretsSupplier.retrieveValue("my-store", "password")).thenReturn("secret");

        assertThat(createObjectUnderTest().translate("my-store:password"), equalTo("secret"));
    }

    @Test
    void translate_without_key_retrieves_whole_value() {
        when(secretsSupplier.retrieveValue("my-store")).thenReturn("whole");

        assertThat(createObjectUnderTest().translate("my-store"), equalTo("whole"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ":key", "bad store:key", "store#1"})
    void translate_with_invalid_reference_throws(final String reference) {
        final SecretsPluginConfigValueTranslator objectUnderTest = createObjectUnderTest();

        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.translate(reference));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ":key", "bad store:key", "store#1"})
    void translateToPluginConfigVariable_with_invalid_reference_throws(final String reference) {
        final SecretsPluginConfigValueTranslator objectUnderTest = createObjectUnderTest();

        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.translateToPluginConfigVariable(reference));
    }

    @Test
    void translateToPluginConfigVariable_with_key_returns_variable_with_key_value() {
        when(secretsSupplier.retrieveValue("my-store", "password")).thenReturn("secret");

        final PluginConfigVariable result = createObjectUnderTest().translateToPluginConfigVariable("my-store:password");

        assertThat(result, instanceOf(SecretsPluginConfigVariable.class));
        assertThat(result.getValue(), equalTo("secret"));
    }

    @Test
    void translateToPluginConfigVariable_without_key_returns_variable_with_whole_value() {
        when(secretsSupplier.retrieveValue("my-store")).thenReturn("whole");

        final PluginConfigVariable result = createObjectUnderTest().translateToPluginConfigVariable("my-store");

        assertThat(result.getValue(), equalTo("whole"));
    }
}
