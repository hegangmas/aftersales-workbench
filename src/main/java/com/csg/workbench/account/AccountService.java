package com.csg.workbench.account;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountMapper mapper;
    private final PasswordEncoder passwords;

    public AccountService(AccountMapper mapper, PasswordEncoder passwords) {
        this.mapper = mapper;
        this.passwords = passwords;
    }

    @Transactional
    public void bootstrapIfEmpty(String username, String passwordHash) {
        if (mapper.count() == 0) {
            mapper.insert(username, passwordHash, "admin", 1);
        }
    }

    public Map<String, Object> find(String username) {
        return mapper.find(username);
    }

    public boolean enabled(String username) {
        Map<String, Object> account = mapper.find(username);
        return account != null && flag(account.get("enabled"));
    }

    public Map<String, Object> sessionState(String username) {
        return mapper.findSessionState(username);
    }

    public String role(String username) {
        Map<String, Object> account = mapper.find(username);
        return account == null ? "" : String.valueOf(account.get("role"));
    }

    public List<Map<String, Object>> list() {
        return mapper.list();
    }

    public List<Map<String, Object>> assignable() {
        return mapper.assignable();
    }

    @Transactional
    public void create(String username, String password, String role) {
        if (username == null || !username.matches("[a-zA-Z0-9_.-]{1,20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号只能包含字母数字和 ._- ，且不超过20字");
        }
        requireStrongPassword(password, username);
        if (!"admin".equals(role) && !"operator".equals(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色只能是管理员或坐席");
        }
        try {
            mapper.insert(username, passwords.encode(password), role, 1);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
        }
    }

    @Transactional
    public void setEnabled(String username, boolean enabled, String actor) {
        Map<String, Object> account = mapper.find(username);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在");
        }
        if (!enabled && "admin".equals(String.valueOf(account.get("role"))) && mapper.enabledAdminsForUpdate() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "至少保留一名启用的管理员");
        }
        if (!enabled && username.equals(actor) && "admin".equals(String.valueOf(account.get("role")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能禁用当前唯一的管理会话");
        }
        mapper.setEnabled(username, enabled ? 1 : 0);
    }

    public LocalDateTime credentialsChangedAt(String username) {
        return mapper.credentialsChangedAt(username);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String nextPassword) {
        Map<String, Object> account = requireAccount(username);
        String hash = String.valueOf(account.get("passwordHash"));
        if (currentPassword == null || currentPassword.isEmpty() || !passwords.matches(currentPassword, hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        storeNewPassword(username, username, nextPassword, hash, "password_change");
    }

    @Transactional
    public void resetPassword(String actor, String username, String nextPassword) {
        Map<String, Object> account = mapper.find(username);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在");
        }
        storeNewPassword(actor, username, nextPassword, String.valueOf(account.get("passwordHash")), "password_reset");
    }

    private Map<String, Object> requireAccount(String username) {
        Map<String, Object> account = mapper.find(username);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        return account;
    }

    private void storeNewPassword(String actor, String username, String nextPassword, String currentHash, String action) {
        requireStrongPassword(nextPassword, username);
        if (passwords.matches(nextPassword, currentHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        if (mapper.updatePassword(username, passwords.encode(nextPassword)) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请刷新后重试");
        }
        mapper.insertAudit(actor, action, username);
        log.info("security audit action={} actor={} target={}", action, actor, username);
    }

    static void requireStrongPassword(String password, String username) {
        if (password == null || password.length() < 8 || password.length() > 72
            || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码长度需要在8到72之间");
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char current = password.charAt(i);
            if ((current >= 'A' && current <= 'Z') || (current >= 'a' && current <= 'z')) {
                letter = true;
            }
            if (current >= '0' && current <= '9') {
                digit = true;
            }
        }
        if (!letter || !digit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码需要同时包含字母和数字");
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能与账号相同");
        }
    }

    public static boolean flag(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return "1".equals(String.valueOf(value)) || "true".equals(String.valueOf(value));
    }
}
