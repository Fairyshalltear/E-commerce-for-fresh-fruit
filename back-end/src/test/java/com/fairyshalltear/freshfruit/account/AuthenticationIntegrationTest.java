package com.fairyshalltear.freshfruit.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.account.entity.AdminAccount;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.AdminAccountMapper;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.account.mapper.BuyerLoginActivityMapper;
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
    private BuyerLoginActivityMapper loginActivities;

    @Autowired
    private AdminAccountMapper admins;

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    private JdbcTemplate jdbc;

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
        assertEquals(0L, loginActivities.selectCount(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerLoginActivity>()
                .eq("buyer_id", persisted.getId())));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrPhone":"13800000001","password":"BuyerPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.id").isNumber())
                .andReturn();
        String loginToken = tokenFrom(loginResult);
        assertEquals(1L, loginActivities.selectCount(new QueryWrapper<com.fairyshalltear.freshfruit.account.entity.BuyerLoginActivity>()
                .eq("buyer_id", persisted.getId())));

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
    void buyerTokensAreRevokedWhenDisabledAndRemainRevokedAfterReactivationOrCredentialReset() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"revokedbuyer","phone":"13800000003","password":"BuyerPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String oldToken = tokenFrom(registration);
        BuyerAccount buyer = buyers.selectOne(new LambdaQueryWrapper<BuyerAccount>()
                .eq(BuyerAccount::getUsername, "revokedbuyer"));

        buyer.setStatus(0);
        buyer.setCredentialVersion(buyer.getCredentialVersion() + 1);
        buyers.updateById(buyer);
        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        buyer.setStatus(1);
        buyers.updateById(buyer);
        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        MvcResult freshLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrPhone":"revokedbuyer","password":"BuyerPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String freshToken = tokenFrom(freshLogin);
        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk());

        buyer.setCredentialVersion(buyer.getCredentialVersion() + 1);
        buyers.updateById(buyer);
        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void initializesAdminFromEnvironmentAndKeepsInitializationIdempotent() throws Exception {
        assertEquals(1L, admins.selectCount(new QueryWrapper<AdminAccount>().eq("username", "testadmin")));
        adminBootstrap.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"testadmin","password":"TestAdminPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.role").value("OWNER"))
                .andReturn();
        String token = tokenFrom(result);

        mockMvc.perform(get("/api/v1/admin/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testadmin"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));
        mockMvc.perform(get("/api/v1/auth/buyer/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertEquals(1L, admins.selectCount(new QueryWrapper<AdminAccount>().eq("username", "testadmin")));
    }

    @Test
    void ownerCanCreateStaffAndPermissionRevocationAffectsExistingTokens() throws Exception {
        String ownerToken = adminToken();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/staff")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"statsstaff","displayName":"统计店员","password":"StaffPassword123!","modulePermissions":["STATISTICS"],"sensitivePermissions":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modulePermissions[0]").value("STATISTICS"))
                .andReturn();
        long staffId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        MvcResult login = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"statsstaff","password":"StaffPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.role").value("STAFF"))
                .andReturn();
        String staffToken = tokenFrom(login);
        mockMvc.perform(get("/api/v1/admin/analytics?period=TODAY&grain=DAY")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/staff").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/staff/" + staffId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"统计店员","modulePermissions":[],"sensitivePermissions":[]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/analytics?period=TODAY&grain=DAY")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingAndCredentialResetRevokeExistingStaffSessions() throws Exception {
        String ownerToken = adminToken();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/staff")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"lifecycle-staff","displayName":"生命周期店员","password":"StaffPassword123!","modulePermissions":["STATISTICS"],"sensitivePermissions":[]}
                                """))
                .andExpect(status().isOk()).andReturn();
        long staffId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        String staffToken = tokenFrom(mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"lifecycle-staff","password":"StaffPassword123!"}
                                """))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(patch("/api/v1/admin/staff/" + staffId + "/enabled")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/analytics?period=TODAY&grain=DAY")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/admin/staff/" + staffId + "/enabled")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/staff/" + staffId + "/credentials")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewStaffPassword123!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/analytics?period=TODAY&grain=DAY")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"lifecycle-staff","password":"NewStaffPassword123!"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void staffModuleChecksCoverAdminApiGroupsAndDenyUnclassifiedRoutes() throws Exception {
        String ownerToken = adminToken();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/staff")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"no-module-staff","displayName":"无模块店员","password":"StaffPassword123!","modulePermissions":[],"sensitivePermissions":[]}
                                """))
                .andExpect(status().isOk()).andReturn();
        MvcResult login = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"no-module-staff","password":"StaffPassword123!"}
                                """))
                .andExpect(status().isOk()).andReturn();
        String staffToken = tokenFrom(login);

        for (String path : new String[] {
                "/api/v1/admin/products", "/api/v1/admin/categories", "/api/v1/admin/reviews",
                "/api/v1/admin/buyers", "/api/v1/admin/orders",
                "/api/v1/admin/inventory/stocks", "/api/v1/admin/after-sales",
                "/api/v1/admin/content/carousels", "/api/v1/admin/analytics?period=TODAY&grain=DAY",
                "/api/v1/admin/unclassified"
        }) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post("/api/v1/admin/product-images").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        int documentsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_document", Integer.class);
        mockMvc.perform(post("/api/v1/admin/inventory/documents")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        assertEquals(documentsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_document", Integer.class));
        long staffId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertEquals(12, jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE admin_id=? AND result='DENIED'", Integer.class, staffId));
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"testadmin","password":"TestAdminPassword123!"}
                                """))
                .andExpect(status().isOk()).andReturn();
        return tokenFrom(result);
    }

    @Test
    void failedAdminLoginIsAuditedWithoutRecordingThePassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing-owner","password":"SecretMustNotAppear123!"}
                                """))
                .andExpect(status().isUnauthorized());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE event_category='AUTHENTICATION' AND operation_type='ADMIN_LOGIN' AND actor_username_snapshot='missing-owner' AND result='FAILED'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE target_summary LIKE '%SecretMustNotAppear123%' OR failure_category LIKE '%SecretMustNotAppear123%'", Integer.class));
        String ownerToken = adminToken();
        MvcResult auditResult = mockMvc.perform(get("/api/v1/admin/audit-logs?category=AUTHENTICATION&result=FAILED")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].actorUsername").value("missing-owner"))
                .andExpect(jsonPath("$.data.items[0].failureCategory").value("INVALID_CREDENTIALS"))
                .andReturn();
        long logId = objectMapper.readTree(auditResult.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("id").asLong();
        mockMvc.perform(get("/api/v1/admin/audit-logs/" + logId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.actorUsername").value("missing-owner"));
    }

    private String tokenFrom(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }
}
