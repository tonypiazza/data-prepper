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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretsPluginConfigVariableTest {
    private static final String SECRET_ID = "my-store";
    private static final String SECRET_KEY = "password";

    @Mock
    private SecretsSupplier secretsSupplier;

    @Test
    void getValue_returns_initial_value() {
        final SecretsPluginConfigVariable objectUnderTest =
                new SecretsPluginConfigVariable(secretsSupplier, SECRET_ID, SECRET_KEY, "initial");

        assertThat(objectUnderTest.getValue(), equalTo("initial"));
        assertThat(objectUnderTest.isUpdatable(), is(true));
    }

    @Test
    void setValue_updates_secret_store_and_value() {
        final SecretsPluginConfigVariable objectUnderTest =
                new SecretsPluginConfigVariable(secretsSupplier, SECRET_ID, SECRET_KEY, "initial");

        objectUnderTest.setValue("updated");

        verify(secretsSupplier).updateValue(SECRET_ID, SECRET_KEY, "updated");
        assertThat(objectUnderTest.getValue(), equalTo("updated"));
    }

    @Test
    void refresh_with_key_refreshes_and_reads_key() {
        when(secretsSupplier.retrieveValue(SECRET_ID, SECRET_KEY)).thenReturn("refreshed");
        final SecretsPluginConfigVariable objectUnderTest =
                new SecretsPluginConfigVariable(secretsSupplier, SECRET_ID, SECRET_KEY, "initial");

        objectUnderTest.refresh();

        final InOrder inOrder = inOrder(secretsSupplier);
        inOrder.verify(secretsSupplier).refresh(SECRET_ID);
        inOrder.verify(secretsSupplier).retrieveValue(SECRET_ID, SECRET_KEY);
        assertThat(objectUnderTest.getValue(), equalTo("refreshed"));
    }

    @Test
    void refresh_without_key_refreshes_and_reads_whole_value() {
        when(secretsSupplier.retrieveValue(SECRET_ID)).thenReturn("refreshed");
        final SecretsPluginConfigVariable objectUnderTest =
                new SecretsPluginConfigVariable(secretsSupplier, SECRET_ID, null, "initial");

        objectUnderTest.refresh();

        verify(secretsSupplier).refresh(SECRET_ID);
        assertThat(objectUnderTest.getValue(), equalTo("refreshed"));
    }
}
