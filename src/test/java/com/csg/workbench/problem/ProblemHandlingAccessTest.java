package com.csg.workbench.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ProblemHandlingAccessTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String PASSWORD = System.getenv().getOrDefault("MVC_TEST_PASSWORD", "TempPass123");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String user = System.getenv("MVC_TEST_DB_USER");
        String password = System.getenv("MVC_TEST_DB_PASSWORD");
        String database = System.getenv().getOrDefault("MVC_TEST_DB_URL",
            "jdbc:mysql://127.0.0.1:3306/soongo_aftersales_mvc?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        if (user == null || password == null) {
            throw new IllegalStateException("MVC database settings are required");
        }
        registry.add("WORKBENCH_DB_URL", () -> database);
        registry.add("WORKBENCH_DB_USER", () -> user);
        registry.add("WORKBENCH_DB_PASSWORD", () -> password);
        registry.add("WORKBENCH_LOGIN_USER", () -> "operator");
        registry.add("WORKBENCH_LOGIN_PASSWORD_HASH", () -> "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD));
        registry.add("WORKBENCH_SECURE_COOKIE", () -> "false");
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createAccountUsesEmptyCreatedBodyAndProblemCreateKeepsJson() throws Exception {
        MockHttpSession admin = login("operator", PASSWORD);
        String title = "json" + token();
        MvcResult problem = send(admin, "/api/problems",
            "{\"device\":\"SIM-AC-001\",\"title\":\"" + title + "\",\"description\":\"车辆连接后未开始充电\"}", 201);
        assertTrue(problem.getResponse().getContentAsString().contains("\"id\""));
        String username = "n" + token();
        String password = generatedPassword();
        MvcResult created = send(admin, "/api/accounts", accountJson(username, password), 201);
        assertEquals("", created.getResponse().getContentAsString());
        assertTrue(read(admin, "/api/accounts").contains("\"username\":\"" + username + "\""));
        assertTrue(read(admin, "/api/accounts/assignable").contains("\"username\":\"" + username + "\""));
        login(username, password);
        send(admin, "/api/accounts", accountJson(username, password), 409);
        mvc.perform(post("/api/accounts").session(admin)
                .header("X-CSRF-TOKEN", csrf(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson(username, generatedPassword())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("账号已存在"));
    }

    @Test
    void claimedTicketRejectsOtherSeatAndKeepsOwnerAdminAndClaimFirst() throws Exception {
        MockHttpSession admin = login("operator", PASSWORD);
        String seatA = "a" + token();
        String seatB = "b" + token();
        String passwordA = generatedPassword();
        String passwordB = generatedPassword();
        assertEquals("", send(admin, "/api/accounts", accountJson(seatA, passwordA), 201)
            .getResponse().getContentAsString());
        send(admin, "/api/accounts", accountJson(seatB, passwordB), 201);
        MockHttpSession agentA = login(seatA, passwordA);
        MockHttpSession agentB = login(seatB, passwordB);

        String earlyId = createProblem(admin, "无主" + token());
        assertEquals("early", JsonPath.<String>read(detail(admin, earlyId), "$.owner"));
        String earlyKey = key("early");
        int earlyVersion = version(admin, earlyId);
        String earlyBefore = fingerprint(earlyId);
        denied(agentB, "/api/problems/" + earlyId + "/followups",
            followup(earlyVersion, "未认领先写"), 409, ProblemService.CLAIM_FIRST, earlyKey, earlyBefore, earlyId);
        send(agentB, "/api/problems/" + earlyId + "/claim", versionBody(earlyVersion), 204);
        assertEquals(seatB, JsonPath.<String>read(detail(agentB, earlyId), "$.owner"));
        send(agentB, "/api/problems/" + earlyId + "/followups",
            followup(version(agentB, earlyId), "认领后可以跟进"), 204);

        String ownedId = createProblem(admin, "已认领" + token());
        send(agentA, "/api/problems/" + ownedId + "/claim", versionBody(version(admin, ownedId)), 204);
        int ownedVersion = version(agentA, ownedId);
        String ownedBefore = fingerprint(ownedId);
        String base = "/api/problems/" + ownedId;
        String repeatedDenial = key("follow");
        denied(agentB, base + "/followups", followup(ownedVersion, "越权跟进"), 403,
            ProblemService.HANDLER_REQUIRED, repeatedDenial, ownedBefore, ownedId);
        denied(agentB, base + "/followups", followup(ownedVersion, "越权跟进"), 403,
            ProblemService.HANDLER_REQUIRED, repeatedDenial, ownedBefore, ownedId);
        denied(agentB, base + "/callback", schedule(ownedVersion), 403,
            ProblemService.HANDLER_REQUIRED, key("schedule"), ownedBefore, ownedId);
        denied(agentB, base + "/callback/result", callback(ownedVersion, "resolved"), 403,
            ProblemService.HANDLER_REQUIRED, key("close"), ownedBefore, ownedId);
        denied(agentB, base + "/handoff",
            "{\"target\":\"" + seatB + "\",\"reason\":\"越权转交\",\"expectedVersion\":" + ownedVersion + "}",
            403, ProblemService.HANDLER_REQUIRED, key("handoff"), ownedBefore, ownedId);
        denied(agentB, base + "/field-withdrawal",
            "{\"reason\":\"越权撤回\",\"expectedVersion\":" + ownedVersion + "}",
            403, ProblemService.HANDLER_REQUIRED, key("withdraw"), ownedBefore, ownedId);
        denied(agentB, base + "/field-orders",
            "{\"externalNo\":\"SIM-DENY-" + seatB + "\",\"expectedVersion\":" + ownedVersion + "}",
            403, ProblemService.HANDLER_REQUIRED, key("link"), ownedBefore, ownedId);
        denied(agentB, base + "/field-orders/result",
            "{\"externalNo\":\"SIM-DENY-" + seatB + "\",\"state\":\"complete\",\"receipt\":\"越权回执\",\"expectedVersion\":"
                + ownedVersion + "}",
            403, ProblemService.HANDLER_REQUIRED, key("receipt"), ownedBefore, ownedId);
        assertEquals(ownedBefore, fingerprint(ownedId));

        send(agentA, base + "/followups", followup(ownedVersion, "负责人跟进"), 204);
        assertEquals("负责人跟进", JsonPath.<String>read(detail(agentA, ownedId), "$.activities[-1].note"));
        int stale = ownedVersion;
        mvc.perform(post(base + "/followups").session(agentA)
                .header("X-CSRF-TOKEN", csrf(agentA))
                .header("Idempotency-Key", key("stale"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(followup(stale, "过期版本")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("工单已被他人更新，请刷新后重试"));
        String sameKey = key("same");
        int current = version(agentA, ownedId);
        send(agentA, base + "/followups", followup(current, "同一条跟进"), 204, sameKey);
        send(agentA, base + "/followups", followup(current, "同一条跟进"), 204, sameKey);
        String afterSame = detail(agentA, ownedId);
        int copies = 0;
        for (Object note : JsonPath.<java.util.List<Object>>read(afterSame, "$.activities[*].note")) {
            if ("同一条跟进".equals(note)) {
                copies++;
            }
        }
        assertEquals(1, copies);

        send(agentA, base + "/handoff",
            "{\"target\":\"" + seatB + "\",\"reason\":\"交给坐席B\",\"expectedVersion\":" + version(agentA, ownedId) + "}", 204);
        assertEquals(seatB, JsonPath.<String>read(detail(agentB, ownedId), "$.owner"));
        send(agentB, base + "/followups", followup(version(agentB, ownedId), "接手后跟进"), 204);
        String handed = fingerprint(ownedId);
        denied(agentA, base + "/followups", followup(version(agentB, ownedId), "原负责人再写"), 403,
            ProblemService.HANDLER_REQUIRED, key("former"), handed, ownedId);
        send(admin, base + "/followups", followup(version(admin, ownedId), "管理员介入"), 204);
        assertEquals("operator", JsonPath.<String>read(detail(admin, ownedId), "$.activities[-1].actor"));

        String blankToken = token();
        String blankId = createProblem(admin, "空负责人" + blankToken);
        jdbc.update("UPDATE problem SET owner = '' WHERE id = ?", blankId);
        assertTrue(read(admin, "/api/problems/board?owner=early&q=" + blankToken + "&limit=20").contains(blankId));
        String blankBefore = fingerprint(blankId);
        denied(agentA, "/api/problems/" + blankId + "/followups",
            followup(version(admin, blankId), "空负责人直接写"), 409, ProblemService.CLAIM_FIRST,
            key("blank"), blankBefore, blankId);
        send(agentA, "/api/problems/" + blankId + "/claim", versionBody(version(admin, blankId)), 204);
        assertEquals(seatA, JsonPath.<String>read(detail(agentA, blankId), "$.owner"));
        assertTrue(!read(admin, "/api/problems/board?owner=early&q=" + blankToken + "&limit=20").contains(blankId));
    }

    @Test
    void twoSeatsCannotClaimTheSameVersion() throws Exception {
        MockHttpSession admin = login("operator", PASSWORD);
        String seatA = "c" + token();
        String seatB = "d" + token();
        String passwordA = generatedPassword();
        String passwordB = generatedPassword();
        send(admin, "/api/accounts", accountJson(seatA, passwordA), 201);
        send(admin, "/api/accounts", accountJson(seatB, passwordB), 201);
        MockHttpSession agentA = login(seatA, passwordA);
        MockHttpSession agentB = login(seatB, passwordB);
        String id = createProblem(admin, "并发认领" + token());
        String body = versionBody(version(admin, id));
        String path = "/api/problems/" + id + "/claim";
        String csrfA = csrf(agentA);
        String csrfB = csrf(agentB);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> left = pool.submit(() -> claimStatus(agentA, csrfA, path, body, key("race-a"), ready, start));
            Future<Integer> right = pool.submit(() -> claimStatus(agentB, csrfB, path, body, key("race-b"), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int leftStatus = left.get(20, TimeUnit.SECONDS);
            int rightStatus = right.get(20, TimeUnit.SECONDS);
            String owner = JsonPath.read(detail(admin, id), "$.owner");
            if (leftStatus == 204) {
                assertEquals(409, rightStatus);
                assertEquals(seatA, owner);
            } else {
                assertEquals(204, rightStatus);
                assertEquals(409, leftStatus);
                assertEquals(seatB, owner);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void denied(MockHttpSession session, String path, String json, int status, String message,
                        String requestKey, String before, String id) throws Exception {
        mvc.perform(post(path).session(session)
                .header("X-CSRF-TOKEN", csrf(session))
                .header("Idempotency-Key", requestKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().is(status))
            .andExpect(jsonPath("$.message").value(message));
        assertEquals(before, fingerprint(id));
        Integer kept = jdbc.queryForObject(
            "SELECT COUNT(*) FROM command_log WHERE request_id = ?", Integer.class, requestKey);
        assertEquals(Integer.valueOf(0), kept);
    }

    private int claimStatus(MockHttpSession session, String csrfToken, String path, String body, String requestKey,
                            CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                return 0;
            }
            return mvc.perform(post(path).session(session)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", requestKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
        } catch (Exception failure) {
            throw new IllegalStateException(failure.getClass().getSimpleName());
        }
    }

    private String fingerprint(String id) {
        java.util.Map<String, Object> problem = jdbc.queryForMap(
            "SELECT state, version, owner, IFNULL(resume_state, '') AS resume_state FROM problem WHERE id = ?", id);
        Integer activities = jdbc.queryForObject("SELECT COUNT(*) FROM activity WHERE problem_id = ?", Integer.class, id);
        Integer followups = jdbc.queryForObject(
            "SELECT COUNT(*) FROM followup WHERE problem_id = ? AND state = 'open'", Integer.class, id);
        Integer fields = jdbc.queryForObject("SELECT COUNT(*) FROM field_order WHERE problem_id = ?", Integer.class, id);
        Integer callbacks = jdbc.queryForObject(
            "SELECT COUNT(*) FROM callback_result WHERE problem_id = ?", Integer.class, id);
        Integer ownership = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ownership_event WHERE problem_id = ?", Integer.class, id);
        String fieldText = jdbc.queryForObject(
            "SELECT IFNULL(GROUP_CONCAT(CONCAT(external_no, ':', state, ':', receipt) ORDER BY id), '') "
                + "FROM field_order WHERE problem_id = ?",
            String.class, id);
        return text(problem.get("state")) + "|" + text(problem.get("version")) + "|" + text(problem.get("owner"))
            + "|" + text(problem.get("resume_state")) + "|a" + activities + "|f" + followups
            + "|o" + fields + "|c" + callbacks + "|w" + ownership + "|" + text(fieldText);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/session/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        mvc.perform(post("/api/session/login").session(session)
                .header("X-CSRF-TOKEN", tokenOf(csrfResult))
                .param("username", username).param("password", password))
            .andExpect(status().isNoContent());
        return session;
    }

    private String createProblem(MockHttpSession session, String title) throws Exception {
        MvcResult created = send(session, "/api/problems",
            "{\"device\":\"SIM-AC-001\",\"title\":\"" + title + "\",\"description\":\"车辆连接后未开始充电\"}", 201);
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private MvcResult send(MockHttpSession session, String path, String json, int expected) throws Exception {
        return send(session, path, json, expected, key("send"));
    }

    private MvcResult send(MockHttpSession session, String path, String json, int expected, String requestKey) throws Exception {
        return mvc.perform(post(path).session(session)
                .header("X-CSRF-TOKEN", csrf(session))
                .header("Idempotency-Key", requestKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().is(expected))
            .andReturn();
    }

    private String read(MockHttpSession session, String path) throws Exception {
        byte[] body = mvc.perform(get(path).session(session)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        return new String(body, StandardCharsets.UTF_8);
    }

    private String detail(MockHttpSession session, String id) throws Exception {
        return read(session, "/api/problems/" + id);
    }

    private int version(MockHttpSession session, String id) throws Exception {
        return ((Number) JsonPath.read(detail(session, id), "$.version")).intValue();
    }

    private static String versionBody(int version) {
        return "{\"expectedVersion\":" + version + "}";
    }

    private static String followup(int version, String note) {
        return "{\"note\":\"" + note + "\",\"expectedVersion\":" + version + "}";
    }

    private static String schedule(int version) {
        String due = LocalDateTime.now(SHANGHAI).plusHours(2).withNano(0).toString();
        return "{\"owner\":\"回访坐席\",\"nextStep\":\"确认是否恢复\",\"dueAt\":\"" + due
            + "\",\"expectedVersion\":" + version + "}";
    }

    private static String callback(int version, String outcome) {
        String contacted = LocalDateTime.now(SHANGHAI).minusMinutes(5).withNano(0).toString();
        return "{\"outcome\":\"" + outcome + "\",\"note\":\"电话已接通\",\"contactedAt\":\"" + contacted
            + "\",\"evidenceSource\":\"customer\",\"evidence\":\"车主确认已正常完成一次充电\",\"expectedVersion\":"
            + version + "}";
    }

    private static String accountJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"role\":\"operator\"}";
    }

    private static String generatedPassword() {
        return "Aa1" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String key(String name) {
        return (name + "-" + UUID.randomUUID().toString().replace("-", "")).substring(0, 24);
    }

    private String csrf(MockHttpSession session) throws Exception {
        return tokenOf(mvc.perform(get("/api/session/csrf").session(session)).andExpect(status().isOk()).andReturn());
    }

    private static String tokenOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString().replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
