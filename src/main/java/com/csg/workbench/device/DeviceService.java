package com.csg.workbench.device;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceService {
    private static final String DEVICE_AC = "SIM-AC-001";
    private static final String DEVICE_DC = "SIM-DC-002";

    public Map<String, Object> lookup(String query) {
        String key = query == null ? "" : query.trim();
        if (DEVICE_AC.equalsIgnoreCase(key) || "19900000001".equals(key)) {
            return acDevice();
        }
        if (DEVICE_DC.equalsIgnoreCase(key) || "19900000002".equals(key)) {
            return dcDevice();
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到模拟设备");
    }

    public void requireDevice(String device) {
        if (!DEVICE_AC.equals(device) && !DEVICE_DC.equals(device)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择查询到的设备");
        }
    }

    private static Map<String, Object> acDevice() {
        Map<String, Object> device = new LinkedHashMap<String, Object>();
        device.put("device", DEVICE_AC);
        device.put("source", "充电桩管理平台（模拟）");
        device.put("observedAt", "2026-11-25T09:40:00+08:00");
        device.put("fault", "示例故障：车辆连接后未开始充电（PE 断线告警）");
        device.put("model", "7kW 交流桩（模拟）");
        device.put("customerName", "李先生（模拟）");
        device.put("customerPhone", "19900000001");
        device.put("siteAddress", "合肥市滨湖新区示例小区 3 栋地下车库（模拟）");
        device.put("manufacturedAt", "2025-06-18（MES 模拟）");
        device.put("mesTestResult", "出厂测试通过（MES 模拟）");
        device.put("warrantyStatus", "在保（模拟）");
        device.put("warrantyUntil", "2027-06-18");
        device.put("simExpiresAt", "2027-05-31（模拟）");
        device.put("firmwareVersion", "v2.4.18（模拟）");
        device.put("onlineStatus", "在线（模拟）");
        device.put("currentStatus", "告警中（模拟）");
        device.put("chargingRecords", acChargingRecords());
        device.put("recentAlerts", acAlerts());
        device.put("simulatedCall", call("CALL-SIM-20261125-01", "19900000001", "02:36", "模拟录音已挂接（400 未接入）"));
        return device;
    }

    private static Map<String, Object> dcDevice() {
        Map<String, Object> device = new LinkedHashMap<String, Object>();
        device.put("device", DEVICE_DC);
        device.put("source", "充电桩管理平台（模拟）");
        device.put("observedAt", "2026-11-25T08:15:00+08:00");
        device.put("fault", "示例故障：设备离线超过 2 小时（通信中断）");
        device.put("model", "120kW 直流双枪桩（模拟）");
        device.put("customerName", "王女士 · 示例场站（模拟）");
        device.put("customerPhone", "19900000002");
        device.put("siteAddress", "合肥市高新区示例公共充电站 2 号位（模拟）");
        device.put("manufacturedAt", "2025-03-10（MES 模拟）");
        device.put("mesTestResult", "出厂测试通过（MES 模拟）");
        device.put("warrantyStatus", "在保（模拟）");
        device.put("warrantyUntil", "2027-03-10");
        device.put("simExpiresAt", "2026-12-05 临期（模拟）");
        device.put("firmwareVersion", "v3.1.04（模拟）");
        device.put("onlineStatus", "离线（模拟）");
        device.put("currentStatus", "离线（模拟）");
        device.put("chargingRecords", dcChargingRecords());
        device.put("recentAlerts", dcAlerts());
        device.put("simulatedCall", call("CALL-SIM-20261125-02", "19900000002", "01:48", "模拟录音已挂接（400 未接入）"));
        return device;
    }

    private static List<Map<String, Object>> acChargingRecords() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(record("CHG-SIM-901", "1号枪", "11-25 09:15", "充电完成", "12.6 kWh", "正常结束"));
        list.add(record("CHG-SIM-889", "1号枪", "11-24 19:30", "充电中断", "3.2 kWh", "PE 接地检测异常"));
        return list;
    }

    private static List<Map<String, Object>> dcChargingRecords() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(record("CHG-SIM-772", "A枪", "11-25 06:10", "充电完成", "41.8 kWh", "BMS 充满停止"));
        return list;
    }

    private static List<Map<String, Object>> acAlerts() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(alert("E-PE-01", "11-25 09:40", "PE 断线告警", "未恢复"));
        list.add(alert("E-START-04", "11-24 19:32", "充电启动异常", "已恢复"));
        return list;
    }

    private static List<Map<String, Object>> dcAlerts() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(alert("E-NET-09", "11-25 08:15", "4G 心跳超时离线", "未恢复"));
        return list;
    }

    private static Map<String, Object> record(String orderNo, String gun, String startedAt,
                                              String status, String energy, String stopReason) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("orderNo", orderNo);
        item.put("gun", gun);
        item.put("startedAt", startedAt);
        item.put("status", status);
        item.put("energy", energy);
        item.put("stopReason", stopReason);
        return item;
    }

    private static Map<String, Object> alert(String code, String reportedAt, String name, String status) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("code", code);
        item.put("reportedAt", reportedAt);
        item.put("name", name);
        item.put("status", status);
        return item;
    }

    private static Map<String, Object> call(String callId, String caller, String duration, String recordingStatus) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("callId", callId);
        item.put("caller", caller);
        item.put("duration", duration);
        item.put("recordingStatus", recordingStatus);
        return item;
    }
}
