package com.fairyshalltear.freshfruit.account.dto;

public record AuthSession(String accessToken, String tokenType, long expiresInSeconds, AccountSummary account) {
}
