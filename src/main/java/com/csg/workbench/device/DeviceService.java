package com.csg.workbench.device;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceService {
    private static final String DEVICE = "SIM-AC-001";

    public Map<String, Object> lookup(String query) {
        if (!DEVICE.equalsIgnoreCase(query) && !"19900000001".equals(query)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到模拟设备");
        }
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("device", DEVICE);
        device.put("source", "充电桩管理平台（模拟）");
        device.put("observedAt", "2026-11-23T09:00:00+08:00");
        device.put("fault", "示例故障：车辆连接后未开始充电");
        device.put("chargingRecords", Collections.emptyList());
        return device;
    }

    public void requireDevice(String device) {
        if (!DEVICE.equals(device)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择查询到的设备");
        }
    }
}
