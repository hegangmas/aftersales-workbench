package com.csg.workbench.problem;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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
    public Map<String, String> create(@RequestHeader("Idempotency-Key") String requestId,
                                      @Valid @RequestBody CreateProblem request) {
        return Collections.singletonMap("id", problems.create(request.device, request.title, request.description, requestId));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return problems.detail(id);
    }

    @PostMapping("/{id}/followups")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void followup(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                         @Valid @RequestBody Followup request) {
        problems.recordFollowup(id, request.note, request.expectedVersion, requestId);
    }

    @GetMapping
    public List<Map<String, Object>> search(@RequestParam String device) {
        return problems.findByDevice(device);
    }

    @GetMapping("/due")
    public Map<String, Object> due(@RequestParam(defaultValue = "team") String scope,
                                   @RequestParam(required = false) String cursor,
                                   @RequestParam(defaultValue = "20") int limit) {
        return problems.due(scope, cursor, limit);
    }

    @GetMapping("/mine")
    public Map<String, Object> mine(@RequestParam(required = false) String cursor,
                                    @RequestParam(defaultValue = "20") int limit) {
        return problems.mine(cursor, limit);
    }

    @GetMapping("/board")
    public Map<String, Object> board(@RequestParam(required = false) String state,
                                     @RequestParam(required = false) String owner,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) String kind,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int limit,
                                     @RequestParam(required = false) String bucket) {
        return problems.board(state, owner, category, kind, q, cursor, limit, bucket);
    }

    @PostMapping("/{id}/callback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void schedule(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                         @Valid @RequestBody Schedule request) {
        problems.schedule(id, request.owner, request.nextStep, request.dueAt, request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/callback/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                       @Valid @RequestBody CallbackResult request) {
        problems.finishCallback(id, request.outcome, request.note, request.contactedAt,
            request.evidenceSource, request.evidence, request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void claim(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                      @Valid @RequestBody Versioned request) {
        problems.claim(id, request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/handoff")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handoff(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                        @Valid @RequestBody Handoff request) {
        problems.handoff(id, request.target, request.reason, request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/field-withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawField(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                              @Valid @RequestBody FieldWithdrawal request) {
        problems.withdrawField(id, request.reason, request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/field-orders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkFieldOrder(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                               @Valid @RequestBody FieldLink request) {
        problems.linkFieldOrder(id, request.externalNo, request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/field-orders/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordFieldResult(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                                  @Valid @RequestBody FieldResult request) {
        problems.recordFieldResult(id, request.externalNo.trim(), request.state, request.receipt,
            request.expectedVersion, requestId);
    }

    public static class Versioned {
        @NotNull(message = "请刷新后重试") public Integer expectedVersion;
    }

    public static class FieldLink extends Versioned {
        @NotBlank(message = "请填写现场单号") @Size(max = 64, message = "现场单号不能超过64字") public String externalNo;
    }

    public static class FieldResult extends FieldLink {
        @NotBlank(message = "请填写现场单状态") @Size(max = 20, message = "现场单状态无效") public String state;
        @NotBlank(message = "请填写现场回执") @Size(max = 700, message = "现场回执不能超过700字") public String receipt;
    }

    public static class FieldWithdrawal extends Versioned {
        @NotBlank(message = "请填写撤回原因") @Size(max = 500, message = "撤回原因不能超过500字") public String reason;
    }

    public static class Handoff extends Versioned {
        @NotBlank(message = "请选择接收人") @Size(max = 20, message = "接收人无效") public String target;
        @NotBlank(message = "请填写转交原因") @Size(max = 500, message = "转交原因不能超过500字") public String reason;
    }

    public static class Schedule extends Versioned {
        @NotBlank(message = "请填写回访负责人") @Size(max = 20, message = "回访负责人不能超过20字") public String owner;
        @NotBlank(message = "请填写待确认事项") @Size(max = 500, message = "待确认事项不能超过500字") public String nextStep;
        public LocalDateTime dueAt;
    }

    public static class CallbackResult extends Versioned {
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

    public static class Followup extends Versioned {
        @NotBlank(message = "请填写跟进记录") @Size(max = 1500, message = "跟进记录不能超过1500字") public String note;
    }
}
