package com.csg.workbench.problem;

import com.csg.workbench.device.DeviceService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProblemService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
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
        lockActive(id);
        mapper.insertActivity(id, "followup", note.trim());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        Map<String, Object> problem = requireProblem(id);
        problem.put("pendingFollowup", mapper.pending(id, LocalDateTime.now(BUSINESS_ZONE)));
        problem.put("activities", mapper.listActivities(id));
        return problem;
    }

    public List<Map<String, Object>> findByDevice(String device) {
        return mapper.findByDevice(device.trim());
    }

    public List<Map<String, Object>> due() {
        return mapper.due(LocalDateTime.now(BUSINESS_ZONE));
    }

    @Transactional
    public void schedule(String id, String owner, String step, LocalDateTime due) {
        lockActive(id);
        if (due == null || !due.isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择未来的回访时间");
        }
        mapper.completeFollowup(id);
        mapper.schedule(id, owner.trim(), step.trim(), due);
        mapper.changeState(id, "waiting");
        mapper.insertActivity(id, "schedule", "回访安排：" + due + "，" + owner.trim() + "，" + step.trim());
    }

    @Transactional
    public void finishCallback(String id, String outcome, String note) {
        lockActive(id);
        if (mapper.completeFollowup(id) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前没有待处理回访");
        }
        String result = note.trim();
        switch (outcome) {
            case "continue":
                mapper.changeState(id, "open");
                mapper.insertActivity(id, "callback", "继续跟进：" + result);
                break;
            case "field":
                mapper.changeState(id, "field");
                mapper.insertActivity(id, "callback", "待现场派单（需在智享乐装提交）：" + result);
                break;
            case "resolved":
                mapper.verify(id, result);
                mapper.changeState(id, "resolved");
                mapper.insertActivity(id, "callback", "确认恢复并结案：" + result);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知回访结果");
        }
    }

    private void lockActive(String id) {
        String state = mapper.lockState(id);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        if ("resolved".equals(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "问题单已结案");
        }
    }

    private Map<String, Object> requireProblem(String id) {
        Map<String, Object> problem = mapper.findProblem(id);
        if (problem == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        return problem;
    }
}
