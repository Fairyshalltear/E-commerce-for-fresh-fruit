package com.fairyshalltear.freshfruit.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.catalog.entity.FruitProduct;

import static com.fairyshalltear.freshfruit.catalog.CatalogErrors.invalid;

/** Optional catalog facts. Missing update fields preserve values; explicit null clears them. */
public final class ProductConsultation {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final Set<String> FIELDS = Set.of("sweetnessDescription", "seasonalMonths", "giftBox", "useCases");
    public static final Set<String> USE_CASES = Set.of("DAILY", "ELDER_GIFT", "FRIEND_GIFT", "BUSINESS_GIFT", "FAMILY");
    private ProductConsultation() {}

    public static void apply(FruitProduct product, Map<String, Object> fields) {
        if (product.getIsGiftBox() == null) product.setIsGiftBox(0);
        if (fields.containsKey("sweetnessDescription")) {
            Object value = fields.get("sweetnessDescription");
            if (value != null && (!(value instanceof String text) || text.length() > 200))
                throw invalid("甜度描述必须是最多 200 字的文字");
            product.setSweetnessDescription(value == null ? null : ((String) value).trim());
        }
        if (fields.containsKey("giftBox")) {
            Object value = fields.get("giftBox");
            if (value != null && !(value instanceof Boolean)) throw invalid("礼盒标记必须是布尔值");
            product.setIsGiftBox(Boolean.TRUE.equals(value) ? 1 : 0);
        }
        if (fields.containsKey("seasonalMonths")) {
            Object value = fields.get("seasonalMonths");
            if (value != null && (!(value instanceof List<?> list) || list.size() > 12
                    || list.stream().anyMatch(item -> !(item instanceof Integer n) || n < 1 || n > 12)))
                throw invalid("应季月份必须为 1 至 12 的整数列表");
            product.setSeasonalMonths(value == null ? null : encode(((List<?>) value).stream().distinct().sorted().toList()));
        }
        if (fields.containsKey("useCases")) {
            Object value = fields.get("useCases");
            if (value != null && (!(value instanceof List<?> list) || list.size() > USE_CASES.size()
                    || list.stream().anyMatch(item -> !(item instanceof String s) || !USE_CASES.contains(s))))
                throw invalid("商品用途包含未知场景");
            product.setUseCases(value == null ? null : encode(((List<?>) value).stream().distinct().toList()));
        }
    }

    public static List<Integer> months(String json) { return decode(json, new TypeReference<List<Integer>>() {}); }
    public static List<String> uses(String json) { return decode(json, new TypeReference<List<String>>() {}); }
    private static String encode(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("商品属性序列化失败", error); }
    }
    private static <T> List<T> decode(String value, TypeReference<List<T>> type) {
        if (value == null || value.isBlank()) return List.of();
        try { return JSON.readValue(value, type); }
        catch (Exception error) { throw new IllegalStateException("商品属性格式无效", error); }
    }
}
