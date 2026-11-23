package com.csg.workbench.auth;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Collections.singletonMap("token", token.getToken());
    }

    @GetMapping
    public Map<String, String> current(Principal principal) {
        return Collections.singletonMap("username", principal.getName());
    }
}
