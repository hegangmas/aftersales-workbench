package com.csg.workbench.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.csg.workbench.account.AccountMapper;
import com.csg.workbench.device.DeviceService;
import com.csg.workbench.sop.SopMapper;
import com.csg.workbench.sop.SopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@ExtendWith(MockitoExtension.class)
class ProblemV11ServiceTest {
    private static final String ID = "22222222-2222-2222-2222-222222222222";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock private ProblemMapper mapper;
    @Mock private DeviceService devices;
    @Mock private AccountMapper accounts;
    @Mock private SopMapper sopMapper;
    private ProblemV11Service service;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "dispatcher", "unused", AuthorityUtils.createAuthorityList("ROLE_DISPATCHER", "ROLE_OPERATOR")));
        service = new ProblemV11Service(mapper, devices, accounts, new ObjectMapper());
        lenient().when(mapper.insertCommandIfAbsent(anyString(), anyString(), anyString())).thenReturn(1);
    }

    @AfterEach
    void teardown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stateMachineBlocksIllegalJumpToClosed() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> ProblemStateMachine.requireTransition(ProblemStateMachine.PENDING_ASSIGN, ProblemStateMachine.CLOSED));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void devicePendingBlocksDispatchUntilBound() {
        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PROCESSING, 2, "agent"));
        Map<String, Object> full = new LinkedHashMap<String, Object>();
        full.put("devicePending", Integer.valueOf(1));
        when(mapper.findProblem(ID)).thenReturn(full);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.dispatchToLezhuang(ID, true, false, "PRJ-01",
                LocalDateTime.now(SHANGHAI).plusHours(2), "", 2, "dispatch-key-01"));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("设备信息待补，未补齐前不得派单至智享乐装", error.getReason());
    }

    @Test
    void replaceTicketCannotCloseWithoutOldPartRegistration() {
        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PENDING_ACCEPT, 5, "agent"));
        Map<String, Object> full = new LinkedHashMap<String, Object>();
        full.put("disposalType", "replace");
        full.put("oldPartRegistered", Integer.valueOf(0));
        when(mapper.findProblem(ID)).thenReturn(full);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.acceptOrReject(ID, true, "验收通过", "", 5, "accept-key-01"));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("换装或换件工单旧件未登记，不得闭环", error.getReason());
    }

    @Test
    void rollbackLezhuangCallbackLogsAnomalyInsteadOfReverting() {
        when(mapper.lockProblem(ID)).thenReturn(row(ProblemStateMachine.PENDING_CALLBACK, 3, "agent"));
        Map<String, Object> order = new LinkedHashMap<String, Object>();
        order.put("state", "arrived");
        when(mapper.findFieldOrder(ID, "LZ-001")).thenReturn(order);
        service.handleLezhuangCallback(ID, "LZ-001", "accepted", "乱序回调", null, null, null, null, null, 3, "cb-key-01");
        verify(mapper).recordFieldRollbackAnomaly(eq(ID), eq("LZ-001"), anyString());
    }

    @Test
    void knowledgeReadOnlyStripsThresholdsAndApprovalRules() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", Long.valueOf(1L));
        row.put("category", "无法充电");
        row.put("title", "无法充电 · SOP步骤");
        row.put("version", "V2.3");
        row.put("customerStepsJson", "[\"步骤1\"]");
        row.put("onsiteStepsJson", "[\"步骤4\"]");
        row.put("requiredEvidenceJson", "[\"photo\"]");
        row.put("gradeThresholdsJson", "{\"A\":\"secret threshold\"}");
        row.put("autoApprovalRulesJson", "[{\"id\":\"r1\"}]");
        when(sopMapper.listPublishedForKnowledge()).thenReturn(Collections.singletonList(row));

        SopService sopService = new SopService(sopMapper, new ObjectMapper());
        List<Map<String, Object>> list = sopService.listKnowledgeReadOnly();
        assertEquals(1, list.size());
        assertFalse(list.get(0).containsKey("gradeThresholds"));
        assertFalse(list.get(0).containsKey("autoApprovalRules"));
    }

    private static Map<String, Object> row(String state, int version, String owner) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("state", state);
        m.put("version", Integer.valueOf(version));
        m.put("owner", owner);
        return m;
    }
}
