package com.fairyshalltear.freshfruit.content;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

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
        "spring.datasource.url=jdbc:h2:mem:contentintegration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:test-schema.sql",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.admin.initial-username=contentowner", "security.admin.initial-password=OwnerPassword123!"
})
@AutoConfigureMockMvc
class StorefrontContentIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void schedulesAndPublishesOnlyEnabledContentAndRejectsUnsafeDestinations() throws Exception {
        String token = login();
        long ownerId = jdbc.queryForObject("SELECT id FROM admin_account WHERE username='contentowner'", Long.class);
        jdbc.update("INSERT INTO product_image(storage_key,uploaded_by,product_id,active,sort_order) VALUES(?,?,NULL,1,0)",
                UUID.randomUUID() + ".jpg", ownerId);
        long imageId = jdbc.queryForObject("SELECT MAX(id) FROM product_image", Long.class);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
        String carousel = """
                {"imageId":%d,"title":"本周鲜果","targetUrl":"/products","sortOrder":3,"enabled":true,"startAt":"%s","endAt":"%s"}
                """.formatted(imageId, now.minusMinutes(5), now.plusMinutes(5));
        mockMvc.perform(post("/api/v1/admin/content/carousels").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(carousel))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("本周鲜果"));
        mockMvc.perform(get("/api/v1/public/content/carousels"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].title").value("本周鲜果"));

        String announcement = """
                {"title":"营业时间","summary":"本周营业安排","body":"每天 9 点开始营业。","sortOrder":2,"pinned":true,"enabled":true,"startAt":"%s","endAt":"%s"}
                """.formatted(now.minusMinutes(5), now.plusMinutes(5));
        MvcResult created = mockMvc.perform(post("/api/v1/admin/content/announcements").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(announcement))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pinned").value(true)).andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(get("/api/v1/public/content/announcements"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].title").value("营业时间"));
        mockMvc.perform(get("/api/v1/public/content/announcements/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.body").value("每天 9 点开始营业。"));

        String unsafe = """
                {"imageId":%d,"title":"跳转检查","targetUrl":"javascript:alert(1)","sortOrder":1,"enabled":true,"startAt":"%s","endAt":null}
                """.formatted(imageId, now.minusMinutes(1));
        mockMvc.perform(post("/api/v1/admin/content/carousels").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(unsafe))
                .andExpect(status().isBadRequest());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"contentowner","password":"OwnerPassword123!"}
                                """))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }
}
