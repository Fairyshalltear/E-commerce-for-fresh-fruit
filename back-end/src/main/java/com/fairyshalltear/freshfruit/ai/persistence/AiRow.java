package com.fairyshalltear.freshfruit.ai.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

/** Internal SQL projection. Never expose credential-bearing rows as API responses. */
public class AiRow extends LinkedHashMap<String, Object> {
    public String text(String name) {
        Object value = get(name);
        try { return value instanceof java.sql.Clob clob ? clob.getSubString(1, Math.toIntExact(clob.length())) : value == null ? null : value.toString(); }
        catch (java.sql.SQLException error) { throw new IllegalStateException("AI数据读取失败", error); }
    }
    public Long number(String name) { Object value = get(name); return value == null ? null : ((Number) value).longValue(); }
    public long id() { return number("id"); }
    public int integer(String name) { Long value = number(name); return value == null ? 0 : Math.toIntExact(value); }
    public boolean flag(String name) { return integer(name) == 1; }
    public BigDecimal decimal(String name) { Object value = get(name); return value == null ? null : new BigDecimal(value.toString()); }
    public LocalDateTime time(String name) {
        Object value = get(name);
        return value == null ? null : value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }
}
