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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretStoreExceptionTest {
    @Test
    void constructor_without_cause_sets_reason_and_message() {
        final SecretStoreException objectUnderTest =
                new SecretStoreException(SecretStoreException.Reason.NOT_FOUND, "missing");

        assertThat(objectUnderTest.getReason(), equalTo(SecretStoreException.Reason.NOT_FOUND));
        assertThat(objectUnderTest.getMessage(), equalTo("missing"));
        assertThat(objectUnderTest.getCause(), nullValue());
    }

    @Test
    void constructor_with_cause_sets_reason_message_and_cause() {
        final RuntimeException cause = new RuntimeException("boom");
        final SecretStoreException objectUnderTest =
                new SecretStoreException(SecretStoreException.Reason.ACCESS_DENIED, "denied", cause);

        assertThat(objectUnderTest.getReason(), equalTo(SecretStoreException.Reason.ACCESS_DENIED));
        assertThat(objectUnderTest.getMessage(), equalTo("denied"));
        assertThat(objectUnderTest.getCause(), sameInstance(cause));
    }

    @Test
    void constructor_with_null_reason_throws() {
        assertThrows(NullPointerException.class, () -> new SecretStoreException(null, "message"));
        assertThrows(NullPointerException.class, () -> new SecretStoreException(null, "message", new RuntimeException()));
    }
}
