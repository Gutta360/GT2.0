package com.gt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class Gt2PostgresIntegrationTest {
    private static final String ADMIN = "integration-admin-token-32-characters-long";
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            System.getenv().getOrDefault("GT2_TEST_POSTGRES_IMAGE", "postgres:18-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("gt2.admin-token", () -> ADMIN);
        registry.add("gt2.snapshot-master-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("gt2.snapshot-storage", () -> Path.of("target/test-snapshots").toAbsolutePath().toString());
        registry.add("gt2.allowed-origins", () -> "https://localhost");
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void deviceRegistrationIsOneUseAndProtectedApisRequireAuthentication() throws Exception {
        Registration device = provision("TENANT_A_" + UUID.randomUUID().toString().substring(0, 6));
        HttpResponse<String> unauthenticated = send("GET", "/api/v1/snapshots", null, null, null);
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        HttpResponse<String> reuse = send("POST", "/api/v1/devices/register",
                "{\"enrollmentCode\":\"" + device.enrollmentCode + "\",\"deviceName\":\"Other\",\"applicationId\":\"com.gutta.gt2\",\"applicationVersion\":\"2.0.0\",\"platform\":\"ANDROID\"}", null, null);
        assertThat(reuse.statusCode()).isEqualTo(401);
    }

    @Test
    void acceptsOrderedPatientEventOnlyOnceAndRejectsMissingPredecessor() throws Exception {
        Registration device = provision("TENANT_B_" + UUID.randomUUID().toString().substring(0, 6));
        UUID patientId = UUID.randomUUID(), eventId = UUID.randomUUID();
        String event = patientEvent(eventId, patientId, 1, "CREATE", "P-00001", "Ravi");
        HttpResponse<String> first = send("POST", "/api/v1/sync/events", batch(device.deviceId, event), device.token, null);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(first.body()).path("results").get(0).path("status").asText()).isEqualTo("ACCEPTED");

        HttpResponse<String> replay = send("POST", "/api/v1/sync/events", batch(device.deviceId, event), device.token, null);
        assertThat(mapper.readTree(replay.body()).path("results").get(0).path("status").asText()).isEqualTo("ALREADY_PROCESSED");

        String gap = patientEvent(UUID.randomUUID(), patientId, 3, "UPDATE", "P-00001", "Ravi");
        HttpResponse<String> missing = send("POST", "/api/v1/sync/events", batch(device.deviceId, gap), device.token, null);
        assertThat(mapper.readTree(missing.body()).path("results").get(0).path("code").asText()).isEqualTo("STALE_OR_GAPPED_VERSION");
    }

    @Test
    void snapshotDownloadsAreTenantIsolatedAndChecksumVerified() throws Exception {
        Registration owner = provision("TENANT_C_" + UUID.randomUUID().toString().substring(0, 6));
        Registration stranger = provision("TENANT_D_" + UUID.randomUUID().toString().substring(0, 6));
        String document = "{\"contractVersion\":1,\"localSchemaVersion\":2,\"createdAt\":\"" + Instant.now() + "\",\"lastAcknowledgedEventId\":null,\"patients\":[]}";
        String body = mapper.writeValueAsString(java.util.Map.of("sha256", sha256(document), "snapshot", document));
        HttpResponse<String> uploaded = send("POST", "/api/v1/snapshots", body, owner.token, null);
        assertThat(uploaded.statusCode()).isEqualTo(201);
        String snapshotId = mapper.readTree(uploaded.body()).path("snapshotId").asText();
        assertThat(send("GET", "/api/v1/snapshots/" + snapshotId, null, stranger.token, null).statusCode()).isEqualTo(404);
        HttpResponse<String> download = send("GET", "/api/v1/snapshots/" + snapshotId, null, owner.token, null);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(document);
        assertThat(download.headers().firstValue("X-Snapshot-Sha256")).contains(sha256(document));
        HttpResponse<String> recovery = send("GET", "/api/v1/snapshots/" + snapshotId + "/recovery-package", null, owner.token, null);
        assertThat(recovery.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(recovery.body()).path("patients").size()).isZero();
        assertThat(recovery.headers().firstValue("X-Snapshot-Sha256")).contains(sha256(recovery.body()));
    }

    private Registration provision(String code) throws Exception {
        HttpResponse<String> tenant = send("POST", "/api/v1/admin/tenants",
                "{\"tenantCode\":\"" + code + "\",\"tenantName\":\"Integration Tenant\"}", null, ADMIN);
        assertThat(tenant.statusCode()).isEqualTo(201);
        String tenantId = mapper.readTree(tenant.body()).path("tenantId").asText();
        HttpResponse<String> enrollment = send("POST", "/api/v1/admin/tenants/" + tenantId + "/enrollment-codes", null, null, ADMIN);
        String enrollmentCode = mapper.readTree(enrollment.body()).path("enrollmentCode").asText();
        HttpResponse<String> registered = send("POST", "/api/v1/devices/register",
                "{\"enrollmentCode\":\"" + enrollmentCode + "\",\"deviceName\":\"Test Tablet\",\"applicationId\":\"com.gutta.gt2\",\"applicationVersion\":\"2.0.0\",\"platform\":\"ANDROID\"}", null, null);
        JsonNode json = mapper.readTree(registered.body());
        return new Registration(json.path("deviceId").asText(), json.path("deviceToken").asText(), enrollmentCode);
    }

    private String batch(String deviceId, String event) {
        return "{\"contractVersion\":1,\"deviceId\":\"" + deviceId + "\",\"batchId\":\"" + UUID.randomUUID() + "\",\"events\":[" + event + "]}";
    }

    private String patientEvent(UUID eventId, UUID patientId, long version, String operation, String number, String name) {
        Instant now = Instant.now();
        String patient = "{\"id\":\"" + patientId + "\",\"patientNumber\":\"" + number + "\",\"firstName\":\"" + name + "\",\"lastName\":null,\"gender\":\"MALE\",\"age\":30,\"mobile\":\"9876543210\",\"address\":\"Test address\",\"referredBy\":\"SELF\",\"doctorName\":null,\"consultationFee\":100.00,\"registrationDate\":\"" + now + "\",\"active\":true,\"createdAt\":\"" + now + "\",\"updatedAt\":\"" + now + "\",\"deletedAt\":null,\"recordVersion\":" + version + "}";
        return "{\"eventId\":\"" + eventId + "\",\"contractVersion\":1,\"entityType\":\"PATIENT\",\"entityId\":\"" + patientId + "\",\"operation\":\"" + operation + "\",\"payload\":{\"contractVersion\":1,\"entityType\":\"PATIENT\",\"operation\":\"" + operation + "\",\"patient\":" + patient + "},\"entityVersion\":" + version + ",\"createdAt\":\"" + now + "\"}";
    }

    private HttpResponse<String> send(String method, String path, String body, String token, String admin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (admin != null) request.header("X-GT2-Admin-Token", admin);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Registration(String deviceId, String token, String enrollmentCode) {}
}
