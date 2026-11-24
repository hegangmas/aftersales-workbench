package com.csg.workbench.account;

import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        requireAdmin(authentication);
        return accounts.list();
    }

    @GetMapping("/assignable")
    public List<Map<String, Object>> assignable() {
        return accounts.assignable();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(Authentication authentication, @Valid @RequestBody CreateAccount request) {
        requireAdmin(authentication);
        accounts.create(request.username, request.password, request.role);
    }

    @PostMapping("/{username}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(Authentication authentication, @PathVariable String username,
                       @Valid @RequestBody StatusChange request) {
        requireAdmin(authentication);
        accounts.setEnabled(username, request.enabled, authentication.getName());
    }

    private static void requireAdmin(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有权限管理账号");
        }
    }

    public static class CreateAccount {
        @NotBlank(message = "请填写账号") public String username;
        @NotBlank(message = "请填写密码") public String password;
        @NotBlank(message = "请选择角色") public String role;
    }

    public static class StatusChange {
        @NotNull(message = "请指定是否启用") public Boolean enabled;
    }
}
