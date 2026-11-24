package com.csg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Rehearses an empty database and a database already at V5.
 * These schemas are created only for the rehearsal and are not the trial library.
 */
class SchemaUpgradeTest {
    private static final String SERVER = "jdbc:mysql://127.0.0.1:3306/?serverTimezone=Asia/Shanghai"
        + "&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";

    @Test
    void emptyDatabaseInitializesThroughV6() throws Exception {
        String schema = "soongo_aftersales_empty_init_test";
        recreate(schema);
        migrateClasspath(schema);
        assertEquals("6", latestVersion(schema));
        assertTrue(tableExists(schema, "problem"));
        assertTrue(tableExists(schema, "account"));
        assertTrue(tableExists(schema, "security_audit"));
        assertFalse(auditStoresSecrets(schema));
    }

    @Test
    void v5DatabaseKeepsEarlyProblemsAndAccounts() throws Exception {
        String schema = "soongo_aftersales_v5_upgrade_test";
        recreate(schema);
        Path directory = Files.createTempDirectory("workbench-v5");
        for (String name : new String[] {
            "V1__trial_schema.sql", "V2__callback_result.sql", "V3__problem_number.sql",
            "V4__resume_state.sql", "V5__accounts_and_ownership.sql"
        }) {
            try (InputStream input = getClass().getResourceAsStream("/db/migration/" + name)) {
                Files.copy(input, directory.resolve(name));
            }
        }
        Flyway.configure().dataSource(url(schema), user(), password())
            .locations("filesystem:" + directory.toAbsolutePath())
            .load()
            .migrate();
        try (Connection connection = DriverManager.getConnection(url(schema), user(), password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO account (username, password_hash, role, enabled) VALUES "
                + "('keptadmin', '{bcrypt}$2a$04$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'admin', 1)");
            statement.executeUpdate("INSERT INTO problem (id, scenario, title, category, channel, description, kind, "
                + "device, owner, state) VALUES ('hist-early-problem', 'JAVA-TRIAL', '历史无主', '充电故障', 'phone', "
                + "'升级前的问题', 'fault', 'SIM-AC-001', 'early', 'open')");
        }
        migrateClasspath(schema);
        try (Connection connection = DriverManager.getConnection(url(schema), user(), password());
             Statement statement = connection.createStatement()) {
            assertEquals("early", scalar(statement, "SELECT owner FROM problem WHERE id = 'hist-early-problem'"));
            assertEquals("历史无主", scalar(statement, "SELECT title FROM problem WHERE id = 'hist-early-problem'"));
            assertEquals("keptadmin", scalar(statement, "SELECT username FROM account WHERE username = 'keptadmin'"));
            assertEquals("1", scalar(statement,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                    + "AND table_name = 'account' AND column_name = 'credentials_changed_at'"));
            assertEquals("1", scalar(statement,
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                    + "AND table_name = 'problem' AND index_name = 'problem_board_updated'"));
            String changed = scalar(statement, "SELECT credentials_changed_at FROM account WHERE username = 'keptadmin'");
            assertTrue(changed != null && !changed.isEmpty());
        }
        assertEquals("6", latestVersion(schema));
        assertFalse(auditStoresSecrets(schema));
    }

    private static void recreate(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(SERVER, user(), password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + schema);
            statement.executeUpdate("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static void migrateClasspath(String schema) {
        Flyway.configure().dataSource(url(schema), user(), password())
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private static String latestVersion(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url(schema), user(), password());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                 "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1")) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private static boolean tableExists(String schema, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(url(schema), user(), password());
             Statement statement = connection.createStatement()) {
            return "1".equals(scalar(statement, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = '" + schema + "' AND table_name = '" + table + "'"));
        }
    }

    private static boolean auditStoresSecrets(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url(schema), user(), password());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT column_name FROM information_schema.columns "
                 + "WHERE table_schema = '" + schema + "' AND table_name = 'security_audit'")) {
            List<String> names = new ArrayList<String>();
            while (rows.next()) {
                names.add(rows.getString(1).toLowerCase());
            }
            for (String name : names) {
                if (name.contains("password") || name.contains("hash") || name.contains("secret")) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String scalar(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private static String url(String schema) {
        return "jdbc:mysql://127.0.0.1:3306/" + schema + "?serverTimezone=Asia/Shanghai"
            + "&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
    }

    private static String user() {
        String value = System.getenv("MVC_TEST_DB_USER");
        if (value == null) {
            throw new IllegalStateException("MVC database settings are required");
        }
        return value;
    }

    private static String password() {
        String value = System.getenv("MVC_TEST_DB_PASSWORD");
        if (value == null) {
            throw new IllegalStateException("MVC database settings are required");
        }
        return value;
    }
}
