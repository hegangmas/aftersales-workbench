package com.csg.workbench.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.csg.workbench.account.AccountMapper;
import com.csg.workbench.device.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * 7×7 状态迁移矩阵及 V1.1 写入口收口（suspendV11 / quickCloseV11 / handoffV11 / escalateV11）验证测试。
 */
@ExtendWith(MockitoExtension.class)
class ProblemTransitionMatrixTest {
    private static final String ID = "33333333-3333-3333-3333-333333333333";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock private ProblemMapper mapper;
    @Mock private DeviceService devices;
    @Mock private AccountMapper accounts;
    private ProblemV11Service service;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "operator.1", "unused", AuthorityUtils.createAuthorityList("ROLE_OPERATOR")));
        service = new ProblemV11Service(mapper, devices, accounts, new ObjectMapper());
        lenient().when(mapper.insertCommandIfAbsent(anyString(), anyString(), anyString())).thenReturn(1);
    }

    @AfterEach
    void teardown() {
        SecurityContextHolder.clearContext();
    }

    private static JsonNode loadContract() throws IOException {
        Path path = Paths.get("web", "src", "contracts", "problem-status.json");
        if (!Files.exists(path)) {
            Path cursor = Paths.get("").toAbsolutePath();
            while (cursor != null) {
                Path candidate = cursor.resolve("web").resolve("src").resolve("contracts").resolve("problem-status.json");
                if (Files.exists(candidate)) {
                    path = candidate;
                    break;
                }
                cursor = cursor.getParent();
            }
        }
        assertTrue(Files.exists(path), "Contract file not found: " + path.toAbsolutePath());
        return new ObjectMapper().readTree(path.toFile());
    }

    @Test
    void sevenBySevenMatrixMatchesContractJson() throws IOException {
        JsonNode root = loadContract();
        JsonNode v11States = root.get("v11States");
        assertNotNull(v11States);

        List<String> allCodes = new ArrayList<String>();
        Map<String, Set<String>> allowedMap = new HashMap<String, Set<String>>();
        Map<String, Boolean> terminalMap = new HashMap<String, Boolean>();

        for (JsonNode stateNode : v11States) {
            String code = stateNode.get("code").asText();
            allCodes.add(code);
            terminalMap.put(code, Boolean.valueOf(stateNode.get("terminal").asBoolean()));
            Set<String> targets = new HashSet<String>();
            for (JsonNode targetNode : stateNode.get("allowedTransitions")) {
                targets.add(targetNode.asText());
            }
            allowedMap.put(code, targets);
        }

        assertEquals(7, allCodes.size(), "V1.1 must define exact 7 states (6+1)");

        int pairCount = 0;
        for (String from : allCodes) {
            for (String to : allCodes) {
                pairCount++;
                if (from.equals(to)) {
                    ProblemStateMachine.requireTransition(from, to);
                    assertFalse(allowedMap.get(from).contains(to));
                } else if (allowedMap.get(from).contains(to)) {
                    assertFalse(terminalMap.get(from).booleanValue(), "Terminal state must not transition: " + from);
                    ProblemStateMachine.requireTransition(from, to);
                } else {
                    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                        () -> ProblemStateMachine.requireTransition(from, to),
                        "Illegal transition should be rejected: " + from + " -> " + to);
                    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
                }
            }
        }
        assertEquals(49, pairCount);
    }

    @Test
    void suspendV11FollowsStateMatrixAndWritesSuspendedAndTransitionLog() {
        List<String> allowedFrom = Arrays.asList(
            ProblemStateMachine.PENDING_ASSIGN,
            ProblemStateMachine.PROCESSING,
            ProblemStateMachine.PENDING_CALLBACK
        );
        int seq = 0;
        for (String from : allowedFrom) {
            seq++;
            int ver = seq;
            when(mapper.lockProblem(ID)).thenReturn(row(from, ver, "operator.1"));
            when(mapper.suspendProblem(ID, "parts_wait", "operator.1", ver)).thenReturn(1);
            service.suspendV11(ID, "parts_wait", false, "等待备件到货", null, ver, "susp-ok-" + seq);
            verify(mapper).suspendProblem(ID, "parts_wait", "operator.1", ver);
            verify(mapper).insertTransition(eq(ID), eq(from), eq(ProblemStateMachine.SUSPENDED),
                eq("operator.1"), eq("suspend"), anyString());
        }

        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PROCESSING, 10, "operator.1"));
        when(mapper.suspendProblem(ID, "customer_reboot", "early", 10)).thenReturn(1);
        LocalDateTime futureDue = LocalDateTime.now(SHANGHAI).plusHours(2);
        service.suspendV11(ID, "customer_reboot", true, "客户重启观察", futureDue, 10, "susp-pool-10");
        verify(mapper).suspendProblem(ID, "customer_reboot", "early", 10);
        verify(mapper).insertOwnership(ID, "operator.1", "operator.1", "early", "handoff", "挂起回池：等待客户断电重启");
        verify(mapper).schedule(ID, "共享池", "客户重启观察", futureDue);
        verify(mapper, times(2)).insertTransition(eq(ID), eq(ProblemStateMachine.PROCESSING), eq(ProblemStateMachine.SUSPENDED),
            eq("operator.1"), eq("suspend"), anyString());

        List<String> disallowedFrom = Arrays.asList(
            ProblemStateMachine.PENDING_ACCEPT,
            ProblemStateMachine.CLOSED,
            ProblemStateMachine.CANCELLED,
            "open"
        );
        for (int i = 0; i < disallowedFrom.size(); i++) {
            String from = disallowedFrom.get(i);
            int ver = 20 + i;
            when(mapper.lockProblem(ID)).thenReturn(row(from, ver, "operator.1"));
            ResponseStatusException err = assertThrows(ResponseStatusException.class,
                () -> service.suspendV11(ID, "parts_wait", false, "不可挂起", null, ver, "susp-bad-" + ver));
            assertEquals(HttpStatus.CONFLICT, err.getStatus());
        }
        verify(mapper, never()).suspendProblem(eq(ID), anyString(), anyString(), eq(20));
    }

    @Test
    void quickCloseV11TransitionsToPendingAcceptAndAcceptClosesToClosed() {
        List<String> allowedFrom = Arrays.asList(
            ProblemStateMachine.PROCESSING,
            ProblemStateMachine.PENDING_CALLBACK
        );
        for (int i = 0; i < allowedFrom.size(); i++) {
            String from = allowedFrom.get(i);
            int ver = i + 1;
            when(mapper.lockProblem(ID)).thenReturn(row(from, ver, "operator.1"));
            when(mapper.activeFieldOrders(ID)).thenReturn(0);
            when(mapper.disposalType(ID)).thenReturn("remote");
            when(mapper.changeState(ID, ProblemStateMachine.PENDING_ACCEPT, ver)).thenReturn(1);

            service.quickCloseV11(ID, "远程指导重启成功", "platform", "平台恢复正常充电", ver, "qc-v11-ok-" + ver);

            verify(mapper, times(ver)).verify(ID, "operator.1", "platform", "平台恢复正常充电");
            verify(mapper).changeState(ID, ProblemStateMachine.PENDING_ACCEPT, ver);
            verify(mapper, never()).changeState(ID, "resolved", ver);
            verify(mapper).insertTransition(eq(ID), eq(from), eq(ProblemStateMachine.PENDING_ACCEPT),
                eq("operator.1"), eq("quick_close"), anyString());
            verify(mapper, times(ver)).insertActivity(eq(ID), eq("operator.1"), eq("quick_close"), anyString());
        }

        // Subsequent normal acceptance closes to closed
        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PENDING_ACCEPT, 5, "operator.1"));
        Map<String, Object> full = new LinkedHashMap<String, Object>();
        full.put("disposalType", "remote");
        full.put("oldPartRegistered", Integer.valueOf(0));
        when(mapper.findProblem(ID)).thenReturn(full);
        when(mapper.closeWithOldPart(ID, ProblemStateMachine.CLOSED, 0, null, 5)).thenReturn(1);
        service.acceptOrReject(ID, true, "客户确认已恢复", null, 5, "qc-v11-accept-5");
        verify(mapper).closeWithOldPart(ID, ProblemStateMachine.CLOSED, 0, null, 5);
        verify(mapper).insertTransition(ID, ProblemStateMachine.PENDING_ACCEPT, ProblemStateMachine.CLOSED,
            "operator.1", "accept_close", "客户确认已恢复");

        // Disallowed disposals and disallowed source states
        for (String blockedDisposal : Arrays.asList("replace", "part", "onsite")) {
            when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PROCESSING, 30, "operator.1"));
            when(mapper.activeFieldOrders(ID)).thenReturn(0);
            when(mapper.disposalType(ID)).thenReturn(blockedDisposal);
            ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
                () -> service.quickCloseV11(ID, "尝试快结", "platform", "记录", 30, "qc-disposal-" + blockedDisposal));
            assertEquals(HttpStatus.CONFLICT, blocked.getStatus());
        }

        List<String> disallowedFrom = Arrays.asList(
            ProblemStateMachine.PENDING_ASSIGN,
            ProblemStateMachine.SUSPENDED,
            ProblemStateMachine.PENDING_ACCEPT,
            ProblemStateMachine.CLOSED,
            ProblemStateMachine.CANCELLED
        );
        for (int i = 0; i < disallowedFrom.size(); i++) {
            String from = disallowedFrom.get(i);
            int ver = 40 + i;
            when(mapper.lockProblem(ID)).thenReturn(row(from, ver, "operator.1"));
            lenient().when(mapper.activeFieldOrders(ID)).thenReturn(0);
            lenient().when(mapper.disposalType(ID)).thenReturn("remote");
            ResponseStatusException err = assertThrows(ResponseStatusException.class,
                () -> service.quickCloseV11(ID, "非法状态快结", "platform", "记录", ver, "qc-bad-" + ver));
            assertEquals(HttpStatus.CONFLICT, err.getStatus());
        }
        verify(mapper, never()).changeState(eq(ID), eq(ProblemStateMachine.PENDING_ACCEPT), eq(40));
    }

    @Test
    void handoffAndEscalateKeepMainStateUnchanged() {
        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PROCESSING, 2, "operator.1"));
        Map<String, Object> targetAccount = new LinkedHashMap<String, Object>();
        targetAccount.put("username", "operator.2");
        targetAccount.put("enabled", Boolean.TRUE);
        when(accounts.find("operator.2")).thenReturn(targetAccount);
        when(mapper.assignOwner(ID, "operator.2", 2)).thenReturn(1);

        service.handoffV11(ID, "operator.2", "交接二线继续跟进", 2, "handoff-v11-1");
        verify(mapper).assignOwner(ID, "operator.2", 2);
        verify(mapper).insertOwnership(ID, "operator.1", "operator.1", "operator.2", "handoff", "交接二线继续跟进");
        verify(mapper).insertActivity(ID, "operator.1", "handoff", "由 operator.1 交给 operator.2：交接二线继续跟进");
        verify(mapper, never()).changeState(anyString(), anyString(), anyInt());
        verify(mapper, never()).insertTransition(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PROCESSING, 3, "operator.1"));
        when(mapper.updateEscalation(ID, "expert", "客户催办升级", 1, 3)).thenReturn(1);
        service.escalateV11(ID, "expert", "客户催办升级", true, 3, "escalate-v11-1");
        verify(mapper).updateEscalation(ID, "expert", "客户催办升级", 1, 3);
        verify(mapper).insertActivity(eq(ID), eq("operator.1"), eq("escalate"), anyString());
        verify(mapper, never()).changeState(anyString(), anyString(), anyInt());
        verify(mapper, never()).insertTransition(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void followupEvidenceAndAssessmentFollowV11Rules() {
        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PROCESSING, 4, "operator.1"));
        when(mapper.changeState(ID, ProblemStateMachine.PROCESSING, 4)).thenReturn(1);
        service.addFollowupV11(ID, "补充电话跟进", 4, "followup-v11-1");
        verify(mapper).insertActivity(ID, "operator.1", "followup", "补充电话跟进");

        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PROCESSING, 5, "operator.1"));
        when(mapper.changeState(ID, ProblemStateMachine.PROCESSING, 5)).thenReturn(1);
        service.addEvidenceV11(ID, "photo", "agent", "pe.jpg", "PE 接地照片", 5, "ev-v11-1");
        verify(mapper).insertEvidence(ID, "photo", "agent", "pe.jpg", "PE 接地照片", "operator.1");

        ResponseStatusException cReplace = assertThrows(ResponseStatusException.class,
            () -> service.assessV11(ID, "C", "replace", "禁止C级换装", 6, "assess-v11-c"));
        assertEquals(HttpStatus.BAD_REQUEST, cReplace.getStatus());

        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PROCESSING, 6, "operator.1"));
        when(mapper.countEvidences(ID)).thenReturn(1);
        when(mapper.updateAssessment(eq(ID), eq("A"), eq("replace"), anyString(), eq(6))).thenReturn(1);
        service.assessV11(ID, "A", "replace", "证据齐全准予换装", 6, "assess-v11-ok");
        verify(mapper).insertAssessment(eq(ID), eq("A"), eq("replace"), eq(1), anyString(), eq("证据齐全准予换装"), eq("operator.1"));
    }

    private static Map<String, Object> row(String state, int version, String owner) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("state", state);
        m.put("version", Integer.valueOf(version));
        m.put("owner", owner);
        return m;
    }
}
