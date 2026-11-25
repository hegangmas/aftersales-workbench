package com.csg.workbench.sop;

import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SopController {
    private final SopService sops;

    public SopController(SopService sops) {
        this.sops = sops;
    }

    @GetMapping("/api/knowledge/sops")
    public List<Map<String, Object>> knowledgeList() {
        return sops.listKnowledgeReadOnly();
    }

    @GetMapping("/api/admin/sops")
    public List<Map<String, Object>> adminList(Authentication authentication) {
        requireRuleEditor(authentication);
        return sops.listAdminEntries();
    }

    @PostMapping("/api/admin/sops")
    @ResponseStatus(HttpStatus.CREATED)
    public void save(Authentication authentication, @Valid @RequestBody SaveSopRequest req) {
        requireRuleEditor(authentication);
        sops.saveDraft(req.category, req.title, req.version, req.evidenceMode,
            req.customerSteps, req.onsiteSteps, req.requiredEvidence, req.gradeHint,
            req.gradeThresholds, req.autoApprovalRules, Boolean.TRUE.equals(req.publishNow),
            authentication.getName());
    }

    @PostMapping("/api/admin/sops/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(Authentication authentication, @PathVariable long id) {
        requireRuleEditor(authentication);
        sops.publish(id, authentication.getName());
    }

    private static void requireRuleEditor(Authentication auth) {
        boolean allowed = auth.getAuthorities().stream().anyMatch(a ->
            "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_QUALITY".equals(a.getAuthority()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员或质量人员可以维护 SOP 与审批规则");
        }
    }

    public static class SaveSopRequest {
        @NotBlank(message = "请填写故障分类") @Size(max = 80) public String category;
        @NotBlank(message = "请填写SOP标题") @Size(max = 120) public String title;
        @NotBlank(message = "请填写版本号") @Size(max = 20) public String version;
        @Size(max = 120) public String evidenceMode;
        public List<String> customerSteps;
        public List<String> onsiteSteps;
        public List<String> requiredEvidence;
        @Size(max = 200) public String gradeHint;
        public Map<String, Object> gradeThresholds;
        public List<Map<String, Object>> autoApprovalRules;
        public Boolean publishNow;
    }
}
