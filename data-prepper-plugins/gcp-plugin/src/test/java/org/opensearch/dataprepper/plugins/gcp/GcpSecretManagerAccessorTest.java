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
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.plugins.secrets.SecretStoreException;
import org.opensearch.dataprepper.plugins.secrets.SecretVersion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GcpSecretManagerAccessorTest {
    private static final String SECRET_NAME = "projects/p/secrets/s";
    private static final String VERSION_1 = SECRET_NAME + "/versions/1";
    private static final String ACCESS_PATH = "/v1/" + SECRET_NAME + "/versions/latest:access";
    private static final String ADD_PATH = "/v1/" + SECRET_NAME + ":addVersion";
    private static final String DESTROY_PATH = "/v1/" + VERSION_1 + ":destroy";
    private static final String PAYLOAD = "super-secret";

    @Mock
    private GoogleCredentials credentials;

    private WireMockServer wireMockServer;
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        lenient().when(credentials.getRequestMetadata(any(URI.class)))
                .thenReturn(Map.of("Authorization", List.of("Bearer test-token")));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private GcpSecretManagerAccessor createObjectUnderTest() {
        return createObjectUnderTest(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
    }

    private GcpSecretManagerAccessor createObjectUnderTest(final HttpClient httpClient) {
        return new GcpSecretManagerAccessor(credentials, httpClient, URI.create(wireMockServer.baseUrl() + "/"),
                new ObjectMapper(), sleeps::add);
    }

    private static String base64(final String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static long crc32c(final String value) {
        final CRC32C crc32c = new CRC32C();
        crc32c.update(value.getBytes(StandardCharsets.UTF_8));
        return crc32c.getValue();
    }

    private static String accessResponse(final String data, final String checksum) {
        final String checksumField = checksum == null ? "" : ",\"dataCrc32c\":\"" + checksum + "\"";
        return "{\"name\":\"" + VERSION_1 + "\",\"payload\":{\"data\":\"" + data + "\"" + checksumField + "}}";
    }

    @Test
    void accessLatest_returns_decoded_payload_and_version_name_with_auth_header() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH))
                .withHeader("Authorization", WireMock.equalTo("Bearer test-token"))
                .willReturn(okJson(accessResponse(base64(PAYLOAD), Long.toString(crc32c(PAYLOAD))))));

        final SecretVersion result = createObjectUnderTest().accessLatest(SECRET_NAME);

        assertThat(result.getVersionName(), equalTo(VERSION_1));
        assertThat(new String(result.getPayload(), StandardCharsets.UTF_8), equalTo(PAYLOAD));
    }

    @Test
    void accessLatest_without_checksum_returns_payload() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(okJson(accessResponse(base64(PAYLOAD), null))));

        assertThat(new String(createObjectUnderTest().accessLatest(SECRET_NAME).getPayload(), StandardCharsets.UTF_8),
                equalTo(PAYLOAD));
    }

    @Test
    void accessLatest_with_checksum_mismatch_throws_OTHER() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(okJson(accessResponse(base64(PAYLOAD), "1"))));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.OTHER));
        assertThat(exception.getMessage(), containsString("Checksum mismatch"));
    }

    @Test
    void accessLatest_with_invalid_base64_throws_OTHER() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(okJson(accessResponse("%%%not-base64%%%", null))));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.OTHER));
    }

    @Test
    void accessLatest_with_invalid_json_response_throws_OTHER() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(aResponse().withStatus(200).withBody("not json")));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.OTHER));
    }

    @ParameterizedTest
    @CsvSource({"404,NOT_FOUND", "403,ACCESS_DENIED", "401,ACCESS_DENIED", "400,OTHER"})
    void accessLatest_with_non_retryable_status_throws_mapped_reason_without_retry(
            final int status, final SecretStoreException.Reason reason) {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(aResponse().withStatus(status)
                .withBody("{\"error\":{\"code\":" + status + ",\"message\":\"Secret problem\"}}")));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getReason(), equalTo(reason));
        assertThat(exception.getMessage(), containsString("HTTP " + status));
        assertThat(exception.getMessage(), containsString("Secret problem"));
        wireMockServer.verify(exactly(1), getRequestedFor(urlEqualTo(ACCESS_PATH)));
        assertThat(sleeps.isEmpty(), is(true));
    }

    @Test
    void accessLatest_with_redirect_status_throws_OTHER_without_following_or_retrying() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(aResponse().withStatus(302)
                .withHeader("Location", "https://evil.example.com/")));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.OTHER));
        wireMockServer.verify(exactly(1), getRequestedFor(urlEqualTo(ACCESS_PATH)));
    }

    @Test
    void accessLatest_error_body_without_message_uses_placeholder() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(aResponse().withStatus(400).withBody("{}")));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getMessage(), containsString("no error message"));
    }

    @Test
    void accessLatest_error_body_that_is_not_json_uses_placeholder() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(aResponse().withStatus(400).withBody("oops")));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getMessage(), containsString("no error message"));
    }

    @Test
    void accessLatest_retries_429_then_succeeds() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).inScenario("retry").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429)).willSetStateTo("second"));
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).inScenario("retry").whenScenarioStateIs("second")
                .willReturn(okJson(accessResponse(base64(PAYLOAD), null))));

        final SecretVersion result = createObjectUnderTest().accessLatest(SECRET_NAME);

        assertThat(result.getVersionName(), equalTo(VERSION_1));
        assertThat(sleeps, equalTo(List.of(GcpSecretManagerAccessor.BASE_BACKOFF_MILLIS)));
    }

    @Test
    void accessLatest_with_429_on_every_attempt_throws_LIMIT_EXCEEDED() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(aResponse().withStatus(429)));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.LIMIT_EXCEEDED));
        wireMockServer.verify(exactly(GcpSecretManagerAccessor.MAX_ATTEMPTS), getRequestedFor(urlEqualTo(ACCESS_PATH)));
        assertThat(sleeps, equalTo(List.of(500L, 1000L)));
    }

    @Test
    void accessLatest_with_503_on_every_attempt_throws_OTHER() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(aResponse().withStatus(503)));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.OTHER));
        wireMockServer.verify(exactly(GcpSecretManagerAccessor.MAX_ATTEMPTS), getRequestedFor(urlEqualTo(ACCESS_PATH)));
    }

    @Test
    void accessLatest_retries_connection_reset_then_succeeds() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).inScenario("fault").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)).willSetStateTo("second"));
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).inScenario("fault").whenScenarioStateIs("second")
                .willReturn(okJson(accessResponse(base64(PAYLOAD), null))));

        assertThat(createObjectUnderTest().accessLatest(SECRET_NAME).getVersionName(), equalTo(VERSION_1));
    }

    @Test
    void accessLatest_when_credentials_fail_on_every_attempt_throws_OTHER_with_cause() throws IOException {
        when(credentials.getRequestMetadata(any(URI.class))).thenThrow(new IOException("token endpoint down"));
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest();

        final SecretStoreException exception =
                assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(exception.getReason(), equalTo(SecretStoreException.Reason.OTHER));
        assertThat(exception.getCause(), instanceOf(IOException.class));
        assertThat(sleeps.size(), equalTo(GcpSecretManagerAccessor.MAX_ATTEMPTS - 1));
    }

    @Test
    void accessLatest_when_interrupted_during_send_throws_and_restores_interrupt() throws Exception {
        final HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        final GcpSecretManagerAccessor objectUnderTest = createObjectUnderTest(httpClient);

        assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(Thread.interrupted(), is(true));
    }

    @Test
    void accessLatest_when_interrupted_during_backoff_throws_and_restores_interrupt() {
        wireMockServer.stubFor(get(urlEqualTo(ACCESS_PATH)).willReturn(aResponse().withStatus(503)));
        final GcpSecretManagerAccessor objectUnderTest = new GcpSecretManagerAccessor(credentials,
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                URI.create(wireMockServer.baseUrl() + "/"), new ObjectMapper(), millis -> {
                    throw new InterruptedException();
                });

        assertThrows(SecretStoreException.class, () -> objectUnderTest.accessLatest(SECRET_NAME));

        assertThat(Thread.interrupted(), is(true));
    }

    @Test
    void addVersion_posts_base64_payload_with_checksum_and_returns_version_name() {
        wireMockServer.stubFor(post(urlEqualTo(ADD_PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.payload.data", WireMock.equalTo(base64(PAYLOAD))))
                .withRequestBody(matchingJsonPath("$.payload.dataCrc32c", WireMock.equalTo(Long.toString(crc32c(PAYLOAD)))))
                .willReturn(okJson("{\"name\":\"" + SECRET_NAME + "/versions/2\"}")));

        final String result = createObjectUnderTest().addVersion(SECRET_NAME, PAYLOAD.getBytes(StandardCharsets.UTF_8));

        assertThat(result, equalTo(SECRET_NAME + "/versions/2"));
    }

    @Test
    void destroyVersion_posts_to_destroy_endpoint() {
        wireMockServer.stubFor(post(urlEqualTo(DESTROY_PATH)).willReturn(okJson("{\"name\":\"" + VERSION_1 + "\"}")));

        createObjectUnderTest().destroyVersion(VERSION_1);

        wireMockServer.verify(exactly(1), postRequestedFor(urlEqualTo(DESTROY_PATH)));
    }

    @Test
    void supportsVersionDestroy_is_true_and_close_does_not_throw() {
        final GcpSecretManagerAccessor objectUnderTest = new GcpSecretManagerAccessor(credentials);

        assertThat(objectUnderTest.supportsVersionDestroy(), is(true));
        objectUnderTest.close();
    }
}
