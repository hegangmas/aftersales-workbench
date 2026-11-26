package com.csg.workbench.problem;

import com.csg.workbench.account.AccountMapper;
import com.csg.workbench.account.AccountService;
import com.csg.workbench.device.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * V1.1 严格一期核心流转服务：
 * - GD+YYYYMMDD+4位流水发号
 * - 严格 6+1 状态机流转校验
 * - 设备待补例外队列（未补齐前禁派单、禁换装）
 * - 图 8 智享乐装派单与模拟结构化回传（含回退异常留痕）
 * - 旧件未登记禁止闭环、已闭环重开新单、调度一键均分、4.11 月度指标
 */
@Service
public class ProblemV11Service {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter BIZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String VERSION_CONFLICT = "工单已被他人更新，请刷新后重试";
    private static final Set<String> CHANNELS = new HashSet<String>(Arrays.asList("400", "miniprogram", "provider", "wecom", "phone"));
    private static final Set<String> URGENCIES = new HashSet<String>(Arrays.asList("low", "normal", "high"));
    private static final Set<String> SUSPEND_REASONS = new HashSet<String>(Arrays.asList(
        "customer_reboot", "customer_evidence", "parts_wait", "field_wait"));
    private static final Set<String> GRADES = new HashSet<String>(Arrays.asList("A", "B", "C"));
    private static final Set<String> DISPOSALS = new HashSet<String>(Arrays.asList("remote", "part", "replace", "onsite"));
    private static final Set<String> ESCALATION_LEVELS = new HashSet<String>(Arrays.asList("none", "l2", "expert"));
    private static final Set<String> EVIDENCE_TYPES = new HashSet<String>(Arrays.asList(
        "photo", "video", "timestamp_log", "site_compare", "swap_test", "platform_data"));
    private static final Set<String> EVIDENCE_SOURCES = new HashSet<String>(Arrays.asList(
        "agent", "sms", "miniprogram", "field"));
    private static final Map<String, Integer> FIELD_ORDER_RANK = buildFieldRank();

    private final ProblemMapper mapper;
    private final DeviceService devices;
    private final AccountMapper accounts;
    private final ObjectMapper json;

    public ProblemV11Service(ProblemMapper mapper, DeviceService devices, AccountMapper accounts, ObjectMapper json) {
        this.mapper = mapper;
        this.devices = devices;
        this.accounts = accounts;
        this.json = json;
    }

    private static Map<String, Integer> buildFieldRank() {
        Map<String, Integer> r = new HashMap<String, Integer>();
        r.put("dispatched", Integer.valueOf(1));
        r.put("accepted", Integer.valueOf(2));
        r.put("arrived", Integer.valueOf(3));
        r.put("completed", Integer.valueOf(4));
        return r;
    }

