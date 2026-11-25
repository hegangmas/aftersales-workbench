package com.csg.workbench.problem;

import com.csg.workbench.account.AccountMapper;
import com.csg.workbench.account.AccountService;
import com.csg.workbench.device.DeviceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProblemService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String VERSION_CONFLICT = "工单已被他人更新，请刷新后重试";
    static final String CLAIM_FIRST = "无主问题请先认领";
    static final String HANDLER_REQUIRED = "只有当前负责人或管理员可以处理这张问题单";
    private static final Set<String> STATES = new HashSet<String>(Arrays.asList(
        "open", "triage", "waiting", "field", "verify", "resolved", "answered", "referred"));
    private static final Set<String> BUCKETS = new HashSet<String>(Arrays.asList(
        "unclaimed", "open", "triage", "waiting", "fieldPending", "fieldActive",
        "verify", "resolved", "answered", "referred"));
    private static final Set<String> KINDS = new HashSet<String>(Arrays.asList("fault", "consult", "service"));
    private final ProblemMapper mapper;
    private final DeviceService devices;
    private final AccountMapper accounts;

    public ProblemService(ProblemMapper mapper, DeviceService devices, AccountMapper accounts) {
        this.mapper = mapper;
        this.devices = devices;
        this.accounts = accounts;
    }

    @Transactional
    public String create(String device, String title, String description, String requestId) {
        String digest = digest("create", device, title, description);
        requireKey(requestId);
        String existing = mapper.commandProblem(requestId);
        if (existing != null) {
            requireSameDigest(requestId, digest);
            return existing;
        }
        devices.requireDevice(device);
        String id = UUID.randomUUID().toString();
        mapper.insertProblem(id, title.trim(), description.trim(), device);
        mapper.insertActivity(id, actor(), "create", "受理问题");
        if (mapper.insertCommandIfAbsent(requestId, digest, id) != 1) {
            requireSameDigest(requestId, digest);
            String concurrent = mapper.commandProblem(requestId);
            if (concurrent != null) {
                return concurrent;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请重试该请求");
        }
        return id;
    }

    @Transactional
    public void recordFollowup(String id, String note, int expectedVersion, String requestId) {
        if (replay(requestId, digest("followup", id, note, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Row row = lockRow(id, expectedVersion);
        requireProcessingActor(row);
        mapper.insertActivity(id, actor(), "followup", note.trim());
        bump(id, row.version, row.state);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        id = resolveReference(id);
        Map<String, Object> problem = requireProblem(id);
        problem.put("pendingFollowup", mapper.pending(id, LocalDateTime.now(BUSINESS_ZONE)));
        problem.put("fieldOrders", mapper.fieldOrders(id));
        problem.put("callbackResults", mapper.callbackResults(id));
        problem.put("activities", mapper.listActivities(id));
        problem.put("ownershipEvents", mapper.ownershipEvents(id));
        return problem;
    }

    public List<Map<String, Object>> findByDevice(String device) {
        return mapper.findByDevice(device.trim());
    }

    public Map<String, Object> due(String scope, String cursor, int limit) {
        int size = pageSize(limit);
        LocalDateTime cursorDue = null;
        Long cursorId = null;
        if (cursor != null && !cursor.trim().isEmpty()) {
            String[] parts = cursor.split("\\|", 2);
            if (parts.length != 2 || parts[1].isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
            }
            try {
                cursorDue = LocalDateTime.parse(parts[0]);
                cursorId = Long.valueOf(parts[1]);
            } catch (DateTimeException | NumberFormatException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
            }
        }
        int mine = "mine".equals(scope) ? 1 : 0;
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String actor = actor();
        List<Map<String, Object>> rows = mapper.duePage(now, mine, actor, cursorDue, cursorId, size + 1);
        Map<String, Object> body = page(rows, size, "dueAt", "followupId");
        body.put("total", Integer.valueOf(mapper.dueCount(now, mine, actor)));
        return body;
    }

    public Map<String, Object> board(String state, String owner, String category, String kind,
                                    String query, String cursor, int limit, String bucket) {
        int size = pageSize(limit);
        String bucketFilter = normalizeToken(bucket, BUCKETS, "问题单状态无效");
        String stateFilter = normalizeToken(state, STATES, "问题单状态无效");
        String ownerMode = "all";
        String ownerValue = null;
        String ownerText = owner == null ? "" : owner.trim();
        if (ownerText.isEmpty() || "all".equals(ownerText)) {
            ownerMode = "all";
        } else if ("early".equals(ownerText)) {
            ownerMode = "early";
        } else if (ownerText.matches("[a-zA-Z0-9_.-]{1,20}")) {
            ownerMode = "user";
            ownerValue = ownerText;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责人筛选无效");
        }
        String categoryFilter = blankToNull(category);
        if (categoryFilter != null && (categoryFilter.length() > 80 || categoryFilter.indexOf('%') >= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "故障分类无效");
        }
        String kindFilter = normalizeToken(kind, KINDS, "问题类型无效");
        String text = query == null ? "" : query.trim();
        if (text.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索关键字过长");
        }
        String like = text.isEmpty() ? null : likePattern(text);
        Long number = text.isEmpty() ? null : shortNumber(text);
        LocalDateTime cursorAt = null;
        String cursorId = null;
        if (cursor != null && !cursor.trim().isEmpty()) {
            String[] parts = cursor.split("\\|", 2);
            if (parts.length != 2 || parts[1].isEmpty() || parts[1].length() > 36) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
            }
            try {
                cursorAt = LocalDateTime.parse(parts[0]);
            } catch (DateTimeException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
            }
            cursorId = parts[1];
        }
        List<Map<String, Object>> rows = mapper.boardPage(stateFilter, ownerMode, ownerValue, categoryFilter,
            kindFilter, bucketFilter, like, number, cursorAt, cursorId, size + 1);
        Map<String, Object> body = page(rows, size, "updatedAt", "id");
        body.put("total", Integer.valueOf(mapper.boardCount(stateFilter, ownerMode, ownerValue,
            categoryFilter, kindFilter, bucketFilter, like, number)));
        body.put("categories", mapper.categories());
        body.put("summary", summary());
        return body;
    }

    private Map<String, Object> summary() {
        Map<String, Object> counts = mapper.boardSummary();
        int unclaimed = countOf(counts, "unclaimed");
        int open = countOf(counts, "openCount");
        int triage = countOf(counts, "triageCount");
        int waiting = countOf(counts, "waitingCount");
        int fieldPending = countOf(counts, "fieldPending");
        int fieldActive = countOf(counts, "fieldActive");
        int verify = countOf(counts, "verifyCount");
        int resolved = countOf(counts, "resolvedCount");
        int answered = countOf(counts, "answeredCount");
        int referred = countOf(counts, "referredCount");
        Map<String, Object> states = new LinkedHashMap<String, Object>();
        states.put("unclaimed", Integer.valueOf(unclaimed));
        states.put("open", Integer.valueOf(open));
        states.put("triage", Integer.valueOf(triage));
        states.put("waiting", Integer.valueOf(waiting));
        states.put("fieldPending", Integer.valueOf(fieldPending));
        states.put("fieldActive", Integer.valueOf(fieldActive));
        states.put("verify", Integer.valueOf(verify));
        states.put("resolved", Integer.valueOf(resolved));
        states.put("answered", Integer.valueOf(answered));
        states.put("referred", Integer.valueOf(referred));
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String actor = actor();
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("total", Integer.valueOf(countOf(counts, "total")));
        summary.put("due", Integer.valueOf(mapper.dueCount(now, 0, actor)));
        summary.put("dueMine", Integer.valueOf(mapper.dueCount(now, 1, actor)));
        summary.put("mine", Integer.valueOf(mapper.mineCount(actor)));
        summary.put("states", states);
        return summary;
    }

    private static int countOf(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return 0;
        }
        return ((Number) row.get(key)).intValue();
    }

    public Map<String, Object> mine(String cursor, int limit) {
        int size = pageSize(limit);
        LocalDateTime cursorUpdated = null;
        String cursorId = null;
        if (cursor != null && !cursor.trim().isEmpty()) {
            String[] parts = cursor.split("\\|", 2);
            if (parts.length != 2 || parts[1].isEmpty() || parts[1].length() > 36) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
            }
            try {
                cursorUpdated = LocalDateTime.parse(parts[0]);
            } catch (DateTimeException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
            }
            cursorId = parts[1];
        }
        String actor = actor();
        List<Map<String, Object>> rows = mapper.minePage(actor, cursorUpdated, cursorId, size + 1);
        Map<String, Object> body = page(rows, size, "updatedAt", "id");
        body.put("total", Integer.valueOf(mapper.mineCount(actor)));
        return body;
    }

    @Transactional
    public void schedule(String id, String owner, String step, LocalDateTime due, int expectedVersion, String requestId) {
        if (replay(requestId, digest("schedule", id, owner, step, String.valueOf(due), String.valueOf(expectedVersion)), id)) {
            return;
        }
        Row row = lockRow(id, expectedVersion);
        requireProcessingActor(row);
        requireFieldStageClear(id, row.state);
        if (due == null || !due.isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择未来的回访时间");
        }
        mapper.completeFollowup(id);
        mapper.schedule(id, owner.trim(), step.trim(), due);
        mapper.setResume(id, resumeAfterSchedule(id, row.state));
        bump(id, row.version, "waiting");
        mapper.insertActivity(id, actor(), "schedule", "回访安排：" + due + "，" + owner.trim() + "，" + step.trim());
    }

    @Transactional
    public void finishCallback(String id, String outcome, String note, LocalDateTime contactedAt,
                               String source, String evidence, int expectedVersion, String requestId) {
        if (replay(requestId, digest("callback", id, outcome, note, String.valueOf(contactedAt), source, evidence,
            String.valueOf(expectedVersion)), id)) {
            return;
        }
        Row row = lockRow(id, expectedVersion);
        requireProcessingActor(row);
        requireFieldStageClear(id, row.state);
        if (contactedAt == null || contactedAt.isAfter(LocalDateTime.now(BUSINESS_ZONE).plusMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写实际联系时间");
        }
        String result = note.trim();
        String next;
        String label;
        switch (outcome) {
            case "continue":
                next = "verify".equals(row.state) || "verify".equals(mapper.resumeState(id)) ? "verify" : "open";
                label = "继续跟进";
                break;
            case "field":
                next = "field";
                label = "待现场派单";
                break;
            case "resolved":
                if (!("customer".equals(source) || "platform".equals(source) || "field".equals(source))
                    || evidence == null || evidence.trim().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认恢复需要核验来源与具体依据");
                }
                mapper.verify(id, actor(), source, evidence.trim());
                next = "resolved";
                label = "确认恢复并结案";
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知回访结果");
        }
        mapper.completeFollowup(id);
        mapper.insertCallback(id, actor(), contactedAt, outcome, result,
            "resolved".equals(outcome) ? source : null, "resolved".equals(outcome) ? evidence.trim() : null);
        mapper.setResume(id, null);
        bump(id, row.version, next);
        mapper.insertActivity(id, actor(), "callback", label + "：" + result);
    }

    @Transactional
    public void claim(String id, int expectedVersion, String requestId) {
        try {
            if (replay(requestId, digest("claim", id, String.valueOf(expectedVersion)), id)) {
                return;
            }
            Row row = lockRow(id, expectedVersion);
            if (!row.unowned()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "问题单已有负责人，请刷新");
            }
            if (mapper.assignOwner(id, actor(), row.version) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
            }
            mapper.insertOwnership(id, actor(), row.owner, actor(), "claim", "");
            mapper.insertActivity(id, actor(), "claim", "认领问题单");
        } catch (PessimisticLockingFailureException locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "问题单已有负责人，请刷新");
        }
    }

    @Transactional
    public void handoff(String id, String target, String reason, int expectedVersion, String requestId) {
        if (replay(requestId, digest("handoff", id, target, reason, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Row row = lockRow(id, expectedVersion);
        if (row.unowned()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CLAIM_FIRST);
        }
        requireProcessingActor(row);
        if (target == null || target.equals(row.owner)) {
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
        if (mapper.assignOwner(id, target, row.version) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        mapper.insertOwnership(id, actor(), row.owner, target, "handoff", why);
        mapper.insertActivity(id, actor(), "handoff", "由 " + row.owner + " 交给 " + target + "：" + why);
    }

    @Transactional
    public void withdrawField(String id, String reason, int expectedVersion, String requestId) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写撤回原因");
        }
        if (replay(requestId, digest("field-withdraw", id, why, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Row row = lockRow(id, expectedVersion);
        requireProcessingActor(row);
        if (!"field".equals(row.state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有待现场派单可以撤回");
        }
        if (mapper.activeFieldOrders(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有现场单在处理，请先登记结果，不能撤回绕过");
        }
        mapper.setResume(id, null);
        bump(id, row.version, "triage");
        mapper.insertActivity(id, actor(), "field_withdraw", "撤回待派单，返回排查：" + why);
    }

    @Transactional
    public void linkFieldOrder(String id, String number, int expectedVersion, String requestId) {
        if (replay(requestId, digest("field-link", id, number, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Row row = lockRow(id, expectedVersion);
        requireProcessingActor(row);
        if (!"field".equals(row.state) || mapper.activeFieldOrders(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅待现场派单的问题可关联一张现场单");
        }
        try {
            mapper.linkFieldOrder(id, number.trim());
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "现场单号已关联");
        }
        bump(id, row.version, row.state);
        mapper.insertActivity(id, actor(), "field_link", "人工关联智享乐装现场单：" + number.trim());
    }

    @Transactional
    public void recordFieldResult(String id, String number, String state, String receipt,
                                  int expectedVersion, String requestId) {
        if (replay(requestId, digest("field-result", id, number, state, receipt, String.valueOf(expectedVersion)), id)) {
            return;
        }
        Row row = lockRow(id, expectedVersion);
        requireProcessingActor(row);
        if (!"field".equals(row.state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前问题未处于现场服务阶段");
        }
        String label;
        String next;
        switch (state) {
            case "in_progress": label = "处理中"; next = "field"; break;
            case "complete": label = "完工"; next = "verify"; break;
            case "rejected": label = "拒单"; next = "triage"; break;
            case "cancelled": label = "取消"; next = "triage"; break;
            default: throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知现场单结果");
        }
        if (mapper.updateFieldOrder(id, number, state, receipt.trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "现场单不可更新");
        }
        mapper.setResume(id, null);
        bump(id, row.version, next);
        mapper.insertActivity(id, actor(), "field_update", "人工登记智享乐装回执 " + number + " · " + label + "：" + receipt.trim());
    }

    private Map<String, Object> page(List<Map<String, Object>> rows, int size, String timeKey, String idKey) {
        boolean more = rows.size() > size;
        List<Map<String, Object>> items = more ? new ArrayList<Map<String, Object>>(rows.subList(0, size)) : rows;
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("items", items);
        if (more && !items.isEmpty()) {
            Map<String, Object> last = items.get(items.size() - 1);
            body.put("nextCursor", String.valueOf(last.get(timeKey)).replace(' ', 'T') + "|" + last.get(idKey));
        } else {
            body.put("nextCursor", null);
        }
        return body;
    }

    private static int pageSize(int limit) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页数量需要在1到50之间");
        }
        return limit;
    }

    private void requireFieldStageClear(String id, String state) {
        if (mapper.activeFieldOrders(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先登记现场单处理结果");
        }
        if ("field".equals(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先关联现场单，不能跳过派单阶段");
        }
    }

    private String resumeAfterSchedule(String id, String state) {
        if ("verify".equals(state)) {
            return "verify";
        }
        if ("waiting".equals(state) && "verify".equals(mapper.resumeState(id))) {
            return "verify";
        }
        return null;
    }

    private Row lockRow(String id, int expectedVersion) {
        Map<String, Object> row = mapper.lockProblem(id);
        if (row == null || row.get("state") == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        String state = String.valueOf(row.get("state"));
        int version = ((Number) row.get("version")).intValue();
        String owner = row.get("owner") == null ? "" : String.valueOf(row.get("owner"));
        if ("resolved".equals(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "问题单已结案");
        }
        if (version != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
        return new Row(state, version, owner);
    }

    private void requireProcessingActor(Row row) {
        if (row.unowned()) {
            if (!admin()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, CLAIM_FIRST);
            }
            return;
        }
        if (!admin() && !actor().equals(row.owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, HANDLER_REQUIRED);
        }
    }

    private void bump(String id, int version, String state) {
        if (mapper.changeState(id, state, version) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, VERSION_CONFLICT);
        }
    }

    private boolean replay(String requestId, String digest, String problemId) {
        requireKey(requestId);
        if (mapper.insertCommandIfAbsent(requestId, digest, problemId) == 1) {
            return false;
        }
        requireSameDigest(requestId, digest);
        return true;
    }

    private void requireSameDigest(String requestId, String digest) {
        if (!digest.equals(mapper.commandDigest(requestId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "重复请求的内容不一致");
        }
    }

    private static void requireKey(String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供幂等键");
        }
    }

    static String digest(String... parts) {
        try {
            MessageDigest algorithm = MessageDigest.getInstance("SHA-256");
            StringBuilder text = new StringBuilder();
            for (String part : parts) {
                text.append(part == null ? "" : part).append('\n');
            }
            byte[] hash = algorithm.digest(text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("digest");
        }
    }

    private String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean admin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeToken(String value, Set<String> allowed, String message) {
        String text = blankToNull(value);
        if (text == null || "all".equals(text)) {
            return null;
        }
        if (!allowed.contains(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return text;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    static String likePattern(String raw) {
        StringBuilder pattern = new StringBuilder("%");
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current == '\\' || current == '%' || current == '_') {
                pattern.append('\\');
            }
            pattern.append(current);
        }
        pattern.append('%');
        return pattern.toString();
    }

    static Long shortNumber(String query) {
        String value = query.toUpperCase(Locale.ROOT);
        if (value.startsWith("AS-")) {
            value = value.substring(3);
        }
        if (!value.matches("[0-9]{1,18}")) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private String resolveReference(String reference) {
        String value = reference.toUpperCase(Locale.ROOT);
        if (!value.matches("(AS-)?[0-9]{1,18}")) {
            return reference;
        }
        String id = mapper.findIdByNumber(Long.parseLong(value.replace("AS-", "")));
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        return id;
    }

    private Map<String, Object> requireProblem(String id) {
        Map<String, Object> problem = mapper.findProblem(id);
        if (problem == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "问题单不存在");
        }
        return problem;
    }

    private static final class Row {
        private final String state;
        private final int version;
        private final String owner;

        private Row(String state, int version, String owner) {
            this.state = state;
            this.version = version;
            this.owner = owner;
        }

        private boolean unowned() {
            return owner == null || owner.trim().isEmpty() || "early".equals(owner);
        }
    }
}
