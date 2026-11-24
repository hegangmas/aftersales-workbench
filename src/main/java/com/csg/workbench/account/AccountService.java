package com.csg.workbench.account;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
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
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码长度需要在8到72之间");
        }
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
        if (!enabled && "admin".equals(String.valueOf(account.get("role"))) && mapper.enabledAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "至少保留一名启用的管理员");
        }
        if (!enabled && username.equals(actor) && "admin".equals(String.valueOf(account.get("role")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能禁用当前唯一的管理会话");
        }
        mapper.setEnabled(username, enabled ? 1 : 0);
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
