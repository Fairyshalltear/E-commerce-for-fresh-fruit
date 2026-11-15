package com.fairyshalltear.freshfruit.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.account.entity.UserAddress;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.account.mapper.UserAddressMapper;
import com.fairyshalltear.freshfruit.cart.mapper.CartItemMapper;
import com.fairyshalltear.freshfruit.catalog.entity.*;
import com.fairyshalltear.freshfruit.catalog.mapper.*;
import com.fairyshalltear.freshfruit.inventory.entity.SkuInventory;
import com.fairyshalltear.freshfruit.inventory.entity.InventoryMovement;
import com.fairyshalltear.freshfruit.inventory.mapper.InventoryMovementMapper;
import com.fairyshalltear.freshfruit.inventory.mapper.SkuInventoryMapper;
import com.fairyshalltear.freshfruit.order.entity.ShopOrder;
import com.fairyshalltear.freshfruit.order.mapper.ShopOrderMapper;
import com.fairyshalltear.freshfruit.order.mapper.OrderItemMapper;
import com.fairyshalltear.freshfruit.order.mapper.PaymentRecordMapper;
import com.fairyshalltear.freshfruit.review.entity.ProductReviewDeletionAudit;
import com.fairyshalltear.freshfruit.review.mapper.ProductReviewDeletionAuditMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orderintegration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:test-schema.sql",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.admin.initial-username=orderadmin", "security.admin.initial-password=OrderAdminPassword123!"
})
@AutoConfigureMockMvc
class OrderIntegrationTest {
    @Autowired private MockMvc mvc; @Autowired private ObjectMapper json;
    @Autowired private ProductCategoryMapper categories; @Autowired private FruitProductMapper products;
    @Autowired private ProductSkuMapper skus; @Autowired private SkuInventoryMapper stocks;
    @Autowired private UserAddressMapper addresses; @Autowired private CartItemMapper carts;
    @Autowired private BuyerAccountMapper buyers;
    @Autowired private ShopOrderMapper orders;
    @Autowired private OrderItemMapper orderItems;
    @Autowired private PaymentRecordMapper paymentRecords;
    @Autowired private InventoryMovementMapper movements;
    @Autowired private ProductReviewDeletionAuditMapper reviewAudits;
    @Autowired private OrderService orderService;