    @Transactional
    public Map<String, String> createV11(String device, boolean devicePending, String title, String description,
                                         String channel, String category, String urgency, LocalDateTime faultOccurredAt,
                                         String requestId) {
        if (faultOccurredAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "故障发生时点为必填（精确到分钟）");
        }
        if (faultOccurredAt.isAfter(LocalDateTime.now(BUSINESS_ZONE).plusMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "故障发生时点不能晚于当前时间");
        }
        String ch = (channel == null || channel.trim().isEmpty()) ? "400" : channel.trim();
        if (!CHANNELS.contains(ch)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报修渠道无效");
        }
        String urg = (urgency == null || urgency.trim().isEmpty()) ? "normal" : urgency.trim();
        if (!URGENCIES.contains(urg)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "紧急度无效");
        }
        String cat = (category == null || category.trim().isEmpty()) ? "无法充电" : category.trim();
        String sn = (device == null || device.trim().isEmpty()) ? null : device.trim();
        if (!devicePending && sn == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在设备页检索设备，或勾选「设备信息待补」进入待补队列");
        }
        String digest = ProblemService.digest("v11-create", sn, String.valueOf(devicePending), title, description, ch, cat, urg, String.valueOf(faultOccurredAt));
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供幂等键");
        }
        String existing = mapper.commandProblem(requestId);
        if (existing != null) {
            Map<String, Object> old = mapper.findProblem(existing);
            Map<String, String> out = new LinkedHashMap<String, String>();
            out.put("id", existing);
            out.put("ticketNo", old == null || old.get("ticketNo") == null ? "" : String.valueOf(old.get("ticketNo")));
            return out;
        }

        String customerId = null;
        String snapshot = null;
        int slaMinutes = 120;
        if (!devicePending && sn != null) {
            Map<String, Object> profile = devices.findBySn(sn);
            if (profile == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到该桩SN档案，请先核对设备或选择设备信息待补");
            }
            customerId = profile.get("customerId") == null ? null : String.valueOf(profile.get("customerId"));
            if (profile.get("slaMinutes") instanceof Number) {
                slaMinutes = ((Number) profile.get("slaMinutes")).intValue();
            }
            snapshot = toJson(profile);
        }

        String id = UUID.randomUUID().toString();
        String ticketNo = nextTicketNo();
        LocalDateTime slaDue = LocalDateTime.now(BUSINESS_ZONE).plusMinutes(slaMinutes);
        mapper.insertProblemV11(id, ticketNo, title.trim(), cat, ch, description.trim(),
            ProblemStateMachine.PENDING_ASSIGN, "early", sn, customerId, devicePending ? 1 : 0,
            urg, actor(), faultOccurredAt, slaDue, snapshot, null);
        mapper.insertTransition(id, "new", ProblemStateMachine.PENDING_ASSIGN, actor(), "create",
            devicePending ? "设备信息待补建单入待补队列" : "受理建单并固化弹屏快照");
        mapper.insertActivity(id, actor(), "create",
            "受理建单 " + ticketNo + (devicePending ? "（设备信息待补）" : "（桩SN：" + sn + "）"));
        mapper.insertCommandIfAbsent(requestId, digest, id);

        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("id", id);
        out.put("ticketNo", ticketNo);
        return out;
    }

    @Transactional
    public void bindDevice(String id, String sn, int expectedVersion, String requestId) {
        if (replay(requestId, ProblemService.digest("v11-bind", id, sn, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        Map<String, Object> profile = devices.findBySn(sn);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该桩SN档案");
        }
        String customerId = profile.get("customerId") == null ? null : String.valueOf(profile.get("customerId"));
        if (mapper.bindDevice(id, sn.trim(), customerId, toJson(profile), expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已被他人更新，请刷新后重试");
        }
        mapper.insertActivity(id, actor(), "bind_device", "补齐设备档案：" + sn.trim() + "，已解除设备待补限制");
    }

    @Transactional
    public void acceptOrAssign(String id, String targetOwner, int expectedVersion, String requestId) {
        String assignee = (targetOwner == null || targetOwner.trim().isEmpty()) ? actor() : targetOwner.trim();
        if (replay(requestId, ProblemService.digest("v11-assign", id, assignee, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        String from = String.valueOf(row.get("state"));
        ProblemStateMachine.requireTransition(from, ProblemStateMachine.PROCESSING);
        if (!assignee.equals(actor()) && !hasAnyRole("ROLE_ADMIN", "ROLE_DISPATCHER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有调度或管理员可指派他人");
        }
        if (mapper.assignOwnerAndState(id, assignee, ProblemStateMachine.PROCESSING, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已被他人更新，请刷新后重试");
        }
        mapper.insertTransition(id, from, ProblemStateMachine.PROCESSING, actor(), "assign", "承接/分配给 " + assignee);
        mapper.insertOwnership(id, actor(), String.valueOf(row.get("owner")), assignee, "claim", "V1.1 分配承接");
        mapper.insertActivity(id, actor(), "assign", "分配给 " + assignee + "，进入处理中");
    }

    @Transactional
    public Map<String, String> dispatchToLezhuang(String id, boolean isClaim, boolean returnToFactory,
                                                  String projectCode, LocalDateTime appointmentAt,
                                                  String photoNames, int expectedVersion, String requestId) {
        if (replay(requestId, ProblemService.digest("v11-dispatch", id, String.valueOf(isClaim),
            String.valueOf(returnToFactory), projectCode, String.valueOf(appointmentAt), photoNames,
            String.valueOf(expectedVersion)), id)) {
            return CollectionsMap("status", "replayed");
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        Map<String, Object> full = mapper.findProblem(id);
        if (isFlag(full.get("devicePending"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "设备信息待补，未补齐前不得派单至智享乐装");
        }
        String from = String.valueOf(row.get("state"));
        ProblemStateMachine.requireTransition(from, ProblemStateMachine.PENDING_CALLBACK);
        if (projectCode == null || projectCode.trim().isEmpty() || appointmentAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请补全项目编号和预约上门时间后再派单");
        }
        String externalNo = "LZ-" + LocalDateTime.now(BUSINESS_ZONE).format(BIZ_DATE) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        mapper.insertDispatchOrder(id, externalNo, isClaim ? 1 : 0, returnToFactory ? 1 : 0,
            projectCode.trim(), appointmentAt, photoNames == null ? "" : photoNames.trim());
        if (mapper.changeState(id, ProblemStateMachine.PENDING_CALLBACK, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已被他人更新，请刷新后重试");
        }
        mapper.insertTransition(id, from, ProblemStateMachine.PENDING_CALLBACK, actor(), "dispatch", "派单至智享乐装 " + externalNo);
        mapper.insertActivity(id, actor(), "dispatch",
            "派单至智享乐装（单号：" + externalNo + "，项目：" + projectCode.trim() + "，索赔：" + (isClaim ? "是" : "否") + "，返厂：" + (returnToFactory ? "是" : "否") + "）");
        return CollectionsMap("externalNo", externalNo);
    }

    @Transactional
    public void handleLezhuangCallback(String id, String externalNo, String eventState, String receipt,
                                       String replacedParts, String oldPartNo, String oldPartPhoto,
                                       String customerAcceptResult, String feeOwner, int expectedVersion, String requestId) {
        if (replay(requestId, ProblemService.digest("v11-lz-cb", id, externalNo, eventState, receipt,
            replacedParts, oldPartNo, customerAcceptResult, feeOwner, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        Map<String, Object> existingOrder = mapper.findFieldOrder(id, externalNo);
        if (existingOrder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到对应的智享乐装派单记录");
        }
        String curOrderState = String.valueOf(existingOrder.get("state"));
        Integer curRank = FIELD_ORDER_RANK.get(curOrderState);
        Integer nextRank = FIELD_ORDER_RANK.get(eventState);
        if (curRank != null && nextRank != null && nextRank.intValue() < curRank.intValue()) {
            String anomaly = "检测到状态回退回调（" + curOrderState + " -> " + eventState + "），已按异常留痕并转人工确认";
            mapper.recordFieldRollbackAnomaly(id, externalNo, anomaly);
            mapper.insertActivity(id, actor(), "callback_anomaly", anomaly);
            return;
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        mapper.updateFieldOrderCallback(id, externalNo, eventState, receipt == null ? "" : receipt.trim(),
            "accepted".equals(eventState) ? now : null,
            "arrived".equals(eventState) ? now : null,
            "completed".equals(eventState) ? now : null,
            replacedParts, oldPartNo, oldPartPhoto, customerAcceptResult, feeOwner);
        if (oldPartNo != null && !oldPartNo.trim().isEmpty()) {
            mapper.markOldPartRegistered(id, oldPartNo.trim());
        }
        String from = String.valueOf(row.get("state"));
        String nextState = "completed".equals(eventState) ? ProblemStateMachine.PENDING_ACCEPT : from;
        if (!nextState.equals(from)) {
            ProblemStateMachine.requireTransition(from, nextState);
        }
        if (mapper.changeState(id, nextState, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已被他人更新，请刷新后重试");
        }
        if (!nextState.equals(from)) {
            mapper.insertTransition(id, from, nextState, actor(), "lezhuang_callback", "乐装完工回传进入待验收");
        }
        mapper.insertActivity(id, actor(), "lezhuang_callback",
            "智享乐装回传（" + externalNo + " · " + eventState + "）：" + (receipt == null ? "" : receipt.trim()));
    }

    @Transactional
    public void acceptOrReject(String id, boolean approved, String note, String oldPartNo,
                               int expectedVersion, String requestId) {
        if (replay(requestId, ProblemService.digest("v11-accept", id, String.valueOf(approved), note, oldPartNo,
            String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        String from = String.valueOf(row.get("state"));
        String target = approved ? ProblemStateMachine.CLOSED : ProblemStateMachine.PROCESSING;
        ProblemStateMachine.requireTransition(from, target);

        Map<String, Object> full = mapper.findProblem(id);
        boolean hasOldPart = isFlag(full.get("oldPartRegistered")) || (oldPartNo != null && !oldPartNo.trim().isEmpty());
        String disposal = full.get("disposalType") == null ? "" : String.valueOf(full.get("disposalType"));
        if (approved && ("replace".equals(disposal) || "part".equals(disposal)) && !hasOldPart) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "换装或换件工单旧件未登记，不得闭环");
        }
        String finalOldPart = (oldPartNo != null && !oldPartNo.trim().isEmpty())
            ? oldPartNo.trim()
            : (full.get("oldPartNo") == null ? null : String.valueOf(full.get("oldPartNo")));
        if (mapper.closeWithOldPart(id, target, hasOldPart ? 1 : 0, finalOldPart, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已被他人更新，请刷新后重试");
        }
        mapper.insertTransition(id, from, target, actor(), approved ? "accept_close" : "accept_reject",
            note == null ? "" : note.trim());
        mapper.insertActivity(id, actor(), approved ? "close" : "reject",
            (approved ? "验收通过并闭环：" : "验收驳回退回处理中：") + (note == null ? "" : note.trim()));
    }

    @Transactional
    public void cancel(String id, String reason, int expectedVersion, String requestId) {
        if (replay(requestId, ProblemService.digest("v11-cancel", id, reason, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        String from = String.valueOf(row.get("state"));
        ProblemStateMachine.requireTransition(from, ProblemStateMachine.CANCELLED);
        if (mapper.changeState(id, ProblemStateMachine.CANCELLED, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已被他人更新，请刷新后重试");
        }
        mapper.insertTransition(id, from, ProblemStateMachine.CANCELLED, actor(), "cancel", reason.trim());
        mapper.insertActivity(id, actor(), "cancel", "取消工单：" + reason.trim());
    }

    @Transactional
    public Map<String, String> reopen(String id, String reason, String requestId) {
        Map<String, Object> old = mapper.findProblem(id);
        if (old == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原工单不存在");
        }
        if (!ProblemStateMachine.CLOSED.equals(old.get("state")) && !"resolved".equals(old.get("state"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅已闭环工单支持重开新单");
        }
        String newId = UUID.randomUUID().toString();
        String ticketNo = nextTicketNo();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        mapper.insertProblemV11(newId, ticketNo, "[重开] " + String.valueOf(old.get("title")),
            String.valueOf(old.get("category")), String.valueOf(old.get("channel")),
            String.valueOf(old.get("description")) + "\n重开原因：" + reason.trim(),
            ProblemStateMachine.PENDING_ASSIGN, "early",
            old.get("device") == null ? null : String.valueOf(old.get("device")),
            old.get("customerId") == null ? null : String.valueOf(old.get("customerId")),
            isFlag(old.get("devicePending")) ? 1 : 0,
            old.get("urgency") == null ? "normal" : String.valueOf(old.get("urgency")),
            actor(), now, now.plusHours(2),
            old.get("snapshotJson") == null ? null : String.valueOf(old.get("snapshotJson")), id);
        mapper.insertTransition(newId, "new", ProblemStateMachine.PENDING_ASSIGN, actor(), "reopen", "由 " + old.get("ticketNo") + " 重开");
        mapper.insertActivity(newId, actor(), "reopen", "由已闭环工单重开新单：" + reason.trim());
        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("id", newId);
        out.put("ticketNo", ticketNo);
        return out;
    }

    @Transactional
    public Map<String, Object> forceDistribute() {
        if (!hasAnyRole("ROLE_ADMIN", "ROLE_DISPATCHER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有调度或管理员可执行一键强制平均分配");
        }
        List<Map<String, Object>> ready = accounts.readyAgents();
        if (ready == null || ready.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前没有处于就绪状态的在岗人员");
        }
        List<Map<String, Object>> pending = mapper.listPendingAssign();
        int count = 0;
        for (int i = 0; i < pending.size(); i++) {
            Map<String, Object> item = pending.get(i);
            String ticketId = String.valueOf(item.get("id"));
            int ver = ((Number) item.get("version")).intValue();
            String assignee = String.valueOf(ready.get(i % ready.size()).get("username"));
            if (mapper.assignOwnerAndState(ticketId, assignee, ProblemStateMachine.PROCESSING, ver) == 1) {
                mapper.insertTransition(ticketId, ProblemStateMachine.PENDING_ASSIGN, ProblemStateMachine.PROCESSING,
                    actor(), "force_distribute", "调度一键均分给 " + assignee);
                mapper.insertActivity(ticketId, actor(), "force_distribute", "调度一键均分给 " + assignee);
                count++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("distributed", Integer.valueOf(count));
        out.put("readyAgents", Integer.valueOf(ready.size()));
        return out;
    }

    @Transactional
    public void addFollowupV11(String id, String note, int expectedVersion, String requestId) {
        String cleanNote = blankToNull(note);
        if (cleanNote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写跟进记录");
        }
        if (replay(requestId, ProblemService.digest("v11-followup", id, cleanNote, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        requireProcessingActor(row);
        String state = String.valueOf(row.get("state"));
        if (mapper.changeState(id, state, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        mapper.insertActivity(id, actor(), "followup", cleanNote);
    }

    @Transactional
    public void handoffV11(String id, String target, String reason, int expectedVersion, String requestId) {
        if (replay(requestId, ProblemService.digest("v11-handoff", id, target, reason, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        String currentOwner = ownerOf(row);
        if (isUnowned(currentOwner)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ProblemService.CLAIM_FIRST);
        }
        requireProcessingActor(row);
        if (target == null || target.equals(currentOwner)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择另一名启用账号");
        }
        Map<String, Object> assignee = accounts.find(target);
        if (assignee == null || !AccountService.flag(assignee.get("enabled"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "接收账号不存在或已禁用");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写转交原因");
        }
        if (mapper.assignOwner(id, target, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        mapper.insertOwnership(id, actor(), currentOwner, target, "handoff", why);
        mapper.insertActivity(id, actor(), "handoff", "由 " + currentOwner + " 交给 " + target + "：" + why);
    }

    @Transactional
    public void suspendV11(String id, String reason, boolean returnToPool, String nextStep, LocalDateTime dueAt,
                           int expectedVersion, String requestId) {
        if (!SUSPEND_REASONS.contains(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "挂起原因无效");
        }
        if (replay(requestId, ProblemService.digest("v11-suspend", id, reason, String.valueOf(returnToPool), nextStep,
            String.valueOf(dueAt), String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        requireProcessingActor(row);
        if (dueAt != null && !dueAt.isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择未来的回访时间");
        }
        String from = String.valueOf(row.get("state"));
        ProblemStateMachine.requireTransition(from, ProblemStateMachine.SUSPENDED);
        String currentOwner = ownerOf(row);
        String nextOwner = returnToPool ? "early" : currentOwner;
        if (mapper.suspendProblem(id, reason, nextOwner, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        if (dueAt != null) {
            mapper.completeFollowup(id);
            String stepText = blankToNull(nextStep) == null ? suspendLabel(reason) : nextStep.trim();
            mapper.schedule(id, returnToPool ? "共享池" : actor(), stepText, dueAt);
        }
        if (returnToPool && !isUnowned(currentOwner)) {
            mapper.insertOwnership(id, actor(), currentOwner, "early", "handoff", "挂起回池：" + suspendLabel(reason));
        }
        String note = "挂起（" + suspendLabel(reason) + "）" + (returnToPool ? "，已回池共享" : "");
        if (blankToNull(nextStep) != null) {
            note = note + "：" + nextStep.trim();
        }
        mapper.insertTransition(id, from, ProblemStateMachine.SUSPENDED, actor(), "suspend", note);
        mapper.insertActivity(id, actor(), "suspend", note);
    }

    @Transactional
    public void quickCloseV11(String id, String note, String evidenceSource, String evidence,
                              int expectedVersion, String requestId) {
        String source = blankToNull(evidenceSource);
        if (source == null) {
            source = "platform";
        }
        if (!("customer".equals(source) || "platform".equals(source) || "field".equals(source))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "核验来源无效");
        }
        String cleanNote = blankToNull(note);
        if (cleanNote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写完结说明");
        }
        String cleanEvidence = blankToNull(evidence);
        if (cleanEvidence == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写完结依据");
        }
        if (replay(requestId, ProblemService.digest("v11-quick-close", id, cleanNote, source, cleanEvidence,
            String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        requireProcessingActor(row);
        String from = String.valueOf(row.get("state"));
        if (mapper.activeFieldOrders(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先登记现场单处理结果");
        }
        String disposal = mapper.disposalType(id);
        if ("replace".equals(disposal) || "part".equals(disposal) || "onsite".equals(disposal)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "换装、换件或上门类工单不能走简单快通道");
        }
        ProblemStateMachine.requireTransition(from, ProblemStateMachine.PENDING_ACCEPT);
        mapper.completeFollowup(id);
        mapper.verify(id, actor(), source, cleanEvidence);
        mapper.setResume(id, null);
        if (mapper.changeState(id, ProblemStateMachine.PENDING_ACCEPT, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        String detail = cleanNote + "（依据：" + cleanEvidence + "）";
        mapper.insertTransition(id, from, ProblemStateMachine.PENDING_ACCEPT, actor(), "quick_close", detail);
        mapper.insertActivity(id, actor(), "quick_close", "简单工单快通道提交待验收：" + detail);
    }

    @Transactional
    public void escalateV11(String id, String level, String reason, boolean compromise,
                            int expectedVersion, String requestId) {
        if (!ESCALATION_LEVELS.contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "升级级别无效");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写升级或妥协原因");
        }
        if (replay(requestId, ProblemService.digest("v11-escalate", id, level, why, String.valueOf(compromise),
            String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        requireProcessingActor(row);
        if (mapper.updateEscalation(id, level, why, compromise ? 1 : 0, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        StringBuilder text = new StringBuilder();
        if (!"none".equals(level)) {
            text.append("升级至").append("expert".equals(level) ? "专家" : "二线");
        } else {
            text.append("解除升级");
        }
        if (compromise) {
            text.append(" · 标记妥协工单");
        }
        text.append("：").append(why);
        mapper.insertActivity(id, actor(), "escalate", text.toString());
    }

    @Transactional
    public void addEvidenceV11(String id, String evidenceType, String source, String fileName, String note,
                               int expectedVersion, String requestId) {
        if (!EVIDENCE_TYPES.contains(evidenceType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "证据类型无效");
        }
        String normalizedSource = blankToNull(source);
        if (normalizedSource == null) {
            normalizedSource = "agent";
        } else if (!EVIDENCE_SOURCES.contains(normalizedSource)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "证据来源无效");
        }
        String cleanFile = fileName == null ? "" : fileName.trim();
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写证据说明");
        }
        if (replay(requestId, ProblemService.digest("v11-evidence", id, evidenceType, normalizedSource, cleanFile, cleanNote,
            String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        requireProcessingActor(row);
        mapper.insertEvidence(id, evidenceType, normalizedSource, cleanFile, cleanNote, actor());
        String state = String.valueOf(row.get("state"));
        if (mapper.changeState(id, state, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        String suffix = cleanFile.isEmpty() ? "" : " [" + cleanFile + "]";
        mapper.insertActivity(id, actor(), "evidence", "登记证据（" + evidenceLabel(evidenceType) + "）" + suffix + "：" + cleanNote);
    }

    @Transactional
    public void assessV11(String id, String grade, String disposalType, String note,
                          int expectedVersion, String requestId) {
        if (!GRADES.contains(grade)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "判定等级无效");
        }
        if (!DISPOSALS.contains(disposalType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "处置方式无效");
        }
        if ("C".equals(grade) && "replace".equals(disposalType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "C级证据不足，禁止选择换装，请补充证据或改为低成本处置");
        }
        String cleanNote = note == null ? "" : note.trim();
        if (replay(requestId, ProblemService.digest("v11-assess", id, grade, disposalType, cleanNote,
            String.valueOf(expectedVersion)), id)) {
            return;
        }
        Map<String, Object> row = lockActive(id, expectedVersion);
        requireProcessingActor(row);
        String approvalNote = null;
        if ("replace".equals(disposalType)) {
            if (mapper.countEvidences(id) < 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "换装处置需至少登记一条证据后再提交判定");
            }
            approvalNote = "系统自动校验通过：证据已登记、在保状态有效、未超换装阈值（模拟自动审批）";
        }
        if (mapper.updateAssessment(id, grade, disposalType, approvalNote, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        mapper.insertAssessment(id, grade, disposalType, "replace".equals(disposalType) ? 1 : 0, approvalNote, cleanNote, actor());
        StringBuilder text = new StringBuilder();
        text.append("判定 ").append(grade).append("级 · ").append(disposalLabel(disposalType));
        if (!cleanNote.isEmpty()) {
            text.append("：").append(cleanNote);
        }
        if (approvalNote != null) {
            text.append("（").append(approvalNote).append("）");
        }
        mapper.insertActivity(id, actor(), "assessment", text.toString());
    }

    public Map<String, Object> monthlyReport() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Map<String, Object> stats = mapper.monthlySummary(now);
        int total = num(stats.get("total"));
        int closed = num(stats.get("closedCount"));
        int replace = num(stats.get("replaceCount"));
        int compromise = num(stats.get("compromiseCount"));
        int overdue = num(stats.get("slaOverdueCount"));
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("totalTickets", Integer.valueOf(total));
        report.put("closedTickets", Integer.valueOf(closed));
        report.put("channelBreakdown", mapper.channelBreakdown());
        report.put("leakRateSimulated", "0.8%（模拟对账口径）");
        report.put("avgAcceptMinutesSimulated", Integer.valueOf(18));
        report.put("slaPassRate", total == 0 ? "100%" : String.format("%.1f%%", (total - overdue) * 100.0 / total));
        report.put("replaceRate", total == 0 ? "0.0%" : String.format("%.1f%%", replace * 100.0 / total));
        report.put("misjudgeRateSimulated", "1.2%（模拟复测口径）");
        report.put("compromiseRate", total == 0 ? "0.0%" : String.format("%.1f%%", compromise * 100.0 / total));
        report.put("devicePendingCount", Integer.valueOf(num(stats.get("devicePendingCount"))));
        report.put("slaOverdueCount", Integer.valueOf(overdue));
        report.put("urgeAndComplaintSimulated", Integer.valueOf(num(stats.get("escalatedCount"))));
        return report;
    }

    private String nextTicketNo() {
        String date = LocalDateTime.now(BUSINESS_ZONE).format(BIZ_DATE);
        mapper.initSequenceDate(date);
        mapper.incrementSequence(date);
        int seq = mapper.currentSequence(date);
        return String.format("GD%s%04d", date, seq);
    }

    private Map<String, Object> lockActive(String id, int expectedVersion) {
        Map<String, Object> row = mapper.lockProblem(id);
        if (row == null || row.get("state") == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在");
        }
        String state = String.valueOf(row.get("state"));
        if (ProblemStateMachine.isTerminal(state) || "resolved".equals(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已处于终态，不可再修改");
        }
        int version = ((Number) row.get("version")).intValue();
        if (version != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已被他人更新，请刷新后重试");
        }
        return row;
    }

    private boolean replay(String requestId, String digest, String problemId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供幂等键");
        }
        if (mapper.insertCommandIfAbsent(requestId, digest, problemId) == 1) {
            return false;
        }
        if (!digest.equals(mapper.commandDigest(requestId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "重复请求的内容不一致");
        }
        return true;
    }

    private static String ownerOf(Map<String, Object> row) {
        Object owner = row.get("owner");
        return owner == null ? "" : String.valueOf(owner);
    }

    private static boolean isUnowned(String owner) {
        return owner == null || owner.trim().isEmpty() || "early".equals(owner);
    }

    private void requireProcessingActor(Map<String, Object> row) {
        String owner = ownerOf(row);
        if (isUnowned(owner)) {
            if (!hasAnyRole("ROLE_ADMIN")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ProblemService.CLAIM_FIRST);
            }
            return;
        }
        if (!hasAnyRole("ROLE_ADMIN") && !actor().equals(owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ProblemService.HANDLER_REQUIRED);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String suspendLabel(String reason) {
        switch (reason) {
            case "customer_reboot": return "等待客户断电重启";
            case "customer_evidence": return "等待客户上传证据";
            case "parts_wait": return "等待备件到货";
            case "field_wait": return "等待乐装回传";
            default: return reason;
        }
    }

    private static String evidenceLabel(String type) {
        switch (type) {
            case "photo": return "现场照片";
            case "video": return "短视频/录屏";
            case "timestamp_log": return "故障时点/日志";
            case "site_compare": return "同站对照";
            case "swap_test": return "单变量替换测试";
            case "platform_data": return "平台报文/充电记录";
            default: return type;
        }
    }

    private static String disposalLabel(String disposal) {
        switch (disposal) {
            case "remote": return "远程处置";
            case "part": return "换件";
            case "replace": return "换装";
            case "onsite": return "上门排查";
            default: return disposal;
        }
    }

    private static String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private static boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        Set<String> set = new HashSet<String>(Arrays.asList(roles));
        for (GrantedAuthority g : auth.getAuthorities()) {
            if (set.contains(g.getAuthority())) return true;
        }
        return false;
    }

    private static boolean isFlag(Object value) {
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static int num(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ignore) {
            return "{}";
        }
    }

    private static Map<String, String> CollectionsMap(String k, String v) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put(k, v);
        return m;
    }
}
