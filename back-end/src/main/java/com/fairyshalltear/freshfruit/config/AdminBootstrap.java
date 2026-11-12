package com.fairyshalltear.freshfruit.config;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fairyshalltear.freshfruit.account.entity.AdminAccount;
import com.fairyshalltear.freshfruit.account.mapper.AdminAccountMapper;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final AdminAccountMapper admins;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrap(AdminAccountMapper admins, PasswordEncoder passwordEncoder,
            @Value("${security.admin.initial-username:}") String username,
            @Value("${security.admin.initial-password:}") String password) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (admins.selectCount(new QueryWrapper<>()) > 0) {
            return;
        }
        if (username.isBlank() && password.isBlank()) {
            log.warn("No administrator exists and bootstrap credentials are not configured");
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Set both ADMIN_INITIAL_USERNAME and ADMIN_INITIAL_PASSWORD");
        }
        if (!username.matches("^[A-Za-z][A-Za-z0-9_]{2,49}$") || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Initial admin username must be 3-50 valid characters and password 12-72 characters");
        }

        AdminAccount admin = new AdminAccount();
        admin.setUsername(username);
        admin.setDisplayName(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setStatus(1);
        try {
            admins.insert(admin);
            log.info("Initialized the first administrator from local environment configuration");
        } catch (DuplicateKeyException exception) {
            if (admins.selectCount(new QueryWrapper<>()) == 0) {
                throw exception;
            }
        }
    }
}
