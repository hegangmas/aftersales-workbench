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
            sql.append("SELECT p.id, p.display_number AS displayNumber, p.ticket_no AS ticketNo, p.title, p.device, p.state, p.owner, ");
            sql.append("p.category, p.kind, p.channel, p.device_pending AS devicePending, p.urgency, p.suspend_reason AS suspendReason, p.grade, ");
            sql.append("p.disposal_type AS disposalType, p.compromise_flag AS compromiseFlag, p.old_part_registered AS oldPartRegistered, ");
            sql.append("p.escalation_level AS escalationLevel, p.sla_due_at AS slaDueAt, ");
            sql.append("p.version AS version, p.updated_at AS updatedAt, ");
            sql.append("(SELECT COUNT(*) FROM field_order fo WHERE fo.problem_id = p.id ");
            sql.append("AND fo.state IN ('linked', 'in_progress')) AS activeFieldOrders, ");
            sql.append("(SELECT f.next_step FROM followup f WHERE f.problem_id = p.id AND f.state = 'open' ");
            sql.append("ORDER BY f.due_at, f.id LIMIT 1) AS nextStep, ");
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
        appendBucket(sql, params);
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

    private static void appendBucket(StringBuilder sql, Map<String, Object> params) {
        if (!present(params, "bucket")) {
            return;
        }
        String bucket = String.valueOf(params.get("bucket"));
        if ("unclaimed".equals(bucket)) {
            sql.append(" AND p.state <> 'resolved' AND (p.owner = 'early' OR p.owner = '')");
        } else if ("open".equals(bucket)) {
            sql.append(" AND p.state = 'open' AND p.owner <> 'early' AND p.owner <> ''");
        } else if ("triage".equals(bucket)) {
            sql.append(" AND p.state = 'triage' AND p.owner <> 'early' AND p.owner <> ''");
        } else if ("waiting".equals(bucket)) {
            sql.append(" AND p.state = 'waiting' AND p.owner <> 'early' AND p.owner <> ''");
        } else if ("verify".equals(bucket)) {
            sql.append(" AND p.state = 'verify' AND p.owner <> 'early' AND p.owner <> ''");
        } else if ("answered".equals(bucket)) {
            sql.append(" AND p.state = 'answered' AND p.owner <> 'early' AND p.owner <> ''");
        } else if ("referred".equals(bucket)) {
            sql.append(" AND p.state = 'referred' AND p.owner <> 'early' AND p.owner <> ''");
        } else if ("resolved".equals(bucket)) {
            sql.append(" AND p.state = 'resolved'");
        } else if ("fieldPending".equals(bucket)) {
            sql.append(" AND p.state = 'field' AND p.owner <> 'early' AND p.owner <> ''");
            sql.append(" AND NOT EXISTS (SELECT 1 FROM field_order fo WHERE fo.problem_id = p.id ");
            sql.append("AND fo.state IN ('linked', 'in_progress'))");
        } else if ("fieldActive".equals(bucket)) {
            sql.append(" AND p.state = 'field' AND p.owner <> 'early' AND p.owner <> ''");
            sql.append(" AND EXISTS (SELECT 1 FROM field_order fo WHERE fo.problem_id = p.id ");
            sql.append("AND fo.state IN ('linked', 'in_progress'))");
        } else if ("device_pending".equals(bucket)) {
            sql.append(" AND p.device_pending = 1 AND p.state NOT IN ('closed', 'cancelled', 'resolved')");
        } else if ("pending_assign".equals(bucket) || "processing".equals(bucket) || "suspended".equals(bucket)
            || "pending_callback".equals(bucket) || "pending_accept".equals(bucket)
            || "closed".equals(bucket) || "cancelled".equals(bucket)) {
            sql.append(" AND p.state = #{bucket}");
        }
    }

    private static boolean present(Map<String, Object> params, String key) {
        if (!params.containsKey(key)) {
            return false;
        }
        Object value = params.get(key);
        return value != null && !"".equals(value);
    }
}
