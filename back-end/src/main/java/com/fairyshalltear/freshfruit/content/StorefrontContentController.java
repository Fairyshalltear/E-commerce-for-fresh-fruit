package com.fairyshalltear.freshfruit.content;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.net.URI;
import java.sql.Statement;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@Validated
public class StorefrontContentController {
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public StorefrontContentController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/api/v1/admin/content/carousels")
    public ApiResponse<List<Carousel>> carousels() {
        return ApiResponse.success(jdbc.query("SELECT c.id,c.image_id,i.storage_key,c.title,c.target_url,c.sort_order,c.enabled,c.start_at,c.end_at FROM storefront_carousel c JOIN product_image i ON i.id=c.image_id ORDER BY c.sort_order,c.id",
                (rs, row) -> new Carousel(rs.getLong("id"), rs.getLong("image_id"), rs.getString("storage_key"),
                        rs.getString("title"), rs.getString("target_url"), rs.getInt("sort_order"), rs.getBoolean("enabled"),
                        rs.getTimestamp("start_at").toLocalDateTime(), local(rs.getTimestamp("end_at")))));
    }

    @PostMapping("/api/v1/admin/content/carousels")
    @Transactional
    public ApiResponse<Carousel> createCarousel(JwtAuthenticationToken auth, @Valid @RequestBody CarouselRequest request) {
        validateWindow(request.startAt(), request.endAt()); validateDestination(request.targetUrl()); validateImage(request.imageId());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO storefront_carousel(image_id,title,target_url,sort_order,enabled,start_at,end_at,created_by) VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, request.imageId()); statement.setString(2, clean(request.title()));
            statement.setString(3, clean(request.targetUrl())); statement.setInt(4, request.sortOrder());
            statement.setBoolean(5, request.enabled()); statement.setObject(6, request.startAt());
            statement.setObject(7, request.endAt()); statement.setLong(8, ownerId(auth)); return statement;
        }, key);
        Long id = generatedId(key);
        audit.record(ownerId(auth), "CAROUSEL_CREATE", "CAROUSEL", id);
        return ApiResponse.success(carousel(id));
    }

    @PutMapping("/api/v1/admin/content/carousels/{id}")
    @Transactional
    public ApiResponse<Carousel> updateCarousel(JwtAuthenticationToken auth, @PathVariable @Positive Long id,
            @Valid @RequestBody CarouselRequest request) {
        requireExists("storefront_carousel", id); validateWindow(request.startAt(), request.endAt());
        validateDestination(request.targetUrl()); validateImage(request.imageId());
        jdbc.update("UPDATE storefront_carousel SET image_id=?,title=?,target_url=?,sort_order=?,enabled=?,start_at=?,end_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                request.imageId(), clean(request.title()), clean(request.targetUrl()), request.sortOrder(), request.enabled(),
                request.startAt(), request.endAt(), id);
        audit.record(ownerId(auth), "CAROUSEL_UPDATE", "CAROUSEL", id);
        return ApiResponse.success(carousel(id));
    }

    @GetMapping("/api/v1/admin/content/announcements")
    public ApiResponse<List<Announcement>> announcements() {
        return ApiResponse.success(jdbc.query("SELECT id,title,summary,body,sort_order,pinned,enabled,start_at,end_at FROM storefront_announcement ORDER BY pinned DESC,sort_order,id",
                (rs, row) -> new Announcement(rs.getLong("id"), rs.getString("title"), rs.getString("summary"), rs.getString("body"),
                        rs.getInt("sort_order"), rs.getBoolean("pinned"), rs.getBoolean("enabled"),
                        rs.getTimestamp("start_at").toLocalDateTime(), local(rs.getTimestamp("end_at")))));
    }

    @PostMapping("/api/v1/admin/content/announcements")
    @Transactional
    public ApiResponse<Announcement> createAnnouncement(JwtAuthenticationToken auth, @Valid @RequestBody AnnouncementRequest request) {
        validateWindow(request.startAt(), request.endAt());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO storefront_announcement(title,summary,body,sort_order,pinned,enabled,start_at,end_at,created_by) VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.title().trim()); statement.setString(2, text(request.summary()));
            statement.setString(3, request.body().trim()); statement.setInt(4, request.sortOrder());
            statement.setBoolean(5, request.pinned()); statement.setBoolean(6, request.enabled());
            statement.setObject(7, request.startAt()); statement.setObject(8, request.endAt());
            statement.setLong(9, ownerId(auth)); return statement;
        }, key);
        Long id = generatedId(key);
        audit.record(ownerId(auth), "ANNOUNCEMENT_CREATE", "ANNOUNCEMENT", id);
        return ApiResponse.success(announcement(id));
    }

    @PutMapping("/api/v1/admin/content/announcements/{id}")
    @Transactional
    public ApiResponse<Announcement> updateAnnouncement(JwtAuthenticationToken auth, @PathVariable @Positive Long id,
            @Valid @RequestBody AnnouncementRequest request) {
        requireExists("storefront_announcement", id); validateWindow(request.startAt(), request.endAt());
        jdbc.update("UPDATE storefront_announcement SET title=?,summary=?,body=?,sort_order=?,pinned=?,enabled=?,start_at=?,end_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                request.title().trim(), text(request.summary()), request.body().trim(), request.sortOrder(), request.pinned(),
                request.enabled(), request.startAt(), request.endAt(), id);
        audit.record(ownerId(auth), "ANNOUNCEMENT_UPDATE", "ANNOUNCEMENT", id);
        return ApiResponse.success(announcement(id));
    }

    @GetMapping("/api/v1/public/content/carousels")
    public ApiResponse<List<PublicCarousel>> publicCarousels() {
        LocalDateTime now = LocalDateTime.now(STORE_ZONE);
        return ApiResponse.success(jdbc.query("SELECT c.id,i.storage_key,c.title,c.target_url FROM storefront_carousel c JOIN product_image i ON i.id=c.image_id WHERE c.enabled=1 AND c.start_at<=? AND (c.end_at IS NULL OR c.end_at>?) ORDER BY c.sort_order,c.id",
                (rs, row) -> new PublicCarousel(rs.getLong("id"), "/api/v1/public/product-images/" + rs.getString("storage_key"),
                        rs.getString("title"), rs.getString("target_url")), now, now));
    }

    @GetMapping("/api/v1/public/content/announcements")
    public ApiResponse<List<AnnouncementSummary>> publicAnnouncements() {
        LocalDateTime now = LocalDateTime.now(STORE_ZONE);
        return ApiResponse.success(jdbc.query("SELECT id,title,summary,pinned,start_at FROM storefront_announcement WHERE enabled=1 AND start_at<=? AND (end_at IS NULL OR end_at>?) ORDER BY pinned DESC,sort_order,id",
                (rs, row) -> new AnnouncementSummary(rs.getLong("id"), rs.getString("title"), rs.getString("summary"),
                        rs.getBoolean("pinned"), rs.getTimestamp("start_at").toLocalDateTime()), now, now));
    }

    @GetMapping("/api/v1/public/content/announcements/{id}")
    public ApiResponse<Announcement> publicAnnouncement(@PathVariable @Positive Long id) {
        LocalDateTime now = LocalDateTime.now(STORE_ZONE);
        List<Announcement> values = jdbc.query("SELECT id,title,summary,body,sort_order,pinned,enabled,start_at,end_at FROM storefront_announcement WHERE id=? AND enabled=1 AND start_at<=? AND (end_at IS NULL OR end_at>?)",
                (rs, row) -> new Announcement(rs.getLong("id"), rs.getString("title"), rs.getString("summary"), rs.getString("body"),
                        rs.getInt("sort_order"), rs.getBoolean("pinned"), rs.getBoolean("enabled"),
                        rs.getTimestamp("start_at").toLocalDateTime(), local(rs.getTimestamp("end_at"))), id, now, now);
        if (values.isEmpty()) throw new ApiException("NOT_FOUND", "公告不存在", HttpStatus.NOT_FOUND);
        return ApiResponse.success(values.get(0));
    }

    private Carousel carousel(Long id) {
        return jdbc.queryForObject("SELECT c.id,c.image_id,i.storage_key,c.title,c.target_url,c.sort_order,c.enabled,c.start_at,c.end_at FROM storefront_carousel c JOIN product_image i ON i.id=c.image_id WHERE c.id=?",
                (rs, row) -> new Carousel(rs.getLong("id"), rs.getLong("image_id"), rs.getString("storage_key"), rs.getString("title"),
                        rs.getString("target_url"), rs.getInt("sort_order"), rs.getBoolean("enabled"),
                        rs.getTimestamp("start_at").toLocalDateTime(), local(rs.getTimestamp("end_at"))), id);
    }
    private Announcement announcement(Long id) {
        return jdbc.queryForObject("SELECT id,title,summary,body,sort_order,pinned,enabled,start_at,end_at FROM storefront_announcement WHERE id=?",
                (rs, row) -> new Announcement(rs.getLong("id"), rs.getString("title"), rs.getString("summary"), rs.getString("body"), rs.getInt("sort_order"),
                        rs.getBoolean("pinned"), rs.getBoolean("enabled"), rs.getTimestamp("start_at").toLocalDateTime(), local(rs.getTimestamp("end_at"))), id);
    }
    private void requireExists(String table, Long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?", Integer.class, id) == 0)
            throw new ApiException("NOT_FOUND", "内容不存在", HttpStatus.NOT_FOUND);
    }
    private void validateImage(Long id) {
        if (id == null || jdbc.queryForObject("SELECT COUNT(*) FROM product_image WHERE id=? AND product_id IS NULL", Integer.class, id) == 0)
            throw new ApiException("VALIDATION_ERROR", "请先上传未绑定商品的有效图片", HttpStatus.BAD_REQUEST);
    }
    private void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || (end != null && !end.isAfter(start))) throw new ApiException("VALIDATION_ERROR", "发布时间范围无效", HttpStatus.BAD_REQUEST);
    }
    private void validateDestination(String target) {
        if (target == null || target.isBlank()) return;
        String value = target.trim().toLowerCase();
        boolean safeRelative = value.startsWith("/") && !value.startsWith("//")
                && !target.contains("\\") && target.chars().noneMatch(Character::isISOControl);
        boolean safeWeb = false;
        try {
            URI uri = URI.create(target.trim());
            safeWeb = ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ignored) { }
        if (!safeRelative && !safeWeb)
            throw new ApiException("VALIDATION_ERROR", "跳转目标仅支持站内路径或 http/https", HttpStatus.BAD_REQUEST);
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String text(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
    static Long generatedId(KeyHolder key) {
        var keys = key.getKeys();
        Object value = keys.get("ID");
        if (value == null) value = keys.get("id");
        if (value == null) value = keys.get("GENERATED_KEY");
        if (value == null) value = keys.get("generated_key");
        if (value == null && keys.size() == 1) value = keys.values().iterator().next();
        if (value instanceof Number number) return number.longValue();
        throw new IllegalStateException("数据库未返回内容主键");
    }
    private Long ownerId(JwtAuthenticationToken auth) { return Long.valueOf(auth.getToken().getSubject()); }
    private LocalDateTime local(java.sql.Timestamp value) { return value == null ? null : value.toLocalDateTime(); }

    public record Carousel(Long id, Long imageId, String storageKey, String title, String targetUrl, int sortOrder,
            boolean enabled, LocalDateTime startAt, LocalDateTime endAt) {}
    public record PublicCarousel(Long id, String imageUrl, String title, String targetUrl) {}
    public record Announcement(Long id, String title, String summary, String body, int sortOrder, boolean pinned,
            boolean enabled, LocalDateTime startAt, LocalDateTime endAt) {}
    public record AnnouncementSummary(Long id, String title, String summary, boolean pinned, LocalDateTime publishedAt) {}
    public record CarouselRequest(@NotNull Long imageId, @Size(max=100) String title, @Size(max=500) String targetUrl,
            @Min(0) int sortOrder, boolean enabled, @NotNull LocalDateTime startAt, LocalDateTime endAt) {}
    public record AnnouncementRequest(@NotBlank @Size(max=150) String title, @Size(max=500) String summary,
            @NotBlank @Size(max=10000) String body, @Min(0) int sortOrder, boolean pinned, boolean enabled,
            @NotNull LocalDateTime startAt, LocalDateTime endAt) {}
}
