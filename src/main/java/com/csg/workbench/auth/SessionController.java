package com.csg.workbench.auth;

import com.csg.workbench.account.AccountService;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    private final AccountService accounts;

    public SessionController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Collections.singletonMap("token", token.getToken());
    }

    @GetMapping
    public Map<String, String> current(Principal principal) {
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("username", principal.getName());
        body.put("role", accounts.role(principal.getName()));
        return body;
    }
}
