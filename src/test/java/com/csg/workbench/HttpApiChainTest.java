package com.csg.workbench;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
class HttpApiChainTest {
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

    @Autowired
    private MockMvc mvc;

    @Test
    void authenticatedGetOnPostRouteIs405WithAllowHeader() throws Exception {
        MockHttpSession session = login();
        mvc.perform(get("/api/problems/not-used/followups").session(session))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
            .andExpect(jsonPath("$.message").value("不支持该请求方法"));
    }

    @Test
    void anonymousGetIs401RatherThan405() throws Exception {
        mvc.perform(get("/api/problems/not-used/followups"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCsrfStaysForbidden() throws Exception {
        MockHttpSession session = login();
        mvc.perform(post("/api/problems/not-used/followups").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\",\"expectedVersion\":0}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unsupportedMediaTypeStays415() throws Exception {
        MockHttpSession session = login();
        String csrf = mvc.perform(get("/api/session/csrf").session(session))
            .andReturn().getResponse().getContentAsString();
        String token = csrf.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/problems/not-used/followups").session(session)
                .header("X-CSRF-TOKEN", token)
                .header("Idempotency-Key", "media-type-key")
                .contentType(MediaType.TEXT_PLAIN).content("hello"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.message").value("不支持该内容类型"));
    }

    @Test
    void internalFailureStays500WithoutSql() throws Exception {
        MockHttpSession session = login();
        mvc.perform(get("/api/test-fault").session(session))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("暂时无法完成操作"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));
    }

    private MockHttpSession login() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/session/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession(false);
        String token = csrf.getResponse().getContentAsString().replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/session/login").session(session)
                .header("X-CSRF-TOKEN", token)
                .param("username", "operator").param("password", PASSWORD))
            .andExpect(status().isNoContent());
        return session;
    }
}

@RestController
class FaultProbe {
    @GetMapping("/api/test-fault")
    public void fail() {
        throw new IllegalStateException("SELECT password FROM account");
    }
}
