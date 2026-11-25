package com.csg.workbench.account;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountMapper {
    @Select("SELECT COUNT(*) FROM account")
    int count();

    @Select("SELECT username, password_hash AS passwordHash, role, enabled, duty_status AS dutyStatus FROM account WHERE username = #{username}")
    Map<String, Object> find(@Param("username") String username);

    @Insert("INSERT INTO account (username, password_hash, role, enabled) VALUES (#{username}, #{hash}, #{role}, #{enabled})")
    void insert(@Param("username") String username, @Param("hash") String hash,
                @Param("role") String role, @Param("enabled") int enabled);

    @Select("SELECT username, role, enabled, duty_status AS dutyStatus, duty_updated_at AS dutyUpdatedAt FROM account ORDER BY username")
    List<Map<String, Object>> list();

    @Select("SELECT username, role, duty_status AS dutyStatus, duty_updated_at AS dutyUpdatedAt FROM account WHERE enabled = 1 ORDER BY username")
    List<Map<String, Object>> assignable();

    @Update("UPDATE account SET duty_status = #{status}, duty_updated_at = CURRENT_TIMESTAMP(6) WHERE username = #{username}")
    int updateDutyStatus(@Param("username") String username, @Param("status") String status);

    @Select("SELECT username, role, duty_status AS dutyStatus, duty_updated_at AS dutyUpdatedAt FROM account WHERE enabled = 1 AND duty_status = 'ready' AND role IN ('operator','engineer','dispatcher') ORDER BY username")
    List<Map<String, Object>> readyAgents();

    @Update("UPDATE account SET enabled = #{enabled} WHERE username = #{username}")
    int setEnabled(@Param("username") String username, @Param("enabled") int enabled);

    @Select("SELECT COUNT(*) FROM account WHERE role = 'admin' AND enabled = 1")
    int enabledAdmins();

    @Select("SELECT COUNT(*) FROM account WHERE role = 'admin' AND enabled = 1 FOR UPDATE")
    int enabledAdminsForUpdate();

    @Select("SELECT enabled, credentials_changed_at AS credentialsChangedAt FROM account WHERE username = #{username}")
    Map<String, Object> findSessionState(@Param("username") String username);

    @Update("UPDATE account SET password_hash = #{hash}, credentials_changed_at = CURRENT_TIMESTAMP(6) "
        + "WHERE username = #{username}")
    int updatePassword(@Param("username") String username, @Param("hash") String hash);

    @Select("SELECT credentials_changed_at FROM account WHERE username = #{username}")
    LocalDateTime credentialsChangedAt(@Param("username") String username);

    @Insert("INSERT INTO security_audit (actor, action, target) VALUES (#{actor}, #{action}, #{target})")
    void insertAudit(@Param("actor") String actor, @Param("action") String action, @Param("target") String target);
}
