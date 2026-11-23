package com.csg.workbench.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.csg.workbench.device.DeviceService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
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
class ProblemServiceTest {
    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock
    private ProblemMapper mapper;
    @Mock
    private DeviceService devices;
    private ProblemService service;

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "operator.1", "unused", AuthorityUtils.createAuthorityList("ROLE_OPERATOR")));
        service = new ProblemService(mapper, devices);
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void followupStoresCurrentLoginName() {
        when(mapper.lockState(ID)).thenReturn("open");
        service.recordFollowup(ID, " 已联系车主 ");
        verify(mapper).insertActivity(ID, "operator.1", "followup", "已联系车主");
    }

    @Test
    void resolvedRejectsFurtherWrites() {
        when(mapper.lockState(ID)).thenReturn("resolved");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.recordFollowup(ID, "再写一条"));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("问题单已结案", error.getReason());
        verify(mapper, never()).insertActivity(any(), any(), any(), any());
    }

    @Test
    void fieldWithoutOrderCannotSkipDispatch() {
        when(mapper.lockState(ID)).thenReturn("field");
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.schedule(ID, "坐席", "确认恢复", future()));
        assertEquals("请先关联现场单，不能跳过派单阶段", error.getReason());
        verify(mapper, never()).changeState(any(), any());
        assertThrows(ResponseStatusException.class,
            () -> service.finishCallback(ID, "continue", "跳过", past(), null, null));
    }

    @Test
    void activeFieldOrderBlocksCallback() {
        when(mapper.lockState(ID)).thenReturn("field");
        when(mapper.activeFieldOrders(ID)).thenReturn(1);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.finishCallback(ID, "resolved", "完工了", past(), "customer", "已恢复"));
        assertEquals("请先登记现场单处理结果", error.getReason());
        verify(mapper, never()).verify(any(), any(), any(), any());
    }

    @Test
    void verifyCallbackReturnsToVerify() {
        when(mapper.lockState(ID)).thenReturn("verify");
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        service.schedule(ID, "坐席", "确认是否恢复", future());
        verify(mapper).setResume(ID, "verify");
        verify(mapper).changeState(ID, "waiting");

        when(mapper.lockState(ID)).thenReturn("waiting");
        when(mapper.resumeState(ID)).thenReturn("verify");
        service.finishCallback(ID, "continue", "仍需复测", past(), null, null);
        verify(mapper).changeState(ID, "verify");
        verify(mapper).setResume(eq(ID), isNull());
    }

    @Test
    void continueFromOpenStaysOpenAndResolvedNeedsEvidence() {
        when(mapper.lockState(ID)).thenReturn("open");
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        service.finishCallback(ID, "continue", "稍后再联系", past(), null, null);
        verify(mapper).changeState(ID, "open");

        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
            () -> service.finishCallback(ID, "resolved", "说已恢复", past(), null, " "));
        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatus());
        verify(mapper, never()).verify(any(), any(), any(), any());

        service.finishCallback(ID, "resolved", "车主确认", past(), "customer", "已完成一次充电");
        verify(mapper).verify(ID, "operator.1", "customer", "已完成一次充电");
        verify(mapper).changeState(ID, "resolved");
    }

    @Test
    void shortNumberAndUuidBothResolve() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", ID);
        row.put("state", "open");
        when(mapper.findIdByNumber(12L)).thenReturn(ID);
        when(mapper.findProblem(ID)).thenReturn(row);
        when(mapper.fieldOrders(ID)).thenReturn(Collections.<Map<String, Object>>emptyList());
        when(mapper.callbackResults(ID)).thenReturn(Collections.<Map<String, Object>>emptyList());
        when(mapper.listActivities(ID)).thenReturn(Collections.<Map<String, Object>>emptyList());

        assertEquals(ID, service.detail("as-12").get("id"));
        assertEquals(ID, service.detail("12").get("id"));
        assertEquals(ID, service.detail(ID).get("id"));
        verify(mapper, never()).findIdByNumber(eq(0L));

        when(mapper.findIdByNumber(99L)).thenReturn(null);
        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> service.detail("AS-99"));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
    }

    private static LocalDateTime future() {
        return LocalDateTime.now(SHANGHAI).plusHours(2);
    }

    private static LocalDateTime past() {
        return LocalDateTime.now(SHANGHAI).minusMinutes(5);
    }
}
