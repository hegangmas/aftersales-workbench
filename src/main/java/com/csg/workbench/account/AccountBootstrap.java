package com.csg.workbench.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AccountBootstrap implements ApplicationRunner {
    private final AccountService accounts;
    private final String username;
    private final String passwordHash;

    public AccountBootstrap(AccountService accounts,
                            @Value("${WORKBENCH_LOGIN_USER}") String username,
                            @Value("${WORKBENCH_LOGIN_PASSWORD_HASH}") String passwordHash) {
        this.accounts = accounts;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!username.matches("[a-zA-Z0-9_.-]{1,20}")
            || !passwordHash.matches("\\{bcrypt}\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalStateException("Configure a workbench username and a {bcrypt} password hash");
        }
        accounts.bootstrapIfEmpty(username, passwordHash);
    }
}
