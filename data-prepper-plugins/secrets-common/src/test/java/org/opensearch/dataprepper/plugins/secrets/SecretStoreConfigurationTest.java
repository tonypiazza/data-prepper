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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SecretStoreConfigurationTest {
    private static final Validator VALIDATOR = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    @Test
    void deserialize_empty_config_uses_defaults() throws IOException {
        final SecretStoreConfiguration objectUnderTest = objectMapper.readValue("{}", SecretStoreConfiguration.class);

        assertThat(objectUnderTest.getRefreshInterval(), equalTo(Duration.ofHours(1)));
        assertThat(objectUnderTest.isDisableRefresh(), is(false));
        assertThat(objectUnderTest.isSkipValidationOnStart(), is(false));
        assertThat(objectUnderTest.isDestroyReplacedVersion(), is(false));
        assertThat(VALIDATOR.validate(objectUnderTest).isEmpty(), is(true));
    }

    @Test
    void deserialize_explicit_values_are_used() throws IOException {
        final String yaml = "refresh_interval: PT2H\ndisable_refresh: true\nskip_validation_on_start: true\n";

        final SecretStoreConfiguration objectUnderTest = objectMapper.readValue(yaml, SecretStoreConfiguration.class);

        assertThat(objectUnderTest.getRefreshInterval(), equalTo(Duration.ofHours(2)));
        assertThat(objectUnderTest.isDisableRefresh(), is(true));
        assertThat(objectUnderTest.isSkipValidationOnStart(), is(true));
    }

    @Test
    void validate_refresh_interval_below_one_hour_has_violation() throws IOException {
        final SecretStoreConfiguration objectUnderTest =
                objectMapper.readValue("refresh_interval: PT59M\n", SecretStoreConfiguration.class);

        final Set<ConstraintViolation<SecretStoreConfiguration>> violations = VALIDATOR.validate(objectUnderTest);

        assertThat(violations.size(), equalTo(1));
        assertThat(violations.iterator().next().getMessage(), equalTo("Refresh interval must be at least 1 hour."));
    }

    @Test
    void validate_null_refresh_interval_has_violation() throws IOException {
        final SecretStoreConfiguration objectUnderTest =
                objectMapper.readValue("refresh_interval: null\n", SecretStoreConfiguration.class);

        final Set<ConstraintViolation<SecretStoreConfiguration>> violations = VALIDATOR.validate(objectUnderTest);

        assertThat(violations.size(), equalTo(1));
        assertThat(violations.iterator().next().getMessage(), equalTo("refresh_interval must not be null"));
    }
}
