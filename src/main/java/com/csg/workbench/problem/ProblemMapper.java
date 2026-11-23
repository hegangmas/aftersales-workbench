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
        + "VALUES (#{id}, 'early', #{action}, #{note})")
    void insertActivity(@Param("id") String id, @Param("action") String action, @Param("note") String note);

    @Select("SELECT id, title, device, state, description FROM problem WHERE id = #{id}")
    Map<String, Object> findProblem(@Param("id") String id);

    @Select("SELECT action, note, created_at AS createdAt FROM activity WHERE problem_id = #{id} ORDER BY id")
    List<Map<String, Object>> listActivities(@Param("id") String id);

    @Select("SELECT id, title, device, state FROM problem WHERE device = #{device} ORDER BY updated_at DESC LIMIT 30")
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

    @Insert("INSERT INTO verification (problem_id, actor, source, evidence) VALUES (#{id}, 'early', 'customer', #{note})")
    void verify(@Param("id") String id, @Param("note") String note);
}
