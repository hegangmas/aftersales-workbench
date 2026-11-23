package com.csg.workbench.problem;

import com.csg.workbench.device.DeviceService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
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
        mapper.insertActivity(id, actor(), "create", "受理问题");
        return id;
    }

    @Transactional
    public void recordFollowup(String id, String note) {
        lockActive(id);
        mapper.insertActivity(id, actor(), "followup", note.trim());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        id = resolveReference(id);
        Map<String, Object> problem = requireProblem(id);
        problem.put("pendingFollowup", mapper.pending(id, LocalDateTime.now(BUSINESS_ZONE)));
        problem.put("fieldOrders", mapper.fieldOrders(id));
        problem.put("callbackResults", mapper.callbackResults(id));
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
        if (mapper.activeFieldOrders(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先登记现场单处理结果");
        }
        if (due == null || !due.isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择未来的回访时间");
        }
        mapper.completeFollowup(id);
        mapper.schedule(id, owner.trim(), step.trim(), due);
        mapper.changeState(id, "waiting");
        mapper.insertActivity(id, actor(), "schedule", "回访安排：" + due + "，" + owner.trim() + "，" + step.trim());
    }

    @Transactional
    public void finishCallback(String id, String outcome, String note, LocalDateTime contactedAt,
                               String source, String evidence) {
        lockActive(id);
        if (mapper.activeFieldOrders(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先登记现场单处理结果");
        }
        if (contactedAt == null || contactedAt.isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写实际联系时间");
        }
        String result = note.trim();
        String next;
        String label;
        switch (outcome) {
            case "continue":
                next = "open";
                label = "继续跟进";
                break;
            case "field":
                next = "field";
                label = "待现场派单";
                break;
            case "resolved":
                if (!("customer".equals(source) || "platform".equals(source) || "field".equals(source))
                    || evidence == null || evidence.trim().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认恢复需要核验来源与具体依据");
                }
                mapper.verify(id, actor(), source, evidence.trim());
                next = "resolved";
                label = "确认恢复并结案";
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知回访结果");
        }
        mapper.completeFollowup(id);
        mapper.insertCallback(id, actor(), contactedAt, outcome, result,
            "resolved".equals(outcome) ? source : null, "resolved".equals(outcome) ? evidence.trim() : null);
        mapper.changeState(id, next);
        mapper.insertActivity(id, actor(), "callback", label + "：" + result);
    }

    private String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Transactional
    public void linkFieldOrder(String id, String number) {
        if (!"field".equals(lockActive(id)) || mapper.activeFieldOrders(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅待现场派单的问题可关联一张现场单");
        }
        try {
            mapper.linkFieldOrder(id, number.trim());
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "现场单号已关联");
        }
        mapper.insertActivity(id, actor(), "field_link", "人工关联智享乐装现场单：" + number.trim());
    }

    @Transactional
    public void recordFieldResult(String id, String number, String state, String receipt) {
        if (!"field".equals(lockActive(id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前问题未处于现场服务阶段");
        }
        String label;
        String next;
        switch (state) {
            case "in_progress": label = "处理中"; next = "field"; break;
            case "complete": label = "完工"; next = "verify"; break;
            case "rejected": label = "拒单"; next = "triage"; break;
            case "cancelled": label = "取消"; next = "triage"; break;
            default: throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知现场单结果");
        }
        if (mapper.updateFieldOrder(id, number, state, receipt.trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "现场单不可更新");
        }
        mapper.changeState(id, next);
        mapper.insertActivity(id, actor(), "field_update", "人工登记智享乐装回执 " + number + " · " + label + "：" + receipt.trim());
    }

    private String lockActive(String id) {
        String state = mapper.lockState(id);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        if ("resolved".equals(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "问题单已结案");
        }
        return state;
    }

    private String resolveReference(String reference) {
        String value = reference.toUpperCase(java.util.Locale.ROOT);
        if (!value.matches("(AS-)?[0-9]{1,18}")) {
            return reference;
        }
        String id = mapper.findIdByNumber(Long.parseLong(value.replace("AS-", "")));
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        return id;
    }

    private Map<String, Object> requireProblem(String id) {
        Map<String, Object> problem = mapper.findProblem(id);
        if (problem == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        return problem;
    }
}
