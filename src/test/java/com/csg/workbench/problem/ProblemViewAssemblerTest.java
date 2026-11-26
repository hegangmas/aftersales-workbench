package com.csg.workbench.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.csg.workbench.problem.dto.DeviceProfileView;
import com.csg.workbench.problem.dto.FieldOrderView;
import com.csg.workbench.problem.dto.ProblemBoardRow;
import com.csg.workbench.problem.dto.ProblemDetailView;
import com.csg.workbench.problem.dto.ProblemViewAssembler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 4 热点 DTO 组装及 camelCase JSON 序列化字段契约验证测试。
 */
class ProblemViewAssemblerTest {
    private final ProblemViewAssembler assembler = new ProblemViewAssembler();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void fieldOrderViewPreservesAllCamelCaseKeys() throws Exception {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("id", Long.valueOf(101L));
        raw.put("externalNo", "LZ-20261126-ABCDEF");
        raw.put("state", "completed");
        raw.put("receipt", "已更换控制板");
        raw.put("isClaim", Integer.valueOf(1));
        raw.put("returnToFactory", Integer.valueOf(0));
        raw.put("projectCode", "PRJ-HF-2026-01");
        raw.put("appointmentAt", Timestamp.valueOf("2026-11-26 14:00:00"));
        raw.put("photoNames", "a.jpg,b.jpg");
        raw.put("acceptedAt", LocalDateTime.of(2026, 11, 26, 10, 10));
        raw.put("arrivedAt", LocalDateTime.of(2026, 11, 26, 11, 20));
        raw.put("completedAt", LocalDateTime.of(2026, 11, 26, 12, 30));
        raw.put("replacedParts", "控制板");
        raw.put("oldPartNo", "OLD-001");
        raw.put("oldPartPhoto", "old.jpg");
        raw.put("customerAcceptResult", "客户验收通过");
        raw.put("feeOwner", "厂内质保");
        raw.put("rollbackAnomalyNote", "");

        FieldOrderView view = assembler.toFieldOrderView(raw);
        assertNotNull(view);
        assertEquals("LZ-20261126-ABCDEF", view.getExternalNo());
        assertEquals(Boolean.TRUE, view.getIsClaim());
        assertEquals(Boolean.FALSE, view.getReturnToFactory());

        JsonNode node = json.readTree(json.writeValueAsString(view));
        assertAllKeysPresentAndCamelCase(node, Arrays.asList(
            "id", "externalNo", "state", "receipt", "isClaim", "returnToFactory",
            "projectCode", "appointmentAt", "photoNames", "acceptedAt", "arrivedAt",
            "completedAt", "replacedParts", "oldPartNo", "oldPartPhoto",
            "customerAcceptResult", "feeOwner", "rollbackAnomalyNote"
        ));
        assertTrue(node.get("isClaim").asBoolean());
        assertFalse(node.get("returnToFactory").asBoolean());
    }

    @Test
    void boardRowViewPreservesAllCamelCaseKeys() throws Exception {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("id", "prob-1");
        raw.put("displayNumber", Long.valueOf(42L));
        raw.put("ticketNo", "GD202611260001");
        raw.put("title", "无法启动充电");
        raw.put("device", "CD26080101");
        raw.put("state", "processing");
        raw.put("owner", "operator.1");
        raw.put("category", "无法充电");
        raw.put("kind", "fault");
        raw.put("channel", "400");
        raw.put("devicePending", Integer.valueOf(0));
        raw.put("urgency", "high");
        raw.put("suspendReason", null);
        raw.put("grade", "A");
        raw.put("disposalType", "remote");
        raw.put("compromiseFlag", Integer.valueOf(1));
        raw.put("oldPartRegistered", Integer.valueOf(0));
        raw.put("escalationLevel", "l2");
        raw.put("slaDueAt", LocalDateTime.of(2026, 11, 26, 12, 0));
        raw.put("version", Integer.valueOf(3));
        raw.put("updatedAt", Timestamp.valueOf("2026-11-26 10:30:00"));
        raw.put("activeFieldOrders", Integer.valueOf(1));
        raw.put("nextStep", "确认复测");
        raw.put("nextDueAt", LocalDateTime.of(2026, 11, 26, 15, 0));

        List<ProblemBoardRow> rows = assembler.toBoardRows(Collections.singletonList(raw));
        assertEquals(1, rows.size());
        ProblemBoardRow row = rows.get(0);
        assertEquals("GD202611260001", row.getTicketNo());
        assertEquals(Boolean.FALSE, row.getDevicePending());
        assertEquals(Boolean.TRUE, row.getCompromiseFlag());

        JsonNode node = json.readTree(json.writeValueAsString(row));
        assertAllKeysPresentAndCamelCase(node, Arrays.asList(
            "id", "displayNumber", "ticketNo", "title", "device", "state", "owner",
            "category", "kind", "channel", "devicePending", "urgency", "suspendReason",
            "grade", "disposalType", "compromiseFlag", "oldPartRegistered", "escalationLevel",
            "slaDueAt", "version", "updatedAt", "activeFieldOrders", "nextStep", "nextDueAt"
        ));
    }

