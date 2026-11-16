package com.fairyshalltear.freshfruit.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:analyticsintegration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:test-schema.sql",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.admin.initial-username=analyticsowner", "security.admin.initial-password=OwnerPassword123!"
})
@AutoConfigureMockMvc
class AdminAnalyticsIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void aggregatesSalesRefundsBuyersAndOrdersAndRejectsInvalidRanges() throws Exception {
        String token = login();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
        jdbc.update("INSERT INTO buyer_account(username,phone,password_hash,nickname,created_at) VALUES('activebuyer','13800000999','hash','活跃买家',?)", timestamp);
        long buyerId = jdbc.queryForObject("SELECT id FROM buyer_account WHERE username='activebuyer'", Long.class);
        jdbc.update("INSERT INTO buyer_login_activity(buyer_id,created_at) VALUES(?,?)", buyerId, timestamp);
        jdbc.update("INSERT INTO product_category(name,sort_order,status) VALUES('统计分类',0,1)");
        jdbc.update("INSERT INTO fruit_product(category_id,name,status,deleted) VALUES(1,'统计苹果',1,0)");
        jdbc.update("INSERT INTO fruit_product(category_id,name,status,deleted) VALUES(1,'并列苹果',1,0),(1,'已删除苹果',1,1)");
        jdbc.update("INSERT INTO product_sku(product_id,specification,price,status) VALUES(1,'500g',10,1)");
        jdbc.update("INSERT INTO product_sku(product_id,specification,price,status) VALUES(2,'500g',10,1),(3,'500g',10,1)");
        jdbc.update("INSERT INTO sku_inventory(sku_id,on_hand,reserved) VALUES(1,20,2)");
        jdbc.update("INSERT INTO sku_inventory(sku_id,on_hand,reserved) VALUES(2,18,0),(3,99,0)");
        insertOrder("AN-PAID", buyerId, timestamp, "CANCELLED", 20);
        insertOrder("AN-FAILED", buyerId, timestamp, "CANCELLED", 10);
        jdbc.update("INSERT INTO payment_record(order_id,idempotency_key,channel,result,amount,created_at) VALUES(1,'pay-ok','ALIPAY','SUCCESS',20,?),(2,'pay-fail','ALIPAY','FAILED',10,?)", timestamp, timestamp);
        jdbc.update("UPDATE shop_order SET paid_at=? WHERE id=1", timestamp);
        jdbc.update("INSERT INTO order_item(order_id,product_id,sku_id,product_name_snapshot,specification_snapshot,unit_price,quantity,line_amount) VALUES(1,2,2,'并列苹果','500g',10,19,190),(1,3,3,'已删除苹果','500g',10,30,300)");
        jdbc.update("INSERT INTO after_sale_request(request_no,order_id,buyer_id,type,status,reason,requested_amount,created_at,updated_at) VALUES('AN-REFUND',1,?,'REFUND','COMPLETED','测试退款',10,?,?)", buyerId, timestamp, timestamp);
        jdbc.update("INSERT INTO after_sale_item(request_id,order_item_id,quantity,refund_quantity,restock_quantity,unit_price) VALUES(1,1,1,1,0,10)");
        jdbc.update("INSERT INTO refund_record(request_id,order_id,amount,result,created_at) VALUES(1,1,10,'SIMULATED_SUCCESS',?)", timestamp);
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM shop_order", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM shop_order WHERE created_at>=? AND created_at<?", Integer.class, today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject("SELECT SUM(cnt) FROM (SELECT COUNT(*) cnt FROM shop_order WHERE created_at>=? AND created_at<? GROUP BY status) s", Integer.class, today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE result='SUCCESS'", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE result='SUCCESS' AND created_at>=? AND created_at<?", Integer.class, today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        MvcResult result = mockMvc.perform(get("/api/v1/admin/analytics?period=CUSTOM&from=" + today + "&to=" + today + "&grain=DAY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.salesAmount").value(10.0))
                .andExpect(jsonPath("$.data.units").value(68)).andExpect(jsonPath("$.data.orders").value(2))
                .andExpect(jsonPath("$.data.registrations").value(1)).andExpect(jsonPath("$.data.activeBuyers").value(1))
                .andExpect(jsonPath("$.data.orderStatuses.CANCELLED").value(2))
                .andExpect(jsonPath("$.data.trend.length()").value(1))
                .andExpect(jsonPath("$.data.hotProducts.length()").value(2))
                .andExpect(jsonPath("$.data.hotProducts[0].id").value(1))
                .andExpect(jsonPath("$.data.hotProducts[0].units").value(19))
                .andExpect(jsonPath("$.data.hotProducts[1].id").value(2))
                .andExpect(jsonPath("$.data.hotProducts[1].units").value(19))
                .andExpect(jsonPath("$.data.stockProducts.length()" ).value(2))
                .andExpect(jsonPath("$.data.stockProducts[0].id").value(1))
                .andExpect(jsonPath("$.data.stockProducts[1].id").value(2))
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(190.0, body.path("data").path("hotProducts").get(0).path("salesAmount").asDouble());

        mockMvc.perform(get("/api/v1/admin/analytics?period=WEEK&grain=WEEK")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value(today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString()))
                .andExpect(jsonPath("$.data.trend.length()").value(1));
        LocalDate emptyDay = today.minusDays(1);
        mockMvc.perform(get("/api/v1/admin/analytics?period=CUSTOM&from=" + emptyDay + "&to=" + emptyDay + "&grain=DAY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.salesAmount").value(0))
                .andExpect(jsonPath("$.data.units").value(0)).andExpect(jsonPath("$.data.orders").value(0))
                .andExpect(jsonPath("$.data.trend.length()").value(1));

        mockMvc.perform(get("/api/v1/admin/analytics?period=CUSTOM&from=" + today + "&to=" + today.minusDays(1) + "&grain=DAY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/analytics?period=TODAY&grain=HOUR")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private void insertOrder(String no, Long buyer, LocalDateTime timestamp, String status, int quantity) {
        jdbc.update("INSERT INTO shop_order(order_no,buyer_id,idempotency_key,status,subtotal,shipping_fee,total_amount,recipient_name,phone,province,city,district,detail_address,created_at,paid_at) VALUES(?,?,? ,?, ?,0,?,'收件人','13800000000','省','市','区','路',?,?)",
                no, buyer, no, status, quantity * 10, quantity * 10, timestamp, "PENDING_SHIPMENT".equals(status) ? timestamp : null);
        long orderId = jdbc.queryForObject("SELECT id FROM shop_order WHERE order_no=?", Long.class, no);
        jdbc.update("INSERT INTO order_item(order_id,product_id,sku_id,product_name_snapshot,specification_snapshot,unit_price,quantity,line_amount) VALUES(?,1,1,'统计苹果','500g',10,?,?)",
                orderId, quantity, quantity * 10);
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"analyticsowner","password":"OwnerPassword123!"}
                                """))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
