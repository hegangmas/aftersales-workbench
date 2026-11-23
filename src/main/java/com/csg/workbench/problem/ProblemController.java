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
        problems.finishCallback(id, request.outcome, request.note);
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
        @NotBlank @Size(max = 64) public String externalNo;
    }

    public static class FieldResult extends FieldLink {
        @NotBlank @Size(max = 20) public String state;
        @NotBlank @Size(max = 700) public String receipt;
    }

    public static class Schedule {
        @NotBlank @Size(max = 20) public String owner;
        @NotBlank @Size(max = 500) public String nextStep;
        public LocalDateTime dueAt;
    }

    public static class CallbackResult {
        @NotBlank @Size(max = 20) public String outcome;
        @NotBlank @Size(max = 1000) public String note;
    }

    public static class CreateProblem {
        @NotBlank @Size(max = 40) public String device;
        @NotBlank @Size(max = 150) public String title;
        @NotBlank @Size(max = 1000) public String description;
    }

    public static class Followup {
        @NotBlank @Size(max = 1500) public String note;
    }
}
