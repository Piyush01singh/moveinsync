package com.moveinsync.mdm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MdmFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullUpdateFlow_ShouldEnforcePoliciesAndPublishDashboardMetrics() throws Exception {
        String adminAuth = basicAuth("admin", "admin123");
        String deviceKey = "moveinsync-device-key";
        String prefix = "99.88.77";
        String imei = "358900001112223";

        createVersion(adminAuth, prefix + ".0", false, "Android 12+");
        createVersion(adminAuth, prefix + ".1", true, "Android 12+");
        createVersion(adminAuth, prefix + ".2", false, "Android 15+");

        String scheduleBody = """
                {
                  "fromVersion":"%s",
                  "toVersion":"%s",
                  "targetRegion":"Bangalore",
                  "customizationTag":"ClientA",
                  "targetDeviceGroup":"drivers-west",
                  "immediate":true,
                  "rolloutPercentage":100
                }
                """.formatted(prefix + ".0", prefix + ".1");

        JsonNode schedule = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/admin/schedules")
                                .header("Authorization", adminAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(scheduleBody))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        long scheduleId = schedule.get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/schedules/{id}/approve", scheduleId)
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved from integration test\"}"))
                .andExpect(status().isOk());

        String heartbeatBody = """
                {
                  "imeiNumber":"%s",
                  "appVersion":"%s",
                  "deviceOs":"Android 14",
                  "deviceModel":"Pixel 8",
                  "locationRegion":"Bangalore",
                  "customizationTag":"ClientA",
                  "deviceGroup":"drivers-west"
                }
                """.formatted(imei, prefix + ".0");

        JsonNode heartbeat = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/device/heartbeat")
                                .header("X-API-KEY", deviceKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(heartbeatBody))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        assertTrue(heartbeat.get("updateAvailable").asBoolean());
        long hbScheduleId = heartbeat.get("scheduleId").asLong();

        mockMvc.perform(post("/api/v1/update/status")
                        .header("X-API-KEY", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imeiNumber":"%s",
                                  "scheduleId":%d,
                                  "status":"INSTALLATION_COMPLETED"
                                }
                                """.formatted(imei, hbScheduleId)))
                .andExpect(status().isConflict());

        postLifecycle(deviceKey, imei, hbScheduleId, prefix + ".0", prefix + ".1", "DOWNLOAD_STARTED");
        postLifecycle(deviceKey, imei, hbScheduleId, prefix + ".0", prefix + ".1", "DOWNLOAD_COMPLETED");
        postLifecycle(deviceKey, imei, hbScheduleId, prefix + ".0", prefix + ".1", "INSTALLATION_STARTED");
        postLifecycle(deviceKey, imei, hbScheduleId, prefix + ".0", prefix + ".1", "INSTALLATION_COMPLETED");

        mockMvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromVersion":"%s",
                                  "toVersion":"%s",
                                  "targetRegion":"Bangalore",
                                  "customizationTag":"ClientA",
                                  "targetDeviceGroup":"drivers-west",
                                  "immediate":true,
                                  "rolloutPercentage":100
                                }
                                """.formatted(prefix + ".1", prefix + ".2")))
                .andExpect(status().isOk());

        JsonNode osBlockedHeartbeat = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/device/heartbeat")
                                .header("X-API-KEY", deviceKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "imeiNumber":"%s",
                                          "appVersion":"%s",
                                          "deviceOs":"Android 14",
                                          "deviceModel":"Pixel 8",
                                          "locationRegion":"Bangalore",
                                          "customizationTag":"ClientA",
                                          "deviceGroup":"drivers-west"
                                        }
                                        """.formatted(imei, prefix + ".1")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );
        assertTrue(osBlockedHeartbeat.get("message").asText().contains("does not satisfy supported range"));

        JsonNode auditTimeline = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/audit/{imei}", imei)
                                .header("Authorization", adminAuth))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );
        assertTrue(auditTimeline.isArray() && auditTimeline.size() >= 5);

        JsonNode dashboard = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/dashboard")
                                .header("Authorization", adminAuth)
                                .param("inactiveMinutes", "60"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        assertTrue(dashboard.has("successRatePercentage"));
        assertTrue(dashboard.has("failureRatePercentage"));
        assertTrue(dashboard.has("rolloutProgressPercentage"));
        assertTrue(dashboard.has("deviceGroupDistribution"));
        assertTrue(dashboard.has("failureStageDistribution"));
        assertTrue(dashboard.has("versionHeatmap"));
    }

    private void postLifecycle(
            String deviceKey,
            String imei,
            long scheduleId,
            String fromVersion,
            String toVersion,
            String status
    ) throws Exception {
        mockMvc.perform(post("/api/v1/update/status")
                        .header("X-API-KEY", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imeiNumber":"%s",
                                  "scheduleId":%d,
                                  "fromVersion":"%s",
                                  "toVersion":"%s",
                                  "status":"%s"
                                }
                                """.formatted(imei, scheduleId, fromVersion, toVersion, status)))
                .andExpect(status().isAccepted());
    }

    private void createVersion(String adminAuth, String versionCode, boolean mandatory, String supportedOsRange) throws Exception {
        mockMvc.perform(post("/api/v1/admin/versions")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionCode":"%s",
                                  "versionName":"%s",
                                  "supportedOsRange":"%s",
                                  "customizationTag":"ClientA",
                                  "mandatory":%s
                                }
                                """.formatted(versionCode, versionCode, supportedOsRange, mandatory)))
                .andExpect(status().isOk());
    }

    private String basicAuth(String username, String password) {
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
