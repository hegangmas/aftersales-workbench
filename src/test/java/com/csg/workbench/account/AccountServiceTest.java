package com.csg.workbench.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock private AccountMapper mapper;
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private AccountService service;

    @BeforeEach
    void setup() {
        service = new AccountService(mapper, encoder);
    }

    @Test
    void wrongCurrentPasswordDoesNotStoreOrAudit() {
        when(mapper.find("agent")).thenReturn(account(encoder.encode("Rightpass1")));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.changePassword("agent", "Wrongpass1", "Nextpass1"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("当前密码不正确", error.getReason());
        verify(mapper, never()).updatePassword(anyString(), anyString());
        verify(mapper, never()).insertAudit(anyString(), anyString(), anyString());
    }

    @Test
    void changeStoresHashAndAuditsWithoutTheSecret() {
        String current = "Rightpass1";
        String next = "Nextpass1";
        when(mapper.find("agent")).thenReturn(account(encoder.encode(current)));
        when(mapper.updatePassword(eq("agent"), anyString())).thenReturn(1);
        service.changePassword("agent", current, next);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(mapper).updatePassword(eq("agent"), hash.capture());
        assertTrue(hash.getValue().startsWith("{bcrypt}"));
        assertFalse(hash.getValue().contains(next));
        assertTrue(encoder.matches(next, hash.getValue()));
        verify(mapper).insertAudit("agent", "password_change", "agent");
    }

    @Test
    void resetRejectsWeakPasswordAndSamePassword() {
        when(mapper.find("agent")).thenReturn(account(encoder.encode("Rightpass1")));
        ResponseStatusException weak = assertThrows(ResponseStatusException.class,
            () -> service.resetPassword("admin", "agent", "onlyletters"));
        assertEquals("密码需要同时包含字母和数字", weak.getReason());
        ResponseStatusException same = assertThrows(ResponseStatusException.class,
            () -> service.resetPassword("admin", "agent", "Rightpass1"));
        assertEquals("新密码不能与当前密码相同", same.getReason());
        verify(mapper, never()).insertAudit(anyString(), anyString(), anyString());
    }

    @Test
    void disableLastAdminLocksAndRejects() {
        Map<String, Object> admin = account(encoder.encode("Rightpass1"));
        admin.put("role", "admin");
        when(mapper.find("admin1")).thenReturn(admin);
        when(mapper.enabledAdminsForUpdate()).thenReturn(1);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.setEnabled("admin1", false, "admin2"));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("至少保留一名启用的管理员", error.getReason());
        verify(mapper, never()).setEnabled("admin1", 0);
    }

    private static Map<String, Object> account(String hash) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("passwordHash", hash);
        row.put("enabled", Integer.valueOf(1));
        row.put("role", "operator");
        return row;
    }
}
