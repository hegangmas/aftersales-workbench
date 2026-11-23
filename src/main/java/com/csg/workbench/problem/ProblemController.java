package com.csg.workbench.problem;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {
    private final ProblemService problems;

    public ProblemController(ProblemService problems) {
        this.problems = problems;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@Valid @RequestBody CreateProblem request) {
        return Collections.singletonMap("id", problems.create(request.device, request.title, request.description));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return problems.detail(id);
    }

    @PostMapping("/{id}/followups")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void followup(@PathVariable String id, @Valid @RequestBody Followup request) {
        problems.recordFollowup(id, request.note);
    }

    @GetMapping
    public List<Map<String, Object>> search(@RequestParam String device) {
        return problems.findByDevice(device);
    }

    @GetMapping("/due")
    public List<Map<String, Object>> due() {
        return problems.due();
    }

    @PostMapping("/{id}/callback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void schedule(@PathVariable String id, @Valid @RequestBody Schedule request) {
        problems.schedule(id, request.owner, request.nextStep, request.dueAt);
    }

    @PostMapping("/{id}/callback/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(@PathVariable String id, @Valid @RequestBody CallbackResult request) {
        problems.finishCallback(id, request.outcome, request.note, request.contactedAt,
            request.evidenceSource, request.evidence);
    }

    @PostMapping("/{id}/field-orders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkFieldOrder(@PathVariable String id, @Valid @RequestBody FieldLink request) {
        problems.linkFieldOrder(id, request.externalNo);
    }

    @PostMapping("/{id}/field-orders/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordFieldResult(@PathVariable String id, @Valid @RequestBody FieldResult request) {
        problems.recordFieldResult(id, request.externalNo.trim(), request.state, request.receipt);
    }

    public static class FieldLink {
        @NotBlank(message = "请填写现场单号") @Size(max = 64, message = "现场单号不能超过64字") public String externalNo;
    }

    public static class FieldResult extends FieldLink {
        @NotBlank(message = "请填写现场单状态") @Size(max = 20, message = "现场单状态无效") public String state;
        @NotBlank(message = "请填写现场回执") @Size(max = 700, message = "现场回执不能超过700字") public String receipt;
    }

    public static class Schedule {
        @NotBlank(message = "请填写回访负责人") @Size(max = 20, message = "回访负责人不能超过20字") public String owner;
        @NotBlank(message = "请填写待确认事项") @Size(max = 500, message = "待确认事项不能超过500字") public String nextStep;
        public LocalDateTime dueAt;
    }

    public static class CallbackResult {
        public LocalDateTime contactedAt;
        @Size(max = 20, message = "恢复核验来源无效") public String evidenceSource;
        @Size(max = 1000, message = "恢复依据不能超过1000字") public String evidence;
        @NotBlank(message = "请填写回访结果") @Size(max = 20, message = "回访结果无效") public String outcome;
        @NotBlank(message = "请填写回访说明") @Size(max = 1000, message = "回访说明不能超过1000字") public String note;
    }

    public static class CreateProblem {
        @NotBlank(message = "请填写设备编号") @Size(max = 40, message = "设备编号不能超过40字") public String device;
        @NotBlank(message = "请填写问题标题") @Size(max = 150, message = "问题标题不能超过150字") public String title;
        @NotBlank(message = "请填写现象描述") @Size(max = 1000, message = "现象描述不能超过1000字") public String description;
    }

    public static class Followup {
        @NotBlank(message = "请填写跟进记录") @Size(max = 1500, message = "跟进记录不能超过1500字") public String note;
    }
}
