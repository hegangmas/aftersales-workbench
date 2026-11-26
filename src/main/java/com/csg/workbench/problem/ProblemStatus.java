package com.csg.workbench.problem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V1.1 正式工单状态枚举（6 主状态 + 已取消）。
 * 与前端 web/src/contracts/problem-status.json 保持单一状态源对齐。
 */
public enum ProblemStatus {
    PENDING_ASSIGN(ProblemStateMachine.PENDING_ASSIGN, "待分配", false),
    PROCESSING(ProblemStateMachine.PROCESSING, "处理中", false),
    SUSPENDED(ProblemStateMachine.SUSPENDED, "挂起", false),
    PENDING_CALLBACK(ProblemStateMachine.PENDING_CALLBACK, "待回传", false),
    PENDING_ACCEPT(ProblemStateMachine.PENDING_ACCEPT, "待验收", false),
    CLOSED(ProblemStateMachine.CLOSED, "已闭环", true),
    CANCELLED(ProblemStateMachine.CANCELLED, "已取消", true);

    private static final Map<String, ProblemStatus> BY_CODE = buildIndex();

    private final String code;
    private final String label;
    private final boolean terminal;

    ProblemStatus(String code, String label, boolean terminal) {
        this.code = code;
        this.label = label;
        this.terminal = terminal;
    }

    private static Map<String, ProblemStatus> buildIndex() {
        Map<String, ProblemStatus> map = new LinkedHashMap<String, ProblemStatus>();
        for (ProblemStatus status : values()) {
            map.put(status.code, status);
        }
        return Collections.unmodifiableMap(map);
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return terminal;
    }

    /**
     * 解析合法的 V1.1 状态码；旧状态码或未知状态码会抛出 IllegalArgumentException。
     */
    public static ProblemStatus fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("工单状态码不能为空");
        }
        ProblemStatus found = BY_CODE.get(code.trim());
        if (found == null) {
            throw new IllegalArgumentException("非法或已废弃的工单状态码: " + code);
        }
        return found;
    }

    public static boolean isValidCode(String code) {
        return code != null && BY_CODE.containsKey(code.trim());
    }
}