    @Test
    void submitsIdempotentlyPaysAndCancelsWithStockConsistencyAndOwnership() throws Exception {
        String buyer=buyerToken("orderbuyer"),other=buyerToken("anotherbuyer");
        long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","orderbuyer")).getId();
        Fixture f=fixture(buyerId);
        mvc.perform(post("/api/v1/buyer/cart").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":"+f.skuId+",\"quantity\":2}"))
                .andExpect(status().isOk());
        String request="{\"addressId\":"+f.addressId+",\"requestKey\":\"submit-1\"}";
        mvc.perform(post("/api/v1/buyer/orders/checkout-preview").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true)).andExpect(jsonPath("$.data.total").value("20.00"));
        JsonNode order=body(mvc.perform(post("/api/v1/buyer/orders").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn());
        long id=order.path("data").path("id").asLong();
        assertEquals("PENDING_PAYMENT",order.path("data").path("status").asText());
        assertEquals(2,stocks.selectById(f.skuId).getReserved());
        assertEquals(0,carts.selectCount(new QueryWrapper<com.fairyshalltear.freshfruit.cart.entity.CartItem>().eq("buyer_id",buyerId)));
        JsonNode duplicate=body(mvc.perform(post("/api/v1/buyer/orders").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn());
        assertEquals(id,duplicate.path("data").path("id").asLong());
        mvc.perform(get("/api/v1/buyer/orders/"+id).header("Authorization",bearer(other))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/buyer/orders/"+id+"/payment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"WECHAT\",\"result\":\"FAILED\",\"requestKey\":\"payment-fail\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        mvc.perform(post("/api/v1/buyer/orders/"+id+"/cancel").header("Authorization",bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertEquals(0,stocks.selectById(f.skuId).getReserved());
        mvc.perform(post("/api/v1/buyer/orders/"+id+"/cancel").header("Authorization",bearer(buyer))).andExpect(status().isConflict());
    }

    @Test
    void concurrentCheckoutsCannotOversellTheSameSku() throws Exception {
        String firstBuyer=buyerToken("stockracebuyer1"),secondBuyer=buyerToken("stockracebuyer2");
        long firstId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","stockracebuyer1")).getId();
        long secondId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","stockracebuyer2")).getId();
        Fixture f=fixture(firstId);long secondAddress=addressFor(secondId);
        mvc.perform(post("/api/v1/buyer/cart").header("Authorization",bearer(firstBuyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":"+f.skuId+",\"quantity\":6}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/buyer/cart").header("Authorization",bearer(secondBuyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":"+f.skuId+",\"quantity\":6}")).andExpect(status().isOk());

        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Integer> first=pool.submit(()->submitCheckout(start,firstBuyer,f.addressId,"stock-race-1"));
            Future<Integer> second=pool.submit(()->submitCheckout(start,secondBuyer,secondAddress,"stock-race-2"));
            start.countDown();
            int firstStatus=first.get(10,TimeUnit.SECONDS),secondStatus=second.get(10,TimeUnit.SECONDS);
            assertTrue((firstStatus==200&&secondStatus==409)||(firstStatus==409&&secondStatus==200));
            assertEquals(6,stocks.selectById(f.skuId).getReserved());
            assertEquals(1,orders.selectCount(new QueryWrapper<ShopOrder>().in("buyer_id",firstId,secondId).eq("status","PENDING_PAYMENT")));
            assertEquals(1,carts.selectCount(new QueryWrapper<com.fairyshalltear.freshfruit.cart.entity.CartItem>().in("buyer_id",firstId,secondId)));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void simulatedPaymentResultsAreIdempotentAndBuyerScoped() throws Exception {
        String buyer=buyerToken("paymentidempotencybuyer"),other=buyerToken("paymentotherbuyer");
        long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","paymentidempotencybuyer")).getId();
        Fixture f=fixture(buyerId);long orderId=createUnpaidOrder(buyer,f,"payment-idempotency");
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/payment").header("Authorization",bearer(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"ALIPAY\",\"result\":\"SUCCESS\",\"requestKey\":\"foreign-payment\"}" )).andExpect(status().isNotFound());
        String failed="{\"channel\":\"WECHAT\",\"result\":\"FAILED\",\"requestKey\":\"same-failed-attempt\"}";
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/payment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(failed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/payment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(failed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/payment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"ALIPAY\",\"result\":\"CANCELLED\",\"requestKey\":\"cancelled-attempt\"}" ))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        String success="{\"channel\":\"ALIPAY\",\"result\":\"SUCCESS\",\"requestKey\":\"same-success-attempt\"}";
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/payment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(success))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_SHIPMENT"));
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/payment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(success))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_SHIPMENT"));
        assertEquals(3,paymentRecords.selectCount(new QueryWrapper<com.fairyshalltear.freshfruit.order.entity.PaymentRecord>().eq("order_id",orderId)));
        assertEquals(1,stocks.selectById(f.skuId).getReserved());
    }

    @Test
    void buyerOrderEndpointsRejectMissingAuthenticationAndInvalidInput() throws Exception {
        mvc.perform(get("/api/v1/buyer/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/buyer/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":0,\"requestKey\":\"\"}" )).andExpect(status().isUnauthorized());
        String buyer=buyerToken("invalidcheckoutbuyer");
        mvc.perform(post("/api/v1/buyer/orders").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":0,\"requestKey\":\"\"}" )).andExpect(status().isBadRequest());
    }

    @Test
    void buyerOrderStatusFilterCoversEveryLifecycleState() throws Exception {
        String buyer=buyerToken("statusfilterbuyer");
        long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","statusfilterbuyer")).getId();
        String admin=adminToken();
        Fixture unpaidFixture=fixture(buyerId);createUnpaidOrder(buyer,unpaidFixture,"filter-unpaid");
        Fixture cancelledFixture=fixture(buyerId);long cancelled=createUnpaidOrder(buyer,cancelledFixture,"filter-cancelled");
        mvc.perform(post("/api/v1/buyer/orders/"+cancelled+"/cancel").header("Authorization",bearer(buyer))).andExpect(status().isOk());
        Fixture awaitingShipment=fixture(buyerId);createPaidOrder(buyer,awaitingShipment,1,"filter-shipment");
        Fixture awaitingReceipt=fixture(buyerId);long shipped=createPaidOrder(buyer,awaitingReceipt,1,"filter-receipt");
        mvc.perform(post("/api/v1/admin/orders/"+shipped+"/dispatch").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"测试物流\",\"trackingNumber\":\"FILTER-1\"}")).andExpect(status().isOk());
        Fixture completedFixture=fixture(buyerId);long completed=createPaidOrder(buyer,completedFixture,1,"filter-completed");
        mvc.perform(post("/api/v1/admin/orders/"+completed+"/dispatch").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"测试物流\",\"trackingNumber\":\"FILTER-2\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/buyer/orders/"+completed+"/confirm-receipt").header("Authorization",bearer(buyer))).andExpect(status().isOk());

        assertEquals(5,orderCount(buyer,"ALL"));
        assertEquals(1,orderCount(buyer,"PENDING_PAYMENT"));
        assertEquals(1,orderCount(buyer,"PENDING_SHIPMENT"));
        assertEquals(1,orderCount(buyer,"PENDING_RECEIPT"));
        assertEquals(1,orderCount(buyer,"COMPLETED"));
        assertEquals(1,orderCount(buyer,"CANCELLED"));
        String from=java.time.LocalDate.now().minusDays(1).atStartOfDay().toString();
        String to=java.time.LocalDate.now().plusDays(1).atStartOfDay().toString();
        mvc.perform(get("/api/v1/admin/orders?from="+from+"&to="+to+"&keyword=statusfilterbuyer&page=1&size=2")
                .header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void returnWindowRejectsExpiredOrdersAndAllowsRefundWithoutRestocking() throws Exception {
        String buyer=buyerToken("returnwindowbuyer"),admin=adminToken();
        long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","returnwindowbuyer")).getId();
        Fixture expiredFixture=fixture(buyerId);long expired=createPaidOrder(buyer,expiredFixture,1,"return-expired");
        dispatchAndComplete(buyer,admin,expired,"WINDOW-EXPIRED");
        ShopOrder expiredOrder=orders.selectById(expired);expiredOrder.completedAt=java.time.LocalDateTime.now().minusDays(7).minusSeconds(1);orders.updateById(expiredOrder);
        mvc.perform(post("/api/v1/buyer/orders/"+expired+"/after-sales").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"RETURN\",\"reason\":\"超时退货\",\"items\":[{\"orderItemId\":"+orderItemId(expired)+",\"quantity\":1}]}" )).andExpect(status().isConflict());

        Fixture allowedFixture=fixture(buyerId);long allowed=createPaidOrder(buyer,allowedFixture,1,"return-window-allowed");
        dispatchAndComplete(buyer,admin,allowed,"WINDOW-ALLOWED");
        ShopOrder allowedOrder=orders.selectById(allowed);allowedOrder.completedAt=java.time.LocalDateTime.now().minusDays(7).plusSeconds(30);orders.updateById(allowedOrder);
        long line=orderItemId(allowed);
        long requestId=body(mvc.perform(post("/api/v1/buyer/orders/"+allowed+"/after-sales").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"RETURN\",\"reason\":\"七天内退货\",\"items\":[{\"orderItemId\":"+line+",\"quantity\":1}]}" )).andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        mvc.perform(post("/api/v1/admin/after-sales/"+requestId+"/review").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"reason\":\"审核通过\"}" )).andExpect(status().isOk());
        mvc.perform(post("/api/v1/buyer/after-sales/"+requestId+"/return-shipment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"邮政\",\"trackingNumber\":\"WINDOW-RETURN\"}" )).andExpect(status().isOk());
        int onHandBefore=stocks.selectById(allowedFixture.skuId).getOnHand();
        mvc.perform(post("/api/v1/admin/after-sales/"+requestId+"/receive").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"orderItemId\":"+line+",\"refundQuantity\":1,\"restockQuantity\":0}]}" )).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertEquals(onHandBefore,stocks.selectById(allowedFixture.skuId).getOnHand());
    }

    @Test
    void concurrentAfterSaleRequestsCannotReserveTheSameOrderQuantityTwice() throws Exception {
        String buyer=buyerToken("afterSalRaceBuyer");long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","afterSalRaceBuyer")).getId();
        Fixture f=fixture(buyerId);long orderId=createPaidOrder(buyer,f,1,"after-sale-race");long lineId=orderItemId(orderId);
        String requestBody="{\"type\":\"REFUND\",\"reason\":\"并发退款申请\",\"items\":[{\"orderItemId\":"+lineId+",\"quantity\":1}]}";
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Integer> first=pool.submit(()->submitAfterSale(start,buyer,orderId,requestBody));
            Future<Integer> second=pool.submit(()->submitAfterSale(start,buyer,orderId,requestBody));
            start.countDown();int firstStatus=first.get(10,TimeUnit.SECONDS),secondStatus=second.get(10,TimeUnit.SECONDS);
            assertTrue((firstStatus==200&&secondStatus==409)||(firstStatus==409&&secondStatus==200));
            assertEquals(1,orderItems.selectById(lineId).refundPendingQuantity);
        } finally {pool.shutdownNow();}
    }

    @Test
    void paidOrderCanBeDispatchedAndTrackedByAdminOnly() throws Exception {
        String buyer=buyerToken("dispatchbuyer");
        long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","dispatchbuyer")).getId();
        Fixture f=fixture(buyerId);
        mvc.perform(post("/api/v1/buyer/cart").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":"+f.skuId+",\"quantity\":3}" )).andExpect(status().isOk());
        String checkout="{\"addressId\":"+f.addressId+",\"requestKey\":\"dispatch-submit\"}";
        JsonNode created=body(mvc.perform(post("/api/v1/buyer/orders").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(checkout)).andExpect(status().isOk()).andReturn());
        long orderId=created.path("data").path("id").asLong();
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/payment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"ALIPAY\",\"result\":\"SUCCESS\",\"requestKey\":\"dispatch-payment\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_SHIPMENT"));
        String admin=adminToken();
        mvc.perform(get("/api/v1/admin/orders?status=PENDING_SHIPMENT&keyword="+created.path("data").path("orderNo").asText()).header("Authorization",bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(post("/api/v1/admin/orders/"+orderId+"/dispatch").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"顺丰速运\",\"trackingNumber\":\"SF123456789\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_RECEIPT"));
        assertEquals(7,stocks.selectById(f.skuId).getOnHand());assertEquals(0,stocks.selectById(f.skuId).getReserved());
        mvc.perform(post("/api/v1/admin/orders/"+orderId+"/tracking-events").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"已揽收\",\"occurredAt\":\"2026-11-14T12:00:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.trackingEvents[0].description").value("已揽收"));
        mvc.perform(post("/api/v1/admin/orders/"+orderId+"/tracking-events").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"已到分拨中心\",\"occurredAt\":\"2026-11-14T11:00:00\"}" )).andExpect(status().isOk());
        mvc.perform(get("/api/v1/buyer/orders/"+orderId).header("Authorization",bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.trackingEvents[0].description").value("已到分拨中心"))
                .andExpect(jsonPath("$.data.trackingEvents[1].description").value("已揽收"));
        String otherBuyer=buyerToken("dispatchotherbuyer");
        mvc.perform(get("/api/v1/buyer/orders/"+orderId).header("Authorization",bearer(otherBuyer))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/orders/"+orderId+"/dispatch").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"顺丰速运\",\"trackingNumber\":\"SF123456789\"}" )).andExpect(status().isConflict());
        Fixture rollbackFixture=fixture(buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","dispatchbuyer")).getId());
        long rollbackOrder=createPaidOrder(buyer,rollbackFixture,1,"dispatch-rollback");
        SkuInventory corrupted=stocks.selectById(rollbackFixture.skuId);corrupted.setReserved(0);stocks.updateById(corrupted);
        long movementCountBefore=movements.selectCount(new QueryWrapper<InventoryMovement>().eq("sku_id",rollbackFixture.skuId));
        mvc.perform(post("/api/v1/admin/orders/"+rollbackOrder+"/dispatch").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"测试\",\"trackingNumber\":\"ROLLBACK-1\"}" )).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/orders/"+rollbackOrder).header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_SHIPMENT"));
        assertEquals(10,stocks.selectById(rollbackFixture.skuId).getOnHand());
        assertEquals(movementCountBefore,movements.selectCount(new QueryWrapper<InventoryMovement>().eq("sku_id",rollbackFixture.skuId)));
        mvc.perform(get("/api/v1/admin/orders").header("Authorization",bearer(buyer))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/confirm-receipt").header("Authorization",bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/buyer/orders?status=COMPLETED").header("Authorization",bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void expiresOldUnpaidOrderAndReleasesReservationOnce() throws Exception {
        String buyer=buyerToken("expirebuyer");long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","expirebuyer")).getId();
        Fixture f=fixture(buyerId);
        mvc.perform(post("/api/v1/buyer/cart").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content("{\"skuId\":"+f.skuId+",\"quantity\":1}" )).andExpect(status().isOk());
        String checkout="{\"addressId\":"+f.addressId+",\"requestKey\":\"expire-submit\"}";
        long orderId=body(mvc.perform(post("/api/v1/buyer/orders").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(checkout)).andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        ShopOrder order=orders.selectById(orderId);order.createdAt=java.time.LocalDateTime.now().minusMinutes(16);orders.updateById(order);
        assertEquals(1,orderService.expireUnpaid(15));
        assertEquals(0,orderService.expireUnpaid(15));
        assertEquals(0,stocks.selectById(f.skuId).getReserved());
        mvc.perform(get("/api/v1/buyer/orders/"+orderId).header("Authorization",bearer(buyer))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));

        Fixture recentFixture=fixture(buyerId);long recentOrderId=createUnpaidOrder(buyer,recentFixture,"expire-recent");
        ShopOrder recent=orders.selectById(recentOrderId);recent.createdAt=java.time.LocalDateTime.now().minusMinutes(14);orders.updateById(recent);
        assertEquals(0,orderService.expireUnpaid(15));
        assertEquals("PENDING_PAYMENT",orders.selectById(recentOrderId).status);
        assertEquals(1,stocks.selectById(recentFixture.skuId).getReserved());

        Fixture boundaryFixture=fixture(buyerId);long boundaryOrderId=createUnpaidOrder(buyer,boundaryFixture,"expire-boundary");
        java.time.LocalDateTime boundaryNow=java.time.LocalDateTime.now().plusSeconds(2);
        ShopOrder boundary=orders.selectById(boundaryOrderId);boundary.createdAt=boundaryNow.minusMinutes(15);orders.updateById(boundary);
        assertEquals(1,orderService.expireUnpaid(boundaryNow,15));
        assertEquals("CANCELLED",orders.selectById(boundaryOrderId).status);
        assertEquals(0,stocks.selectById(boundaryFixture.skuId).getReserved());
    }

    @Test
    void concurrentPaymentAndExpirationCommitOnlyOneTransition() throws Exception {
        String buyer=buyerToken("expirepayrace");long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","expirepayrace")).getId();
        Fixture f=fixture(buyerId);long orderId=createUnpaidOrder(buyer,f,"expire-pay-race");
        ShopOrder order=orders.selectById(orderId);order.createdAt=java.time.LocalDateTime.now().minusMinutes(16);orders.updateById(order);

        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<MvcResult> payment=pool.submit(()->{
                start.await();
                return mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/payment").header("Authorization",bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"ALIPAY\",\"result\":\"SUCCESS\",\"requestKey\":\"race-payment\"}")).andReturn();
            });
            Future<Integer> expiration=pool.submit(()->{start.await();return orderService.expireUnpaid(15);});
            start.countDown();
            int paymentStatus=payment.get(10,TimeUnit.SECONDS).getResponse().getStatus();
            expiration.get(10,TimeUnit.SECONDS);

            ShopOrder finalOrder=orders.selectById(orderId);
            assertTrue(paymentStatus==200||paymentStatus==409);
            assertTrue("PENDING_SHIPMENT".equals(finalOrder.status)||"CANCELLED".equals(finalOrder.status));
            assertEquals("PENDING_SHIPMENT".equals(finalOrder.status)?1:0,stocks.selectById(f.skuId).getReserved());
            assertEquals(1,orders.selectCount(new QueryWrapper<ShopOrder>().eq("id",orderId).eq("status",finalOrder.status)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expirationFailureForOneOrderDoesNotBlockFollowingOrders() throws Exception {
        String buyer=buyerToken("expirebatchbuyer");
        long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","expirebatchbuyer")).getId();
        Fixture brokenFixture=fixture(buyerId),validFixture=fixture(buyerId);
        long brokenId=createUnpaidOrder(buyer,brokenFixture,"expire-broken");
        long validId=createUnpaidOrder(buyer,validFixture,"expire-valid");

        ShopOrder broken=orders.selectById(brokenId);broken.createdAt=java.time.LocalDateTime.now().minusMinutes(20);orders.updateById(broken);
        ShopOrder valid=orders.selectById(validId);valid.createdAt=java.time.LocalDateTime.now().minusMinutes(16);orders.updateById(valid);
        SkuInventory brokenStock=stocks.selectById(brokenFixture.skuId);brokenStock.setReserved(0);stocks.updateById(brokenStock);

        assertEquals(1,orderService.expireUnpaid(15));
        assertEquals("PENDING_PAYMENT",orders.selectById(brokenId).status);
        assertEquals("CANCELLED",orders.selectById(validId).status);
        assertEquals(0,stocks.selectById(validFixture.skuId).getReserved());
        assertEquals(0,orderService.expireUnpaid(15));
    }

    @Test
    void supportsPartialUnshippedRefundAndReturnRestockWorkflow() throws Exception {
        String buyer=buyerToken("aftersalebuyer");String admin=adminToken();long buyerId=buyers.selectOne(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerAccount>().eq("username","aftersalebuyer")).getId();
        Fixture refundFixture=fixture(buyerId);long refundOrder=createPaidOrder(buyer,refundFixture,2,"refund-flow");
        String refundBody="{\"type\":\"REFUND\",\"reason\":\"商品问题\",\"description\":\"部分商品损坏\",\"items\":[{\"orderItemId\":"+orderItemId(refundOrder)+",\"quantity\":1}]}";
        String excessiveRefund=refundBody.replace("\"quantity\":1","\"quantity\":3");
        mvc.perform(post("/api/v1/buyer/orders/"+refundOrder+"/after-sales").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(excessiveRefund)).andExpect(status().isConflict());
        JsonNode refund=body(mvc.perform(post("/api/v1/buyer/orders/"+refundOrder+"/after-sales").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(refundBody)).andExpect(status().isOk()).andReturn());
        long refundId=refund.path("data").path("id").asLong();
        mvc.perform(get("/api/v1/admin/after-sales").header("Authorization",bearer(buyer))).andExpect(status().isForbidden());
        String refundOther=buyerToken("refundotherbuyer");
        mvc.perform(get("/api/v1/buyer/after-sales/"+refundId).header("Authorization",bearer(refundOther))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/after-sales/"+refundId+"/review").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"reason\":\"核实通过\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertEquals(1,stocks.selectById(refundFixture.skuId).getReserved());
        mvc.perform(post("/api/v1/admin/after-sales/"+refundId+"/review").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"reason\":\"重复\"}" )).andExpect(status().isConflict());

        Fixture fullRefundFixture=fixture(buyerId);long fullRefundOrder=createPaidOrder(buyer,fullRefundFixture,1,"full-refund-flow");
        long fullRefundId=body(mvc.perform(post("/api/v1/buyer/orders/"+fullRefundOrder+"/after-sales").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"REFUND\",\"reason\":\"整单退款\",\"items\":[{\"orderItemId\":"+orderItemId(fullRefundOrder)+",\"quantity\":1}]}" )).andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        mvc.perform(post("/api/v1/admin/after-sales/"+fullRefundId+"/review").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"reason\":\"确认整单退款\"}" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/buyer/orders/"+fullRefundOrder).header("Authorization",bearer(buyer))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertEquals(0,stocks.selectById(fullRefundFixture.skuId).getReserved());

        Fixture rejectedFixture=fixture(buyerId);long rejectedOrder=createPaidOrder(buyer,rejectedFixture,1,"rejected-refund-flow");
        long rejectedId=body(mvc.perform(post("/api/v1/buyer/orders/"+rejectedOrder+"/after-sales").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"REFUND\",\"reason\":\"申请退款\",\"items\":[{\"orderItemId\":"+orderItemId(rejectedOrder)+",\"quantity\":1}]}" )).andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        mvc.perform(post("/api/v1/admin/after-sales/"+rejectedId+"/review").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":false,\"reason\":\"不符合退款条件\"}" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertEquals(1,stocks.selectById(rejectedFixture.skuId).getReserved());

        Fixture returnFixture=fixture(buyerId);long returnOrder=createPaidOrder(buyer,returnFixture,2,"return-flow");
        mvc.perform(post("/api/v1/admin/orders/"+returnOrder+"/dispatch").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"carrier\":\"邮政\",\"trackingNumber\":\"T100\"}" )).andExpect(status().isOk());
        mvc.perform(post("/api/v1/buyer/orders/"+returnOrder+"/confirm-receipt").header("Authorization",bearer(buyer))).andExpect(status().isOk());
        long returnLine=orderItemId(returnOrder);
        JsonNode returnRequest=body(mvc.perform(post("/api/v1/buyer/orders/"+returnOrder+"/after-sales").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"RETURN\",\"reason\":\"质量问题\",\"description\":\"申请退货\",\"items\":[{\"orderItemId\":"+returnLine+",\"quantity\":2}]}" )).andExpect(status().isOk()).andReturn());
        long returnId=returnRequest.path("data").path("id").asLong();
        mvc.perform(post("/api/v1/admin/after-sales/"+returnId+"/review").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"reason\":\"同意退货\"}" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WAITING_RETURN"));
        mvc.perform(post("/api/v1/buyer/after-sales/"+returnId+"/return-shipment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content("{\"carrier\":\"邮政\",\"trackingNumber\":\"R100\"}" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WAITING_RECEIPT"));
        String returnOther=buyerToken("returnotherbuyer");
        mvc.perform(post("/api/v1/buyer/after-sales/"+returnId+"/return-shipment").header("Authorization",bearer(returnOther)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"邮政\",\"trackingNumber\":\"FOREIGN\"}" )).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/after-sales/"+returnId+"/receive").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"orderItemId\":"+returnLine+",\"refundQuantity\":3,\"restockQuantity\":2}]}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/after-sales/"+returnId+"/receive").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"orderItemId\":"+returnLine+",\"refundQuantity\":1,\"restockQuantity\":2}]}" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertEquals(10,stocks.selectById(returnFixture.skuId).getOnHand());
        var incoming=movements.selectList(new QueryWrapper<InventoryMovement>().eq("sku_id",returnFixture.skuId).eq("type","RETURN_RESTOCK"));
        assertEquals(1,incoming.size());assertEquals("AFTER_SALE",incoming.get(0).getSourceType());assertEquals(returnId,incoming.get(0).getSourceId());
        var outgoing=movements.selectList(new QueryWrapper<InventoryMovement>().eq("sku_id",returnFixture.skuId).eq("type","ORDER_DISPATCH"));
        assertEquals(1,outgoing.size());assertEquals("ORDER",outgoing.get(0).getSourceType());assertEquals(returnOrder,outgoing.get(0).getSourceId());

        String otherBuyer=buyerToken("reviewotherbuyer");
        mvc.perform(post("/api/v1/buyer/reviews").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":"+orderItemId(rejectedOrder)+",\"rating\":5,\"content\":\"未完成订单评价\"}" )).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/buyer/reviews").header("Authorization",bearer(otherBuyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":"+returnLine+",\"rating\":5,\"content\":\"越权评价\"}" )).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/buyer/reviews").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":"+returnLine+",\"rating\":6,\"content\":\"无效评分\"}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/buyer/reviews").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":"+returnLine+",\"rating\":5,\"content\":\"新鲜\"}" )).andExpect(status().isOk());
        long productId=orderItems.selectById(returnLine).productId;
        mvc.perform(get("/api/v1/public/products/"+productId+"/reviews")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].buyerId").doesNotExist()).andExpect(jsonPath("$.data.items[0].orderItemId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].deletedBy").doesNotExist());
        mvc.perform(post("/api/v1/buyer/reviews").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":"+returnLine+",\"rating\":4,\"content\":\"重复\"}" )).andExpect(status().isConflict());
        long reviewId=body(mvc.perform(get("/api/v1/admin/reviews?deleted=false").header("Authorization",bearer(admin))).andExpect(status().isOk()).andReturn()).path("data").path("items").get(0).path("id").asLong();
        mvc.perform(post("/api/v1/admin/reviews/"+reviewId+"/delete").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"违规内容\"}" )).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/reviews/"+reviewId+"/delete").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"重复删除\"}" )).andExpect(status().isConflict());
        assertEquals(1,reviewAudits.selectCount(new QueryWrapper<ProductReviewDeletionAudit>().eq("review_id",reviewId)));
        mvc.perform(get("/api/v1/public/products/"+productId+"/reviews")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/public/products/"+productId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewSummary.count").value(0));
        mvc.perform(get("/api/v1/admin/reviews?deleted=true").header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].deletionReason").value("违规内容"));
    }

    private long createPaidOrder(String buyer,Fixture f,int quantity,String key)throws Exception{
        mvc.perform(post("/api/v1/buyer/cart").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content("{\"skuId\":"+f.skuId+",\"quantity\":"+quantity+"}" )).andExpect(status().isOk());
        String body="{\"addressId\":"+f.addressId+",\"requestKey\":\""+key+"-submit\"}";
        long id=body(mvc.perform(post("/api/v1/buyer/orders").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
        mvc.perform(post("/api/v1/buyer/orders/"+id+"/payment").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"WECHAT\",\"result\":\"SUCCESS\",\"requestKey\":\""+key+"-payment\"}" )).andExpect(status().isOk());
        return id;
    }
    private long createUnpaidOrder(String buyer,Fixture f,String key)throws Exception{
        mvc.perform(post("/api/v1/buyer/cart").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":"+f.skuId+",\"quantity\":1}" )).andExpect(status().isOk());
        String checkout="{\"addressId\":"+f.addressId+",\"requestKey\":\""+key+"\"}";
        return body(mvc.perform(post("/api/v1/buyer/orders").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(checkout)).andExpect(status().isOk()).andReturn()).path("data").path("id").asLong();
    }
    private long orderItemId(long orderId){return orderItems.selectList(new QueryWrapper<com.fairyshalltear.freshfruit.order.entity.OrderItem>().eq("order_id",orderId).orderByAsc("id")).get(0).id;}

    private void dispatchAndComplete(String buyer,String admin,long orderId,String tracking)throws Exception {
        mvc.perform(post("/api/v1/admin/orders/"+orderId+"/dispatch").header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"测试物流\",\"trackingNumber\":\""+tracking+"\"}" )).andExpect(status().isOk());
        mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/confirm-receipt").header("Authorization",bearer(buyer))).andExpect(status().isOk());
    }

    private Integer submitCheckout(CountDownLatch start,String token,long addressId,String key)throws Exception {
        start.await();return mvc.perform(post("/api/v1/buyer/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":"+addressId+",\"requestKey\":\""+key+"\"}")).andReturn().getResponse().getStatus();
    }
    private Integer submitAfterSale(CountDownLatch start,String token,long orderId,String body)throws Exception {
        start.await();return mvc.perform(post("/api/v1/buyer/orders/"+orderId+"/after-sales").header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }
    private long orderCount(String token,String filter)throws Exception {
        return body(mvc.perform(get("/api/v1/buyer/orders?status="+filter).header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn()).path("data").path("total").asLong();
    }
    private long addressFor(long buyerId) {
        UserAddress address=new UserAddress();address.setBuyerId(buyerId);address.setRecipientName("收件人");address.setPhone("13800000000");address.setProvince("浙江省");address.setCity("杭州市");address.setDistrict("西湖区");address.setDetailAddress("测试路 1 号");address.setAddressTag("HOME");address.setIsDefault(1);addresses.insert(address);return address.getId();
    }

    private Fixture fixture(long buyerId) {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,8);
        ProductCategory root=new ProductCategory();root.setName("order-root-"+suffix);root.setStatus(1);root.setSortOrder(0);categories.insert(root);
        ProductCategory leaf=new ProductCategory();leaf.setParentId(root.getId());leaf.setName("order-leaf-"+suffix);leaf.setStatus(1);leaf.setSortOrder(0);categories.insert(leaf);
        FruitProduct product=new FruitProduct();product.setCategoryId(leaf.getId());product.setName("订单测试水果");product.setStatus(1);product.setDeleted(0);product.setIsRecommended(0);product.setRecommendSort(0);product.setVersion(0L);products.insert(product);
        ProductSku sku=new ProductSku();sku.setProductId(product.getId());sku.setSpecification("500g");sku.setPrice(new BigDecimal("10.00"));sku.setStatus(1);sku.setSortOrder(0);skus.insert(sku);
        SkuInventory stock=new SkuInventory();stock.setSkuId(sku.getId());stock.setOnHand(10);stock.setReserved(0);stock.setLowStockThreshold(0);stock.setVersion(0L);stocks.insert(stock);
        return new Fixture(sku.getId(),addressFor(buyerId));
    }
    private String buyerToken(String username)throws Exception {return register(username).path("data").path("accessToken").asText();}
    private String adminToken() throws Exception {return body(mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"orderadmin\",\"password\":\"OrderAdminPassword123!\"}")).andExpect(status().isOk()).andReturn()).path("data").path("accessToken").asText();}
    private JsonNode register(String username) {
        try {MvcResult result=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\""+username+"\",\"phone\":\"1380000"+String.format("%04d",Math.floorMod(username.hashCode(),10000))+"\",\"password\":\"BuyerPassword123!\"}"))
                .andExpect(status().isOk()).andReturn();return body(result);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private JsonNode body(MvcResult result) {try{return json.readTree(result.getResponse().getContentAsString());}catch(Exception e){throw new IllegalStateException(e);}}
    private String bearer(String token){return "Bearer "+token;}
    private record Fixture(long skuId,long addressId) {}
}
