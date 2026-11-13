package com.fairyshalltear.freshfruit.account.dto;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BuyerAdminDtos {
    private BuyerAdminDtos() {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record Query(String keyword, Integer status, Integer page, Integer size) {
        public Query {
            if (page == null) page = 1;
            if (size == null) size = 12;
        }
    }
    public record BuyerSummary(Long id, String username, String phone, String nickname,
            String avatarUrl, int status, LocalDateTime createdAt) {}
    public record BuyerDetail(Long id, String username, String phone, String nickname,
            String avatarUrl, int status, LocalDateTime createdAt) {}
    public record StatusRequest(@NotNull @Min(0) @Max(1) Integer status) {}
    public record PasswordResetRequest(@NotBlank @Size(min = 8, max = 72) String temporaryPassword) {}
}
