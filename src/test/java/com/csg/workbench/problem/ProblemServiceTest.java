package com.csg.workbench.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.csg.workbench.account.AccountMapper;
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

    @Mock private ProblemMapper mapper;
    @Mock private DeviceService devices;
    @Mock private AccountMapper accounts;
    private ProblemService service;

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "operator.1", "unused", AuthorityUtils.createAuthorityList("ROLE_OPERATOR")));
        service = new ProblemService(mapper, devices, accounts);
        lenient().when(mapper.insertCommandIfAbsent(anyString(), anyString(), anyString())).thenReturn(1);
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void followupStoresCurrentLoginName() {
        when(mapper.lockProblem(ID)).thenReturn(row("open", 3, "operator.1"));
        when(mapper.changeState(eq(ID), eq("open"), eq(3))).thenReturn(1);
        service.recordFollowup(ID, " 已联系车主 ", 3, "followup-key1");
        verify(mapper).insertActivity(ID, "operator.1", "followup", "已联系车主");
    }

    @Test
    void resolvedRejectsFurtherWrites() {
        when(mapper.lockProblem(ID)).thenReturn(row("resolved", 4, "operator.1"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.recordFollowup(ID, "再写一条", 4, "followup-key2"));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("问题单已结案", error.getReason());
        verify(mapper, never()).insertActivity(any(), any(), any(), any());
    }

    @Test
    void staleVersionDoesNotOverwrite() {
        when(mapper.lockProblem(ID)).thenReturn(row("open", 5, "early"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.recordFollowup(ID, "旧版本", 4, "followup-key3"));
        assertEquals("工单已被他人更新，请刷新后重试", error.getReason());
        verify(mapper, never()).insertActivity(any(), any(), any(), any());
    }

    @Test
    void repeatedKeyDoesNotRepeatFollowup() {
        when(mapper.insertCommandIfAbsent(eq("followup-same"), anyString(), eq(ID))).thenReturn(0);
        when(mapper.commandDigest("followup-same")).thenReturn(ProblemService.digest("followup", ID, "同一条", "3"));
        service.recordFollowup(ID, "同一条", 3, "followup-same");
        verify(mapper, never()).insertActivity(any(), any(), any(), any());
    }

    @Test
    void fieldWithoutOrderCannotSkipDispatch() {
        when(mapper.lockProblem(ID)).thenReturn(row("field", 1, "operator.1"));
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.schedule(ID, "坐席", "确认恢复", future(), 1, "schedule-key1"));
        assertEquals("请先关联现场单，不能跳过派单阶段", error.getReason());
        verify(mapper, never()).changeState(any(), any(), anyInt());
    }

    @Test
    void claimSucceedsOnceForUnownedTicket() {
        when(mapper.lockProblem(ID)).thenReturn(row("open", 2, "early"), row("open", 3, "operator.1"));
        when(mapper.assignOwner(ID, "operator.1", 2)).thenReturn(1);
        service.claim(ID, 2, "claim-key-001");
        verify(mapper).insertOwnership(ID, "operator.1", "early", "operator.1", "claim", "");
        ResponseStatusException second = assertThrows(ResponseStatusException.class,
            () -> service.claim(ID, 2, "claim-key-002"));
        assertEquals("工单已被他人更新，请刷新后重试", second.getReason());
    }

    @Test
    void verifyCallbackReturnsToVerify() {
        when(mapper.lockProblem(ID)).thenReturn(row("verify", 1, "operator.1"), row("waiting", 2, "operator.1"));
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        when(mapper.changeState(eq(ID), eq("waiting"), eq(1))).thenReturn(1);
        when(mapper.changeState(eq(ID), eq("verify"), eq(2))).thenReturn(1);
        when(mapper.resumeState(ID)).thenReturn("verify");
        service.schedule(ID, "坐席", "确认是否恢复", future(), 1, "schedule-key2");
        verify(mapper).setResume(ID, "verify");
        service.finishCallback(ID, "continue", "仍需复测", past(), null, null, 2, "callback-key1");
        verify(mapper).changeState(ID, "verify", 2);
        verify(mapper).setResume(eq(ID), isNull());
    }

    @Test
    void withdrawPendingDispatchReturnsToTriage() {
        when(mapper.lockProblem(ID)).thenReturn(row("field", 4, "operator.1"));
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        when(mapper.changeState(ID, "triage", 4)).thenReturn(1);
        service.withdrawField(ID, " 客户改口，先远程排查 ", 4, "withdraw-key1");
        verify(mapper).insertActivity(eq(ID), eq("operator.1"), eq("field_withdraw"),
            eq("撤回待派单，返回排查：客户改口，先远程排查"));
        verify(mapper).changeState(ID, "triage", 4);
    }

    @Test
    void withdrawDoesNotBypassActiveFieldOrder() {
        when(mapper.lockProblem(ID)).thenReturn(row("field", 4, "operator.1"));
        when(mapper.activeFieldOrders(ID)).thenReturn(1);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.withdrawField(ID, "想跳过上门", 4, "withdraw-key2"));
        assertEquals("已有现场单在处理，请先登记结果，不能撤回绕过", error.getReason());
        verify(mapper, never()).changeState(anyString(), anyString(), anyInt());
    }

    @Test
    void withdrawRejectsBlankReasonAndClosedCase() {
        ResponseStatusException blank = assertThrows(ResponseStatusException.class,
            () -> service.withdrawField(ID, "  ", 4, "withdraw-key3"));
        assertEquals(HttpStatus.BAD_REQUEST, blank.getStatus());
        when(mapper.lockProblem(ID)).thenReturn(row("resolved", 4, "operator.1"));
        ResponseStatusException closed = assertThrows(ResponseStatusException.class,
            () -> service.withdrawField(ID, "结案后撤回", 4, "withdraw-key4"));
        assertEquals("问题单已结案", closed.getReason());
    }

    @Test
    void boardRejectsUnknownFiltersBeforeQuery() {
        ResponseStatusException state = assertThrows(ResponseStatusException.class,
            () -> service.board("nope", "all", null, null, null, null, 20, null));
        assertEquals(HttpStatus.BAD_REQUEST, state.getStatus());
        assertEquals("问题单状态无效", state.getReason());
        ResponseStatusException bucket = assertThrows(ResponseStatusException.class,
            () -> service.board(null, "all", null, null, null, null, 20, "drop"));
        assertEquals("问题单状态无效", bucket.getReason());
        ResponseStatusException owner = assertThrows(ResponseStatusException.class,
            () -> service.board(null, "bad owner", null, null, null, null, 20, "open"));
        assertEquals("负责人筛选无效", owner.getReason());
        verify(mapper, never()).boardPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
        verify(mapper, never()).boardSummary();
    }

    @Test
    void boardSummaryUsesSameBucketCountsAsTheListTotal() {
        when(mapper.boardPage(nullable(String.class), nullable(String.class), nullable(String.class),
            nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
            nullable(Long.class), nullable(java.time.LocalDateTime.class), nullable(String.class), anyInt()))
            .thenReturn(Collections.<Map<String, Object>>emptyList());
        when(mapper.boardCount(nullable(String.class), nullable(String.class), nullable(String.class),
            nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
            nullable(Long.class))).thenReturn(6);
        when(mapper.categories()).thenReturn(Collections.<String>emptyList());
        Map<String, Object> counts = new LinkedHashMap<String, Object>();
        counts.put("total", Integer.valueOf(6));
        counts.put("unclaimed", Integer.valueOf(1));
        counts.put("openCount", Integer.valueOf(2));
        counts.put("triageCount", Integer.valueOf(0));
        counts.put("waitingCount", Integer.valueOf(0));
        counts.put("fieldPending", Integer.valueOf(1));
        counts.put("fieldActive", Integer.valueOf(1));
        counts.put("verifyCount", Integer.valueOf(0));
        counts.put("resolvedCount", Integer.valueOf(1));
        counts.put("answeredCount", Integer.valueOf(0));
        counts.put("referredCount", Integer.valueOf(0));
        when(mapper.boardSummary()).thenReturn(counts);
        when(mapper.dueCount(any(), eq(0), eq("operator.1"))).thenReturn(4);
        when(mapper.dueCount(any(), eq(1), eq("operator.1"))).thenReturn(1);
        when(mapper.mineCount("operator.1")).thenReturn(3);

        Map<String, Object> body = service.board(null, null, null, null, null, null, 20, "fieldPending");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) body.get("summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> states = (Map<String, Object>) summary.get("states");
        int summed = 0;
        for (Object value : states.values()) {
            summed += ((Number) value).intValue();
        }
        assertEquals(Integer.valueOf(6), summary.get("total"));
        assertEquals(6, summed);
        assertEquals(Integer.valueOf(1), states.get("unclaimed"));
        assertEquals(Integer.valueOf(1), states.get("fieldPending"));
        assertEquals(Integer.valueOf(1), states.get("fieldActive"));
        assertEquals(Integer.valueOf(4), summary.get("due"));
        assertEquals(Integer.valueOf(1), summary.get("dueMine"));
        assertEquals(Integer.valueOf(3), summary.get("mine"));
        assertEquals(Integer.valueOf(6), body.get("total"));
        verify(mapper).boardPage(isNull(), eq("all"), isNull(), isNull(), isNull(), eq("fieldPending"),
            isNull(), isNull(), isNull(), isNull(), eq(21));
    }

    @Test
    void shortNumberAndLikeStayLiteral() {
        assertEquals(Long.valueOf(12L), ProblemService.shortNumber("as-12"));
        assertEquals(Long.valueOf(12L), ProblemService.shortNumber("12"));
        assertEquals(null, ProblemService.shortNumber("SIM-AC-001"));
        assertEquals("%100\\%%", ProblemService.likePattern("100%"));
        assertEquals("%a\\_b%", ProblemService.likePattern("a_b"));
    }

    @Test
    void claimedTicketRejectsOtherOperatorAndKeepsWritesOff() {
        loginAs("operator.2", false);
        when(mapper.lockProblem(ID)).thenReturn(row("open", 3, "operator.1"));
        ResponseStatusException followup = assertThrows(ResponseStatusException.class,
            () -> service.recordFollowup(ID, "越权跟进", 3, "deny-followup"));
        assertEquals(HttpStatus.FORBIDDEN, followup.getStatus());
        assertEquals(ProblemService.HANDLER_REQUIRED, followup.getReason());
        ResponseStatusException schedule = assertThrows(ResponseStatusException.class,
            () -> service.schedule(ID, "坐席", "确认恢复", future(), 3, "deny-schedule"));
        assertEquals(ProblemService.HANDLER_REQUIRED, schedule.getReason());
        ResponseStatusException callback = assertThrows(ResponseStatusException.class,
            () -> service.finishCallback(ID, "resolved", "想直接结案", past(), "customer", "依据", 3, "deny-callback"));
        assertEquals(ProblemService.HANDLER_REQUIRED, callback.getReason());
        ResponseStatusException handoff = assertThrows(ResponseStatusException.class,
            () -> service.handoff(ID, "operator.2", "接手", 3, "deny-handoff"));
        assertEquals(ProblemService.HANDLER_REQUIRED, handoff.getReason());
        when(mapper.lockProblem(ID)).thenReturn(row("field", 4, "operator.1"));
        ResponseStatusException withdraw = assertThrows(ResponseStatusException.class,
            () -> service.withdrawField(ID, "想撤回", 4, "deny-withdraw"));
        assertEquals(ProblemService.HANDLER_REQUIRED, withdraw.getReason());
        ResponseStatusException link = assertThrows(ResponseStatusException.class,
            () -> service.linkFieldOrder(ID, "SIM-DENY", 4, "deny-link"));
        assertEquals(ProblemService.HANDLER_REQUIRED, link.getReason());
        ResponseStatusException receipt = assertThrows(ResponseStatusException.class,
            () -> service.recordFieldResult(ID, "SIM-DENY", "complete", "想完工", 4, "deny-receipt"));
        assertEquals(ProblemService.HANDLER_REQUIRED, receipt.getReason());
        verify(mapper, never()).insertActivity(any(), any(), any(), any());
        verify(mapper, never()).changeState(anyString(), anyString(), anyInt());
        verify(mapper, never()).schedule(anyString(), anyString(), anyString(), any());
        verify(mapper, never()).insertCallback(anyString(), anyString(), any(), anyString(), anyString(), any(), any());
        verify(mapper, never()).verify(anyString(), anyString(), anyString(), anyString());
        verify(mapper, never()).linkFieldOrder(anyString(), anyString());
        verify(mapper, never()).updateFieldOrder(anyString(), anyString(), anyString(), anyString());
        verify(mapper, never()).assignOwner(anyString(), anyString(), anyInt());
        verify(mapper, never()).setResume(anyString(), any());
        verify(mapper, never()).completeFollowup(anyString());
    }

    @Test
    void adminCanHandleTicketOwnedByAnotherSeat() {
        loginAs("admin.1", true);
        when(mapper.lockProblem(ID)).thenReturn(row("open", 6, "operator.1"));
        when(mapper.changeState(ID, "open", 6)).thenReturn(1);
        service.recordFollowup(ID, "管理员补充", 6, "admin-followup");
        verify(mapper).insertActivity(ID, "admin.1", "followup", "管理员补充");
    }

    @Test
    void unownedTicketRequiresClaimBeforeOperatorWrites() {
        when(mapper.lockProblem(ID)).thenReturn(row("open", 2, "early"), row("open", 2, ""));
        ResponseStatusException early = assertThrows(ResponseStatusException.class,
            () -> service.recordFollowup(ID, "未认领", 2, "early-followup"));
        assertEquals(HttpStatus.CONFLICT, early.getStatus());
        assertEquals(ProblemService.CLAIM_FIRST, early.getReason());
        ResponseStatusException blank = assertThrows(ResponseStatusException.class,
            () -> service.schedule(ID, "坐席", "先认领", future(), 2, "blank-schedule"));
        assertEquals(ProblemService.CLAIM_FIRST, blank.getReason());
        verify(mapper, never()).insertActivity(any(), any(), any(), any());
        verify(mapper, never()).schedule(anyString(), anyString(), anyString(), any());
    }

    @Test
    void adminCanWriteUnownedTicketAndHandoffStillRequiresClaim() {
        loginAs("admin.1", true);
        when(mapper.lockProblem(ID)).thenReturn(row("open", 2, "early"));
        when(mapper.changeState(ID, "open", 2)).thenReturn(1);
        service.recordFollowup(ID, "管理员先记一笔", 2, "admin-early-followup");
        verify(mapper).insertActivity(ID, "admin.1", "followup", "管理员先记一笔");
        ResponseStatusException handoff = assertThrows(ResponseStatusException.class,
            () -> service.handoff(ID, "operator.1", "还没人认领", 2, "admin-early-handoff"));
        assertEquals(HttpStatus.CONFLICT, handoff.getStatus());
        assertEquals(ProblemService.CLAIM_FIRST, handoff.getReason());
        verify(mapper, never()).assignOwner(anyString(), anyString(), anyInt());
    }

    @Test
    void blankOwnerCanStillBeClaimedOnce() {
        when(mapper.lockProblem(ID)).thenReturn(row("open", 1, " "), row("open", 2, "operator.1"));
        when(mapper.assignOwner(ID, "operator.1", 1)).thenReturn(1);
        service.claim(ID, 1, "claim-blank-owner");
        verify(mapper).insertOwnership(ID, "operator.1", " ", "operator.1", "claim", "");
        ResponseStatusException second = assertThrows(ResponseStatusException.class,
            () -> service.claim(ID, 1, "claim-blank-owner-2"));
        assertEquals(HttpStatus.CONFLICT, second.getStatus());
    }

    @Test
    void v11AssessmentEnforcesCGradeAndEvidenceGateForReplace() {
        ResponseStatusException cReplace = assertThrows(ResponseStatusException.class,
            () -> service.assess(ID, "C", "replace", "尝试C级换装", 2, "assess-c-replace"));
        assertEquals(HttpStatus.BAD_REQUEST, cReplace.getStatus());
        when(mapper.lockProblem(ID)).thenReturn(row("open", 2, "operator.1"));
        when(mapper.countEvidences(ID)).thenReturn(0, 1);
        ResponseStatusException noEvidence = assertThrows(ResponseStatusException.class,
            () -> service.assess(ID, "A", "replace", "无证据换装", 2, "assess-no-ev"));
        assertEquals(HttpStatus.CONFLICT, noEvidence.getStatus());
        when(mapper.updateAssessment(eq(ID), eq("A"), eq("replace"), anyString(), eq(2))).thenReturn(1);
        service.assess(ID, "A", "replace", "有日志佐证", 2, "assess-ok-ev");
        verify(mapper).insertActivity(eq(ID), eq("operator.1"), eq("assessment"), anyString());
    }

    @Test
    void v11SuspendQuickCloseAndEscalateRespectBoundaries() {
        when(mapper.lockProblem(ID)).thenReturn(row("open", 3, "operator.1"));
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        when(mapper.suspendProblem(ID, "customer_reboot", "early", 3)).thenReturn(1);
        service.suspend(ID, "customer_reboot", true, "客户断电后观察", null, 3, "suspend-key-1");
        verify(mapper).insertOwnership(ID, "operator.1", "operator.1", "early", "handoff", "挂起回池：等待客户断电重启");

        when(mapper.lockProblem(ID)).thenReturn(row("open", 4, "operator.1"));
        when(mapper.disposalType(ID)).thenReturn("replace", "remote");
        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
            () -> service.quickClose(ID, "想快结", "platform", "平台记录", 4, "qc-key-1"));
        assertEquals(HttpStatus.CONFLICT, blocked.getStatus());
        when(mapper.changeState(ID, "resolved", 4)).thenReturn(1);
        service.quickClose(ID, "远程重启恢复", "platform", "平台已恢复正常充电", 4, "qc-key-2");
        verify(mapper).verify(ID, "operator.1", "platform", "平台已恢复正常充电");

        when(mapper.lockProblem(ID)).thenReturn(row("open", 5, "operator.1"));
        when(mapper.updateEscalation(ID, "l2", "客户不配合排查", 1, 5)).thenReturn(1);
        service.escalate(ID, "l2", "客户不配合排查", true, 5, "esc-key-1");
        verify(mapper).insertActivity(eq(ID), eq("operator.1"), eq("escalate"), anyString());
    }

    @Test
    void dueAndMineRejectMalformedCursorWithBadRequest() {
        ResponseStatusException badDueTime = assertThrows(ResponseStatusException.class,
            () -> service.due("team", "not-a-time|12", 20));
        assertEquals(HttpStatus.BAD_REQUEST, badDueTime.getStatus());
        assertEquals("分页参数无效", badDueTime.getReason());
        ResponseStatusException badDueId = assertThrows(ResponseStatusException.class,
            () -> service.due("team", "2026-11-25T10:00:00|abc", 20));
        assertEquals(HttpStatus.BAD_REQUEST, badDueId.getStatus());
        assertEquals("分页参数无效", badDueId.getReason());
        ResponseStatusException badMine = assertThrows(ResponseStatusException.class,
            () -> service.mine("not-a-time|id-1", 20));
        assertEquals(HttpStatus.BAD_REQUEST, badMine.getStatus());
        assertEquals("分页参数无效", badMine.getReason());
    }

    @Test
    void suspendWithReturnToPoolResetsOwnerAndLogsOwnership() {
        when(mapper.lockProblem(ID)).thenReturn(row("open", 3, "operator.1"));
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        when(mapper.suspendProblem(ID, "customer_reboot", "early", 3)).thenReturn(1);
        service.suspend(ID, "customer_reboot", true, "等待重启结果", future(), 3, "suspend-key1");
        verify(mapper).insertOwnership(ID, "operator.1", "operator.1", "early", "handoff", "挂起回池：等待客户断电重启");
        verify(mapper).insertActivity(eq(ID), eq("operator.1"), eq("suspend"), eq("挂起（等待客户断电重启），已回池共享：等待重启结果"));
    }

    @Test
    void quickCloseRejectsReplaceDisposalAndAllowsRemote() {
        when(mapper.lockProblem(ID)).thenReturn(row("open", 2, "operator.1"), row("open", 3, "operator.1"));
        when(mapper.activeFieldOrders(ID)).thenReturn(0);
        when(mapper.disposalType(ID)).thenReturn("replace", "remote");
        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
            () -> service.quickClose(ID, "远程解决", "platform", "平台正常", 2, "quick-close-1"));
        assertEquals(HttpStatus.CONFLICT, blocked.getStatus());
        when(mapper.changeState(ID, "resolved", 3)).thenReturn(1);
        service.quickClose(ID, "远程重启恢复", "platform", "平台显示已正常充电", 3, "quick-close-2");
        verify(mapper).verify(ID, "operator.1", "platform", "平台显示已正常充电");
        verify(mapper).changeState(ID, "resolved", 3);
    }

    @Test
    void assessmentBlocksGradeCReplaceAndRequiresEvidenceForReplace() {
        ResponseStatusException gradeC = assertThrows(ResponseStatusException.class,
            () -> service.assess(ID, "C", "replace", "尝试换装", 1, "assess-key1"));
        assertEquals(HttpStatus.BAD_REQUEST, gradeC.getStatus());
        when(mapper.lockProblem(ID)).thenReturn(row("open", 2, "operator.1"), row("open", 3, "operator.1"));
        when(mapper.countEvidences(ID)).thenReturn(0, 1);
        ResponseStatusException noEvidence = assertThrows(ResponseStatusException.class,
            () -> service.assess(ID, "A", "replace", "无证据换装", 2, "assess-key2"));
        assertEquals(HttpStatus.CONFLICT, noEvidence.getStatus());
        when(mapper.updateAssessment(eq(ID), eq("A"), eq("replace"), anyString(), eq(3))).thenReturn(1);
        service.assess(ID, "A", "replace", "证据齐全", 3, "assess-key3");
        verify(mapper).insertActivity(eq(ID), eq("operator.1"), eq("assessment"), anyString());
    }

    @Test
    void shortNumberAndUuidBothResolve() {
        Map<String, Object> found = new LinkedHashMap<String, Object>();
        found.put("id", ID);
        found.put("state", "open");
        when(mapper.findIdByNumber(12L)).thenReturn(ID);
        when(mapper.findProblem(ID)).thenReturn(found);
        when(mapper.fieldOrdersDetailed(ID)).thenReturn(Collections.<Map<String, Object>>emptyList());
        when(mapper.callbackResults(ID)).thenReturn(Collections.<Map<String, Object>>emptyList());
        when(mapper.listActivities(ID)).thenReturn(Collections.<Map<String, Object>>emptyList());
        when(mapper.ownershipEvents(ID)).thenReturn(Collections.<Map<String, Object>>emptyList());
        assertEquals(ID, service.detail("as-12").get("id"));
        assertEquals(ID, service.detail(ID).get("id"));
    }

    private void loginAs(String name, boolean admin) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            name, "unused", AuthorityUtils.createAuthorityList(admin ? "ROLE_ADMIN" : "ROLE_OPERATOR")));
    }

    private static Map<String, Object> row(String state, int version, String owner) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("state", state);
        row.put("version", Integer.valueOf(version));
        row.put("owner", owner);
        return row;
    }

    private static LocalDateTime future() {
        return LocalDateTime.now(SHANGHAI).plusHours(2);
    }

    private static LocalDateTime past() {
        return LocalDateTime.now(SHANGHAI).minusMinutes(5);
    }
}
