package com.fairyshalltear.freshfruit.account.dto;

import java.util.List;

public record AccountSummary(Long id, String username, String displayName, String role,
        List<String> modulePermissions, List<String> sensitivePermissions) {
    public AccountSummary(Long id, String username, String displayName, String role) {
        this(id, username, displayName, role, List.of(), List.of());
    }
}
