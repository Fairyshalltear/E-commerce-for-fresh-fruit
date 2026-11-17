package com.fairyshalltear.freshfruit.ai;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class AiDtos {
    private AiDtos() {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record Context(@Positive Long productId, @Positive Long orderId, @Positive Long afterSaleId,
            @DecimalMin("0.01") @DecimalMax("99999999.99") BigDecimal budget,
            @Min(1) @Max(12) Integer month, Boolean giftBox,
            @Pattern(regexp="DAILY|ELDER_GIFT|FRIEND_GIFT|BUSINESS_GIFT|FAMILY") String useCase,
            @Size(max=100) String taste, @Min(1) @Max(999) Integer quantity) {
        public static Context empty() { return new Context(null,null,null,null,null,null,null,null,null); }
    }
    public record NewConversation(@Valid Context context) {}
    public record MessageInput(@NotBlank @Size(max=80) String clientRequestKey,
            @NotBlank @Size(max=1000) String text, @Valid Context context) {}
    public record RetryInput(@NotBlank @Size(max=80) String clientRequestKey) {}
    public record FeedbackInput(@NotNull Boolean helpful, @Size(max=500) String reason) {}
    public record Card(String type, Long id, String title, String subtitle, String price, String image,
            String path, Context context) {}
    public record Source(int ordinal, String type, Long id, Long version, String title, String excerpt, String path) {}
    public record Conversation(long id, String title, String ownerKind, Context context,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expiresAt) {}
    public record Message(long id, long sequence, String role, String content, Long requestId, String status,
            String mode, String scene, String errorCode, String errorMessage, List<Card> cards,
            List<Source> sources, Boolean helpful, String feedbackReason, LocalDateTime createdAt) {}
    public record ConversationDetail(Conversation conversation, List<Message> messages) {}
    public record Request(long id, long conversationId, long questionId, long answerId,
            String status, String scene, String mode, String errorCode, String errorMessage,
            LocalDateTime createdAt, LocalDateTime finishedAt) {}
    public record GuestSession(String token, long conversationId, LocalDateTime expiresAt) {}
    public record KnowledgeInput(@NotBlank @Size(max=150) String title,
            @NotBlank @Pattern(regexp="FRUIT|PRODUCT|FAQ|POLICY") String type,
            @NotBlank @Size(max=20000) String body, @Size(max=300) String standardQuestion,
            @Size(max=30) List<@NotBlank @Size(max=300) String> aliases,
            @Size(max=150) String sourceLabel, @Positive Long productId, @Min(0) Long version) {}
    public record Knowledge(long id, String type, String originKind, Long productId, String title,
            String body, String standardQuestion, List<String> aliases, String sourceLabel, boolean enabled,
            long version, Long activeRevision, Long latestRevision, String indexStatus, String indexError,
            LocalDateTime updatedAt, List<Map<String,Object>> revisions) {}
    public record VersionInput(@NotNull @Min(0) Long version) {}
    public record EnabledInput(@NotNull Boolean enabled, @NotNull @Min(0) Long version) {}
    public record SearchInput(@NotBlank @Size(max=1000) String question) {}
    public record RuleInput(@NotBlank @Size(max=100) String name,
            @NotBlank @Pattern(regexp="INPUT|OUTPUT") String scope,
            @NotBlank @Pattern(regexp="LITERAL|ALL|ANY") String matchMode,
            @NotEmpty @Size(max=20) List<@NotBlank @Size(max=100) String> terms,
            @Min(0) @Max(999) int priority, @NotBlank @Size(max=200) String message,
            boolean enabled, @Min(0) Long version) {}
    public record Rule(long id, String name, String scope, String matchMode, List<String> terms,
            int priority, String message, boolean enabled, long version) {}
    public record ModelInput(@Positive Long baseId, @NotBlank @Size(max=100) String name,
            @Size(max=500) String chatBaseUrl, @NotBlank @Size(max=100) String chatModel,
            @Size(max=500) String chatKey, boolean clearChatKey,
            @Size(max=500) String embeddingBaseUrl, @Size(max=100) String embeddingModel,
            @Size(max=500) String embeddingKey, boolean clearEmbeddingKey, boolean reuseChatKey,
            @Min(64) @Max(2048) int embeddingDimensions,
            @NotBlank @Size(max=6000) String promptTemplate, @Valid Parameters parameters) {}
    public record Parameters(@DecimalMin("0") @DecimalMax("2") BigDecimal temperature,
            @Min(64) @Max(4096) Integer maxOutputTokens, @Min(0) @Max(10) Integer historyTurns,
            @Min(1) @Max(30) Integer connectTimeoutSeconds, @Min(2) @Max(60) Integer firstContentSeconds,
            @Min(2) @Max(60) Integer idleTimeoutSeconds, @Min(10) @Max(180) Integer totalTimeoutSeconds,
            @Min(1) @Max(60) Integer buyerPerMinute, @Min(1) @Max(20) Integer guestPerMinute,
            @Min(1) @Max(1000) Integer buyerDaily, @Min(1) @Max(100) Integer guestDaily,
            @Min(1) @Max(100000) Integer globalDailyCalls) {
        public Parameters {
            temperature = temperature == null ? new BigDecimal("0.20") : temperature;
            maxOutputTokens = maxOutputTokens == null ? 1200 : maxOutputTokens;
            historyTurns = historyTurns == null ? 10 : historyTurns;
            connectTimeoutSeconds = connectTimeoutSeconds == null ? 10 : connectTimeoutSeconds;
            firstContentSeconds = firstContentSeconds == null ? 20 : firstContentSeconds;
            idleTimeoutSeconds = idleTimeoutSeconds == null ? 15 : idleTimeoutSeconds;
            totalTimeoutSeconds = totalTimeoutSeconds == null ? 90 : totalTimeoutSeconds;
            buyerPerMinute = buyerPerMinute == null ? 10 : buyerPerMinute;
            guestPerMinute = guestPerMinute == null ? 3 : guestPerMinute;
            buyerDaily = buyerDaily == null ? 100 : buyerDaily;
            guestDaily = guestDaily == null ? 20 : guestDaily;
            globalDailyCalls = globalDailyCalls == null ? 1000 : globalDailyCalls;
        }
        public static Parameters defaults() { return new Parameters(null,null,null,null,null,null,null,null,null,null,null,null); }
    }
    public record Capability(String name, boolean success, long elapsedMs, String message, Integer dimensions) {}
}
