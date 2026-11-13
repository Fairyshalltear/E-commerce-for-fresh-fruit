package com.fairyshalltear.freshfruit.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.fairyshalltear.freshfruit.catalog.entity.FruitProduct;
import com.fairyshalltear.freshfruit.catalog.entity.ProductCategory;
import com.fairyshalltear.freshfruit.catalog.entity.ProductSku;
import com.fairyshalltear.freshfruit.catalog.mapper.FruitProductMapper;
import com.fairyshalltear.freshfruit.catalog.mapper.ProductCategoryMapper;
import com.fairyshalltear.freshfruit.catalog.mapper.ProductSkuMapper;
import com.fairyshalltear.freshfruit.inventory.entity.SkuInventory;
import com.fairyshalltear.freshfruit.inventory.mapper.SkuInventoryMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cartintegration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-schema.sql",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.admin.initial-username=cartadmin",
        "security.admin.initial-password=CartAdminPassword123!"
})
@AutoConfigureMockMvc
class CartIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductCategoryMapper categories;
    @Autowired private FruitProductMapper products;
    @Autowired private ProductSkuMapper skus;
    @Autowired private SkuInventoryMapper inventory;

    @Test
    void mergesSameSkuCalculatesCurrentSubtotalAndDoesNotReserveStock() throws Exception {
        Fixture fixture = fixture("10.00", 10, 2);
        String token = buyerToken("cartbuyer");

        JsonNode first = body(mockMvc.perform(post("/api/v1/buyer/cart").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + fixture.skuId() + ",\"quantity\":2}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode second = body(mockMvc.perform(post("/api/v1/buyer/cart").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + fixture.skuId() + ",\"quantity\":1}"))
                .andExpect(status().isOk()).andReturn());

        assertEquals(1, first.path("data").path("items").size());
        assertEquals(3, second.path("data").path("items").get(0).path("quantity").asInt());
        assertEquals("30.00", second.path("data").path("selectedSubtotal").asText());
        assertEquals(2, inventory.selectById(fixture.skuId()).getReserved());
    }

    @Test
    void detectsPriceChangeRequiresAcceptanceAndValidatesWithoutCreatingOrder() throws Exception {
        Fixture fixture = fixture("10.00", 10, 2);
        String token = buyerToken("pricebuyer");
        JsonNode added = add(token, fixture.skuId(), 2);
        long itemId = added.path("data").path("items").get(0).path("id").asLong();

        ProductSku sku = skus.selectById(fixture.skuId());
        sku.setPrice(new BigDecimal("12.00"));
        skus.updateById(sku);
        JsonNode invalid = body(mockMvc.perform(post("/api/v1/buyer/cart/checkout-validation")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        assertEquals(false, invalid.path("data").path("valid").asBoolean());
        assertEquals(true, invalid.path("data").path("cart").path("items").get(0).path("priceChanged").asBoolean());
        assertEquals("24.00", invalid.path("data").path("cart").path("selectedSubtotal").asText());

        mockMvc.perform(put("/api/v1/buyer/cart/items/" + itemId + "/accepted-price")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":12.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/buyer/cart/checkout-validation")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));
        assertEquals(2, inventory.selectById(fixture.skuId()).getReserved());
    }

    @Test
    void rejectsOverStockAndDoesNotExposeAnotherBuyersCartItem() throws Exception {
        Fixture fixture = fixture("4.00", 10, 2);
        String firstToken = buyerToken("ownerbuyer");
        String otherToken = buyerToken("otherbuyer");
        mockMvc.perform(post("/api/v1/buyer/cart").header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + fixture.skuId() + ",\"quantity\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        JsonNode added = add(firstToken, fixture.skuId(), 1);
        long itemId = added.path("data").path("items").get(0).path("id").asLong();
        mockMvc.perform(patch("/api/v1/buyer/cart/items/" + itemId + "/quantity")
                        .header("Authorization", bearer(otherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/buyer/cart").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void keepsUnavailableItemsVisibleForRemovalAndRejectsAdminRole() throws Exception {
        Fixture fixture = fixture("5.00", 8, 1);
        String token = buyerToken("staleitembuyer");
        JsonNode added = add(token, fixture.skuId(), 2);
        long itemId = added.path("data").path("items").get(0).path("id").asLong();
        ProductSku sku = skus.selectById(fixture.skuId());
        sku.setStatus(0);
        skus.updateById(sku);

        mockMvc.perform(get("/api/v1/buyer/cart").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].purchasable").value(false))
                .andExpect(jsonPath("$.data.items[0].unavailableReason").value("ITEM_UNAVAILABLE"));
        mockMvc.perform(post("/api/v1/buyer/cart/checkout-validation").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(false));
        mockMvc.perform(delete("/api/v1/buyer/cart/items/" + itemId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));

        mockMvc.perform(get("/api/v1/buyer/cart").header("Authorization", bearer(adminToken())))
                .andExpect(status().isForbidden());
    }

    private JsonNode add(String token, long skuId, int quantity) throws Exception {
        return body(mockMvc.perform(post("/api/v1/buyer/cart").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":" + skuId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk()).andReturn());
    }

    private Fixture fixture(String price, int onHand, int reserved) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ProductCategory root = new ProductCategory();
        root.setName("cart-root-" + suffix);
        root.setStatus(1);
        root.setSortOrder(0);
        categories.insert(root);
        ProductCategory leaf = new ProductCategory();
        leaf.setParentId(root.getId());
        leaf.setName("cart-leaf-" + suffix);
        leaf.setStatus(1);
        leaf.setSortOrder(0);
        categories.insert(leaf);

        FruitProduct product = new FruitProduct();
        product.setCategoryId(leaf.getId());
        product.setName("测试水果 " + suffix);
        product.setStatus(1);
        product.setDeleted(0);
        product.setIsRecommended(0);
        product.setRecommendSort(0);
        product.setVersion(0L);
        products.insert(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSpecification("测试规格");
        sku.setPrice(new BigDecimal(price));
        sku.setStatus(1);
        sku.setSortOrder(0);
        skus.insert(sku);

        SkuInventory stock = new SkuInventory();
        stock.setSkuId(sku.getId());
        stock.setOnHand(onHand);
        stock.setReserved(reserved);
        stock.setLowStockThreshold(0);
        stock.setVersion(0L);
        inventory.insert(stock);
        return new Fixture(sku.getId());
    }

    private String buyerToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"phone\":\"1380000" + phoneSuffix(username)
                                + "\",\"password\":\"BuyerPassword123!\"}"))
                .andExpect(status().isOk()).andReturn();
        return body(result).path("data").path("accessToken").asText();
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cartadmin\",\"password\":\"CartAdminPassword123!\"}"))
                .andExpect(status().isOk()).andReturn();
        return body(result).path("data").path("accessToken").asText();
    }

    private String phoneSuffix(String value) {
        return String.format("%04d", Math.floorMod(value.hashCode(), 10000));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record Fixture(long skuId) {}
}
