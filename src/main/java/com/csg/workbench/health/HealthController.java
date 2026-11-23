package com.csg.workbench.health;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class HealthController {
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            if (one == null || one.intValue() != 1) {
                throw new IllegalStateException("unexpected");
            }
        } catch (RuntimeException exception) {
            log.warn("health check failed: {}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "服务暂不可用");
        }
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("status", "up");
        body.put("mode", "simulated");
        return body;
    }
}
