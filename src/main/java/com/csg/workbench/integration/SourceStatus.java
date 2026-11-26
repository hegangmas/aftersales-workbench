package com.csg.workbench.integration;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部集成源状态与时间戳辅助工具。
 */
public final class SourceStatus {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SourceStatus() {
    }

    public static Map<String, Object> of(String name, boolean ok, String note, String queriedAt) {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("name", name);
        state.put("ok", Boolean.valueOf(ok));
        state.put("note", note);
        state.put("queriedAt", resolveQueriedAt(queriedAt));
        return Collections.unmodifiableMap(state);
    }

    public static String resolveQueriedAt(String queriedAt) {
        if (queriedAt != null && !queriedAt.trim().isEmpty()) {
            return queriedAt;
        }
        return LocalDateTime.now(BUSINESS_ZONE).format(STAMP);
    }
}
