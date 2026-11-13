package com.fairyshalltear.freshfruit.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.account.dto.CreateDeliveryAddressRequest;
import com.fairyshalltear.freshfruit.common.api.ApiException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:freshfruitaddresses;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-schema.sql",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.admin.initial-username=testadmin",
        "security.admin.initial-password=TestAdminPassword123!"
})
@AutoConfigureMockMvc
class DeliveryAddressIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryAddressService addressService;

    @DynamicPropertySource
    static void avatarStorage(DynamicPropertyRegistry registry) {
        registry.add("freshfruit.avatar.storage-dir", () -> PathForTest.AVATARS.toString());
    }

    @Test
    void buyerCanManageOwnAddressAndCannotReadOrChangeAnotherBuyersAddress() throws Exception {
        BuyerSession owner = registerBuyer();
        BuyerSession other = registerBuyer();
        long addressId = createAddress(owner.token(), "\"defaultAddress\":false");

        mockMvc.perform(get("/api/v1/buyer/addresses/" + addressId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientName").value("张三"))
                .andExpect(jsonPath("$.data.addressTag").value("HOME"));

        mockMvc.perform(get("/api/v1/buyer/addresses")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/buyer/addresses/" + addressId)
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(put("/api/v1/buyer/addresses/" + addressId)
                        .header("Authorization", "Bearer " + other.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody("COMPANY")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(put("/api/v1/buyer/addresses/" + addressId + "/default")
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(delete("/api/v1/buyer/addresses/" + addressId)
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(put("/api/v1/buyer/addresses/" + addressId)
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"李四\",\"phone\":\"13800000002\","
                                + "\"province\":\"上海\",\"city\":\"上海\",\"district\":\"浦东新区\","
                                + "\"detailAddress\":\"世纪大道 1 号\",\"addressTag\":\"COMPANY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientName").value("李四"))
                .andExpect(jsonPath("$.data.addressTag").value("COMPANY"));

        mockMvc.perform(delete("/api/v1/buyer/addresses/" + addressId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/buyer/addresses")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void rejectsInvalidAddressTagAndUnauthenticatedRequests() throws Exception {
        BuyerSession buyer = registerBuyer();
        mockMvc.perform(post("/api/v1/buyer/addresses")
                        .header("Authorization", "Bearer " + buyer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody("OTHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/buyer/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enforcesFiveAddressLimitAndDefaultTransitions() throws Exception {
        BuyerSession buyer = registerBuyer();
        long firstId = createAddress(buyer.token(), "\"defaultAddress\":true");
        long secondId = createAddress(buyer.token(), "\"defaultAddress\":false");
        long thirdId = createAddress(buyer.token(), "\"defaultAddress\":false");

        mockMvc.perform(put("/api/v1/buyer/addresses/" + secondId + "/default")
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(true));
        mockMvc.perform(get("/api/v1/buyer/addresses/" + firstId)
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(false));

        mockMvc.perform(delete("/api/v1/buyer/addresses/" + secondId)
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/buyer/addresses/" + firstId)
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(false));
        mockMvc.perform(get("/api/v1/buyer/addresses/" + thirdId)
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(false));

        createAddress(buyer.token(), "\"defaultAddress\":false");
        createAddress(buyer.token(), "\"defaultAddress\":false");
        createAddress(buyer.token(), "\"defaultAddress\":false");
        mockMvc.perform(post("/api/v1/buyer/addresses")
                        .header("Authorization", "Bearer " + buyer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody("HOME")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_REACHED"));
    }

    @Test
    void concurrentAddressCreationCannotExceedFive() throws Exception {
        BuyerSession buyer = registerBuyer();
        for (int index = 0; index < 4; index++) {
            createAddress(buyer.token(), "\"defaultAddress\":false");
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> createConcurrently(buyer.id(), start, "并发甲"));
            Future<String> second = executor.submit(() -> createConcurrently(buyer.id(), start, "并发乙"));
            start.countDown();
            String firstResult = first.get();
            String secondResult = second.get();
            org.junit.jupiter.api.Assertions.assertTrue(
                    (firstResult == null && "ADDRESS_LIMIT_REACHED".equals(secondResult))
                            || (secondResult == null && "ADDRESS_LIMIT_REACHED".equals(firstResult)));
        } finally {
            executor.shutdownNow();
        }
        mockMvc.perform(get("/api/v1/buyer/addresses")
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    private String createConcurrently(Long buyerId, CountDownLatch start, String recipient) throws Exception {
        start.await();
        try {
            addressService.create(buyerId, new CreateDeliveryAddressRequest(recipient, "13800000003",
                    "浙江省", "杭州市", "西湖区", "文三路 1 号", "HOME", false));
            return null;
        } catch (ApiException exception) {
            return exception.code();
        }
    }

    private long createAddress(String token, String optionalFields) throws Exception {
        String body = "{\"recipientName\":\"张三\",\"phone\":\"13800000001\","
                + "\"province\":\"浙江省\",\"city\":\"杭州市\",\"district\":\"西湖区\","
                + "\"detailAddress\":\"文三路 1 号\",\"addressTag\":\"HOME\""
                + (optionalFields.isBlank() ? "" : "," + optionalFields) + "}";
        MvcResult result = mockMvc.perform(post("/api/v1/buyer/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("id").asLong();
    }

    private String addressBody(String tag) {
        return "{\"recipientName\":\"张三\",\"phone\":\"13800000001\","
                + "\"province\":\"浙江省\",\"city\":\"杭州市\",\"district\":\"西湖区\","
                + "\"detailAddress\":\"文三路 1 号\",\"addressTag\":\"" + tag + "\"}";
    }

    private BuyerSession registerBuyer() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "address" + suffix;
        String phone = "139" + String.format("%08d",
                Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"BuyerPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new BuyerSession(data.path("accessToken").asText(), data.path("account").path("id").asLong());
    }

    private record BuyerSession(String token, Long id) {
    }

    private static final class PathForTest {
        static final java.nio.file.Path AVATARS = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                "fresh-fruit-address-avatar-test-" + UUID.randomUUID());
    }
}
