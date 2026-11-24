package com.csg.workbench.account;

import com.csg.workbench.auth.WorkbenchSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
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
    private final UserDetailsService users;

    public AccountController(AccountService accounts, UserDetailsService users) {
        this.accounts = accounts;
        this.users = users;
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

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, HttpServletRequest request,
                               @Valid @RequestBody PasswordChange body) {
        accounts.changePassword(authentication.getName(), body.currentPassword, body.nextPassword);
        keepCurrentSession(request, authentication.getName());
    }

    @PostMapping("/{username}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(Authentication authentication, HttpServletRequest request,
                              @PathVariable String username, @Valid @RequestBody PasswordReset body) {
        requireAdmin(authentication);
        accounts.resetPassword(authentication.getName(), username, body.password);
        if (username.equals(authentication.getName())) {
            keepCurrentSession(request, username);
        }
    }

    @PostMapping("/{username}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(Authentication authentication, @PathVariable String username,
                       @Valid @RequestBody StatusChange request) {
        requireAdmin(authentication);
        accounts.setEnabled(username, request.enabled, authentication.getName());
    }

    private void keepCurrentSession(HttpServletRequest request, String username) {
        LocalDateTime changedAt = accounts.credentialsChangedAt(username);
        request.changeSessionId();
        request.getSession().setAttribute(WorkbenchSession.CREDENTIALS_CHANGED_AT, changedAt);
        UserDetails details = users.loadUserByUsername(username);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            details, details.getPassword(), details.getAuthorities()));
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

    public static class PasswordChange {
        @NotBlank(message = "请填写当前密码")
        @Size(max = 72, message = "密码长度需要在8到72之间")
        public String currentPassword;
        @NotBlank(message = "请填写新密码")
        @Size(min = 8, max = 72, message = "密码长度需要在8到72之间")
        public String nextPassword;
    }

    public static class PasswordReset {
        @NotBlank(message = "请填写新密码")
        @Size(min = 8, max = 72, message = "密码长度需要在8到72之间")
        public String password;
    }
}
