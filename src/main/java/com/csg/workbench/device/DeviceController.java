package com.csg.workbench.device;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceController {
    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @GetMapping("/api/devices/lookup")
    public Map<String, Object> lookup(@RequestParam String query) {
        return devices.lookup(query.trim());
    }
}
