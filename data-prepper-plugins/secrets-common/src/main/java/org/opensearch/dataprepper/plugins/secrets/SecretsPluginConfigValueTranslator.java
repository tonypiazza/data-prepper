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

import org.opensearch.dataprepper.model.plugin.PluginConfigValueTranslator;
import org.opensearch.dataprepper.model.plugin.PluginConfigVariable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates {@code ${{<prefix>:<secretConfigurationId>[:<secretKey>]}}} references using a {@link SecretsSupplier}.
 */
public class SecretsPluginConfigValueTranslator implements PluginConfigValueTranslator {
    static final String SECRET_CONFIGURATION_ID_GROUP = "secretConfigurationId";
    static final String SECRET_KEY_GROUP = "secretKey";
    static final Pattern SECRETS_REF_PATTERN = Pattern.compile(
            String.format("^(?<%s>[a-zA-Z0-9\\/_+.=@-]+)(:(?<%s>.+))?$",
                    SECRET_CONFIGURATION_ID_GROUP, SECRET_KEY_GROUP));

    private final String prefix;
    private final SecretsSupplier secretsSupplier;

    public SecretsPluginConfigValueTranslator(final String prefix, final SecretsSupplier secretsSupplier) {
        this.prefix = prefix;
        this.secretsSupplier = secretsSupplier;
    }

    @Override
    public Object translate(final String value) {
        final Matcher matcher = match(value);
        return retrieve(matcher.group(SECRET_CONFIGURATION_ID_GROUP), matcher.group(SECRET_KEY_GROUP));
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public PluginConfigVariable translateToPluginConfigVariable(final String value) {
        final Matcher matcher = match(value);
        final String secretId = matcher.group(SECRET_CONFIGURATION_ID_GROUP);
        final String secretKey = matcher.group(SECRET_KEY_GROUP);
        return new SecretsPluginConfigVariable(secretsSupplier, secretId, secretKey, retrieve(secretId, secretKey));
    }

    private Object retrieve(final String secretId, final String secretKey) {
        return secretKey != null ? secretsSupplier.retrieveValue(secretId, secretKey) : secretsSupplier.retrieveValue(secretId);
    }

    private Matcher match(final String value) {
        final Matcher matcher = SECRETS_REF_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format(
                    "Unable to parse %s or %s according to pattern %s",
                    SECRET_CONFIGURATION_ID_GROUP, SECRET_KEY_GROUP, SECRETS_REF_PATTERN.pattern()));
        }
        return matcher;
    }
}
