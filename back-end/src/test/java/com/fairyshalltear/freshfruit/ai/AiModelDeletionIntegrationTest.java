package com.fairyshalltear.freshfruit.ai;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fairyshalltear.freshfruit.ai.model.AiModelService;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:ai-model-deletion;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql",
 "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
 "security.admin.initial-username=testadmin","security.admin.initial-password=TestAdminPassword123!",
 "freshfruit.ai.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 "freshfruit.ai.jobs-enabled=false","freshfruit.ai.allow-local-endpoints=true"
})
@AutoConfigureMockMvc
class AiModelDeletionIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired AiMapper db;
    @Autowired AiModelService models; @Autowired PlatformTransactionManager transactions;
    String owner,label;
    @BeforeEach void setup() throws Exception {
        label=UUID.randomUUID().toString().substring(0,8);
        owner=call("POST","/api/v1/admin/auth/login",Map.of("username","testadmin","password","TestAdminPassword123!"),null,200).path("accessToken").asText();
        db.update("UPDATE ai_model_config SET is_active=0");
    }
    @Test void unusedDraftIsPermanentlyDeletedAndAudited() throws Exception {
        long id=create();
        assertEquals("DELETE",detail(id).path("deletionMode").asText());
        assertEquals("DELETE",call("DELETE",path(id),null,owner,200).path("mode").asText());
        assertEquals(0,db.count("SELECT COUNT(*) FROM ai_model_config WHERE id=?",id));
        assertFalse(listed(id)); call("GET",path(id),null,owner,404);
        call("DELETE",path(id),null,owner,404);
        assertEquals(1,db.count("SELECT COUNT(*) FROM admin_operation_log WHERE operation_type='AI_MODEL_DELETE' AND target_id=?",id));
    }
    @Test void archivedConfigurationKeepsHistoricalModelAndBlocksManagementReuse() throws Exception {
        long id=create(); long request=request(id,"COMPLETED");
        long conversation=db.one("SELECT conversation_id FROM ai_request WHERE id=?",request).number("conversation_id");
        JsonNode before=call("GET","/api/v1/admin/ai/conversations/"+conversation,null,owner,200);
        assertEquals("ARCHIVE",detail(id).path("deletionMode").asText());
        assertEquals("ARCHIVE",call("DELETE",path(id),null,owner,200).path("mode").asText());
        assertFalse(listed(id));
        assertNotNull(db.one("SELECT deleted_at FROM ai_model_config WHERE id=?",id).time("deleted_at"));
        JsonNode after=call("GET","/api/v1/admin/ai/conversations/"+conversation,null,owner,200);
        assertEquals(before.path("attempts"),after.path("attempts"));
        assertEquals("mock-model",after.path("attempts").get(0).path("model").asText());
        assertEquals(id,after.path("attempts").get(0).path("configId").asLong());
        assertEquals(before.path("messages"),after.path("messages"));
        assertEquals("mock-model",models.settings(id).chatModel());
        call("GET",path(id),null,owner,404);
        for(String operation:List.of("activate","disable","test","profiles")) call("POST",path(id)+"/"+operation,null,owner,404);
        var input=input();input.put("baseId",id);call("POST","/api/v1/admin/ai/models",input,owner,404);
        call("DELETE",path(id),null,owner,404);
        assertEquals(1,db.count("SELECT COUNT(*) FROM admin_operation_log WHERE operation_type='AI_MODEL_ARCHIVE' AND target_id=?",id));
    }
    @Test void activeVersionMustBeClosedBeforePermanentDeletion() throws Exception {
        long id=create();call("POST",path(id)+"/activate",null,owner,200);
        assertEquals("BLOCKED",detail(id).path("deletionMode").asText());
        call("DELETE",path(id),null,owner,409);
        assertEquals(id,models.active().orElseThrow().id());
        call("POST",path(id)+"/disable",null,owner,200);
        assertEquals("DELETE",call("DELETE",path(id),null,owner,200).path("mode").asText());
    }
    @Test void acceptedAndRunningAnswersBlockDeletionAfterConfigurationIsClosed() throws Exception {
        for(String status:List.of("ACCEPTED","RUNNING")) {
            long id=create(),request=request(id,status);
            assertEquals("BLOCKED",detail(id).path("deletionMode").asText());
            call("DELETE",path(id),null,owner,409);
            assertNull(db.one("SELECT deleted_at FROM ai_model_config WHERE id=?",id).time("deleted_at"));
            db.update("UPDATE ai_request SET status='CANCELLED' WHERE id=?",request);
            assertEquals("ARCHIVE",call("DELETE",path(id),null,owner,200).path("mode").asText());
        }
    }
    @Test void liveProfilesBlockDeletionAndRetiredOrFailedProfilesRemainTraceable() throws Exception {
        for(String status:List.of("BUILDING","READY","ACTIVE","RETIRED","FAILED")) {
            long id=create();long profile=db.insert("ai_embedding_profile",values("config_id",id,"model","mock-embedding","dimensions",1024,"status",status));
            boolean live=Set.of("BUILDING","READY","ACTIVE").contains(status);
            assertEquals(live?"BLOCKED":"ARCHIVE",detail(id).path("deletionMode").asText());
            call("DELETE",path(id),null,owner,live?409:200);
            if(live) { db.update("UPDATE ai_embedding_profile SET status='RETIRED' WHERE id=?",profile);call("DELETE",path(id),null,owner,200); }
            assertEquals(id,db.one("SELECT config_id FROM ai_embedding_profile WHERE id=?",profile).number("config_id"));
        }
    }
    @Test void pendingAndRunningIndexTasksProtectEvenFailedProfiles() throws Exception {
        for(String status:List.of("PENDING","RUNNING")) {
            long id=create(),profile=db.insert("ai_embedding_profile",values("config_id",id,"model","mock-embedding","dimensions",1024,"status","FAILED"));
            long document=db.insert("ai_knowledge_document",values("type","FAQ","title","保存方法","draft_body","冷藏"));
            long revision=db.insert("ai_knowledge_revision",values("document_id",document,"revision_no",1,"title","保存方法","body","冷藏","source_label","测试"));
            long chunk=db.insert("ai_knowledge_chunk",values("revision_id",revision,"ordinal",0,"content","冷藏"));
            db.update("INSERT INTO ai_chunk_embedding(chunk_id,profile_id,vector_json) VALUES(?,?,?)",chunk,profile,"[1,0]");
            long task=db.insert("ai_index_task",values("document_id",document,"revision_id",revision,"profile_id",profile,"expected_version",0,"status",status));
            call("DELETE",path(id),null,owner,409);
            db.update("UPDATE ai_index_task SET status='DONE' WHERE id=?",task);
            assertEquals("ARCHIVE",call("DELETE",path(id),null,owner,200).path("mode").asText());
            assertEquals(1,db.count("SELECT COUNT(*) FROM ai_chunk_embedding WHERE profile_id=?",profile));
            assertEquals(1,db.count("SELECT COUNT(*) FROM ai_index_task WHERE id=?",task));
        }
    }
    @Test void onlyOwnerCanDeleteIncludingDirectRequestsAndInvalidIds() throws Exception {
        long id=create();
        call("POST","/api/v1/admin/staff",Map.of("username","staff"+label,"password","StaffPassword123!","displayName","店员","modulePermissions",List.of("AI"),"sensitivePermissions",List.of()),owner,200);
        String staff=call("POST","/api/v1/admin/auth/login",Map.of("username","staff"+label,"password","StaffPassword123!"),null,200).path("accessToken").asText();
        String buyer=call("POST","/api/v1/auth/register",Map.of("username","buyer"+label,"phone","139"+String.format("%08d",Math.abs(label.hashCode()%100000000)),"password","BuyerPassword123!","nickname","测试"),null,200).path("accessToken").asText();
        call("DELETE",path(id),null,staff,403);call("DELETE",path(id),null,buyer,403);call("DELETE",path(id),null,null,401);
        call("DELETE",path(0),null,owner,400);call("DELETE",path(Long.MAX_VALUE),null,owner,404);
        assertEquals(1,db.count("SELECT COUNT(*) FROM ai_model_config WHERE id=? AND deleted_at IS NULL",id));
    }
    @Test void acceptingSnapshotSerializesWithClosingAndDeletingConfiguration() throws Exception {
        long id=create();call("POST",path(id)+"/activate",null,owner,200);
        long admin=db.one("SELECT id FROM admin_account WHERE account_type='OWNER'").id();
        var captured=new CountDownLatch(1);var release=new CountDownLatch(1);var closing=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            Future<Long> acceptance=pool.submit(()->new TransactionTemplate(transactions).execute(tx->{
                assertEquals(id,models.active().orElseThrow().id());captured.countDown();
                try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new RuntimeException(e); }
                return request(id,"ACCEPTED");
            }));
            assertTrue(captured.await(5,TimeUnit.SECONDS));
            Future<ApiException> deletion=pool.submit(()->{
                closing.countDown();models.disable(admin,id);
                return assertThrows(ApiException.class,()->models.delete(admin,id));
            });
            assertTrue(closing.await(5,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->deletion.get(150,TimeUnit.MILLISECONDS));
            release.countDown();long request=acceptance.get(5,TimeUnit.SECONDS);
            assertEquals("AI_CONFLICT",deletion.get(5,TimeUnit.SECONDS).code());
            assertEquals(id,db.one("SELECT config_id FROM ai_request WHERE id=?",request).number("config_id"));
            db.update("UPDATE ai_request SET status='CANCELLED' WHERE id=?",request);
            assertEquals("ARCHIVE",call("DELETE",path(id),null,owner,200).path("mode").asText());
        } finally { release.countDown();pool.shutdownNow(); }
    }
    private String path(long id) { return "/api/v1/admin/ai/models/"+id; }
    private Map<String,Object> input() { return new LinkedHashMap<>(Map.of("name","删除测试"+label,"chatBaseUrl","http://localhost:1234/v1","chatModel","mock-model","chatKey","sk-not-a-real-key","embeddingDimensions",1024,"promptTemplate","请回答{question}")); }
    private long create() throws Exception { return call("POST","/api/v1/admin/ai/models",input(),owner,200).path("id").asLong(); }
    private JsonNode detail(long id) throws Exception { return call("GET",path(id),null,owner,200); }
    private boolean listed(long id) throws Exception { for(JsonNode item:call("GET","/api/v1/admin/ai/models",null,owner,200)) if(item.path("id").asLong()==id)return true;return false; }
    private long request(long config,String status) {
        long conversation=db.insert("ai_conversation",values("owner_kind","GUEST","guest_token_hash",UUID.randomUUID().toString(),"expires_at",LocalDateTime.now().plusHours(24)));
        long question=db.insert("ai_message",values("conversation_id",conversation,"sequence_no",1,"role","USER","content","草莓怎么保存"));
        long answer=db.insert("ai_message",values("conversation_id",conversation,"sequence_no",2,"role","ASSISTANT","content","冷藏保存"));
        return db.insert("ai_request",values("conversation_id",conversation,"question_id",question,"answer_id",answer,"client_request_key",UUID.randomUUID().toString(),"payload_hash","a".repeat(64),"generation_epoch",0,"lease_until",LocalDateTime.now().plusSeconds(30),"config_id",config,"status",status));
    }
    private JsonNode call(String method,String path,Object payload,String token,int expected) throws Exception {
        var request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),URI.create(path));
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(payload!=null)request.contentType("application/json").content(json.writeValueAsBytes(payload));
        var result=mvc.perform(request).andReturn();String body=result.getResponse().getContentAsString();assertEquals(expected,result.getResponse().getStatus(),body);
        return json.readTree(body).path("data");
    }
}
