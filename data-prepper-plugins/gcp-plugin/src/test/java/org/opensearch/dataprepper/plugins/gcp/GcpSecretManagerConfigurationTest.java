/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 */

package org.opensearch.dataprepper.plugins.gcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.dataprepper.gcp.api.GcpCredentialsOptions;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GcpSecretManagerConfigurationTest {
    private static final Validator VALIDATOR = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    private GcpSecretManagerConfiguration read(final String yaml) throws IOException {
        return objectMapper.readValue(yaml, GcpSecretManagerConfiguration.class);
    }

    @Test
    void deserialize_minimal_config_uses_defaults() throws IOException {
        final GcpSecretManagerConfiguration objectUnderTest = read("secret_id: my-secret\n");

        assertThat(objectUnderTest.getSecretId(), equalTo("my-secret"));
        assertThat(objectUnderTest.getGcpConfig() == null, is(true));
        assertThat(objectUnderTest.isDestroyReplacedVersion(), is(true));
        assertThat(objectUnderTest.getRefreshInterval(), equalTo(Duration.ofHours(1)));
        assertThat(VALIDATOR.validate(objectUnderTest).isEmpty(), is(true));
    }

    @Test
    void deserialize_destroy_replaced_version_false_is_used() throws IOException {
        assertThat(read("secret_id: my-secret\ndestroy_replaced_version: false\n").isDestroyReplacedVersion(), is(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"my-secret", "MY_secret-1", "projects/my-project/secrets/my-secret"})
    void validate_valid_secret_id_has_no_violations(final String secretId) throws IOException {
        assertThat(VALIDATOR.validate(read("secret_id: " + secretId + "\n")).isEmpty(), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"my.secret", "my secret", "projects/my-project/secrets/", "projects//secrets/s",
            "projects/p/secrets/s/versions/1", "projects/p/locations/l/secrets/s"})
    void validate_invalid_secret_id_has_violation(final String secretId) throws IOException {
        final Set<ConstraintViolation<GcpSecretManagerConfiguration>> violations =
                VALIDATOR.validate(read("secret_id: \"" + secretId + "\"\n"));

        assertThat(violations.size(), equalTo(1));
    }

    @Test
    void validate_secret_id_longer_than_255_characters_has_violation() throws IOException {
        assertThat(VALIDATOR.validate(read("secret_id: " + "a".repeat(256) + "\n")).size(), equalTo(1));
    }

    @Test
    void validate_missing_secret_id_has_violation() throws IOException {
        assertThat(VALIDATOR.validate(read("refresh_interval: PT2H\n")).size(), equalTo(1));
    }

    @Test
    void resolveSecretName_with_full_name_returns_it_unchanged() throws IOException {
        final GcpSecretManagerConfiguration objectUnderTest = read("secret_id: projects/other/secrets/s\n");

        assertThat(objectUnderTest.resolveSecretName("store", Optional.of("default-project")),
                equalTo("projects/other/secrets/s"));
    }

    @Test
    void resolveSecretName_with_short_name_prefers_store_project() throws IOException {
        final GcpSecretManagerConfiguration objectUnderTest = read("secret_id: s\ngcp:\n  project_id: store-project\n");

        assertThat(objectUnderTest.resolveSecretName("store", Optional.of("default-project")),
                equalTo("projects/store-project/secrets/s"));
    }

    @Test
    void resolveSecretName_with_short_name_and_no_store_project_uses_default_project() throws IOException {
        assertThat(read("secret_id: s\n").resolveSecretName("store", Optional.of("default-project")),
                equalTo("projects/default-project/secrets/s"));
    }

    @Test
    void resolveSecretName_with_gcp_block_without_project_uses_default_project() throws IOException {
        assertThat(read("secret_id: s\ngcp:\n  auth_mode: adc\n").resolveSecretName("store", Optional.of("default-project")),
                equalTo("projects/default-project/secrets/s"));
    }

    @Test
    void resolveSecretName_with_no_project_anywhere_throws_naming_store() throws IOException {
        final GcpSecretManagerConfiguration objectUnderTest = read("secret_id: s\n");

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> objectUnderTest.resolveSecretName("my-store", Optional.empty()));

        assertThat(exception.getMessage(), containsString("my-store"));
    }

    @Test
    void toCredentialsOptions_without_gcp_block_returns_default_options() throws IOException {
        assertThat(read("secret_id: s\n").toCredentialsOptions(), equalTo(GcpCredentialsOptions.builder().build()));
    }

    @Test
    void toCredentialsOptions_with_gcp_block_delegates_to_gcp_config() throws IOException {
        final GcpSecretManagerConfiguration objectUnderTest = read("secret_id: s\ngcp:\n  project_id: store-project\n");

        assertThat(objectUnderTest.toCredentialsOptions(), equalTo(objectUnderTest.getGcpConfig().toCredentialsOptions()));
    }
}
