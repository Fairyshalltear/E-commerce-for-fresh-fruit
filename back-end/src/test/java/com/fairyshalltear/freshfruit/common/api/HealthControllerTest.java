package com.fairyshalltear.freshfruit.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void returnsHealthStatusInStandardEnvelope() {
        ApiResponse<java.util.Map<String, String>> response = new HealthController().health();

        assertEquals("OK", response.code());
        assertEquals("ok", response.data().get("status"));
    }
}
