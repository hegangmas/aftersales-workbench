package com.csg.workbench.device;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DeviceController {
    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @GetMapping({"/api/devices/lookup", "/api/devices/search"})
    public Map<String, Object> lookup(@RequestParam String query,
                                      @RequestParam(required = false) String failSource) {
        return devices.lookup(query, failSource);
    }

    @GetMapping("/api/customers")
    public List<Map<String, Object>> customers() {
        return devices.listCustomers();
    }

    @PostMapping("/api/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createCustomer(Authentication auth, @Valid @RequestBody CreateCustomerRequest req) {
        boolean allowed = auth.getAuthorities().stream().anyMatch(a ->
            "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_DISPATCHER".equals(a.getAuthority()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员或调度可以维护客户与项目档案");
        }
        String id = devices.createCustomerAndDevice(req.name, req.contactName, req.phone, req.projectCode,
            req.projectName, req.address, req.customerType, req.slaMinutes == null ? 120 : req.slaMinutes.intValue(),
            req.sn, req.model, req.productType, req.warrantyUntil);
        return Collections.singletonMap("id", id);
    }

    public static class CreateCustomerRequest {
        @NotBlank(message = "请填写客户名称") public String name;
        @NotBlank(message = "请填写联系人") public String contactName;
        @NotBlank(message = "请填写联系电话") public String phone;
        public String projectCode;
        public String projectName;
        public String address;
        public String customerType;
        public Integer slaMinutes;
        public String sn;
        public String model;
        public String productType;
        public String warrantyUntil;
    }
}
