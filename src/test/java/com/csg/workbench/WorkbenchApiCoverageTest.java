package com.csg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
class WorkbenchApiCoverageTest {
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
    void teamBoardFiltersSearchPagesAndLeavesEarlyOwner() throws Exception {
        MockHttpSession session = login("operator", PASSWORD);
        String token = "board" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String first = createProblem(session, token + "甲");
        String second = createProblem(session, token + "乙");
        String third = createProblem(session, token + "丙");
        String category = "分类" + token;
        jdbc.update("UPDATE problem SET category = ?, kind = 'consult' WHERE id = ?", category, third);

        String listed = read(session, "/api/problems/board?q=" + token + "&limit=20");
        assertTrue(JsonPath.<Integer>read(listed, "$.total") >= 3);
        assertEquals("early", ownerOf(listed, first));
        assertFalse(listed.contains("\"owner\":\"operator\"") && ownerOf(listed, first).equals("operator"));
        Number display = JsonPath.read(detail(session, first), "$.displayNumber");
        String byShort = read(session, "/api/problems/board?q=AS-" + display);
        assertEquals(first, JsonPath.<String>read(byShort, "$.items[0].id"));
        String byDigits = read(session, "/api/problems/board?q=" + display);
        assertTrue(ids(byDigits).contains(first));
        String byDevice = read(session, "/api/problems/board?q=SIM-AC-001&limit=20");
        assertTrue(ids(byDevice).contains(first));
        assertEquals(first, JsonPath.<String>read(
            read(session, "/api/problems/board?q=" + token + "甲&limit=20"), "$.items[0].id"));

        String early = read(session, "/api/problems/board?owner=early&q=" + token + "&limit=20");
        assertTrue(ids(early).contains(first));
        assertEquals("early", ownerOf(early, first));
        send(session, "/api/problems/" + second + "/claim", versionBody(session, second), 204);
        String claimed = read(session, "/api/problems/board?owner=operator&q=" + token + "&limit=20");
        assertTrue(ids(claimed).contains(second));
        assertEquals("operator", ownerOf(claimed, second));
        assertFalse(ids(read(session, "/api/problems/board?owner=early&q=" + token + "&limit=20")).contains(second));

        String waitingBody = callbackBody();
        send(session, "/api/problems/" + first + "/callback", withVersion(session, first, waitingBody), 204);
        String waiting = read(session, "/api/problems/board?state=waiting&q=" + token + "&limit=20");
        assertEquals(first, JsonPath.<String>read(waiting, "$.items[0].id"));
        assertEquals("waiting", JsonPath.<String>read(waiting, "$.items[0].state"));
        assertTrue(waiting.contains("\"nextDueAt\":\"20"));
        assertTrue(waiting.contains("\"updatedAt\":\"20"));

        String typed = new String(mvc.perform(get("/api/problems/board").session(session)
                .param("kind", "consult").param("category", category).param("limit", "20"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(),
            java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(third, JsonPath.<String>read(typed, "$.items[0].id"));
        assertEquals("consult", JsonPath.<String>read(typed, "$.items[0].kind"));
        assertEquals(category, JsonPath.<String>read(typed, "$.items[0].category"));

        List<String> full = ids(read(session, "/api/problems/board?q=" + token + "&limit=20"));
        List<String> paged = new ArrayList<String>();
        String cursor = null;
        for (int i = 0; i < full.size(); i++) {
            String path = "/api/problems/board?q=" + token + "&limit=1" + (cursor == null ? "" : "&cursor=" + cursor);
            String page = read(session, path);
            List<String> one = ids(page);
            assertEquals(1, one.size());
            paged.add(one.get(0));
            cursor = JsonPath.read(page, "$.nextCursor");
            if (i < full.size() - 1) {
                assertTrue(cursor != null && cursor.contains("|"));
            }
        }
        assertEquals(full, paged);
        String newest = createProblem(session, token + "丁");
        String firstCursor = JsonPath.read(read(session, "/api/problems/board?q=" + token + "&limit=1"), "$.nextCursor");
        String secondPage = read(session, "/api/problems/board?q=" + token + "&limit=1&cursor=" + firstCursor);
        assertNotEquals(newest, ids(secondPage).get(0));
        assertFalse(ids(secondPage).contains(newest));

        mvc.perform(get("/api/problems/board").session(session).param("state", "nope"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("问题单状态无效"));
        mvc.perform(get("/api/problems/board").session(session).param("owner", "bad owner"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("负责人筛选无效"));
        mvc.perform(get("/api/problems/board").session(session).param("limit", "51"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/problems/board").session(session).param("cursor", "bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("分页参数无效"));
        assertTrue(mvc.perform(get("/api/problems/mine").session(session).param("limit", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
            .andReturn().getResponse().getContentAsString().contains("\"nextCursor\""));
        assertTrue(mvc.perform(get("/api/problems/due").session(session).param("scope", "team").param("limit", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
            .andReturn().getResponse().getContentAsString().contains("\"nextCursor\""));
        mvc.perform(get("/api/problems/due").session(session).param("scope", "mine").param("limit", "20"))
            .andExpect(status().isOk());
        String overview = read(session, "/api/problems/board?limit=1");
        assertSummaryClosed(overview);
        assertEquals(num(overview, "$.summary.total"), num(overview, "$.total"));
        assertEquals(num(overview, "$.summary.due"), num(read(session, "/api/problems/due?scope=team&limit=1"), "$.total"));
        assertEquals(num(overview, "$.summary.dueMine"), num(read(session, "/api/problems/due?scope=mine&limit=1"), "$.total"));
        assertEquals(num(overview, "$.summary.mine"), num(read(session, "/api/problems/mine?limit=1"), "$.total"));
        mvc.perform(get("/api/problems/board").session(session).param("bucket", "nope"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("问题单状态无效"));
        mvc.perform(get("/api/problems/board")).andExpect(status().isUnauthorized());
    }

    @Test
    void summaryBucketsMatchTheSameReadQuery() throws Exception {
        MockHttpSession session = login("operator", PASSWORD);
        String mark = "sum" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String early = createProblem(session, mark + "无主");
        String owned = createProblem(session, mark + "处理");
        send(session, "/api/problems/" + owned + "/claim", versionBody(session, owned), 204);
        String pending = createProblem(session, mark + "待派");
        send(session, "/api/problems/" + pending + "/claim", versionBody(session, pending), 204);
        jdbc.update("UPDATE problem SET state = 'field' WHERE id = ?", pending);
        String active = createProblem(session, mark + "现场");
        send(session, "/api/problems/" + active + "/claim", versionBody(session, active), 204);
        jdbc.update("UPDATE problem SET state = 'field' WHERE id = ?", active);
        jdbc.update("INSERT INTO field_order (problem_id, external_no, state, receipt) VALUES (?, ?, 'linked', '')",
            active, "SIM-SUM-" + mark);

        String overview = read(session, "/api/problems/board?limit=1");
        assertSummaryClosed(overview);
        for (String bucket : new String[] {
            "unclaimed", "open", "triage", "waiting", "fieldPending", "fieldActive",
            "verify", "resolved", "answered", "referred"}) {
            assertEquals(num(overview, "$.summary.states." + bucket),
                num(read(session, "/api/problems/board?bucket=" + bucket + "&limit=1"), "$.total"), bucket);
        }
        String pool = read(session, "/api/problems/board?bucket=unclaimed&q=" + mark + "&limit=20");
        assertTrue(ids(pool).contains(early));
        assertFalse(ids(pool).contains(owned));
        String handling = read(session, "/api/problems/board?bucket=open&q=" + mark + "&limit=20");
        assertTrue(ids(handling).contains(owned));
        assertFalse(ids(handling).contains(early));
        Map<String, Object> pendingRow = row(read(session, "/api/problems/board?bucket=fieldPending&q=" + mark + "&limit=20"), pending);
        Map<String, Object> activeRow = row(read(session, "/api/problems/board?bucket=fieldActive&q=" + mark + "&limit=20"), active);
        assertEquals(0, ((Number) pendingRow.get("activeFieldOrders")).intValue());
        assertTrue(((Number) activeRow.get("activeFieldOrders")).intValue() > 0);
        assertTrue(pendingRow.get("version") instanceof Number);
        mvc.perform(get("/api/problems/" + early).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("充电故障"))
            .andExpect(jsonPath("$.kind").value("fault"));
    }

    @Test
    void passwordChangeAndAdminResetKeepSessionsAndSecrets() throws Exception {
        MockHttpSession admin = login("operator", PASSWORD);
        String seat = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String initial = "Aa1" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String changed = "Bb2" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String reset = "Cc3" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        send(admin, "/api/accounts", "{\"username\":\"" + seat + "\",\"password\":\"" + initial + "\",\"role\":\"operator\"}", 201);

        MockHttpSession current = login(seat, initial);
        MockHttpSession other = login(seat, initial);
        Logger logger = (Logger) LoggerFactory.getLogger("com.csg.workbench.account.AccountService");
        ListAppender<ILoggingEvent> logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            mvc.perform(post("/api/accounts/password").session(current)
                    .header("X-CSRF-TOKEN", csrf(current)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"Wrongpass1\",\"nextPassword\":\"" + changed + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("当前密码不正确"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(changed))));
            mvc.perform(post("/api/accounts/password").session(current)
                    .header("X-CSRF-TOKEN", csrf(current)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + initial + "\",\"nextPassword\":\"short1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("密码长度需要在8到72之间"));
            mvc.perform(post("/api/accounts/password").session(current)
                    .header("X-CSRF-TOKEN", csrf(current)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + initial + "\",\"nextPassword\":\"longletters\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("密码需要同时包含字母和数字"));
            mvc.perform(post("/api/accounts/password").session(current)
                    .header("X-CSRF-TOKEN", csrf(current)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + initial + "\",\"nextPassword\":\"" + changed + "\"}"))
                .andExpect(status().isNoContent());
            mvc.perform(get("/api/session").session(current)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(seat));
            mvc.perform(get("/api/session").session(other)).andExpect(status().isUnauthorized());
            loginExpect(seat, initial, 401);
            loginExpect(seat, changed, 204);

            mvc.perform(post("/api/accounts/operator/password").session(current)
                    .header("X-CSRF-TOKEN", csrf(current)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"" + reset + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("没有权限管理账号"));
            MvcResult anonymousCsrf = mvc.perform(get("/api/session/csrf")).andExpect(status().isOk()).andReturn();
            mvc.perform(post("/api/accounts/" + seat + "/password")
                    .session((MockHttpSession) anonymousCsrf.getRequest().getSession(false))
                    .header("X-CSRF-TOKEN", tokenOf(anonymousCsrf))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"" + reset + "\"}"))
                .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/accounts/" + seat + "/password").session(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"" + reset + "\"}"))
                .andExpect(status().isForbidden());
            mvc.perform(post("/api/accounts/" + seat + "/password").session(admin)
                    .header("X-CSRF-TOKEN", csrf(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"" + reset + "\"}"))
                .andExpect(status().isNoContent());
            mvc.perform(get("/api/session").session(current)).andExpect(status().isUnauthorized());
            loginExpect(seat, changed, 401);
            loginExpect(seat, reset, 204);
            Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_audit WHERE action = 'password_reset' AND actor = 'operator' AND target = ?",
                Integer.class, seat);
            assertEquals(Integer.valueOf(1), audits);
            String auditText = jdbc.queryForObject(
                "SELECT CONCAT(id, ' ', actor, ' ', action, ' ', target, ' ', created_at) FROM security_audit "
                    + "WHERE target = ? ORDER BY id DESC LIMIT 1", String.class, seat);
            assertFalse(auditText.contains(reset));
            assertFalse(auditText.contains("{bcrypt}"));
            for (ILoggingEvent event : logs.list) {
                String message = event.getFormattedMessage();
                assertFalse(message.contains(initial));
                assertFalse(message.contains(changed));
                assertFalse(message.contains(reset));
                assertFalse(message.contains("{bcrypt}"));
            }
        } finally {
            logger.detachAppender(logs);
        }

        mvc.perform(get("/api/accounts/password").session(admin))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
            .andExpect(jsonPath("$.message").value("不支持该请求方法"));
        mvc.perform(post("/api/accounts/password").session(admin)
                .header("X-CSRF-TOKEN", csrf(admin))
                .contentType(MediaType.TEXT_PLAIN).content("hello"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.message").value("不支持该内容类型"));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/session/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession(false);
        mvc.perform(post("/api/session/login").session(session)
                .header("X-CSRF-TOKEN", tokenOf(csrf))
                .param("username", username).param("password", password))
            .andExpect(status().isNoContent());
        return session;
    }

    private void loginExpect(String username, String password, int status) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/session/csrf")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/session/login").session((MockHttpSession) csrf.getRequest().getSession(false))
                .header("X-CSRF-TOKEN", tokenOf(csrf))
                .param("username", username).param("password", password))
            .andExpect(status().is(status));
    }

    private String createProblem(MockHttpSession session, String title) throws Exception {
        MvcResult created = send(session, "/api/problems",
            "{\"device\":\"SIM-AC-001\",\"title\":\"" + title + "\",\"description\":\"车辆连接后未开始充电\"}", 201);
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private MvcResult send(MockHttpSession session, String path, String json, int expected) throws Exception {
        return mvc.perform(post(path).session(session)
                .header("X-CSRF-TOKEN", csrf(session))
                .header("Idempotency-Key", ("k" + UUID.randomUUID().toString().replace("-", "")).substring(0, 24))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().is(expected))
            .andReturn();
    }

    private String read(MockHttpSession session, String path) throws Exception {
        return mvc.perform(get(path).session(session)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String detail(MockHttpSession session, String id) throws Exception {
        return read(session, "/api/problems/" + id);
    }

    private String versionBody(MockHttpSession session, String id) throws Exception {
        return "{\"expectedVersion\":" + version(session, id) + "}";
    }

    private String withVersion(MockHttpSession session, String id, String json) throws Exception {
        return json.substring(0, json.length() - 1) + ",\"expectedVersion\":" + version(session, id) + "}";
    }

    private int version(MockHttpSession session, String id) throws Exception {
        return ((Number) JsonPath.read(detail(session, id), "$.version")).intValue();
    }

    private static String callbackBody() {
        return "{\"owner\":\"测试坐席\",\"nextStep\":\"确认是否恢复\",\"dueAt\":\""
            + LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusHours(2).withNano(0) + "\"}";
    }

    private static List<String> ids(String json) {
        return JsonPath.read(json, "$.items[*].id");
    }

    private static int num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).intValue();
    }

    private static void assertSummaryClosed(String json) {
        int summed = 0;
        for (String key : new String[] {
            "unclaimed", "open", "triage", "waiting", "fieldPending", "fieldActive",
            "verify", "resolved", "answered", "referred"}) {
            summed += num(json, "$.summary.states." + key);
        }
        assertEquals(num(json, "$.summary.total"), summed);
    }

    private static Map<String, Object> row(String json, String id) {
        List<Map<String, Object>> items = JsonPath.read(json, "$.items");
        for (Map<String, Object> item : items) {
            if (id.equals(item.get("id"))) {
                return item;
            }
        }
        throw new AssertionError("missing row");
    }

    private static String ownerOf(String json, String id) {
        List<java.util.Map<String, Object>> items = JsonPath.read(json, "$.items");
        for (java.util.Map<String, Object> item : items) {
            if (id.equals(item.get("id"))) {
                return String.valueOf(item.get("owner"));
            }
        }
        throw new AssertionError("missing owner");
    }

    private String csrf(MockHttpSession session) throws Exception {
        return tokenOf(mvc.perform(get("/api/session/csrf").session(session)).andExpect(status().isOk()).andReturn());
    }

    private static String tokenOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString().replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
