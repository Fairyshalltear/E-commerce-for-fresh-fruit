package com.fairyshalltear.freshfruit.ai;

import java.sql.Clob;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiJson {
    private final ObjectMapper mapper;
    public AiJson(ObjectMapper mapper) { this.mapper = mapper; }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("AI数据序列化失败", error); }
    }
    public String string(Object value) {
        try { return value instanceof Clob clob ? clob.getSubString(1, Math.toIntExact(clob.length())) : value == null ? null : value.toString(); }
        catch (Exception error) { throw new IllegalStateException("AI数据读取失败", error); }
    }
    public JsonNode tree(Object value) {
        if (value == null) return mapper.createObjectNode();
        try { return mapper.readTree(string(value)); }
        catch (Exception error) { throw new IllegalStateException("AI数据格式无效", error); }
    }
    public <T> T read(Object value, Class<T> type) {
        try { return mapper.readValue(string(value), type); }
        catch (Exception error) { throw new IllegalStateException("AI数据格式无效", error); }
    }
    public <T> List<T> list(Object value, Class<T> type) {
        if (value == null) return List.of();
        try { return mapper.readValue(string(value), mapper.getTypeFactory().constructCollectionType(List.class, type)); }
        catch (Exception error) { throw new IllegalStateException("AI列表格式无效", error); }
    }
    public List<String> strings(Object value) { return list(value, String.class); }
}
