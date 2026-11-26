package com.csg.workbench.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.csg.workbench.device.DeviceMapper;
import com.csg.workbench.device.DeviceService;
import com.csg.workbench.integration.CallCenterClient.CallCenterResult;
import com.csg.workbench.integration.InstallClient.InstallResult;
import com.csg.workbench.integration.LezhuangClient.LezhuangResult;
import com.csg.workbench.integration.MesClient.MesResult;
import com.csg.workbench.problem.ProblemMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class SimulatedIntegrationClientsTest {

    private static final String TEST_SN = "SIM-AC-001";
    private static final String TEST_PROJECT = "PRJ-2025-01";
    private static final String TEST_PHONE = "13800138000";
    private static final String TEST_TIME = "2026-11-26 09:00:00";

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private ProblemMapper problemMapper;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            SimulatedMesClient.class,
            SimulatedInstallClient.class,
            SimulatedLezhuangClient.class,
            SimulatedCallCenterClient.class);

    @Test
    void mesClientReturnsNormalAndDegradedResults() {
        MesClient client = new SimulatedMesClient();
        Map<String, Object> row = sampleDeviceRow();

        MesResult ok = client.queryDevice(TEST_SN, row, false, TEST_TIME);
        assertEquals("2025-05-01", ok.getManufacturedAt());
        assertEquals("MES-BATCH-01", ok.getMesBatch());
        assertEquals("出厂测试通过", ok.getMesTestResult());
        assertEquals("https://mes.internal/trace/" + TEST_SN, ok.getTraceDeepLink());
        assertStatus(ok.getSourceStatus(), "MES生产系统", true, "已同步（模拟）", TEST_TIME);

        MesResult degraded = client.queryDevice(TEST_SN, row, true, TEST_TIME);
        assertNull(degraded.getManufacturedAt());
        assertNull(degraded.getMesBatch());
        assertEquals("MES接口暂不可用（降级）", degraded.getMesTestResult());
        assertEquals("https://mes.internal/trace/" + TEST_SN, degraded.getTraceDeepLink());
        assertStatus(degraded.getSourceStatus(), "MES生产系统", false, "超时降级，已标注缺口", TEST_TIME);
    }

    @Test
    void installClientReturnsNormalAndDegradedResults() {
        InstallClient client = new SimulatedInstallClient();
        Map<String, Object> row = sampleDeviceRow();

        InstallResult ok = client.queryInstall(TEST_SN, row, false, TEST_TIME);
        assertEquals("2025-05-15", ok.getInstalledAt());
        assertStatus(ok.getSourceStatus(), "售后安装系统", true, "已同步（模拟）", TEST_TIME);

        InstallResult degraded = client.queryInstall(TEST_SN, row, true, TEST_TIME);
        assertNull(degraded.getInstalledAt());
        assertStatus(degraded.getSourceStatus(), "售后安装系统", false, "暂不可用", TEST_TIME);
    }

    @Test
    void lezhuangClientReturnsNormalAndDegradedResults() {
        LezhuangClient client = new SimulatedLezhuangClient();

        LezhuangResult ok = client.queryServiceRecords(TEST_SN, TEST_PROJECT, false, TEST_TIME);
        assertEquals(2, ok.getRepairHistory().size());
        assertEquals("LZ-20261012-09", ok.getRepairHistory().get(0).get("orderNo"));
        assertEquals("#lezhuang/LZ-20261012-09", ok.getRepairHistory().get(0).get("deepLink"));
        assertEquals("https://lezhuang.internal/install/" + TEST_PROJECT, ok.getInstallOrderDeepLink());
        assertStatus(ok.getSourceStatus(), "智享乐装", true, "已同步（模拟）", TEST_TIME);

        LezhuangResult degraded = client.queryServiceRecords(TEST_SN, TEST_PROJECT, true, TEST_TIME);
        assertTrue(degraded.getRepairHistory().isEmpty());
        assertEquals("https://lezhuang.internal/install/" + TEST_PROJECT, degraded.getInstallOrderDeepLink());
        assertStatus(degraded.getSourceStatus(), "智享乐装", false, "暂不可用", TEST_TIME);
    }

    @Test
    void callCenterClientReturnsNormalAndDegradedResults() {
        CallCenterClient client = new SimulatedCallCenterClient();

        CallCenterResult ok = client.queryCallHistory(TEST_SN, TEST_PHONE, false, TEST_TIME);
        assertEquals(2, ok.getCallRecordings().size());
        assertEquals("REC-" + TEST_SN + "-01", ok.getCallRecordings().get(0).get("id"));
        assertNotNull(ok.getSimulatedCall());
        assertEquals("CALL-400-SIM-01", ok.getSimulatedCall().get("callId"));
        assertEquals(TEST_PHONE, ok.getSimulatedCall().get("caller"));
        assertStatus(ok.getSourceStatus(), "400客服系统", true, "已同步（模拟）", TEST_TIME);

        CallCenterResult fallbackPhone = client.queryCallHistory(TEST_SN, null, false, TEST_TIME);
        assertEquals("138****1234", fallbackPhone.getSimulatedCall().get("caller"));

        CallCenterResult degraded = client.queryCallHistory(TEST_SN, TEST_PHONE, true, TEST_TIME);
        assertTrue(degraded.getCallRecordings().isEmpty());
        assertNull(degraded.getSimulatedCall());
        assertStatus(degraded.getSourceStatus(), "400客服系统", false, "暂不可用", TEST_TIME);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deviceServiceAggregatesAllFourSourcesAndSupportsFailSourceDegradation() {
        Map<String, Object> row = sampleDeviceRow();
        when(deviceMapper.searchDevices(TEST_SN)).thenReturn(Collections.singletonList(row));
        when(problemMapper.findByDevice(TEST_SN)).thenReturn(Collections.<Map<String, Object>>emptyList());

        DeviceService service = new DeviceService(deviceMapper, problemMapper);

        Map<String, Object> normal = service.lookup(TEST_SN, null);
        assertEquals(TEST_SN, normal.get("sn"));
        assertEquals("2025-05-01", normal.get("manufacturedAt"));
        assertEquals("MES-BATCH-01", normal.get("mesBatch"));
        assertEquals("出厂测试通过", normal.get("mesTestResult"));
        assertEquals("2025-05-15", normal.get("installedAt"));
        assertEquals(2, ((List<Map<String, Object>>) normal.get("repairHistory")).size());
        assertEquals(2, ((List<Map<String, Object>>) normal.get("callRecordings")).size());
        assertNotNull(normal.get("simulatedCall"));

        List<Map<String, Object>> statuses = (List<Map<String, Object>>) normal.get("sourceStatuses");
        assertEquals(4, statuses.size());
        assertEquals("MES生产系统", statuses.get(0).get("name"));
        assertEquals("售后安装系统", statuses.get(1).get("name"));
        assertEquals("400客服系统", statuses.get(2).get("name"));
        assertEquals("智享乐装", statuses.get(3).get("name"));
        for (int i = 0; i < statuses.size(); i++) {
            assertEquals(Boolean.TRUE, statuses.get(i).get("ok"));
        }

        Map<String, String> deepLinks = (Map<String, String>) normal.get("deepLinks");
        assertEquals("https://charger-platform.internal/devices/" + TEST_SN, deepLinks.get("devicePlatform"));
        assertEquals("https://mes.internal/trace/" + TEST_SN, deepLinks.get("mesBatch"));
        assertEquals("https://lezhuang.internal/install/" + TEST_PROJECT, deepLinks.get("installOrder"));

        Map<String, Object> mesFailed = service.lookup(TEST_SN, "mes");
        assertNull(mesFailed.get("manufacturedAt"));
        assertNull(mesFailed.get("mesBatch"));
        assertEquals("MES接口暂不可用（降级）", mesFailed.get("mesTestResult"));
        List<Map<String, Object>> mesStatuses = (List<Map<String, Object>>) mesFailed.get("sourceStatuses");
        assertEquals(Boolean.FALSE, mesStatuses.get(0).get("ok"));
        assertEquals("超时降级，已标注缺口", mesStatuses.get(0).get("note"));
        assertEquals(Boolean.TRUE, mesStatuses.get(1).get("ok"));

        Map<String, Object> installFailed = service.lookup(TEST_SN, "install");
        assertNull(installFailed.get("installedAt"));
        List<Map<String, Object>> installStatuses = (List<Map<String, Object>>) installFailed.get("sourceStatuses");
        assertEquals(Boolean.FALSE, installStatuses.get(1).get("ok"));
        assertEquals("暂不可用", installStatuses.get(1).get("note"));

        Map<String, Object> callFailed = service.lookup(TEST_SN, "400");
        assertTrue(((List<Map<String, Object>>) callFailed.get("callRecordings")).isEmpty());
        assertNull(callFailed.get("simulatedCall"));
        List<Map<String, Object>> callStatuses = (List<Map<String, Object>>) callFailed.get("sourceStatuses");
        assertEquals(Boolean.FALSE, callStatuses.get(2).get("ok"));
        assertEquals("暂不可用", callStatuses.get(2).get("note"));

        Map<String, Object> lezhuangFailed = service.lookup(TEST_SN, "lezhuang");
        assertTrue(((List<Map<String, Object>>) lezhuangFailed.get("repairHistory")).isEmpty());
        List<Map<String, Object>> lzStatuses = (List<Map<String, Object>>) lezhuangFailed.get("sourceStatuses");
        assertEquals(Boolean.FALSE, lzStatuses.get(3).get("ok"));
        assertEquals("暂不可用", lzStatuses.get(3).get("note"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deviceServiceDegradesGracefullyWhenClientThrows() {
        Map<String, Object> row = sampleDeviceRow();
        when(deviceMapper.searchDevices(TEST_SN)).thenReturn(Collections.singletonList(row));
        when(problemMapper.findByDevice(TEST_SN)).thenReturn(Collections.<Map<String, Object>>emptyList());

        MesClient throwingMes = new MesClient() {
            @Override
            public MesResult queryDevice(String sn, Map<String, Object> localRow, boolean simulateFailure, String queriedAt) {
                throw new IllegalStateException("MES timeout");
            }
        };
        DeviceService service = new DeviceService(
            deviceMapper,
            problemMapper,
            throwingMes,
            new SimulatedInstallClient(),
            new SimulatedLezhuangClient(),
            new SimulatedCallCenterClient());

        Map<String, Object> result = service.lookup(TEST_SN, null);
        assertEquals("MES接口暂不可用（降级）", result.get("mesTestResult"));
        List<Map<String, Object>> statuses = (List<Map<String, Object>>) result.get("sourceStatuses");
        assertEquals(Boolean.FALSE, statuses.get(0).get("ok"));
        assertEquals(Boolean.TRUE, statuses.get(1).get("ok"));
    }

    @Test
    void conditionalBeansRegisterByDefaultAndDisableWhenModeChanged() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("simulatedMesClient"));
            assertTrue(context.containsBean("simulatedInstallClient"));
            assertTrue(context.containsBean("simulatedLezhuangClient"));
            assertTrue(context.containsBean("simulatedCallCenterClient"));
        });

        contextRunner
            .withPropertyValues("workbench.integration.mode=simulated")
            .run(context -> {
                assertEquals(1, context.getBeansOfType(MesClient.class).size());
                assertEquals(1, context.getBeansOfType(InstallClient.class).size());
                assertEquals(1, context.getBeansOfType(LezhuangClient.class).size());
                assertEquals(1, context.getBeansOfType(CallCenterClient.class).size());
            });

        contextRunner
            .withPropertyValues("workbench.integration.mode=remote")
            .run(context -> {
                assertFalse(context.containsBean("simulatedMesClient"));
                assertFalse(context.containsBean("simulatedInstallClient"));
                assertFalse(context.containsBean("simulatedLezhuangClient"));
                assertFalse(context.containsBean("simulatedCallCenterClient"));
            });
    }

    private static void assertStatus(Map<String, Object> status, String name, boolean ok, String note, String queriedAt) {
        assertNotNull(status);
        assertEquals(name, status.get("name"));
        assertEquals(Boolean.valueOf(ok), status.get("ok"));
        assertEquals(note, status.get("note"));
        assertEquals(queriedAt, status.get("queriedAt"));
    }

    private static Map<String, Object> sampleDeviceRow() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("sn", TEST_SN);
        row.put("customerId", "CUST-001");
        row.put("customerName", "测试客户");
        row.put("contactName", "张工");
        row.put("customerPhone", TEST_PHONE);
        row.put("siteAddress", "深圳市南山区");
        row.put("projectCode", TEST_PROJECT);
        row.put("projectName", "示范充电站");
        row.put("customerType", "enterprise");
        row.put("slaMinutes", Integer.valueOf(120));
        row.put("model", "7kW交流桩");
        row.put("productType", "交流桩");
        row.put("manufacturedAt", "2025-05-01");
        row.put("mesBatch", "MES-BATCH-01");
        row.put("mesTestResult", "出厂测试通过");
        row.put("installedAt", "2025-05-15");
        row.put("warrantyStatus", "在保");
        row.put("warrantyUntil", "2027-05-15");
        row.put("extendedWarranty", "无");
        row.put("simExpiresAt", "2027-06-30");
        row.put("firmwareVersion", "V2.4.18");
        row.put("onlineStatus", "在线");
        row.put("currentStatus", "告警");
        row.put("realtimeTelemetry", "PE 断线");
        return row;
    }
}
