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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SecretStoreTest {
    private static final String SECRET_NAME = "projects/p/secrets/s";

    @Mock
    private SecretStoreAccessor accessor;

    @Mock
    private SecretStoreConfiguration configuration;

    @Test
    void getters_return_constructor_values() {
        final SecretStore objectUnderTest = new SecretStore(SECRET_NAME, accessor, configuration);

        assertThat(objectUnderTest.getSecretName(), equalTo(SECRET_NAME));
        assertThat(objectUnderTest.getAccessor(), sameInstance(accessor));
        assertThat(objectUnderTest.getConfiguration(), sameInstance(configuration));
    }

    @Test
    void constructor_with_null_arguments_throws() {
        assertThrows(NullPointerException.class, () -> new SecretStore(null, accessor, configuration));
        assertThrows(NullPointerException.class, () -> new SecretStore(SECRET_NAME, null, configuration));
        assertThrows(NullPointerException.class, () -> new SecretStore(SECRET_NAME, accessor, null));
    }
}