    @Test
    void problemDetailViewPreservesAllCamelCaseKeys() throws Exception {
        Map<String, Object> fieldOrder = new LinkedHashMap<String, Object>();
        fieldOrder.put("id", Long.valueOf(9L));
        fieldOrder.put("externalNo", "LZ-20261126-112233");
        fieldOrder.put("state", "dispatched");
        fieldOrder.put("isClaim", Boolean.TRUE);
        fieldOrder.put("returnToFactory", Boolean.FALSE);

        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("id", "prob-detail-1");
        raw.put("displayNumber", Long.valueOf(88L));
        raw.put("ticketNo", "GD202611260088");
        raw.put("title", "交流桩跳枪");
        raw.put("device", "CD26080101");
        raw.put("state", "pending_callback");
        raw.put("description", "充电 5 分钟后中断");
        raw.put("resumeState", null);
        raw.put("category", "无法充电");
        raw.put("kind", "fault");
        raw.put("channel", "400");
        raw.put("owner", "operator.1");
        raw.put("version", Integer.valueOf(4));
        raw.put("faultOccurredAt", LocalDateTime.of(2026, 11, 26, 9, 55));
        raw.put("suspendReason", null);
        raw.put("grade", "B");
        raw.put("disposalType", "part");
        raw.put("compromiseFlag", Integer.valueOf(0));
        raw.put("escalationLevel", "none");
        raw.put("escalationReason", null);
        raw.put("slaDueAt", LocalDateTime.of(2026, 11, 26, 11, 55));
        raw.put("autoApprovalNote", null);
        raw.put("customerId", "CUST-001");
        raw.put("devicePending", Integer.valueOf(0));
        raw.put("urgency", "normal");
        raw.put("symptomTags", "PE告警");
        raw.put("acceptedBy", "operator.1");
        raw.put("warrantyConfirmed", "在保");
        raw.put("snapshotJson", "{}");
        raw.put("oldPartRegistered", Integer.valueOf(1));
        raw.put("oldPartNo", "OLD-88");
        raw.put("reopenedFromId", null);
        raw.put("createdAt", LocalDateTime.of(2026, 11, 26, 10, 0));
        raw.put("updatedAt", LocalDateTime.of(2026, 11, 26, 10, 20));
        raw.put("pendingFollowup", Collections.singletonMap("owner", "operator.1"));
        raw.put("fieldOrders", Collections.singletonList(fieldOrder));
        raw.put("callbackResults", Collections.emptyList());
        raw.put("activities", Collections.emptyList());
        raw.put("ownershipEvents", Collections.emptyList());
        raw.put("evidences", Collections.emptyList());
        raw.put("transitions", Collections.emptyList());
        raw.put("assessments", Collections.emptyList());
        raw.put("sop", Collections.singletonMap("title", "充电故障标准排查 SOP"));

        ProblemDetailView view = assembler.toDetailView(raw);
        assertNotNull(view);
        assertEquals(1, view.getFieldOrders().size());
        assertEquals("LZ-20261126-112233", view.getFieldOrders().get(0).getExternalNo());
        assertEquals(Boolean.TRUE, view.getOldPartRegistered());

        JsonNode node = json.readTree(json.writeValueAsString(view));
        assertAllKeysPresentAndCamelCase(node, Arrays.asList(
            "id", "displayNumber", "ticketNo", "title", "device", "state", "description",
            "resumeState", "category", "kind", "channel", "owner", "version", "faultOccurredAt",
            "suspendReason", "grade", "disposalType", "compromiseFlag", "escalationLevel",
            "escalationReason", "slaDueAt", "autoApprovalNote", "customerId", "devicePending",
            "urgency", "symptomTags", "acceptedBy", "warrantyConfirmed", "snapshotJson",
            "oldPartRegistered", "oldPartNo", "reopenedFromId", "createdAt", "updatedAt",
            "pendingFollowup", "fieldOrders", "callbackResults", "activities", "ownershipEvents",
            "evidences", "transitions", "assessments", "sop"
        ));
    }

