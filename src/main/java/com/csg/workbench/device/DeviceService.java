package com.csg.workbench.device;

import com.csg.workbench.problem.ProblemMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DeviceMapper devices;
    private final ProblemMapper problems;

    public DeviceService(DeviceMapper devices, ProblemMapper problems) {
        this.devices = devices;
        this.problems = problems;
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
        String now = LocalDateTime.now(BUSINESS_ZONE).format(STAMP);
        boolean mesFail = "mes".equalsIgnoreCase(failSource);
        boolean installFail = "install".equalsIgnoreCase(failSource);
        boolean callFail = "400".equalsIgnoreCase(failSource);
        boolean lezhuangFail = "lezhuang".equalsIgnoreCase(failSource);

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
        out.put("manufacturedAt", mesFail ? null : row.get("manufacturedAt"));
        out.put("mesBatch", mesFail ? null : row.get("mesBatch"));
        out.put("mesTestResult", mesFail ? "MES接口暂不可用（降级）" : row.get("mesTestResult"));
        out.put("installedAt", installFail ? null : row.get("installedAt"));
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
        sources.add(sourceState("MES生产系统", !mesFail, mesFail ? "超时降级，已标注缺口" : "已同步（模拟）", now));
        sources.add(sourceState("售后安装系统", !installFail, installFail ? "暂不可用" : "已同步（模拟）", now));
        sources.add(sourceState("400客服系统", !callFail, callFail ? "暂不可用" : "已同步（模拟）", now));
        sources.add(sourceState("智享乐装", !lezhuangFail, lezhuangFail ? "暂不可用" : "已同步（模拟）", now));
        out.put("sourceStatuses", sources);

        // 模拟历史报修、历史维修、充电与录音、深链
        out.put("repairHistory", lezhuangFail ? new ArrayList<Map<String, Object>>() : sampleRepairs(sn));
        out.put("chargingRecords", sampleCharging(sn));
        out.put("recentAlerts", sampleAlerts(sn));
        out.put("callRecordings", callFail ? new ArrayList<Map<String, Object>>() : sampleRecordings(sn));
        out.put("simulatedCall", callFail ? null : sampleCall(String.valueOf(row.get("customerPhone"))));
        out.put("relatedProblems", problems.findByDevice(sn));
        out.put("deepLinks", deepLinks(sn, String.valueOf(row.get("projectCode"))));
        return out;
    }

    private static Map<String, Object> sourceState(String name, boolean ok, String note, String queriedAt) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        m.put("ok", Boolean.valueOf(ok));
        m.put("note", note);
        m.put("queriedAt", queriedAt);
        return m;
    }

    private static List<Map<String, Object>> sampleRepairs(String sn) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> r1 = new LinkedHashMap<String, Object>();
        r1.put("date", "2026-10-12");
        r1.put("orderNo", "LZ-20261012-09");
        r1.put("summary", "更换 4G 通信模块");
        r1.put("source", "智享乐装（模拟）");
        r1.put("deepLink", "#lezhuang/LZ-20261012-09");
        list.add(r1);
        Map<String, Object> r2 = new LinkedHashMap<String, Object>();
        r2.put("date", "2026-09-05");
        r2.put("orderNo", "RM-20260905-03");
        r2.put("summary", "远程升级主板程序（" + sn + "）");
        r2.put("source", "远程运维（模拟）");
        r2.put("deepLink", "#charger-platform/" + sn);
        list.add(r2);
        return list;
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

    private static List<Map<String, Object>> sampleRecordings(String sn) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> r1 = new LinkedHashMap<String, Object>();
        r1.put("id", "REC-" + sn + "-01");
        r1.put("time", "11-25 10:22");
        r1.put("title", "通话录音");
        r1.put("duration", "00:45");
        list.add(r1);
        Map<String, Object> r2 = new LinkedHashMap<String, Object>();
        r2.put("id", "REC-" + sn + "-02");
        r2.put("time", "11-25 10:15");
        r2.put("title", "通话录音");
        r2.put("duration", "03:28");
        list.add(r2);
        return list;
    }

    private static Map<String, Object> sampleCall(String phone) {
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("callId", "CALL-400-SIM-01");
        call.put("caller", phone == null ? "138****1234" : phone);
        call.put("duration", "00:22");
        call.put("recordingStatus", "录音中");
        return call;
    }

    private static Map<String, String> deepLinks(String sn, String projectCode) {
        Map<String, String> links = new LinkedHashMap<String, String>();
        links.put("devicePlatform", "https://charger-platform.internal/devices/" + sn);
        links.put("mesBatch", "https://mes.internal/trace/" + sn);
        links.put("installOrder", "https://lezhuang.internal/install/" + projectCode);
        return links;
    }
}
