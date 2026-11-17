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
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GcpSecretPluginConfigTest {
    private static final Validator VALIDATOR = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    @Test
    void deserialize_maps_each_store_name_to_its_configuration() throws IOException {
        final GcpSecretPluginConfig objectUnderTest = objectMapper.readValue(
                "store-a:\n  secret_id: a\nstore-b:\n  secret_id: projects/p/secrets/b\n", GcpSecretPluginConfig.class);

        assertThat(objectUnderTest.getSecretManagerConfigurationMap().size(), equalTo(2));
        assertThat(objectUnderTest.getSecretManagerConfigurationMap().get("store-a").getSecretId(), equalTo("a"));
        assertThat(VALIDATOR.validate(objectUnderTest).isEmpty(), is(true));
    }

    @Test
    void validate_cascades_into_store_configurations() throws IOException {
        final GcpSecretPluginConfig objectUnderTest =
                objectMapper.readValue("store-a:\n  secret_id: \"bad secret\"\n", GcpSecretPluginConfig.class);

        assertThat(VALIDATOR.validate(objectUnderTest).size(), equalTo(1));
    }

    @Test
    void getSecretManagerConfigurationMap_is_unmodifiable() throws IOException {
        final GcpSecretPluginConfig objectUnderTest =
                objectMapper.readValue("store-a:\n  secret_id: a\n", GcpSecretPluginConfig.class);

        assertThrows(UnsupportedOperationException.class, () -> objectUnderTest.getSecretManagerConfigurationMap().clear());
    }
}
