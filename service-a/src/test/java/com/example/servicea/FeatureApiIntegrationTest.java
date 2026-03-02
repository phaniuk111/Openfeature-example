package com.example.servicea;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestMethodOrder(OrderAnnotation.class)
class FeatureApiIntegrationTest {

    private static final Path TEMP_DIR;
    private static final Path FLAG_FILE;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("service-a-filemode-it");
            FLAG_FILE = TEMP_DIR.resolve("flags-dev.json");
            writeFlags(true, 100, "Hello from dev", false);
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("app.env", () -> "dev");
        registry.add("app.flags.mode", () -> "per-env");
        registry.add("app.flags.base-path", () -> TEMP_DIR.toString());
        registry.add("app.flags.explicit-file", () -> FLAG_FILE.toString());
        registry.add("app.flags.deadline-ms", () -> "500");
    }

    @Test
    @Order(1)
    void returnsFlagsFromInitialFile() {
        Map<String, Object> payload = fetchFlags("dev");

        assertThat(payload.get("service")).isEqualTo("service-a");
        assertThat(payload.get("environment")).isEqualTo("dev");
        assertThat(payload.get("newUiEnabled")).isEqualTo(true);
        assertThat(((Number) payload.get("dataflowBatchSize")).intValue()).isEqualTo(100);
        assertThat(payload.get("welcomeBanner")).isEqualTo("Hello from dev");
        assertThat(payload.get("checkoutV2")).isEqualTo(false);
    }

    @Test
    @Order(2)
    void reloadsFlagsAfterFileChange() throws IOException {
        writeFlags(false, 2000, "Hello from updated file", true);

        Awaitility.await()
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            Map<String, Object> payload = fetchFlags("dev");
                            assertThat(payload.get("newUiEnabled")).isEqualTo(false);
                            assertThat(((Number) payload.get("dataflowBatchSize")).intValue())
                                    .isEqualTo(2000);
                            assertThat(payload.get("welcomeBanner"))
                                    .isEqualTo("Hello from updated file");
                            assertThat(payload.get("checkoutV2")).isEqualTo(true);
                        });
    }

    private Map<String, Object> fetchFlags(String environment) {
        String url = "http://localhost:" + port + "/api/flags?environment=" + environment;
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private static void writeFlags(
            boolean newUiEnabled, int batchSize, String banner, boolean checkoutV2)
            throws IOException {
        String json =
                "{\n"
                        + "  \"$schema\": \"https://flagd.dev/schema/v0/flags.json\",\n"
                        + "  \"flags\": {\n"
                        + "    \"new-ui\": {\n"
                        + "      \"state\": \"ENABLED\",\n"
                        + "      \"variants\": {\"on\": true, \"off\": false},\n"
                        + "      \"defaultVariant\": \""
                        + (newUiEnabled ? "on" : "off")
                        + "\"\n"
                        + "    },\n"
                        + "    \"dataflow-batch-size\": {\n"
                        + "      \"state\": \"ENABLED\",\n"
                        + "      \"variants\": {\"small\": 100, \"large\": 2000},\n"
                        + "      \"defaultVariant\": \""
                        + (batchSize >= 2000 ? "large" : "small")
                        + "\"\n"
                        + "    },\n"
                        + "    \"welcome-banner\": {\n"
                        + "      \"state\": \"ENABLED\",\n"
                        + "      \"variants\": {\"text\": \""
                        + banner
                        + "\"},\n"
                        + "      \"defaultVariant\": \"text\"\n"
                        + "    },\n"
                        + "    \"checkout-v2\": {\n"
                        + "      \"state\": \"ENABLED\",\n"
                        + "      \"variants\": {\"on\": true, \"off\": false},\n"
                        + "      \"defaultVariant\": \""
                        + (checkoutV2 ? "on" : "off")
                        + "\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n";

        Files.writeString(
                FLAG_FILE,
                json,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.setLastModifiedTime(FLAG_FILE, FileTime.from(Instant.now().plusMillis(50)));
    }
}
