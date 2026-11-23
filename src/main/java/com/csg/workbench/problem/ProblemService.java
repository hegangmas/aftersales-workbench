package com.csg.workbench.problem;

import com.csg.workbench.device.DeviceService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProblemService {
    private final ProblemMapper mapper;
    private final DeviceService devices;

    public ProblemService(ProblemMapper mapper, DeviceService devices) {
        this.mapper = mapper;
        this.devices = devices;
    }

    @Transactional
    public String create(String device, String title, String description) {
        devices.requireDevice(device);
        String id = UUID.randomUUID().toString();
        mapper.insertProblem(id, title.trim(), description.trim(), device);
        mapper.insertActivity(id, "create", "受理问题");
        return id;
    }

    @Transactional
    public void recordFollowup(String id, String note) {
        requireProblem(id);
        mapper.insertActivity(id, "followup", note.trim());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        Map<String, Object> problem = requireProblem(id);
        problem.put("activities", mapper.listActivities(id));
        return problem;
    }

    private Map<String, Object> requireProblem(String id) {
        Map<String, Object> problem = mapper.findProblem(id);
        if (problem == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        return problem;
    }
}
