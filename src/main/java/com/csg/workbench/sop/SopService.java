package com.csg.workbench.sop;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 知识库（只读）与管理→SOP与规则（唯一写端）严格分离：
 * 知识库只返回已发布条目，且绝不下发 A/B/C 阈值数字与换装自动审批条件。
 */
@Service
public class SopService {
    private final SopMapper mapper;
    private final ObjectMapper json;

    public SopService(SopMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public List<Map<String, Object>> listKnowledgeReadOnly() {
        List<Map<String, Object>> rows = mapper.listPublishedForKnowledge();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            result.add(toKnowledgeView(row));
        }
        return result;
    }

    public Map<String, Object> findKnowledgeByCategory(String category) {
        Map<String, Object> row = mapper.findPublishedByCategory(category == null ? "无法充电" : category);
        if (row == null) {
            row = mapper.findPublishedByCategory("无法充电");
        }
        return row == null ? Collections.<String, Object>emptyMap() : toKnowledgeView(row);
    }

    public List<Map<String, Object>> listAdminEntries() {
        List<Map<String, Object>> rows = mapper.listAllForAdmin();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = toKnowledgeView(row);
            item.put("status", row.get("status"));
            item.put("gradeThresholds", parseMap(row.get("gradeThresholdsJson")));
            item.put("autoApprovalRules", parseList(row.get("autoApprovalRulesJson")));
            item.put("updatedBy", row.get("updatedBy"));
            result.add(item);
        }
        return result;
    }

    @Transactional
    public void saveDraft(String category, String title, String version, String evidenceMode,
                          List<String> customerSteps, List<String> onsiteSteps, List<String> requiredEvidence,
                          String gradeHint, Map<String, Object> gradeThresholds,
                          List<Map<String, Object>> autoApprovalRules, boolean publishNow, String actor) {
        if (customerSteps == null || customerSteps.isEmpty() || customerSteps.size() > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户可执行步骤需控制在1到3步以内");
        }
        if (onsiteSteps == null || onsiteSteps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少填写一条上门验证步骤");
        }
        String cleanCategory = category.trim();
        if (publishNow) {
            mapper.archivePublished(cleanCategory);
        }
        mapper.insert(
            cleanCategory,
            title.trim(),
            version.trim(),
            publishNow ? "published" : "draft",
            evidenceMode == null ? "" : evidenceMode.trim(),
            toJson(customerSteps),
            toJson(onsiteSteps),
            toJson(requiredEvidence == null ? Collections.emptyList() : requiredEvidence),
            gradeHint == null ? "" : gradeHint.trim(),
            toJson(gradeThresholds == null ? Collections.emptyMap() : gradeThresholds),
            toJson(autoApprovalRules == null ? Collections.emptyList() : autoApprovalRules),
            actor
        );
    }

    @Transactional
    public void publish(long id, String actor) {
        Map<String, Object> target = mapper.findById(id);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SOP条目不存在");
        }
        mapper.archivePublished(String.valueOf(target.get("category")));
        mapper.markPublished(id, actor);
    }

    private Map<String, Object> toKnowledgeView(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", row.get("id"));
        item.put("category", row.get("category"));
        item.put("title", row.get("title"));
        item.put("version", row.get("version"));
        item.put("evidenceMode", row.get("evidenceMode"));
        item.put("customerSteps", parseList(row.get("customerStepsJson")));
        item.put("onsiteSteps", parseList(row.get("onsiteStepsJson")));
        item.put("requiredEvidence", parseList(row.get("requiredEvidenceJson")));
        item.put("gradeHint", row.get("gradeHint"));
        item.put("updatedAt", row.get("updatedAt"));
        return item;
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("json");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseList(Object raw) {
        if (raw == null) return Collections.emptyList();
        try {
            return json.readValue(String.valueOf(raw), List.class);
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(Object raw) {
        if (raw == null) return Collections.emptyMap();
        try {
            return json.readValue(String.valueOf(raw), Map.class);
        } catch (Exception ignore) {
            return Collections.emptyMap();
        }
    }
}
