package com.fairyshalltear.freshfruit.ai.model;

import com.fairyshalltear.freshfruit.ai.AiDtos.Parameters;
import com.fairyshalltear.freshfruit.ai.persistence.AiRow;

public record AiModelSettings(long id, String chatBaseUrl, String chatModel, String chatCipher,
        String embeddingBaseUrl, String embeddingModel, String embeddingCipher, int dimensions,
        String promptTemplate, boolean enabled, Parameters parameters) {
    public static AiModelSettings from(AiRow row) {
        return new AiModelSettings(row.id(),row.text("chat_base_url"),row.text("chat_model"),row.text("chat_key_cipher"),
            row.text("embedding_base_url"),row.text("embedding_model"),row.text("embedding_key_cipher"),row.integer("embedding_dimensions"),
            row.text("prompt_template"),row.flag("enabled"),new Parameters(row.decimal("temperature"),row.integer("max_output_tokens"),
                row.integer("history_turns"),row.integer("connect_timeout_seconds"),row.integer("first_content_seconds"),
                row.integer("idle_timeout_seconds"),row.integer("total_timeout_seconds"),row.integer("buyer_per_minute"),row.integer("guest_per_minute"),
                row.integer("buyer_daily"),row.integer("guest_daily"),row.integer("global_daily_calls")));
    }
    // Only these five template variables can be supplied; fixed safety constraints are added outside this template.
    public static final String DEFAULT_PROMPT="你是鲜果商城客服，请用简洁、友好的中文回答。\n问题：{question}\n已完成对话：{history}\n知识依据：{knowledge}\n当前商品：{products}\n业务政策：{policy}";
}
