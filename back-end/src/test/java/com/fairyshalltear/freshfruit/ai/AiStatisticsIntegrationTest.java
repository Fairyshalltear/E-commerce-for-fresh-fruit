package com.fairyshalltear.freshfruit.ai;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fairyshalltear.freshfruit.ai.admin.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:ai-statistics;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql",
 "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
 "security.admin.initial-username=testadmin","security.admin.initial-password=TestAdminPassword123!","freshfruit.ai.jobs-enabled=false"
})
@AutoConfigureMockMvc
class AiStatisticsIntegrationTest {
    @Autowired AiMapper db;@Autowired AiStatisticsService statistics;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
    @Test void fixedFactsUseCreationDayAndExcludeUnratedAndProcessingFromDenominators(){
        LocalDate day=LocalDate.of(2020,1,2);LocalDateTime start=day.atTime(23,59);
        db.insert("ai_question_fact",values("owner_kind","BUYER","scene","ORDER","topic","订单与物流查询","created_at",start));
        db.insert("ai_question_fact",values("owner_kind","GUEST","scene","PRODUCT","topic","水果商品咨询","created_at",start));
        long first=db.insert("ai_attempt_fact",values("status","COMPLETED","scene","ORDER","helpful",1,"total_ms",1500,"first_content_ms",200,"created_at",start));
        db.insert("ai_attempt_fact",values("status","DEGRADED","scene","PRODUCT","is_retry",1,"total_ms",100,"created_at",start));
        db.insert("ai_attempt_fact",values("status","FAILED","scene","PRODUCT","total_ms",500,"created_at",start));
        db.insert("ai_attempt_fact",values("status","RUNNING","scene","GENERAL","created_at",start));
        Map<String,Object> result=statistics.statistics(day,day.plusDays(1));
        assertEquals(2L,result.get("questions"));assertEquals(4L,result.get("attempts"));assertEquals(1L,result.get("retries"));assertEquals(2/3d,(Double)result.get("successRate"),.001);assertEquals(1d,result.get("satisfaction"));assertEquals(.5d,result.get("feedbackCoverage"));
        List<?> trend=(List<?>)result.get("trend");assertEquals(2,trend.size());assertEquals(2L,((Map<?,?>)trend.get(0)).get("questions"));assertEquals(0L,((Map<?,?>)trend.get(1)).get("questions"));
        db.update("UPDATE ai_attempt_fact SET helpful=0 WHERE id=?",first);assertEquals(0d,statistics.statistics(day,day).get("satisfaction"));
        db.update("UPDATE ai_attempt_fact SET conversation_id=NULL,request_id=NULL WHERE id=?",first);assertEquals(4L,statistics.statistics(day,day).get("attempts"));
    }
    @Test void emptySamplesAreNullAndDateBoundsAreValidated(){
        Map<String,Object> empty=statistics.statistics(LocalDate.of(2010,1,1),LocalDate.of(2010,1,1));
        assertEquals(0L,empty.get("questions"));for(String key:List.of("successRate","satisfaction","feedbackCoverage","averageMs","firstContentMs"))assertNull(empty.get(key),key);
        assertThrows(RuntimeException.class,()->statistics.statistics(LocalDate.of(2010,1,2),LocalDate.of(2010,1,1)));
        assertThrows(RuntimeException.class,()->statistics.statistics(LocalDate.of(2010,1,1),LocalDate.of(2011,1,1)));
    }
    @Test void endpointIsOwnerOrAiStaffAndReturnsActualEmptyData()throws Exception{
        var login=mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/auth/login").contentType("application/json").content(json.writeValueAsBytes(Map.of("username","testadmin","password","TestAdminPassword123!")))).andReturn();
        String token=json.readTree(login.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        var result=mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/ai/statistics?from=2010-01-01&to=2010-01-01").header("Authorization","Bearer "+token)).andReturn();assertEquals(200,result.getResponse().getStatus());assertTrue(json.readTree(result.getResponse().getContentAsString()).path("data").path("satisfaction").isNull());
        assertEquals(401,mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/ai/statistics")).andReturn().getResponse().getStatus());
    }
}
