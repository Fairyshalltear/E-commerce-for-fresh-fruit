package com.fairyshalltear.freshfruit.ai.persistence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import static com.fairyshalltear.freshfruit.ai.AiErrors.missing;

@Repository
public class AiMapper {
    private final JdbcTemplate jdbc;
    public AiMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<AiRow> rows(String sql, Object... arguments) {
        return jdbc.query(sql, (rs, index) -> {
            AiRow row = new AiRow();
            for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++)
                row.put(rs.getMetaData().getColumnLabel(column).toLowerCase(Locale.ROOT), rs.getObject(column));
            return row;
        }, arguments);
    }
    public Optional<AiRow> optional(String sql, Object... arguments) { return rows(sql, arguments).stream().findFirst(); }
    public AiRow one(String sql, Object... arguments) { return optional(sql, arguments).orElseThrow(() -> missing()); }
    public int update(String sql, Object... arguments) { return jdbc.update(sql, arguments); }
    public long count(String sql, Object... arguments) { return jdbc.queryForObject(sql, Long.class, arguments); }
    public long insert(String table, Map<String, Object> values) {
        // Table and column identifiers only come from source constants; user input is bound below.
        if (!table.matches("ai_[a-z_]+") || values.keySet().stream().anyMatch(key -> !key.matches("[a-z_]+")))
            throw new IllegalArgumentException("无效持久化标识");
        String sql = "INSERT INTO " + table + "(" + String.join(",", values.keySet()) + ") VALUES(" +
                String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")";
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            int index = 1;
            for (Object value : values.values()) statement.setObject(index++, value);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("插入未返回主键");
        return key.getKey().longValue();
    }
    public static Map<String, Object> values(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("字段必须成对");
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put((String) pairs[index], pairs[index + 1]);
        return result;
    }
}
