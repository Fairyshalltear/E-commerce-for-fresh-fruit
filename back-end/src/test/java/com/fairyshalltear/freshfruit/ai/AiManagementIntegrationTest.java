package com.fairyshalltear.freshfruit.ai;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import com.fairyshalltear.freshfruit.ai.knowledge.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:ai-management;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql",
 "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
 "security.admin.initial-username=testadmin","security.admin.initial-password=TestAdminPassword123!",
 "freshfruit.ai.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 "freshfruit.ai.jobs-enabled=false","freshfruit.ai.allow-local-endpoints=true"
})
@AutoConfigureMockMvc
class AiManagementIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @Autowired AiMapper db;@Autowired AiIndexWorker index;@Autowired AiKnowledgeRetriever retriever;@Autowired AiUsageService usage;
    String owner,label;
    @BeforeEach void setup()throws Exception{
        label=UUID.randomUUID().toString().substring(0,8);
        owner=call("POST","/api/v1/admin/auth/login",Map.of("username","testadmin","password","TestAdminPassword123!"),null,200).path("accessToken").asText();
        // Process leftovers explicitly; scheduling is disabled in deterministic tests.
        while(index.runOne()){}
    }
    @Test void aiAccessIsExplicitAndModelsOwnerOnlyEvenForAuthorizedStaff()throws Exception{
        JsonNode staff=call("POST","/api/v1/admin/staff",Map.of("username","staff"+label,"password","StaffPassword123!","displayName","AI测试","modulePermissions",List.of("AI"),"sensitivePermissions",List.of()),owner,200);
        String token=call("POST","/api/v1/admin/auth/login",Map.of("username","staff"+label,"password","StaffPassword123!"),null,200).path("accessToken").asText();
        call("GET","/api/v1/admin/ai/knowledge",null,token,200);call("GET","/api/v1/admin/ai/rules",null,token,200);
        call("GET","/api/v1/admin/ai/models",null,token,403);call("GET","/api/v1/admin/ai/models/profiles",null,token,403);
        jdbc.update("DELETE FROM admin_permission WHERE admin_id=?",staff.path("id").asLong());
        call("GET","/api/v1/admin/ai/knowledge",null,token,403);
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO admin_permission(admin_id,permission_scope,permission_key) VALUES(?,'MODULE','UNKNOWN')",staff.path("id").asLong()));
        call("GET","/api/v1/admin/ai/knowledge",null,null,401);
    }
    @Test void unpublishedDraftDoesNotReplaceEffectiveFaqAndDisablingIsImmediate()throws Exception{
        String question="专属测试"+label+"怎么保存？";
        JsonNode doc=call("POST","/api/v1/admin/ai/knowledge",knowledge(question,"旧标准答案",0),owner,200);long id=doc.path("id").asLong();
        assertTrue(retriever.search(question,false).hits().isEmpty());
        doc=call("POST","/api/v1/admin/ai/knowledge/"+id+"/publish",Map.of("version",0),owner,200);
        while(index.runOne()){}
        assertEquals("KEYWORD_ONLY",call("GET","/api/v1/admin/ai/knowledge/"+id,null,owner,200).path("indexStatus").asText());
        assertTrue(retriever.search(question,false).hits().stream().anyMatch(h->h.exactFaq()&&h.document().getText().contains("旧标准答案")));
        doc=call("PUT","/api/v1/admin/ai/knowledge/"+id,knowledge(question,"新草稿答案",doc.path("version").asLong()),owner,200);
        assertFalse(retriever.search(question,false).hits().stream().anyMatch(h->h.document().getText().contains("新草稿答案")));
        call("PUT","/api/v1/admin/ai/knowledge/"+id,knowledge(question,"竞争草稿",0),owner,409);
        call("PATCH","/api/v1/admin/ai/knowledge/"+id+"/enabled",Map.of("version",doc.path("version").asLong(),"enabled",false),owner,200);
        assertTrue(retriever.search(question,false).hits().stream().noneMatch(h->h.source().id()==id));
        assertEquals(1,db.count("SELECT COUNT(*) FROM ai_knowledge_revision WHERE document_id=?",id));
    }
    @Test void credentialDraftsAreMaskedPreserveOmittedAndClearExplicitly()throws Exception{
        var payload=new LinkedHashMap<String,Object>();payload.put("name","模型"+label);payload.put("chatBaseUrl","http://localhost:1234/v1");payload.put("chatModel","mock-model");payload.put("chatKey","sk-not-a-real-key");payload.put("embeddingDimensions",1024);payload.put("promptTemplate","请回答{question}");
        JsonNode first=call("POST","/api/v1/admin/ai/models",payload,owner,200);
        assertTrue(first.path("hasChatKey").asBoolean());assertFalse(first.toString().contains("sk-not-a-real-key"));
        payload.remove("chatKey");payload.put("baseId",first.path("id").asLong());
        JsonNode preserved=call("POST","/api/v1/admin/ai/models",payload,owner,200);assertTrue(preserved.path("hasChatKey").asBoolean());
        assertEquals(db.one("SELECT chat_key_cipher FROM ai_model_config WHERE id=?",first.path("id").asLong()).text("chat_key_cipher"),db.one("SELECT chat_key_cipher FROM ai_model_config WHERE id=?",preserved.path("id").asLong()).text("chat_key_cipher"));
        payload.put("clearChatKey",true);JsonNode cleared=call("POST","/api/v1/admin/ai/models",payload,owner,200);assertFalse(cleared.path("hasChatKey").asBoolean());
        call("POST","/api/v1/admin/ai/models/"+first.path("id").asLong()+"/activate",Map.of(),owner,200);
        call("POST","/api/v1/admin/ai/models/"+first.path("id").asLong()+"/disable",Map.of(),owner,200);
        assertEquals(0,db.count("SELECT COUNT(*) FROM ai_model_config WHERE is_active=1"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM ai_model_config WHERE id=? AND enabled=1",first.path("id").asLong()));
    }
    @Test void dailyQuotaIsAtomicAndPersistsInDatabase(){
        String scope="TEST:"+label;
        assertTrue(usage.reserve(scope,2));assertTrue(usage.reserve(scope,2));assertFalse(usage.reserve(scope,2));
        assertEquals(2,db.count("SELECT count_value FROM ai_usage_counter WHERE scope_key=?",scope));
        assertFalse(new AiUsageService(db).reserve(scope,2));
    }
    @Test void concurrentQuotaReservationsStayBoundedAndGuestIpFrequencyIsShared()throws Exception{
        String scope="PARALLEL:"+label;var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try{
            var work=new ArrayList<java.util.concurrent.Future<Boolean>>();for(int i=0;i<24;i++)work.add(pool.submit(()->usage.reserve(scope,5)));
            int accepted=0;for(var task:work)if(task.get())accepted++;assertEquals(5,accepted);
            assertEquals(5,db.count("SELECT count_value FROM ai_usage_counter WHERE scope_key=?",scope));
        }finally{pool.shutdownNow();}
        var frequencies=new AiUsageService(db);for(int i=0;i<3;i++)frequencies.frequency("guest-"+i,"test-ip",true,AiDtos.Parameters.defaults());
        var failure=assertThrows(com.fairyshalltear.freshfruit.common.api.ApiException.class,()->frequencies.frequency("another-guest","test-ip",true,AiDtos.Parameters.defaults()));
        assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,failure.status());
    }
    @Test void ruleValidationVersionsAndAuditDoNotCopyTestInput()throws Exception{
        var input=new LinkedHashMap<String,Object>(Map.of("name","规则"+label,"scope","OUTPUT","matchMode","ALL","terms",List.of(label),"priority",1,"message","已拦截","enabled",true));
        call("POST","/api/v1/admin/ai/rules",input,owner,400);input.put("matchMode","REGEX");call("POST","/api/v1/admin/ai/rules",input,owner,400);
        input.put("matchMode","LITERAL");JsonNode rule=call("POST","/api/v1/admin/ai/rules",input,owner,200);long id=rule.path("id").asLong();input.put("version",0);
        call("PUT","/api/v1/admin/ai/rules/"+id,input,owner,200);call("PUT","/api/v1/admin/ai/rules/"+id,input,owner,409);
        assertTrue(call("POST","/api/v1/admin/ai/rules/test?scope=OUTPUT",Map.of("question",label),owner,200).path("blocked").asBoolean());
        call("PATCH","/api/v1/admin/ai/rules/"+id+"/enabled",Map.of("version",1,"enabled",false),owner,200);
        assertFalse(call("POST","/api/v1/admin/ai/rules/test?scope=OUTPUT",Map.of("question",label),owner,200).path("blocked").asBoolean());
        assertEquals(3,db.count("SELECT COUNT(*) FROM admin_operation_log WHERE target_type='AI_RULE' AND target_id=? AND operation_type IN ('AI_RULE_SAVE','AI_RULE_ENABLE')",id));
    }
    @Test void aiRequestAndFeedbackKeysAndConversationDomainsAreConstrained(){
        String hash="a".repeat(64);
        long conversation=db.insert("ai_conversation",values("owner_kind","GUEST","guest_token_hash",hash,"expires_at",LocalDateTime.now().plusHours(24)));
        long question=db.insert("ai_message",values("conversation_id",conversation,"sequence_no",1,"role","USER","content","问题"));
        long answer=db.insert("ai_message",values("conversation_id",conversation,"sequence_no",2,"role","ASSISTANT","content","回答"));
        var request=values("conversation_id",conversation,"question_id",question,"answer_id",answer,"client_request_key","key","payload_hash",hash,"generation_epoch",1,"lease_until",LocalDateTime.now().plusSeconds(30));
        db.insert("ai_request",request);assertThrows(Exception.class,()->db.insert("ai_request",request));
        jdbc.update("INSERT INTO ai_feedback(answer_id,helpful) VALUES(?,1)",answer);assertThrows(Exception.class,()->jdbc.update("INSERT INTO ai_feedback(answer_id,helpful) VALUES(?,0)",answer));
        assertThrows(Exception.class,()->db.insert("ai_conversation",values("owner_kind","BUYER","guest_token_hash",hash,"expires_at",LocalDateTime.now())));
        assertThrows(Exception.class,()->db.insert("ai_message",values("conversation_id",conversation,"sequence_no",1,"role","USER","content","重复")));
    }
    private Map<String,Object> knowledge(String question,String body,long version){return Map.of("title","测试知识"+label,"type","FAQ","body",body,"standardQuestion",question,"aliases",List.of("同义"+label),"sourceLabel","已审核测试资料","version",version);}
    private JsonNode call(String method,String path,Object payload,String token,int expected)throws Exception{
        var request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create(path));
        if(token!=null)request.header("Authorization","Bearer "+token);if(payload!=null)request.contentType("application/json").content(json.writeValueAsBytes(payload));
        var result=mvc.perform(request).andReturn();assertEquals(expected,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
