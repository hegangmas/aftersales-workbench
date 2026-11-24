package com.csg.workbench.problem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardQueriesTest {
    @Test
    void pageSelectsVersionFieldActivityAndNextStepWithoutInterpolatingBucket() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("bucket", "fieldPending");
        String page = new BoardQueries().page(params);
        assertTrue(page.contains("p.version AS version"));
        assertTrue(page.contains("AS activeFieldOrders"));
        assertTrue(page.contains("AS nextStep"));
        assertTrue(page.contains("p.state = 'field'"));
        assertTrue(page.contains("NOT EXISTS"));
        assertFalse(page.contains("fieldPending"));
        assertFalse(page.contains("${"));

        String count = new BoardQueries().count(params);
        assertTrue(count.startsWith("SELECT COUNT(*)"));
        assertTrue(count.contains("NOT EXISTS"));
        assertFalse(count.contains("ORDER BY"));
        assertFalse(count.contains("nextStep"));
    }

    @Test
    void unknownBucketIsNotCopiedIntoSql() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("bucket", "resolved' OR 1=1 --");
        String sql = new BoardQueries().page(params);
        assertFalse(sql.contains("OR 1=1"));
        assertFalse(sql.contains("resolved'"));
    }

    @Test
    void unclaimedAndActiveFieldBucketsStayMutuallyExclusive() {
        Map<String, Object> unclaimed = new LinkedHashMap<String, Object>();
        unclaimed.put("bucket", "unclaimed");
        String pool = new BoardQueries().count(unclaimed);
        assertTrue(pool.contains("p.state <> 'resolved'"));
        assertTrue(pool.contains("p.owner = 'early'"));

        Map<String, Object> active = new LinkedHashMap<String, Object>();
        active.put("bucket", "fieldActive");
        String onsite = new BoardQueries().count(active);
        assertTrue(onsite.contains("EXISTS"));
        assertTrue(onsite.contains("p.owner <> 'early'"));
        assertFalse(onsite.contains("NOT EXISTS"));
    }
}
