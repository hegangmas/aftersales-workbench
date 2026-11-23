package com.csg.workbench.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiErrorsTest {
    @Test
    void keepsBusinessChineseAndDropsSql() {
        assertEquals("问题单已结案", ApiErrors.safeMessage("问题单已结案", 409));
        assertEquals("请先关联现场单，不能跳过派单阶段",
            ApiErrors.safeMessage("请先关联现场单，不能跳过派单阶段", 409));
        String leaked = ApiErrors.safeMessage("SELECT password FROM problem", 500);
        assertEquals("暂时无法完成操作", leaked);
        assertFalse(leaked.toLowerCase().contains("select"));
        assertEquals("当前状态不允许此操作", ApiErrors.safeMessage("java.lang.NullPointerException", 409));
    }

    @Test
    void handlerReturnsOnlyMessage() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        ResponseEntity<java.util.Map<String, String>> conflict = handler.status(
            new ResponseStatusException(HttpStatus.CONFLICT, "问题单已结案"));
        assertEquals(409, conflict.getStatusCodeValue());
        assertEquals("问题单已结案", conflict.getBody().get("message"));
        assertEquals(1, conflict.getBody().size());

        ResponseEntity<java.util.Map<String, String>> database = handler.database(
            new DuplicateKeyException("Duplicate entry for key problem"));
        assertEquals(500, database.getStatusCodeValue());
        assertEquals("暂时无法完成操作", database.getBody().get("message"));
        assertFalse(database.getBody().get("message").toLowerCase().contains("duplicate"));
    }
}
