package com.csg.workbench.account;

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

    @Select("SELECT username, password_hash AS passwordHash, role, enabled FROM account WHERE username = #{username}")
    Map<String, Object> find(@Param("username") String username);

    @Insert("INSERT INTO account (username, password_hash, role, enabled) VALUES (#{username}, #{hash}, #{role}, #{enabled})")
    void insert(@Param("username") String username, @Param("hash") String hash,
                @Param("role") String role, @Param("enabled") int enabled);

    @Select("SELECT username, role, enabled FROM account ORDER BY username")
    List<Map<String, Object>> list();

    @Select("SELECT username, role FROM account WHERE enabled = 1 ORDER BY username")
    List<Map<String, Object>> assignable();

    @Update("UPDATE account SET enabled = #{enabled} WHERE username = #{username}")
    int setEnabled(@Param("username") String username, @Param("enabled") int enabled);

    @Select("SELECT COUNT(*) FROM account WHERE role = 'admin' AND enabled = 1")
    int enabledAdmins();
}
