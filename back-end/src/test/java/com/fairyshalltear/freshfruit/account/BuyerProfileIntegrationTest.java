package com.fairyshalltear.freshfruit.account;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:freshfruit;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class BuyerProfileIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BuyerAccountMapper buyers;

    @Test
    void buyerCanReadAndUpdateOwnProfileButCannotChangeUsername() throws Exception {
        BuyerSession first = registerBuyer();
        BuyerSession second = registerBuyer();
        String replacementPhone = nextPhone();

        mockMvc.perform(get("/api/v1/buyer/profile")
                        .header("Authorization", "Bearer " + first.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(first.username()))
                .andExpect(jsonPath("$.data.phone").value(first.phone()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mockMvc.perform(patch("/api/v1/buyer/profile")
                        .header("Authorization", "Bearer " + first.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"phone\":\"" + second.phone() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.data.phone").exists());

        mockMvc.perform(patch("/api/v1/buyer/profile")
                        .header("Authorization", "Bearer " + first.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"phone\":\"" + replacementPhone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.phone").value(replacementPhone));

        mockMvc.perform(patch("/api/v1/buyer/profile")
                        .header("Authorization", "Bearer " + first.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"changedname\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.username").exists());

        BuyerAccount persisted = buyers.selectById(first.id());
        org.junit.jupiter.api.Assertions.assertEquals(first.username(), persisted.getUsername());
        org.junit.jupiter.api.Assertions.assertEquals(replacementPhone, persisted.getPhone());
    }

    @Test
    void profileRequiresBuyerAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/buyer/profile"))
                .andExpect(status().isUnauthorized());

        MvcResult adminLogin = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testadmin\",\"password\":\"TestAdminPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = objectMapper.readTree(adminLogin.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/buyer/profile").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndUpdatesCredential() throws Exception {
        BuyerSession buyer = registerBuyer();
        BuyerAccount before = buyers.selectById(buyer.id());

        mockMvc.perform(put("/api/v1/buyer/profile/password")
                        .header("Authorization", "Bearer " + buyer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"NewBuyerPassword123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        org.junit.jupiter.api.Assertions.assertEquals(before.getPasswordHash(), buyers.selectById(buyer.id()).getPasswordHash());

        mockMvc.perform(put("/api/v1/buyer/profile/password")
                        .header("Authorization", "Bearer " + buyer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"BuyerPassword123!\",\"newPassword\":\"NewBuyerPassword123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        BuyerAccount after = buyers.selectById(buyer.id());
        assertNotEquals(before.getPasswordHash(), after.getPasswordHash());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrPhone\":\"" + buyer.username()
                                + "\",\"password\":\"NewBuyerPassword123!\"}"))
                .andExpect(status().isOk());
    }

    private BuyerSession registerBuyer() throws Exception {
        long sequence = IDS.incrementAndGet();
        String username = "buyer" + Long.toString(sequence, 36);
        String phone = nextPhone(sequence);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"BuyerPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new BuyerSession(data.path("accessToken").asText(),
                data.path("account").path("id").asLong(), username, phone);
    }

    private record BuyerSession(String token, long id, String username, String phone) {
    }

    private String nextPhone() {
        return nextPhone(IDS.incrementAndGet());
    }

    private String nextPhone(long sequence) {
        return "139" + String.format("%08d", sequence % 100_000_000);
    }
}
