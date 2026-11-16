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

import java.util.Objects;

/**
 * Thrown by a {@link SecretStoreAccessor} when a call to the cloud secret store fails.
 */
public class SecretStoreException extends RuntimeException {
    /**
     * The category of failure, used for metrics.
     */
    public enum Reason {
        NOT_FOUND,
        LIMIT_EXCEEDED,
        ACCESS_DENIED,
        OTHER
    }

    private final Reason reason;

    public SecretStoreException(final Reason reason, final String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public SecretStoreException(final Reason reason, final String message, final Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason getReason() {
        return reason;
    }
}