    @Test
    void deviceProfileViewPreservesAllCamelCaseKeysAndHandlesNulls() throws Exception {
        assertNull(assembler.toDeviceProfileView(null));
        assertNull(assembler.toDetailView(null));
        assertNull(assembler.toBoardRow(null));
        assertNull(assembler.toFieldOrderView(null));
        assertTrue(assembler.toBoardRows(null).isEmpty());
        assertTrue(assembler.toFieldOrderViews(null).isEmpty());

        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("device", "CD26080101");
        raw.put("sn", "CD26080101");
        raw.put("matchCount", Integer.valueOf(1));
        raw.put("source", "本地模拟主档（4源并行聚合）");
        raw.put("observedAt", "2026-11-26T10:30:00");
        raw.put("fault", "正常（PE 在线）");
        raw.put("customerId", "CUST-001");
        raw.put("customerName", "合肥滨湖示例充电站");
        raw.put("contactName", "王经理");
        raw.put("customerPhone", "13800001234");
        raw.put("siteAddress", "合肥市滨湖新区");
        raw.put("projectCode", "PRJ-HF-2026-01");
        raw.put("projectName", "合肥滨湖产业园充电项目");
        raw.put("customerType", "station");
        raw.put("slaMinutes", Integer.valueOf(120));
        raw.put("model", "7kW交流桩");
        raw.put("productType", "交流桩");
        raw.put("manufacturedAt", "2025-06-01");
        raw.put("mesBatch", "MES-2025-06");
        raw.put("mesTestResult", "出厂检验合格");
        raw.put("installedAt", "2025-06-15");
        raw.put("warrantyStatus", "在保");
        raw.put("warrantyUntil", "2027-06-15");
        raw.put("extendedWarranty", "无");
        raw.put("simExpiresAt", "2027-06-30");
        raw.put("firmwareVersion", "V2.4.18");
        raw.put("onlineStatus", "在线");
        raw.put("currentStatus", "正常");
        raw.put("realtimeTelemetry", "PE 在线");
        raw.put("sourceStatuses", Collections.singletonList(Collections.singletonMap("name", "MES生产系统")));
        raw.put("repairHistory", Collections.emptyList());
        raw.put("chargingRecords", Collections.emptyList());
        raw.put("recentAlerts", Collections.emptyList());
        raw.put("callRecordings", Collections.emptyList());
        raw.put("simulatedCall", Collections.singletonMap("status", "idle"));
        raw.put("relatedProblems", Collections.emptyList());
        raw.put("deepLinks", Collections.singletonMap("devicePlatform", "https://charger-platform.internal/devices/CD26080101"));

        DeviceProfileView view = assembler.toDeviceProfileView(raw);
        assertNotNull(view);
        assertEquals("CD26080101", view.getSn());
        assertEquals(Integer.valueOf(120), view.getSlaMinutes());
        assertEquals(1, view.getSourceStatuses().size());

        JsonNode node = json.readTree(json.writeValueAsString(view));
        assertAllKeysPresentAndCamelCase(node, Arrays.asList(
            "device", "sn", "matchCount", "source", "observedAt", "fault",
            "customerId", "customerName", "contactName", "customerPhone", "siteAddress",
            "projectCode", "projectName", "customerType", "slaMinutes", "model",
            "productType", "manufacturedAt", "mesBatch", "mesTestResult", "installedAt",
            "warrantyStatus", "warrantyUntil", "extendedWarranty", "simExpiresAt",
            "firmwareVersion", "onlineStatus", "currentStatus", "realtimeTelemetry",
            "sourceStatuses", "repairHistory", "chargingRecords", "recentAlerts",
            "callRecordings", "simulatedCall", "relatedProblems", "deepLinks"
        ));
    }

    private static void assertAllKeysPresentAndCamelCase(JsonNode node, List<String> expectedKeys) {
        for (String key : expectedKeys) {
            assertTrue(node.has(key), "Missing serialized JSON key: " + key);
        }
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            assertFalse(field.contains("_"), "Serialized JSON key must be camelCase without underscores: " + field);
        }
    }
}
