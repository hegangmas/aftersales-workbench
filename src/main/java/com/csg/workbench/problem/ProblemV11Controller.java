package com.csg.workbench.problem;

import java.time.LocalDateTime;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProblemV11Controller {
    private final ProblemV11Service v11;

    public ProblemV11Controller(ProblemV11Service v11) {
        this.v11 = v11;
    }

    @PostMapping("/api/v11/problems")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@RequestHeader("Idempotency-Key") String requestId,
                                      @Valid @RequestBody CreateV11Request req) {
        return v11.createV11(req.device, Boolean.TRUE.equals(req.devicePending), req.title, req.description,
            req.channel, req.category, req.urgency, req.faultOccurredAt, requestId);
    }

    @PostMapping("/api/v11/problems/{id}/bind-device")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bindDevice(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                           @Valid @RequestBody BindDeviceRequest req) {
        v11.bindDevice(id, req.device, req.expectedVersion.intValue(), requestId);
    }

    @PostMapping("/api/v11/problems/{id}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                       @Valid @RequestBody AssignRequest req) {
        v11.acceptOrAssign(id, req.target, req.expectedVersion.intValue(), requestId);
    }

    @PostMapping("/api/v11/problems/{id}/dispatch")
    public Map<String, String> dispatch(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                                        @Valid @RequestBody DispatchRequest req) {
        return v11.dispatchToLezhuang(id, Boolean.TRUE.equals(req.isClaim), Boolean.TRUE.equals(req.returnToFactory),
            req.projectCode, req.appointmentAt, req.photoNames, req.expectedVersion.intValue(), requestId);
    }

    @PostMapping("/api/v11/problems/{id}/lezhuang-callback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lezhuangCallback(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                                 @Valid @RequestBody LezhuangCallbackRequest req) {
        v11.handleLezhuangCallback(id, req.externalNo, req.state, req.receipt, req.replacedParts,
            req.oldPartNo, req.oldPartPhoto, req.customerAcceptResult, req.feeOwner,
            req.expectedVersion.intValue(), requestId);
    }

    @PostMapping("/api/v11/problems/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                       @Valid @RequestBody AcceptRequest req) {
        v11.acceptOrReject(id, Boolean.TRUE.equals(req.approved), req.note, req.oldPartNo,
            req.expectedVersion.intValue(), requestId);
    }

    @PostMapping("/api/v11/problems/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                       @Valid @RequestBody CancelRequest req) {
        v11.cancel(id, req.reason, req.expectedVersion.intValue(), requestId);
    }

    @PostMapping("/api/v11/problems/{id}/reopen")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> reopen(@PathVariable String id, @RequestHeader("Idempotency-Key") String requestId,
                                      @Valid @RequestBody ReopenRequest req) {
        return v11.reopen(id, req.reason, requestId);
    }

    @PostMapping("/api/v11/problems/force-distribute")
    public Map<String, Object> forceDistribute() {
        return v11.forceDistribute();
    }

    @GetMapping("/api/reports/monthly")
    public Map<String, Object> monthlyReport() {
        return v11.monthlyReport();
    }

    public static class CreateV11Request {
        public String device;
        public Boolean devicePending;
        @NotBlank(message = "请填写故障现象标题") public String title;
        @NotBlank(message = "请填写现象描述") public String description;
        public String channel;
        public String category;
        public String urgency;
        @NotNull(message = "请填写故障发生时点（精确到分钟）") public LocalDateTime faultOccurredAt;
    }

    public static class BindDeviceRequest extends ProblemController.Versioned {
        @NotBlank(message = "请填写桩SN") public String device;
    }

    public static class AssignRequest extends ProblemController.Versioned {
        public String target;
    }

    public static class DispatchRequest extends ProblemController.Versioned {
        @NotNull(message = "请选择是否索赔") public Boolean isClaim;
        @NotNull(message = "请选择桩是否返厂") public Boolean returnToFactory;
        @NotBlank(message = "请填写项目编号") public String projectCode;
        @NotNull(message = "请选择预约上门时间") public LocalDateTime appointmentAt;
        public String photoNames;
    }

    public static class LezhuangCallbackRequest extends ProblemController.Versioned {
        @NotBlank(message = "请提供乐装单号") public String externalNo;
        @NotBlank(message = "请提供回传状态") public String state;
        public String receipt;
        public String replacedParts;
        public String oldPartNo;
        public String oldPartPhoto;
        public String customerAcceptResult;
        public String feeOwner;
    }

    public static class AcceptRequest extends ProblemController.Versioned {
        @NotNull(message = "请明确验收结论") public Boolean approved;
        public String note;
        public String oldPartNo;
    }

    public static class CancelRequest extends ProblemController.Versioned {
        @NotBlank(message = "请填写取消原因") public String reason;
    }

    public static class ReopenRequest {
        @NotBlank(message = "请填写重开原因") public String reason;
    }
}
