package com.csg.workbench.problem;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface ProblemMapper {
    @Insert("INSERT INTO problem (id, scenario, title, category, channel, description, kind, device) "
        + "VALUES (#{id}, 'JAVA-TRIAL', #{title}, '充电故障', 'phone', #{description}, 'fault', #{device})")
    void insertProblem(@Param("id") String id, @Param("title") String title,
                       @Param("description") String description, @Param("device") String device);

    @Insert("INSERT INTO activity (problem_id, actor, action, note) "
        + "VALUES (#{id}, #{actor}, #{action}, #{note})")
    void insertActivity(@Param("id") String id, @Param("actor") String actor, @Param("action") String action, @Param("note") String note);

    @Select("SELECT id, display_number AS displayNumber, title, device, state, description FROM problem WHERE id = #{id}")
    Map<String, Object> findProblem(@Param("id") String id);

    @Select("SELECT id FROM problem WHERE display_number = #{number}")
    String findIdByNumber(@Param("number") long number);

    @Select("SELECT action, note, created_at AS createdAt FROM activity WHERE problem_id = #{id} ORDER BY id")
    List<Map<String, Object>> listActivities(@Param("id") String id);

    @Select("SELECT id, display_number AS displayNumber, title, device, state FROM problem WHERE device = #{device} ORDER BY updated_at DESC LIMIT 30")
    List<Map<String, Object>> findByDevice(@Param("device") String device);

    @Select("SELECT state FROM problem WHERE id = #{id} FOR UPDATE")
    String lockState(@Param("id") String id);

    @Select("SELECT id, owner, next_step AS nextStep, due_at AS dueAt, due_at <= #{now} AS overdue "
        + "FROM followup WHERE problem_id = #{id} AND state = 'open' ORDER BY id DESC LIMIT 1")
    Map<String, Object> pending(@Param("id") String id, @Param("now") LocalDateTime now);

    @Select("SELECT f.problem_id AS problemId, p.title, f.owner, f.next_step AS nextStep, f.due_at AS dueAt "
        + "FROM followup f JOIN problem p ON p.id = f.problem_id "
        + "WHERE f.state = 'open' AND f.due_at <= #{now} ORDER BY f.due_at, f.id LIMIT 50")
    List<Map<String, Object>> due(@Param("now") LocalDateTime now);

    @Insert("INSERT INTO followup (problem_id, owner, next_step, due_at) VALUES (#{id}, #{owner}, #{step}, #{due})")
    void schedule(@Param("id") String id, @Param("owner") String owner,
                  @Param("step") String step, @Param("due") LocalDateTime due);

    @Update("UPDATE followup SET state = 'done' WHERE problem_id = #{id} AND state = 'open'")
    int completeFollowup(@Param("id") String id);

    @Update("UPDATE problem SET state = #{state}, version = version + 1 WHERE id = #{id}")
    void changeState(@Param("id") String id, @Param("state") String state);

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
}
