package com.fairyshalltear.freshfruit.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.audit.entity.AdminOperationLog;
import com.fairyshalltear.freshfruit.audit.mapper.AdminOperationLogMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:buyeradminintegration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-schema.sql",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.admin.initial-username=buyeradmin",
        "security.admin.initial-password=BuyerAdminPassword123!"
})
@AutoConfigureMockMvc
class BuyerAdministrationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private BuyerAccountMapper buyers;
    @Autowired private AdminOperationLogMapper logs;

    @Test
    void searchesAndReturnsBuyerWhitelistOnlyForAdministrators() throws Exception {
        String buyerToken = registerBuyer("findbuyer", "13800000021");
        String adminToken = adminToken();
        BuyerAccount buyer = buyers.selectOne(new LambdaQueryWrapper<BuyerAccount>().eq(BuyerAccount::getUsername, "findbuyer"));

        mockMvc.perform(get("/api/v1/admin/buyers").param("keyword", "findbuyer").param("page", "1").param("size", "1")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(buyer.getId()))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].credentialVersion").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/buyers/" + buyer.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.addresses").doesNotExist())
                .andExpect(jsonPath("$.data.orders").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/buyers").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusAndPasswordChangesAreAuditedAndRevokeExistingTokens() throws Exception {
        String buyerToken = registerBuyer("managedbuyer", "13800000022");
        String adminToken = adminToken();
        BuyerAccount buyer = buyers.selectOne(new LambdaQueryWrapper<BuyerAccount>().eq(BuyerAccount::getUsername, "managedbuyer"));

        mockMvc.perform(patch("/api/v1/admin/buyers/" + buyer.getId() + "/status")
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk());
        assertEquals(1L, logs.selectCount(new LambdaQueryWrapper<AdminOperationLog>()
                .eq(AdminOperationLog::getTargetId, buyer.getId()).eq(AdminOperationLog::getOperationType, "BUYER_DISABLE")));
        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/admin/buyers/" + buyer.getId() + "/status")
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk());
        String newToken = loginBuyer("managedbuyer", "BuyerPassword123!");
        mockMvc.perform(post("/api/v1/admin/buyers/" + buyer.getId() + "/password-reset")
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"TemporaryPassword987!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", bearer(newToken)))
                .andExpect(status().isUnauthorized());
        assertEquals(1L, logs.selectCount(new LambdaQueryWrapper<AdminOperationLog>()
                .eq(AdminOperationLog::getTargetId, buyer.getId()).eq(AdminOperationLog::getOperationType, "BUYER_PASSWORD_RESET")));
        assertFalse(buyers.selectById(buyer.getId()).getPasswordHash().contains("TemporaryPassword987!"));

        long version = buyers.selectById(buyer.getId()).getCredentialVersion();
        mockMvc.perform(post("/api/v1/admin/buyers/" + buyer.getId() + "/password-reset")
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(version, buyers.selectById(buyer.getId()).getCredentialVersion());
    }

    private String registerBuyer(String username, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"BuyerPassword123!\",\"nickname\":\"搜索昵称\"}"))
                .andExpect(status().isOk()).andReturn();
        return token(result);
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"buyeradmin\",\"password\":\"BuyerAdminPassword123!\"}"))
                .andExpect(status().isOk()).andReturn();
        return token(result);
    }

    private String loginBuyer(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrPhone\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return token(result);
    }

    private String token(MvcResult result) throws Exception {
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }
    private String bearer(String value) { return "Bearer " + value; }
}
