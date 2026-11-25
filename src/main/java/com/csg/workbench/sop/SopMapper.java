package com.csg.workbench.sop;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SopMapper {
    @Select("SELECT id, category, title, version, status, evidence_mode AS evidenceMode, "
        + "customer_steps_json AS customerStepsJson, onsite_steps_json AS onsiteStepsJson, "
        + "required_evidence_json AS requiredEvidenceJson, grade_hint AS gradeHint, "
        + "updated_at AS updatedAt "
        + "FROM sop_entry WHERE status = 'published' ORDER BY id")
    List<Map<String, Object>> listPublishedForKnowledge();

    @Select("SELECT id, category, title, version, status, evidence_mode AS evidenceMode, "
        + "customer_steps_json AS customerStepsJson, onsite_steps_json AS onsiteStepsJson, "
        + "required_evidence_json AS requiredEvidenceJson, grade_hint AS gradeHint, "
        + "grade_thresholds_json AS gradeThresholdsJson, auto_approval_rules_json AS autoApprovalRulesJson, "
        + "updated_by AS updatedBy, updated_at AS updatedAt "
        + "FROM sop_entry ORDER BY category, id DESC")
    List<Map<String, Object>> listAllForAdmin();

    @Select("SELECT id, category, title, version, status, evidence_mode AS evidenceMode, "
        + "customer_steps_json AS customerStepsJson, onsite_steps_json AS onsiteStepsJson, "
        + "required_evidence_json AS requiredEvidenceJson, grade_hint AS gradeHint, "
        + "grade_thresholds_json AS gradeThresholdsJson, auto_approval_rules_json AS autoApprovalRulesJson "
        + "FROM sop_entry WHERE category = #{category} AND status = 'published' ORDER BY id DESC LIMIT 1")
    Map<String, Object> findPublishedByCategory(@Param("category") String category);

    @Select("SELECT id, category, version, status FROM sop_entry WHERE id = #{id}")
    Map<String, Object> findById(@Param("id") long id);

    @Insert("INSERT INTO sop_entry (category, title, version, status, evidence_mode, customer_steps_json, "
        + "onsite_steps_json, required_evidence_json, grade_hint, grade_thresholds_json, auto_approval_rules_json, updated_by) "
        + "VALUES (#{category}, #{title}, #{version}, #{status}, #{evidenceMode}, #{customerStepsJson}, "
        + "#{onsiteStepsJson}, #{requiredEvidenceJson}, #{gradeHint}, #{gradeThresholdsJson}, #{autoApprovalRulesJson}, #{updatedBy})")
    void insert(@Param("category") String category, @Param("title") String title, @Param("version") String version,
                @Param("status") String status, @Param("evidenceMode") String evidenceMode,
                @Param("customerStepsJson") String customerStepsJson, @Param("onsiteStepsJson") String onsiteStepsJson,
                @Param("requiredEvidenceJson") String requiredEvidenceJson, @Param("gradeHint") String gradeHint,
                @Param("gradeThresholdsJson") String gradeThresholdsJson,
                @Param("autoApprovalRulesJson") String autoApprovalRulesJson,
                @Param("updatedBy") String updatedBy);

    @Update("UPDATE sop_entry SET status = 'archived' WHERE category = #{category} AND status = 'published'")
    int archivePublished(@Param("category") String category);

    @Update("UPDATE sop_entry SET status = 'published', updated_by = #{actor} WHERE id = #{id}")
    int markPublished(@Param("id") long id, @Param("actor") String actor);
}
