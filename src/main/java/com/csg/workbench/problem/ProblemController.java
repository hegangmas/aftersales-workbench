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
    private final ProblemV11Service v11;

    public ProblemController(ProblemService problems, ProblemV11Service v11) {
        this.problems = problems;
        this.v11 = v11;
    }

    /**
     * @deprecated 旧版问题单创建接口（仅保留向后兼容，严禁新增字段）。
     *     新需求请统一使用 V1.1 接口 {@code POST /api/v11/problems}
     *     （{@link ProblemV11Controller#create}）。
     */
    @Deprecated
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@RequestHeader("Idempotency-Key") String requestId,
                                      @Valid @RequestBody CreateProblem request) {
        return Collections.singletonMap("id", problems.create(
            request.device, request.title, request.description,
            request.channel, request.category, request.faultOccurredAt, requestId));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return problems.detail(id);
    }

    @PostMapping("/{id}/followups")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void followup(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                         @Valid @RequestBody Followup request) {
        v11.addFollowupV11(id, request.note, request.expectedVersion, requestId);
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

    /**
     * @deprecated 旧版预约回访接口（会切入旧态 {@code waiting}，仅保留向后兼容，严禁新增字段）。
     *     V1.1 已收口至 {@code /api/v11/problems/*} 状态机流转（暂停等待请使用挂起能力）。
     */
    @Deprecated
    @PostMapping("/{id}/callback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void schedule(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                         @Valid @RequestBody Schedule request) {
        problems.schedule(id, request.owner, request.nextStep, request.dueAt, request.expectedVersion, requestId);
    }

    /**
     * @deprecated 旧版回访结果与结案接口（仅保留向后兼容，严禁新增字段）。
     *     V1.1 验收闭环/驳回请使用 {@code POST /api/v11/problems/{id}/accept}
     *     （{@link ProblemV11Controller#accept}），转派现场请使用 {@code POST /api/v11/problems/{id}/dispatch}
     *     （{@link ProblemV11Controller#dispatch}）。
     */
    @Deprecated
    @PostMapping("/{id}/callback/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                       @Valid @RequestBody CallbackResult request) {
        problems.finishCallback(id, request.outcome, request.note, request.contactedAt,
            request.evidenceSource, request.evidence, request.expectedVersion, requestId);
    }

    /**
     * @deprecated 旧版无主工单认领接口（仅保留向后兼容，严禁新增字段）。
     *     V1.1 认领与指派请统一使用 {@code POST /api/v11/problems/{id}/assign}
     *     （{@link ProblemV11Controller#assign}）。
     */
    @Deprecated
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
        v11.handoffV11(id, request.target, request.reason, request.expectedVersion, requestId);
    }

    /**
     * @deprecated 旧版待现场派单撤回接口（仅保留向后兼容，严禁新增字段）。
     *     V1.1 请使用 {@code POST /api/v11/problems/{id}/accept}（驳回退回处理中）或
     *     {@code POST /api/v11/problems/{id}/cancel}（{@link ProblemV11Controller#cancel}）。
     */
    @Deprecated
    @PostMapping("/{id}/field-withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawField(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                              @Valid @RequestBody FieldWithdrawal request) {
        problems.withdrawField(id, request.reason, request.expectedVersion, requestId);
    }

    /**
     * @deprecated 旧版现场单人工关联接口（仅保留向后兼容，严禁新增字段）。
     *     V1.1 派单请统一使用 {@code POST /api/v11/problems/{id}/dispatch}
     *     （{@link ProblemV11Controller#dispatch}）。
     */
    @Deprecated
    @PostMapping("/{id}/field-orders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkFieldOrder(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                               @Valid @RequestBody FieldLink request) {
        problems.linkFieldOrder(id, request.externalNo, request.expectedVersion, requestId);
    }

    /**
     * @deprecated 旧版现场单回执登记接口（仅保留向后兼容，严禁新增字段）。
     *     V1.1 结构化回传请统一使用 {@code POST /api/v11/problems/{id}/lezhuang-callback}
     *     （{@link ProblemV11Controller#lezhuangCallback}）。
     */
    @Deprecated
    @PostMapping("/{id}/field-orders/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordFieldResult(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                                  @Valid @RequestBody FieldResult request) {
        problems.recordFieldResult(id, request.externalNo.trim(), request.state, request.receipt,
            request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                        @Valid @RequestBody SuspendRequest request) {
        v11.suspendV11(id, request.reason, Boolean.TRUE.equals(request.returnToPool),
            request.nextStep, request.dueAt, request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/quick-close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quickClose(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                           @Valid @RequestBody QuickCloseRequest request) {
        v11.quickCloseV11(id, request.note, request.evidenceSource, request.evidence,
            request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/escalate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void escalate(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                         @Valid @RequestBody EscalateRequest request) {
        v11.escalateV11(id, request.level, request.reason, Boolean.TRUE.equals(request.compromise),
            request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/evidences")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addEvidence(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                            @Valid @RequestBody EvidenceRequest request) {
        v11.addEvidenceV11(id, request.evidenceType, request.source, request.fileName,
            request.note, request.expectedVersion, requestId);
    }

    @PostMapping("/{id}/assessment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assess(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                       @Valid @RequestBody AssessmentRequest request) {
        v11.assessV11(id, request.grade, request.disposalType, request.note,
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
        @Size(max = 30, message = "渠道无效") public String channel;
        @Size(max = 80, message = "故障分类不能超过80字") public String category;
        public LocalDateTime faultOccurredAt;
    }

    public static class SuspendRequest extends Versioned {
        @NotBlank(message = "请选择挂起原因") @Size(max = 40, message = "挂起原因无效") public String reason;
        public Boolean returnToPool;
        @Size(max = 500, message = "待确认事项不能超过500字") public String nextStep;
        public LocalDateTime dueAt;
    }

    public static class QuickCloseRequest extends Versioned {
        @NotBlank(message = "请填写完结说明") @Size(max = 1000, message = "完结说明不能超过1000字") public String note;
        @Size(max = 20, message = "核验来源无效") public String evidenceSource;
        @NotBlank(message = "请填写完结依据") @Size(max = 1000, message = "完结依据不能超过1000字") public String evidence;
    }

    public static class EscalateRequest extends Versioned {
        @NotBlank(message = "请选择升级级别") @Size(max = 20, message = "升级级别无效") public String level;
        @NotBlank(message = "请填写升级或妥协原因") @Size(max = 500, message = "原因不能超过500字") public String reason;
        public Boolean compromise;
    }

    public static class EvidenceRequest extends Versioned {
        @NotBlank(message = "请选择证据类型") @Size(max = 40, message = "证据类型无效") public String evidenceType;
        @Size(max = 30, message = "证据来源无效") public String source;
        @Size(max = 200, message = "文件名不能超过200字") public String fileName;
        @NotBlank(message = "请填写证据说明") @Size(max = 1000, message = "证据说明不能超过1000字") public String note;
    }

    public static class AssessmentRequest extends Versioned {
        @NotBlank(message = "请选择判定等级") @Size(max = 10, message = "判定等级无效") public String grade;
        @NotBlank(message = "请选择处置方式") @Size(max = 20, message = "处置方式无效") public String disposalType;
        @Size(max = 1000, message = "判定说明不能超过1000字") public String note;
    }

    public static class Followup extends Versioned {
        @NotBlank(message = "请填写跟进记录") @Size(max = 1500, message = "跟进记录不能超过1500字") public String note;
    }
}
