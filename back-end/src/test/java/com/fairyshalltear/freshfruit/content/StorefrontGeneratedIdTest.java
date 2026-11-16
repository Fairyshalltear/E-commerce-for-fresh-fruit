package com.fairyshalltear.freshfruit.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;

class StorefrontGeneratedIdTest {
    @Test
    void readsGeneratedKeyIndependentOfDriverColumnLabel() {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        key.getKeyList().add(Map.of("GENERATED_KEY", 42L));

        assertEquals(42L, StorefrontContentController.generatedId(key));
    }
}
