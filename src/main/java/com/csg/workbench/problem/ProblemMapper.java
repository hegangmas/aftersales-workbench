package com.csg.workbench.problem;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
