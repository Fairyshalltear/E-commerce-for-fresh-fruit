package com.fairyshalltear.freshfruit.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:freshfruitavatar;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class AvatarIntegrationTest {
    private static final Path AVATAR_DIR = Path.of(System.getProperty("java.io.tmpdir"),
            "fresh-fruit-avatar-test-" + UUID.randomUUID());
    private static final byte[] VALID_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jkioAAAAASUVORK5CYII=");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void avatarStorage(DynamicPropertyRegistry registry) {
        registry.add("freshfruit.avatar.storage-dir", AVATAR_DIR::toString);
    }

    @Test
    void buyerCanUploadAndReadAnAcceptedAvatar() throws Exception {
        BuyerSession buyer = registerBuyer();
        MockMultipartFile upload = new MockMultipartFile("file", "avatar.png", "image/png", VALID_PNG);
        MvcResult result = mockMvc.perform(multipart("/api/v1/buyer/profile/avatar")
                        .file(upload)
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").isString())
                .andReturn();
        String avatarUrl = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();

        mockMvc.perform(get(avatarUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void avatarUploadRejectsMismatchedInvalidAndOversizedFiles() throws Exception {
        BuyerSession buyer = registerBuyer();
        String authorization = "Bearer " + buyer.token();

        mockMvc.perform(multipart("/api/v1/buyer/profile/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "text/plain", VALID_PNG))
                        .header("Authorization", authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(multipart("/api/v1/buyer/profile/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", "not an image".getBytes()))
                        .header("Authorization", authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        mockMvc.perform(multipart("/api/v1/buyer/profile/avatar")
                        .file(new MockMultipartFile("file", "large.png", "image/png", tooLarge))
                        .header("Authorization", authorization))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    private BuyerSession registerBuyer() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "avatar" + suffix;
        String phone = "139" + String.format("%08d",
                Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"BuyerPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new BuyerSession(data.path("accessToken").asText());
    }

    private record BuyerSession(String token) {
    }
}
