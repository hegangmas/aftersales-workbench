package com.csg.workbench.problem;

import java.util.Map;

public class BoardQueries {
    public String page(Map<String, Object> params) {
        return sql(params, true);
    }

    public String count(Map<String, Object> params) {
        return sql(params, false);
    }

    private static String sql(Map<String, Object> params, boolean page) {
        StringBuilder sql = new StringBuilder();
        if (page) {
            sql.append("SELECT p.id, p.display_number AS displayNumber, p.title, p.device, p.state, p.owner, ");
            sql.append("p.category, p.kind, p.updated_at AS updatedAt, ");
            sql.append("(SELECT MIN(f.due_at) FROM followup f WHERE f.problem_id = p.id AND f.state = 'open') AS nextDueAt ");
            sql.append("FROM problem p WHERE 1 = 1");
        } else {
            sql.append("SELECT COUNT(*) FROM problem p WHERE 1 = 1");
        }
        if (present(params, "state")) {
            sql.append(" AND p.state = #{state}");
        }
        Object mode = params.containsKey("ownerMode") ? params.get("ownerMode") : null;
        if ("early".equals(mode)) {
            sql.append(" AND (p.owner = 'early' OR p.owner = '')");
        } else if ("user".equals(mode)) {
            sql.append(" AND p.owner = #{owner}");
        }
        if (present(params, "category")) {
            sql.append(" AND p.category = #{category}");
        }
        if (present(params, "kind")) {
            sql.append(" AND p.kind = #{kind}");
        }
        if (present(params, "like")) {
            sql.append(" AND (p.title LIKE #{like} ESCAPE '\\\\' OR p.device LIKE #{like} ESCAPE '\\\\'");
            if (present(params, "number")) {
                sql.append(" OR p.display_number = #{number}");
            }
            sql.append(")");
        } else if (present(params, "number")) {
            sql.append(" AND p.display_number = #{number}");
        }
        if (page && present(params, "cursorAt")) {
            sql.append(" AND (p.updated_at < #{cursorAt} OR (p.updated_at = #{cursorAt} AND p.id < #{cursorId}))");
        }
        if (page) {
            sql.append(" ORDER BY p.updated_at DESC, p.id DESC LIMIT #{limit}");
        }
        return sql.toString();
    }

    private static boolean present(Map<String, Object> params, String key) {
        if (!params.containsKey(key)) {
            return false;
        }
        Object value = params.get(key);
        return value != null && !"".equals(value);
    }
}
