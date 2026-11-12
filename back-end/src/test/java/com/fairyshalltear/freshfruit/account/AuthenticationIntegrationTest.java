package com.fairyshalltear.freshfruit.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.AdminAccountMapper;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.config.AdminBootstrap;

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
class AuthenticationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BuyerAccountMapper buyers;

    @Autowired
    private AdminAccountMapper admins;

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Test
    void registersLogsInAndProtectsBuyerRoutes() throws Exception {
        String registration = """
                {"username":"buyerone","phone":"13800000001","password":"BuyerPassword123!","nickname":"水果买家"}
                """;
        MvcResult registrationResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.account.role").value("BUYER"))
                .andReturn();
        String registeredToken = tokenFrom(registrationResult);

        BuyerAccount persisted = buyers.selectOne(new LambdaQueryWrapper<BuyerAccount>()
                .eq(BuyerAccount::getUsername, "buyerone"));
        assertNotEquals("BuyerPassword123!", persisted.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(persisted.getPasswordHash().startsWith("$2a$"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"buyertwo","phone":"13800000001","password":"BuyerPassword123!"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.data.phone").exists());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrPhone":"13800000001","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrPhone":"13800000001","password":"BuyerPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.id").isNumber())
                .andReturn();
        String loginToken = tokenFrom(loginResult);

        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", "Bearer " + registeredToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("buyerone"));
        mockMvc.perform(get("/api/v1/auth/buyer/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/admin/auth/me").header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void validatesRequestFieldsUsingStandardErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"x","phone":"123","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.username").exists())
                .andExpect(jsonPath("$.data.phone").exists())
                .andExpect(jsonPath("$.data.password").exists());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"longpass","phone":"13800000002","password":"果果果果果果果果果果果果果果果果果果果果果果果果果果"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    void initializesAdminFromEnvironmentAndKeepsInitializationIdempotent() throws Exception {
        assertEquals(1L, admins.selectCount(new QueryWrapper<>()));
        adminBootstrap.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"testadmin","password":"TestAdminPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.role").value("ADMIN"))
                .andReturn();
        String token = tokenFrom(result);

        mockMvc.perform(get("/api/v1/admin/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testadmin"));
        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertEquals(1L, admins.selectCount(new QueryWrapper<>()));
    }

    private String tokenFrom(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }
}
