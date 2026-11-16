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

import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretVersionTest {
    private static final String VERSION_NAME = "projects/p/secrets/s/versions/1";
    private static final String PAYLOAD = "super-secret-value";

    @Test
    void getters_return_constructor_values() {
        final SecretVersion objectUnderTest = new SecretVersion(VERSION_NAME, PAYLOAD.getBytes(StandardCharsets.UTF_8));

        assertThat(objectUnderTest.getVersionName(), equalTo(VERSION_NAME));
        assertThat(new String(objectUnderTest.getPayload(), StandardCharsets.UTF_8), equalTo(PAYLOAD));
    }

    @Test
    void constructor_copies_payload_so_later_changes_do_not_leak_in() {
        final byte[] payload = PAYLOAD.getBytes(StandardCharsets.UTF_8);
        final SecretVersion objectUnderTest = new SecretVersion(VERSION_NAME, payload);

        payload[0] = 'X';

        assertThat(new String(objectUnderTest.getPayload(), StandardCharsets.UTF_8), equalTo(PAYLOAD));
    }

    @Test
    void getPayload_returns_a_copy() {
        final SecretVersion objectUnderTest = new SecretVersion(VERSION_NAME, PAYLOAD.getBytes(StandardCharsets.UTF_8));

        objectUnderTest.getPayload()[0] = 'X';

        assertThat(new String(objectUnderTest.getPayload(), StandardCharsets.UTF_8), equalTo(PAYLOAD));
    }

    @Test
    void toString_includes_version_name_but_not_payload() {
        final SecretVersion objectUnderTest = new SecretVersion(VERSION_NAME, PAYLOAD.getBytes(StandardCharsets.UTF_8));

        assertThat(objectUnderTest.toString(), containsString(VERSION_NAME));
        assertThat(objectUnderTest.toString(), not(containsString(PAYLOAD)));
    }

    @Test
    void constructor_with_null_versionName_throws() {
        assertThrows(NullPointerException.class, () -> new SecretVersion(null, new byte[0]));
    }

    @Test
    void constructor_with_null_payload_throws() {
        assertThrows(NullPointerException.class, () -> new SecretVersion(VERSION_NAME, null));
    }
}
