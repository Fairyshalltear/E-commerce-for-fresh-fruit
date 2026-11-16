package com.fairyshalltear.freshfruit.analytics;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@Validated
public class AdminAnalyticsController {
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;

    public AdminAnalyticsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public ApiResponse<Dashboard> dashboard(@Valid @ModelAttribute RangeRequest request) {
        DateRange range = parse(request);
        LocalDateTime from = range.from().atStartOfDay();
        LocalDateTime to = range.to().plusDays(1).atStartOfDay();
        Map<LocalDate, BigDecimal> sales = new LinkedHashMap<>();
        Map<LocalDate, Long> units = new LinkedHashMap<>(), orders = new LinkedHashMap<>(), registrations = new LinkedHashMap<>();
        addAmounts(sales, "SELECT DATE(created_at),SUM(amount) FROM payment_record WHERE result='SUCCESS' AND created_at>=? AND created_at<? GROUP BY DATE(created_at)", from, to, 1);
        addAmounts(sales, "SELECT DATE(created_at),SUM(amount) FROM refund_record WHERE result='SIMULATED_SUCCESS' AND created_at>=? AND created_at<? GROUP BY DATE(created_at)", from, to, -1);
        addCounts(orders, "SELECT DATE(created_at),COUNT(*) FROM shop_order WHERE created_at>=? AND created_at<? GROUP BY DATE(created_at)", from, to);
        addCounts(registrations, "SELECT DATE(created_at),COUNT(*) FROM buyer_account WHERE created_at>=? AND created_at<? GROUP BY DATE(created_at)", from, to);
        addCounts(units, "SELECT DATE(so.paid_at),SUM(oi.quantity) FROM shop_order so JOIN order_item oi ON oi.order_id=so.id WHERE so.paid_at>=? AND so.paid_at<? GROUP BY DATE(so.paid_at)", from, to);
        addRefundedUnits(units, from, to);

        List<TrendPoint> trend = fill(range, request.grain(), sales, units, orders, registrations);
        long active = jdbc.queryForObject("SELECT COUNT(DISTINCT buyer_id) FROM (SELECT buyer_id FROM buyer_login_activity WHERE created_at>=? AND created_at<? UNION SELECT buyer_id FROM shop_order WHERE created_at>=? AND created_at<?) activity",
                Long.class, from, to, from, to);
        Map<String, Long> status = new LinkedHashMap<>();
        jdbc.query("SELECT status,COUNT(*) FROM shop_order WHERE created_at>=? AND created_at<? GROUP BY status ORDER BY status",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) status.put(rs.getString(1), rs.getLong(2));
                    return null;
                }, from, to);
        BigDecimal totalSales = trend.stream().map(TrendPoint::salesAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalUnits = trend.stream().mapToLong(TrendPoint::units).sum();
        long totalOrders = trend.stream().mapToLong(TrendPoint::orders).sum();
        long totalRegistrations = trend.stream().mapToLong(TrendPoint::registrations).sum();
        return ApiResponse.success(new Dashboard(range.from(), range.to(), request.grain(), totalSales,
                totalUnits, totalOrders, totalRegistrations, active, status, trend,
                topSales(from, to), topStock()));
    }

    private DateRange parse(RangeRequest request) {
        LocalDate today = LocalDate.now(STORE_ZONE);
        LocalDate from, to;
        String period = request.period() == null ? "MONTH" : request.period().toUpperCase();
        switch (period) {
            case "TODAY" -> { from = today; to = today; }
            case "WEEK" -> { from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); to = today; }
            case "MONTH" -> { from = today.withDayOfMonth(1); to = today; }
            case "LAST_30_DAYS" -> { from = today.minusDays(29); to = today; }
            case "CUSTOM" -> { from = request.from(); to = request.to(); }
            default -> throw validation("不支持的统计周期");
        }
        if (from == null || to == null || from.isAfter(to) || from.isBefore(today.minusYears(3)) || to.isAfter(today)) {
            throw validation("自定义日期范围无效，且最多查询最近三年");
        }
        String grain = request.grain() == null ? "DAY" : request.grain().toUpperCase();
        if (!List.of("DAY", "WEEK", "MONTH").contains(grain)) throw validation("不支持的统计粒度");
        return new DateRange(from, to);
    }

    private List<TrendPoint> fill(DateRange range, String requestedGrain, Map<LocalDate, BigDecimal> sales,
            Map<LocalDate, Long> units, Map<LocalDate, Long> orders, Map<LocalDate, Long> registrations) {
        String grain = requestedGrain == null ? "DAY" : requestedGrain.toUpperCase();
        Map<LocalDate, Accumulator> buckets = new LinkedHashMap<>();
        LocalDate start = bucket(range.from(), grain), end = bucket(range.to(), grain);
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = next(cursor, grain)) buckets.put(cursor, new Accumulator());
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            Accumulator bucket = buckets.get(bucket(date, grain));
            bucket.sales = bucket.sales.add(sales.getOrDefault(date, BigDecimal.ZERO));
            bucket.units += units.getOrDefault(date, 0L);
            bucket.orders += orders.getOrDefault(date, 0L);
            bucket.registrations += registrations.getOrDefault(date, 0L);
        }
        return buckets.entrySet().stream().map(entry -> new TrendPoint(entry.getKey(), entry.getValue().sales,
                entry.getValue().units, entry.getValue().orders, entry.getValue().registrations)).toList();
    }

    private void addAmounts(Map<LocalDate, BigDecimal> target, String sql, LocalDateTime from, LocalDateTime to, int sign) {
        jdbc.query(sql, (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { while (rs.next()) {
            LocalDate day = dateFromTimestamp(rs.getString(1));
            target.merge(day, rs.getBigDecimal(2).multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
        } return null; }, from, to);
    }

    private void addCounts(Map<LocalDate, Long> target, String sql, LocalDateTime from, LocalDateTime to) {
        jdbc.query(sql, (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
            while (rs.next()) target.merge(dateFromTimestamp(rs.getString(1)), rs.getLong(2), Long::sum);
            return null;
        }, from, to);
    }

    private void addRefundedUnits(Map<LocalDate, Long> units, LocalDateTime from, LocalDateTime to) {
        jdbc.query("SELECT DATE(rr.created_at),SUM(asi.refund_quantity) FROM refund_record rr JOIN after_sale_item asi ON asi.request_id=rr.request_id WHERE rr.result='SIMULATED_SUCCESS' AND rr.created_at>=? AND rr.created_at<? GROUP BY DATE(rr.created_at)",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) units.merge(dateFromTimestamp(rs.getString(1)), -rs.getLong(2), Long::sum);
                    return null;
                }, from, to);
    }

    private LocalDate dateFromTimestamp(String value) { return LocalDate.parse(value.substring(0, 10)); }

    private List<RankedProduct> topSales(LocalDateTime from, LocalDateTime to) {
        Map<Long, ProductSales> products = new LinkedHashMap<>();
        jdbc.query("SELECT fp.id,fp.name,SUM(oi.quantity) units,SUM(oi.unit_price*oi.quantity) amount FROM fruit_product fp JOIN order_item oi ON oi.product_id=fp.id JOIN shop_order so ON so.id=oi.order_id WHERE fp.deleted=0 AND so.paid_at>=? AND so.paid_at<? GROUP BY fp.id,fp.name",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { while (rs.next()) products.put(rs.getLong("id"), new ProductSales(rs.getLong("id"), rs.getString("name"), rs.getLong("units"), rs.getBigDecimal("amount"))); return null; }, from, to);
        jdbc.query("SELECT fp.id,fp.name FROM fruit_product fp JOIN order_item oi ON oi.product_id=fp.id JOIN after_sale_item asi ON asi.order_item_id=oi.id JOIN refund_record rr ON rr.request_id=asi.request_id WHERE fp.deleted=0 AND rr.result='SIMULATED_SUCCESS' AND rr.created_at>=? AND rr.created_at<? GROUP BY fp.id,fp.name",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { while (rs.next()) products.putIfAbsent(rs.getLong("id"), new ProductSales(rs.getLong("id"), rs.getString("name"), 0, BigDecimal.ZERO)); return null; }, from, to);
        jdbc.query("SELECT fp.id,SUM(asi.refund_quantity) units,SUM(asi.unit_price*asi.refund_quantity) amount FROM refund_record rr JOIN after_sale_item asi ON asi.request_id=rr.request_id JOIN order_item oi ON oi.id=asi.order_item_id JOIN fruit_product fp ON fp.id=oi.product_id WHERE fp.deleted=0 AND rr.result='SIMULATED_SUCCESS' AND rr.created_at>=? AND rr.created_at<? GROUP BY fp.id",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { while (rs.next()) {
                    ProductSales product = products.get(rs.getLong("id"));
                    if (product != null) { product.units -= rs.getLong("units"); product.amount = product.amount.subtract(rs.getBigDecimal("amount")); }
                } return null; }, from, to);
        return products.values().stream().filter(product -> product.units > 0)
                .sorted(Comparator.comparingLong((ProductSales product) -> product.units).reversed().thenComparingLong(product -> product.id))
                .limit(10).map(product -> new RankedProduct(product.id, product.name, product.units, product.amount, null)).toList();
    }

    private List<RankedProduct> topStock() {
        return jdbc.query("SELECT fp.id,fp.name,SUM(si.on_hand-si.reserved) available FROM fruit_product fp JOIN product_sku ps ON ps.product_id=fp.id JOIN sku_inventory si ON si.sku_id=ps.id WHERE fp.deleted=0 AND ps.status=1 GROUP BY fp.id,fp.name ORDER BY available DESC,fp.id LIMIT 10",
                (rs, row) -> new RankedProduct(rs.getLong("id"), rs.getString("name"), null, null, rs.getLong("available")));
    }

    private LocalDate bucket(LocalDate date, String grain) {
        return switch (grain.toUpperCase()) {
            case "WEEK" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "MONTH" -> date.withDayOfMonth(1);
            default -> date;
        };
    }
    private LocalDate next(LocalDate date, String grain) {
        return switch (grain.toUpperCase()) { case "WEEK" -> date.plusWeeks(1); case "MONTH" -> date.plusMonths(1); default -> date.plusDays(1); };
    }
    private ApiException validation(String message) { return new ApiException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST); }

    public record RangeRequest(String period, LocalDate from, LocalDate to, String grain) {}
    public record Dashboard(LocalDate from, LocalDate to, String grain, BigDecimal salesAmount,
            long units, long orders, long registrations, long activeBuyers, Map<String,Long> orderStatuses,
            List<TrendPoint> trend, List<RankedProduct> hotProducts, List<RankedProduct> stockProducts) {}
    public record TrendPoint(LocalDate date, BigDecimal salesAmount, long units, long orders, long registrations) {}
    public record RankedProduct(Long id, String name, Long units, BigDecimal salesAmount, Long availableStock) {}
    private record DateRange(LocalDate from, LocalDate to) {}
    private static final class Accumulator { BigDecimal sales=BigDecimal.ZERO; long units,orders,registrations; }
    private static final class ProductSales {
        final Long id; final String name; long units; BigDecimal amount;
        ProductSales(Long id, String name, long units, BigDecimal amount) { this.id=id; this.name=name; this.units=units; this.amount=amount; }
    }
}
