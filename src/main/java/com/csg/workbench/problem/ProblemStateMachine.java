package com.csg.workbench.problem;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * V1.1 第 3.2 节严格状态机（6 主状态 + 已取消）。
 * 禁止任何跨状态直跳。
 */
public final class ProblemStateMachine {
    public static final String PENDING_ASSIGN = "pending_assign";
    public static final String PROCESSING = "processing";
    public static final String SUSPENDED = "suspended";
    public static final String PENDING_CALLBACK = "pending_callback";
    public static final String PENDING_ACCEPT = "pending_accept";
    public static final String CLOSED = "closed";
    public static final String CANCELLED = "cancelled";

    public static final Set<String> ALL_STATES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        PENDING_ASSIGN, PROCESSING, SUSPENDED, PENDING_CALLBACK, PENDING_ACCEPT, CLOSED, CANCELLED
    )));

    private static final Map<String, Set<String>> ALLOWED = buildTransitions();

    private ProblemStateMachine() {
    }

    private static Map<String, Set<String>> buildTransitions() {
        Map<String, Set<String>> map = new HashMap<String, Set<String>>();
        map.put(PENDING_ASSIGN, set(PROCESSING, SUSPENDED, CANCELLED));
        map.put(PROCESSING, set(SUSPENDED, PENDING_CALLBACK, PENDING_ACCEPT));
        map.put(SUSPENDED, set(PROCESSING, PENDING_CALLBACK, CANCELLED));
        map.put(PENDING_CALLBACK, set(PENDING_ACCEPT, SUSPENDED));
        map.put(PENDING_ACCEPT, set(CLOSED, PROCESSING));
        map.put(CLOSED, Collections.<String>emptySet());
        map.put(CANCELLED, Collections.<String>emptySet());
        return Collections.unmodifiableMap(map);
    }

    private static Set<String> set(String... states) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(states)));
    }

    public static boolean isTerminal(String state) {
        return CLOSED.equals(state) || CANCELLED.equals(state);
    }

    public static void requireTransition(String from, String to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单状态流转无效");
        }
        if (from.equals(to)) {
            return;
        }
        Set<String> targets = ALLOWED.get(from);
        if (targets == null || !targets.contains(to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "当前状态「" + label(from) + "」不允许流转至「" + label(to) + "」");
        }
    }

    public static String label(String state) {
        if (PENDING_ASSIGN.equals(state)) return "待分配";
        if (PROCESSING.equals(state)) return "处理中";
        if (SUSPENDED.equals(state)) return "挂起";
        if (PENDING_CALLBACK.equals(state)) return "待回传";
        if (PENDING_ACCEPT.equals(state)) return "待验收";
        if (CLOSED.equals(state)) return "已闭环";
        if (CANCELLED.equals(state)) return "已取消";
        return state == null ? "未知" : state;
    }
}
