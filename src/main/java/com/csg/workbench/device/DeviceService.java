package com.csg.workbench.device;

import com.csg.workbench.integration.CallCenterClient;
import com.csg.workbench.integration.CallCenterClient.CallCenterResult;
import com.csg.workbench.integration.InstallClient;
import com.csg.workbench.integration.InstallClient.InstallResult;
import com.csg.workbench.integration.LezhuangClient;
import com.csg.workbench.integration.LezhuangClient.LezhuangResult;
import com.csg.workbench.integration.MesClient;
import com.csg.workbench.integration.MesClient.MesResult;
import com.csg.workbench.integration.SimulatedCallCenterClient;
import com.csg.workbench.integration.SimulatedInstallClient;
import com.csg.workbench.integration.SimulatedLezhuangClient;
import com.csg.workbench.integration.SimulatedMesClient;
import com.csg.workbench.integration.SourceStatus;
import com.csg.workbench.problem.ProblemMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceService {
    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private final DeviceMapper devices;
    private final ProblemMapper problems;
    private final MesClient mesClient;
    private final InstallClient installClient;
    private final LezhuangClient lezhuangClient;
    private final CallCenterClient callCenterClient;

    @Autowired
    public DeviceService(DeviceMapper devices,
                         ProblemMapper problems,
                         MesClient mesClient,
                         InstallClient installClient,
                         LezhuangClient lezhuangClient,
                         CallCenterClient callCenterClient) {
        this.devices = devices;
        this.problems = problems;
        this.mesClient = mesClient;
        this.installClient = installClient;
        this.lezhuangClient = lezhuangClient;
        this.callCenterClient = callCenterClient;
    }

    public DeviceService(DeviceMapper devices, ProblemMapper problems) {
        this(devices, problems,
            new SimulatedMesClient(),
            new SimulatedInstallClient(),
            new SimulatedLezhuangClient(),
            new SimulatedCallCenterClient());
    }

    public Map<String, Object> lookup(String query) {
        return lookup(query, null);
    }

    public Map<String, Object> lookup(String query, String failSource) {
        String key = query == null ? "" : query.trim();
        if (key.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入桩SN、手机号或项目编号");
        }
        List<Map<String, Object>> found = devices.searchDevices(key);
        if (found == null || found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到匹配设备");
        }
        Map<String, Object> row = found.get(0);
        return assembleProfile(row, failSource, found.size());
    }

    public Map<String, Object> findBySn(String sn) {
        if (sn == null || sn.trim().isEmpty()) {
            return null;
        }
        Map<String, Object> row = devices.findBySn(sn.trim());
        return row == null ? null : assembleProfile(row, null, 1);
    }

    public void requireDevice(String sn) {
        if (sn == null || sn.trim().isEmpty() || devices.findBySn(sn.trim()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择查询到的设备");
        }
    }

    public List<Map<String, Object>> listCustomers() {
        return devices.listCustomers();
    }

    @Transactional
    public String createCustomerAndDevice(String name, String contactName, String phone, String projectCode,
                                          String projectName, String address, String customerType, int slaMinutes,
                                          String sn, String model, String productType, String warrantyUntil) {
        String customerId = "CUST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        devices.insertCustomer(customerId, name.trim(), contactName.trim(), phone.trim(),
            projectCode == null ? "" : projectCode.trim(),
            projectName == null ? "" : projectName.trim(),
            address == null ? "" : address.trim(),
            customerType == null ? "retail" : customerType.trim(),
            slaMinutes <= 0 ? 120 : slaMinutes);
        if (sn != null && !sn.trim().isEmpty()) {
            devices.insertDevice(sn.trim(), customerId,
                model == null || model.trim().isEmpty() ? "7kW交流桩" : model.trim(),
                productType == null || productType.trim().isEmpty() ? "交流桩" : productType.trim(),
                "2025-06-01", "MES-SIM-NEW", "2025-06-15", "在保",
                warrantyUntil == null || warrantyUntil.trim().isEmpty() ? "2027-06-15" : warrantyUntil.trim(),
                "2027-06-30", "V2.4.18");
        }
        return customerId;
    }

    private Map<String, Object> assembleProfile(Map<String, Object> row, String failSource, int matchCount) {
        String sn = String.valueOf(row.get("sn"));
        String now = SourceStatus.resolveQueriedAt(null);
        String projectCode = String.valueOf(row.get("projectCode"));
        String customerPhone = row.get("customerPhone") == null ? null : String.valueOf(row.get("customerPhone"));

        boolean mesFail = "mes".equalsIgnoreCase(failSource);
        boolean installFail = "install".equalsIgnoreCase(failSource);
        boolean callFail = "400".equalsIgnoreCase(failSource);
        boolean lezhuangFail = "lezhuang".equalsIgnoreCase(failSource);

        MesResult mes = queryMesSafe(sn, row, mesFail, now);
        InstallResult install = queryInstallSafe(sn, row, installFail, now);
        CallCenterResult callCenter = queryCallCenterSafe(sn, customerPhone, callFail, now);
        LezhuangResult lezhuang = queryLezhuangSafe(sn, projectCode, lezhuangFail, now);

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("device", sn);
        out.put("sn", sn);
        out.put("matchCount", Integer.valueOf(matchCount));
        out.put("source", "本地模拟主档（4源并行聚合）");
        out.put("observedAt", now);
        out.put("fault", String.valueOf(row.get("currentStatus")) + "（" + String.valueOf(row.get("realtimeTelemetry")) + "）");

        // 兼容旧字段与工作台左栏 11 独立字段
        out.put("customerId", row.get("customerId"));
        out.put("customerName", row.get("customerName"));
        out.put("contactName", row.get("contactName"));
        out.put("customerPhone", row.get("customerPhone"));
        out.put("siteAddress", row.get("siteAddress"));
        out.put("projectCode", row.get("projectCode"));
        out.put("projectName", row.get("projectName"));
        out.put("customerType", row.get("customerType"));
        out.put("slaMinutes", row.get("slaMinutes"));
        out.put("model", row.get("model"));
        out.put("productType", row.get("productType"));
        out.put("manufacturedAt", mes.getManufacturedAt());
        out.put("mesBatch", mes.getMesBatch());
        out.put("mesTestResult", mes.getMesTestResult());
        out.put("installedAt", install.getInstalledAt());
        out.put("warrantyStatus", row.get("warrantyStatus"));
        out.put("warrantyUntil", row.get("warrantyUntil"));
        out.put("extendedWarranty", row.get("extendedWarranty"));
        out.put("simExpiresAt", row.get("simExpiresAt"));
        out.put("firmwareVersion", row.get("firmwareVersion"));
        out.put("onlineStatus", row.get("onlineStatus"));
        out.put("currentStatus", row.get("currentStatus"));
        out.put("realtimeTelemetry", row.get("realtimeTelemetry"));

        // 4 源并行状态
        List<Map<String, Object>> sources = new ArrayList<Map<String, Object>>();
        sources.add(mes.getSourceStatus());
        sources.add(install.getSourceStatus());
        sources.add(callCenter.getSourceStatus());
        sources.add(lezhuang.getSourceStatus());
        out.put("sourceStatuses", sources);

        // 模拟历史报修、历史维修、充电与录音、深链
        out.put("repairHistory", lezhuang.getRepairHistory());
        out.put("chargingRecords", sampleCharging(sn));
        out.put("recentAlerts", sampleAlerts(sn));
        out.put("callRecordings", callCenter.getCallRecordings());
        out.put("simulatedCall", callCenter.getSimulatedCall());
        out.put("relatedProblems", problems.findByDevice(sn));
        out.put("deepLinks", deepLinks(sn, mes.getTraceDeepLink(), lezhuang.getInstallOrderDeepLink()));
        return out;
    }

    private MesResult queryMesSafe(String sn, Map<String, Object> row, boolean fail, String now) {
        try {
            return mesClient.queryDevice(sn, row, fail, now);
        } catch (RuntimeException ex) {
            log.warn("MES client call failed for sn={}: {}", sn, ex.getMessage());
            return MesResult.degraded(sn, "超时降级，已标注缺口", now);
        }
    }

    private InstallResult queryInstallSafe(String sn, Map<String, Object> row, boolean fail, String now) {
        try {
            return installClient.queryInstall(sn, row, fail, now);
        } catch (RuntimeException ex) {
            log.warn("Install client call failed for sn={}: {}", sn, ex.getMessage());
            return InstallResult.degraded("暂不可用", now);
        }
    }

    private CallCenterResult queryCallCenterSafe(String sn, String phone, boolean fail, String now) {
        try {
            return callCenterClient.queryCallHistory(sn, phone, fail, now);
        } catch (RuntimeException ex) {
            log.warn("CallCenter client call failed for sn={}: {}", sn, ex.getMessage());
            return CallCenterResult.degraded("暂不可用", now);
        }
    }

    private LezhuangResult queryLezhuangSafe(String sn, String projectCode, boolean fail, String now) {
        try {
            return lezhuangClient.queryServiceRecords(sn, projectCode, fail, now);
        } catch (RuntimeException ex) {
            log.warn("Lezhuang client call failed for sn={}: {}", sn, ex.getMessage());
            return LezhuangResult.degraded(projectCode, "暂不可用", now);
        }
    }

    private static List<Map<String, Object>> sampleCharging(String sn) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> c1 = new LinkedHashMap<String, Object>();
        c1.put("orderNo", "CHG-" + sn + "-01");
        c1.put("startedAt", "11-25 09:15");
        c1.put("status", "充电完成");
        c1.put("energy", "12.6 kWh");
        c1.put("stopReason", "正常结束");
        list.add(c1);
        Map<String, Object> c2 = new LinkedHashMap<String, Object>();
        c2.put("orderNo", "CHG-" + sn + "-02");
        c2.put("startedAt", "11-24 19:30");
        c2.put("status", "充电中断");
        c2.put("energy", "3.2 kWh");
        c2.put("stopReason", "PE 接地检测异常");
        list.add(c2);
        return list;
    }

    private static List<Map<String, Object>> sampleAlerts(String sn) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> a1 = new LinkedHashMap<String, Object>();
        a1.put("code", "E-PE-01");
        a1.put("reportedAt", "11-25 09:40");
        a1.put("name", "PE 断线告警（" + sn + "）");
        a1.put("status", "未恢复");
        list.add(a1);
        return list;
    }

    private static Map<String, String> deepLinks(String sn, String mesTraceLink, String installOrderLink) {
        Map<String, String> links = new LinkedHashMap<String, String>();
        links.put("devicePlatform", "https://charger-platform.internal/devices/" + sn);
        links.put("mesBatch", mesTraceLink);
        links.put("installOrder", installOrderLink);
        return links;
    }
}
