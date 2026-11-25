package com.csg.workbench.problem;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface ProblemMapper {
    @Insert("INSERT INTO problem (id, scenario, title, category, channel, description, kind, device, fault_occurred_at, sla_due_at) "
        + "VALUES (#{id}, 'JAVA-TRIAL', #{title}, #{category}, #{channel}, #{description}, 'fault', #{device}, "
        + "#{faultOccurredAt,jdbcType=TIMESTAMP}, #{slaDueAt,jdbcType=TIMESTAMP})")
    void insertProblem(@Param("id") String id, @Param("title") String title,
                       @Param("category") String category, @Param("channel") String channel,
                       @Param("description") String description, @Param("device") String device,
                       @Param("faultOccurredAt") LocalDateTime faultOccurredAt,
                       @Param("slaDueAt") LocalDateTime slaDueAt);

    @Insert("INSERT INTO activity (problem_id, actor, action, note) "
        + "VALUES (#{id}, #{actor}, #{action}, #{note})")
    void insertActivity(@Param("id") String id, @Param("actor") String actor, @Param("action") String action, @Param("note") String note);

    @Select("SELECT id, display_number AS displayNumber, ticket_no AS ticketNo, title, device, state, description, resume_state AS resumeState, "
        + "category, kind, channel, owner, version, fault_occurred_at AS faultOccurredAt, "
        + "suspend_reason AS suspendReason, grade, disposal_type AS disposalType, "
        + "compromise_flag AS compromiseFlag, escalation_level AS escalationLevel, "
        + "escalation_reason AS escalationReason, sla_due_at AS slaDueAt, "
        + "auto_approval_note AS autoApprovalNote, customer_id AS customerId, device_pending AS devicePending, "
        + "urgency, symptom_tags AS symptomTags, accepted_by AS acceptedBy, warranty_confirmed AS warrantyConfirmed, "
        + "snapshot_json AS snapshotJson, old_part_registered AS oldPartRegistered, old_part_no AS oldPartNo, "
        + "reopened_from_id AS reopenedFromId, created_at AS createdAt, updated_at AS updatedAt FROM problem WHERE id = #{id}")
    Map<String, Object> findProblem(@Param("id") String id);

    @Select("SELECT id FROM problem WHERE ticket_no = #{ticketNo}")
    String findIdByTicketNo(@Param("ticketNo") String ticketNo);

    @Select("SELECT id FROM problem WHERE display_number = #{number}")
    String findIdByNumber(@Param("number") long number);

    @Select("SELECT actor, action, note, created_at AS createdAt FROM activity WHERE problem_id = #{id} ORDER BY id")
    List<Map<String, Object>> listActivities(@Param("id") String id);

    @Select("SELECT id, display_number AS displayNumber, title, device, state, owner, "
        + "(SELECT COUNT(*) FROM field_order fo WHERE fo.problem_id = problem.id "
        + "AND fo.state IN ('linked', 'in_progress')) AS activeFieldOrders "
        + "FROM problem WHERE device = #{device} ORDER BY updated_at DESC LIMIT 30")
    List<Map<String, Object>> findByDevice(@Param("device") String device);

    @Select("SELECT state, version, owner FROM problem WHERE id = #{id} FOR UPDATE")
    Map<String, Object> lockProblem(@Param("id") String id);

    @Select("SELECT id, owner, next_step AS nextStep, due_at AS dueAt, due_at <= #{now} AS overdue "
        + "FROM followup WHERE problem_id = #{id} AND state = 'open' ORDER BY id DESC LIMIT 1")
    Map<String, Object> pending(@Param("id") String id, @Param("now") LocalDateTime now);

    @Select("SELECT f.id AS followupId, f.problem_id AS problemId, p.display_number AS displayNumber, p.title, p.state, "
        + "f.owner, f.next_step AS nextStep, f.due_at AS dueAt "
        + "FROM followup f JOIN problem p ON p.id = f.problem_id "
        + "WHERE f.state = 'open' AND f.due_at <= #{now} "
        + "AND (#{mine} = 0 OR f.owner = #{actor} OR p.owner = #{actor}) "
        + "AND (#{cursorId,jdbcType=BIGINT} IS NULL OR f.due_at > #{cursorDue,jdbcType=TIMESTAMP} "
        + "OR (f.due_at = #{cursorDue,jdbcType=TIMESTAMP} AND f.id > #{cursorId,jdbcType=BIGINT})) "
        + "ORDER BY f.due_at, f.id LIMIT #{limit}")
    List<Map<String, Object>> duePage(@Param("now") LocalDateTime now, @Param("mine") int mine,
                                      @Param("actor") String actor, @Param("cursorDue") LocalDateTime cursorDue,
                                      @Param("cursorId") Long cursorId, @Param("limit") int limit);

    @SelectProvider(type = BoardQueries.class, method = "page")
    List<Map<String, Object>> boardPage(@Param("state") String state, @Param("ownerMode") String ownerMode,
                                        @Param("owner") String owner, @Param("category") String category,
                                        @Param("kind") String kind, @Param("bucket") String bucket,
                                        @Param("like") String like, @Param("number") Long number,
                                        @Param("cursorAt") LocalDateTime cursorAt, @Param("cursorId") String cursorId,
                                        @Param("limit") int limit);

    @SelectProvider(type = BoardQueries.class, method = "count")
    int boardCount(@Param("state") String state, @Param("ownerMode") String ownerMode,
                   @Param("owner") String owner, @Param("category") String category,
                   @Param("kind") String kind, @Param("bucket") String bucket,
                   @Param("like") String like, @Param("number") Long number);

    @Select("SELECT COUNT(*) AS total, "
        + "COALESCE(SUM(p.state <> 'resolved' AND (p.owner = 'early' OR p.owner = '')), 0) AS unclaimed, "
        + "COALESCE(SUM(p.state = 'open' AND p.owner <> 'early' AND p.owner <> ''), 0) AS openCount, "
        + "COALESCE(SUM(p.state = 'triage' AND p.owner <> 'early' AND p.owner <> ''), 0) AS triageCount, "
        + "COALESCE(SUM(p.state = 'waiting' AND p.owner <> 'early' AND p.owner <> ''), 0) AS waitingCount, "
        + "COALESCE(SUM(p.state = 'field' AND p.owner <> 'early' AND p.owner <> '' AND NOT EXISTS "
        + "(SELECT 1 FROM field_order fo WHERE fo.problem_id = p.id AND fo.state IN ('linked', 'in_progress'))), 0) AS fieldPending, "
        + "COALESCE(SUM(p.state = 'field' AND p.owner <> 'early' AND p.owner <> '' AND EXISTS "
        + "(SELECT 1 FROM field_order fo WHERE fo.problem_id = p.id AND fo.state IN ('linked', 'in_progress'))), 0) AS fieldActive, "
        + "COALESCE(SUM(p.state = 'verify' AND p.owner <> 'early' AND p.owner <> ''), 0) AS verifyCount, "
        + "COALESCE(SUM(p.state = 'resolved'), 0) AS resolvedCount, "
        + "COALESCE(SUM(p.state = 'answered' AND p.owner <> 'early' AND p.owner <> ''), 0) AS answeredCount, "
        + "COALESCE(SUM(p.state = 'referred' AND p.owner <> 'early' AND p.owner <> ''), 0) AS referredCount "
        + "FROM problem p")
    Map<String, Object> boardSummary();

    @Select("SELECT COUNT(*) FROM followup f JOIN problem p ON p.id = f.problem_id "
        + "WHERE f.state = 'open' AND f.due_at <= #{now} "
        + "AND (#{mine} = 0 OR f.owner = #{actor} OR p.owner = #{actor})")
    int dueCount(@Param("now") LocalDateTime now, @Param("mine") int mine, @Param("actor") String actor);

    @Select("SELECT COUNT(*) FROM problem WHERE owner = #{actor} AND state <> 'resolved' AND owner <> 'early'")
    int mineCount(@Param("actor") String actor);

    @Select("SELECT DISTINCT category FROM problem ORDER BY category")
    List<String> categories();

    @Select("SELECT id, display_number AS displayNumber, title, device, state, owner, updated_at AS updatedAt "
        + "FROM problem WHERE owner = #{actor} AND state <> 'resolved' AND owner <> 'early' "
        + "AND (#{cursorId,jdbcType=VARCHAR} IS NULL OR updated_at < #{cursorUpdated,jdbcType=TIMESTAMP} "
        + "OR (updated_at = #{cursorUpdated,jdbcType=TIMESTAMP} AND id < #{cursorId,jdbcType=VARCHAR})) "
        + "ORDER BY updated_at DESC, id DESC LIMIT #{limit}")
    List<Map<String, Object>> minePage(@Param("actor") String actor, @Param("cursorUpdated") LocalDateTime cursorUpdated,
                                       @Param("cursorId") String cursorId, @Param("limit") int limit);

    @Insert("INSERT INTO followup (problem_id, owner, next_step, due_at) VALUES (#{id}, #{owner}, #{step}, #{due})")
    void schedule(@Param("id") String id, @Param("owner") String owner,
                  @Param("step") String step, @Param("due") LocalDateTime due);

    @Update("UPDATE followup SET state = 'done' WHERE problem_id = #{id} AND state = 'open'")
    int completeFollowup(@Param("id") String id);

    @Update("UPDATE problem SET state = #{state}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int changeState(@Param("id") String id, @Param("state") String state, @Param("version") int version);

    @Update("UPDATE problem SET owner = #{owner}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int assignOwner(@Param("id") String id, @Param("owner") String owner, @Param("version") int version);

    @Insert("INSERT IGNORE INTO command_log (request_id, digest, problem_id) VALUES (#{requestId}, #{digest}, #{problemId})")
    int insertCommandIfAbsent(@Param("requestId") String requestId, @Param("digest") String digest,
                              @Param("problemId") String problemId);

    @Select("SELECT digest FROM command_log WHERE request_id = #{requestId}")
    String commandDigest(@Param("requestId") String requestId);

    @Select("SELECT problem_id FROM command_log WHERE request_id = #{requestId}")
    String commandProblem(@Param("requestId") String requestId);

    @Insert("INSERT INTO ownership_event (problem_id, actor, from_owner, to_owner, action, reason) "
        + "VALUES (#{id}, #{actor}, #{fromOwner}, #{toOwner}, #{action}, #{reason})")
    void insertOwnership(@Param("id") String id, @Param("actor") String actor, @Param("fromOwner") String fromOwner,
                         @Param("toOwner") String toOwner, @Param("action") String action, @Param("reason") String reason);

    @Select("SELECT actor, from_owner AS fromOwner, to_owner AS toOwner, action, reason, created_at AS createdAt "
        + "FROM ownership_event WHERE problem_id = #{id} ORDER BY id")
    List<Map<String, Object>> ownershipEvents(@Param("id") String id);

    @Select("SELECT resume_state FROM problem WHERE id = #{id}")
    String resumeState(@Param("id") String id);

    @Update("UPDATE problem SET resume_state = #{resume,jdbcType=VARCHAR} WHERE id = #{id}")
    void setResume(@Param("id") String id, @Param("resume") String resume);

    @Insert("INSERT INTO verification (problem_id, actor, source, evidence) VALUES (#{id}, #{actor}, #{source}, #{note})")
    void verify(@Param("id") String id, @Param("actor") String actor,
                @Param("source") String source, @Param("note") String note);

    @Insert("INSERT INTO callback_result (problem_id, actor, contacted_at, outcome, note, evidence_source, evidence) "
        + "VALUES (#{id}, #{actor}, #{when}, #{outcome}, #{note}, #{source}, #{evidence})")
    void insertCallback(@Param("id") String id, @Param("actor") String actor,
                        @Param("when") LocalDateTime when, @Param("outcome") String outcome,
                        @Param("note") String note, @Param("source") String source,
                        @Param("evidence") String evidence);

    @Select("SELECT actor, contacted_at AS contactedAt, outcome, note, evidence_source AS evidenceSource, evidence "
        + "FROM callback_result WHERE problem_id = #{id} ORDER BY activity_id DESC")
    List<Map<String, Object>> callbackResults(@Param("id") String id);

    @Select("SELECT external_no AS externalNo, state, receipt FROM field_order WHERE problem_id = #{id} ORDER BY id DESC")
    List<Map<String, Object>> fieldOrders(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM field_order WHERE problem_id = #{id} AND state IN ('linked', 'in_progress')")
    int activeFieldOrders(@Param("id") String id);

    @Insert("INSERT INTO field_order (problem_id, external_no, state, receipt) VALUES (#{id}, #{number}, 'linked', '')")
    void linkFieldOrder(@Param("id") String id, @Param("number") String number);

    @Update("UPDATE field_order SET state = #{state}, receipt = #{receipt} WHERE problem_id = #{id} "
        + "AND external_no = #{number} AND state IN ('linked', 'in_progress') AND state <> #{state}")
    int updateFieldOrder(@Param("id") String id, @Param("number") String number,
                         @Param("state") String state, @Param("receipt") String receipt);

    @Update("UPDATE problem SET state = 'waiting', suspend_reason = #{reason}, owner = #{owner}, "
        + "version = version + 1 WHERE id = #{id} AND version = #{version}")
    int suspendProblem(@Param("id") String id, @Param("reason") String reason,
                       @Param("owner") String owner, @Param("version") int version);

    @Update("UPDATE problem SET escalation_level = #{level}, escalation_reason = #{reason,jdbcType=VARCHAR}, "
        + "compromise_flag = #{compromise}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int updateEscalation(@Param("id") String id, @Param("level") String level,
                         @Param("reason") String reason, @Param("compromise") int compromise,
                         @Param("version") int version);

    @Update("UPDATE problem SET grade = #{grade}, disposal_type = #{disposalType}, "
        + "auto_approval_note = #{autoApprovalNote,jdbcType=VARCHAR}, version = version + 1 "
        + "WHERE id = #{id} AND version = #{version}")
    int updateAssessment(@Param("id") String id, @Param("grade") String grade,
                         @Param("disposalType") String disposalType,
                         @Param("autoApprovalNote") String autoApprovalNote,
                         @Param("version") int version);

    @Select("SELECT disposal_type FROM problem WHERE id = #{id}")
    String disposalType(@Param("id") String id);

    @Insert("INSERT INTO evidence_item (problem_id, evidence_type, source, file_name, note, actor) "
        + "VALUES (#{id}, #{evidenceType}, #{source}, #{fileName}, #{note}, #{actor})")
    void insertEvidence(@Param("id") String id, @Param("evidenceType") String evidenceType,
                        @Param("source") String source, @Param("fileName") String fileName,
                        @Param("note") String note, @Param("actor") String actor);

    @Select("SELECT id, evidence_type AS evidenceType, source, file_name AS fileName, note, actor, "
        + "created_at AS createdAt FROM evidence_item WHERE problem_id = #{id} ORDER BY id DESC")
    List<Map<String, Object>> listEvidences(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM evidence_item WHERE problem_id = #{id}")
    int countEvidences(@Param("id") String id);

    @Insert("INSERT IGNORE INTO ticket_sequence (biz_date, seq) VALUES (#{bizDate}, 0)")
    void initSequenceDate(@Param("bizDate") String bizDate);

    @Update("UPDATE ticket_sequence SET seq = seq + 1 WHERE biz_date = #{bizDate}")
    int incrementSequence(@Param("bizDate") String bizDate);

    @Select("SELECT seq FROM ticket_sequence WHERE biz_date = #{bizDate}")
    int currentSequence(@Param("bizDate") String bizDate);

    @Insert("INSERT INTO problem (id, ticket_no, scenario, title, category, channel, description, kind, state, owner, "
        + "device, customer_id, device_pending, urgency, accepted_by, fault_occurred_at, sla_due_at, snapshot_json, reopened_from_id) "
        + "VALUES (#{id}, #{ticketNo}, 'V11-STRICT', #{title}, #{category}, #{channel}, #{description}, 'fault', "
        + "#{state}, #{owner}, #{device,jdbcType=VARCHAR}, #{customerId,jdbcType=VARCHAR}, #{devicePending}, "
        + "#{urgency}, #{acceptedBy}, #{faultOccurredAt,jdbcType=TIMESTAMP}, #{slaDueAt,jdbcType=TIMESTAMP}, "
        + "#{snapshotJson,jdbcType=VARCHAR}, #{reopenedFromId,jdbcType=VARCHAR})")
    void insertProblemV11(@Param("id") String id, @Param("ticketNo") String ticketNo, @Param("title") String title,
                          @Param("category") String category, @Param("channel") String channel,
                          @Param("description") String description, @Param("state") String state,
                          @Param("owner") String owner, @Param("device") String device,
                          @Param("customerId") String customerId, @Param("devicePending") int devicePending,
                          @Param("urgency") String urgency, @Param("acceptedBy") String acceptedBy,
                          @Param("faultOccurredAt") LocalDateTime faultOccurredAt,
                          @Param("slaDueAt") LocalDateTime slaDueAt, @Param("snapshotJson") String snapshotJson,
                          @Param("reopenedFromId") String reopenedFromId);

    @Update("UPDATE problem SET device = #{device}, customer_id = #{customerId,jdbcType=VARCHAR}, device_pending = 0, "
        + "snapshot_json = #{snapshotJson,jdbcType=VARCHAR}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int bindDevice(@Param("id") String id, @Param("device") String device, @Param("customerId") String customerId,
                   @Param("snapshotJson") String snapshotJson, @Param("version") int version);

    @Update("UPDATE problem SET state = #{state}, owner = #{owner}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int assignOwnerAndState(@Param("id") String id, @Param("owner") String owner, @Param("state") String state,
                            @Param("version") int version);

    @Update("UPDATE problem SET state = #{state}, old_part_registered = #{oldPartRegistered}, "
        + "old_part_no = #{oldPartNo,jdbcType=VARCHAR}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int closeWithOldPart(@Param("id") String id, @Param("state") String state, @Param("oldPartRegistered") int oldPartRegistered,
                         @Param("oldPartNo") String oldPartNo, @Param("version") int version);

    @Update("UPDATE problem SET old_part_registered = 1, old_part_no = #{oldPartNo} WHERE id = #{id}")
    int markOldPartRegistered(@Param("id") String id, @Param("oldPartNo") String oldPartNo);

    @Insert("INSERT INTO state_transition_log (problem_id, from_state, to_state, actor, action, reason) "
        + "VALUES (#{id}, #{fromState}, #{toState}, #{actor}, #{action}, #{reason})")
    void insertTransition(@Param("id") String id, @Param("fromState") String fromState, @Param("toState") String toState,
                          @Param("actor") String actor, @Param("action") String action, @Param("reason") String reason);

    @Select("SELECT from_state AS fromState, to_state AS toState, actor, action, reason, created_at AS createdAt "
        + "FROM state_transition_log WHERE problem_id = #{id} ORDER BY id")
    List<Map<String, Object>> listTransitions(@Param("id") String id);

    @Insert("INSERT INTO assessment_record (problem_id, grade, disposal_type, rule_version, auto_approved, approval_note, note, actor) "
        + "VALUES (#{id}, #{grade}, #{disposalType}, 'V2.3', #{autoApproved}, #{approvalNote,jdbcType=VARCHAR}, #{note}, #{actor})")
    void insertAssessment(@Param("id") String id, @Param("grade") String grade, @Param("disposalType") String disposalType,
                          @Param("autoApproved") int autoApproved, @Param("approvalNote") String approvalNote,
                          @Param("note") String note, @Param("actor") String actor);

    @Select("SELECT id, grade, disposal_type AS disposalType, rule_version AS ruleVersion, auto_approved AS autoApproved, "
        + "approval_note AS approvalNote, note, actor, created_at AS createdAt "
        + "FROM assessment_record WHERE problem_id = #{id} ORDER BY id DESC")
    List<Map<String, Object>> listAssessments(@Param("id") String id);

    @Insert("INSERT INTO field_order (problem_id, external_no, state, receipt, is_claim, return_to_factory, project_code, appointment_at, photo_names) "
        + "VALUES (#{id}, #{externalNo}, 'dispatched', '', #{isClaim}, #{returnToFactory}, #{projectCode}, #{appointmentAt,jdbcType=TIMESTAMP}, #{photoNames})")
    void insertDispatchOrder(@Param("id") String id, @Param("externalNo") String externalNo,
                             @Param("isClaim") int isClaim, @Param("returnToFactory") int returnToFactory,
                             @Param("projectCode") String projectCode, @Param("appointmentAt") LocalDateTime appointmentAt,
                             @Param("photoNames") String photoNames);

    @Select("SELECT id, external_no AS externalNo, state, receipt, is_claim AS isClaim, return_to_factory AS returnToFactory, "
        + "project_code AS projectCode, appointment_at AS appointmentAt, photo_names AS photoNames, "
        + "accepted_at AS acceptedAt, arrived_at AS arrivedAt, completed_at AS completedAt, "
        + "replaced_parts AS replacedParts, old_part_no AS oldPartNo, old_part_photo AS oldPartPhoto, "
        + "customer_accept_result AS customerAcceptResult, fee_owner AS feeOwner, rollback_anomaly_note AS rollbackAnomalyNote "
        + "FROM field_order WHERE problem_id = #{id} ORDER BY id DESC")
    List<Map<String, Object>> fieldOrdersDetailed(@Param("id") String id);

    @Select("SELECT id, state FROM field_order WHERE problem_id = #{id} AND external_no = #{externalNo} LIMIT 1")
    Map<String, Object> findFieldOrder(@Param("id") String id, @Param("externalNo") String externalNo);

    @Update("UPDATE field_order SET state = #{state}, receipt = #{receipt}, "
        + "accepted_at = COALESCE(accepted_at, #{acceptedAt,jdbcType=TIMESTAMP}), "
        + "arrived_at = COALESCE(arrived_at, #{arrivedAt,jdbcType=TIMESTAMP}), "
        + "completed_at = COALESCE(completed_at, #{completedAt,jdbcType=TIMESTAMP}), "
        + "replaced_parts = COALESCE(#{replacedParts,jdbcType=VARCHAR}, replaced_parts), "
        + "old_part_no = COALESCE(#{oldPartNo,jdbcType=VARCHAR}, old_part_no), "
        + "old_part_photo = COALESCE(#{oldPartPhoto,jdbcType=VARCHAR}, old_part_photo), "
        + "customer_accept_result = COALESCE(#{customerAcceptResult,jdbcType=VARCHAR}, customer_accept_result), "
        + "fee_owner = COALESCE(#{feeOwner,jdbcType=VARCHAR}, fee_owner) "
        + "WHERE problem_id = #{id} AND external_no = #{externalNo}")
    int updateFieldOrderCallback(@Param("id") String id, @Param("externalNo") String externalNo,
                                 @Param("state") String state, @Param("receipt") String receipt,
                                 @Param("acceptedAt") LocalDateTime acceptedAt, @Param("arrivedAt") LocalDateTime arrivedAt,
                                 @Param("completedAt") LocalDateTime completedAt, @Param("replacedParts") String replacedParts,
                                 @Param("oldPartNo") String oldPartNo, @Param("oldPartPhoto") String oldPartPhoto,
                                 @Param("customerAcceptResult") String customerAcceptResult, @Param("feeOwner") String feeOwner);

    @Update("UPDATE field_order SET rollback_anomaly_note = #{note} WHERE problem_id = #{id} AND external_no = #{externalNo}")
    int recordFieldRollbackAnomaly(@Param("id") String id, @Param("externalNo") String externalNo, @Param("note") String note);

    @Select("SELECT id, version FROM problem WHERE state = 'pending_assign' ORDER BY created_at ASC LIMIT 50")
    List<Map<String, Object>> listPendingAssign();

    @Select("SELECT COUNT(*) AS total, "
        + "COALESCE(SUM(state = 'closed' OR state = 'resolved'), 0) AS closedCount, "
        + "COALESCE(SUM(disposal_type = 'replace'), 0) AS replaceCount, "
        + "COALESCE(SUM(compromise_flag = 1), 0) AS compromiseCount, "
        + "COALESCE(SUM(escalation_level <> 'none'), 0) AS escalatedCount, "
        + "COALESCE(SUM(device_pending = 1 AND state NOT IN ('closed','cancelled','resolved')), 0) AS devicePendingCount, "
        + "COALESCE(SUM(sla_due_at IS NOT NULL AND sla_due_at < #{now} AND state NOT IN ('closed','cancelled','resolved')), 0) AS slaOverdueCount "
        + "FROM problem")
    Map<String, Object> monthlySummary(@Param("now") LocalDateTime now);

    @Select("SELECT channel, COUNT(*) AS count FROM problem GROUP BY channel ORDER BY count DESC")
    List<Map<String, Object>> channelBreakdown();
}
