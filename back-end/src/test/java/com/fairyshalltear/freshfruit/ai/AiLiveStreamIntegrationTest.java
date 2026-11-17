package com.fairyshalltear.freshfruit.ai;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fairyshalltear.freshfruit.ai.chat.*;
import com.fairyshalltear.freshfruit.ai.knowledge.*;
import com.fairyshalltear.freshfruit.ai.model.*;
import com.fairyshalltear.freshfruit.ai.safety.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:ai-live;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql",
 "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
 "security.admin.initial-username=testadmin","security.admin.initial-password=TestAdminPassword123!",
 "freshfruit.ai.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 "freshfruit.ai.jobs-enabled=false","freshfruit.ai.allow-local-endpoints=true"
})
@AutoConfigureMockMvc
class AiLiveStreamIntegrationTest {
    static final HttpServer provider;
    static final ExecutorService handlers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"mock-provider");t.setDaemon(true);return t;});
    static final AtomicInteger mode=new AtomicInteger(),calls=new AtomicInteger();
    static final AtomicReference<String> capturedInput=new AtomicReference<>();
    static volatile CountDownLatch started=new CountDownLatch(1),cancelled=new CountDownLatch(1);
    static final ObjectMapper codec=new ObjectMapper();
    static {
        try{provider=HttpServer.create(new InetSocketAddress("localhost",0),0);provider.setExecutor(handlers);provider.createContext("/v1/chat/completions",AiLiveStreamIntegrationTest::generate);provider.start();}catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired AiMapper db;@Autowired AiModelService models;@Autowired AiKnowledgeService knowledge;@Autowired AiIndexWorker index;@Autowired AiRuleService rules;
    String token,label;long admin,conversation,document,configId;
    @BeforeEach void setup()throws Exception{
        label=UUID.randomUUID().toString().substring(0,8);calls.set(0);mode.set(0);started=new CountDownLatch(1);cancelled=new CountDownLatch(1);
        admin=db.one("SELECT id FROM admin_account WHERE account_type='OWNER' ORDER BY id LIMIT 1").id();
        String base="http://localhost:"+provider.getAddress().getPort()+"/v1";
        var p=new Parameters(null,null,null,null,3,3,15,60,null,null,null,null);
        long config=((Number)models.save(admin,new ModelInput(null,"mock"+label,base,"mock","sk-test-only",false,"","",null,false,false,64,"请依据资料回答{question}，{knowledge}",p)).get("id")).longValue();models.activate(admin,config);configId=config;
        var doc=knowledge.save(admin,null,new KnowledgeInput("草莓保鲜"+label,"FRUIT","草莓应冷藏保存，保持干燥，食用前清洗。资料标识"+label,null,List.of(),"已审核测试资料",null,null));document=doc.id();knowledge.publish(admin,document,doc.version());while(index.runOne()){}
        String phone="139"+String.format("%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100000000L));
        token=call("POST","/api/v1/auth/register",Map.of("username","live"+label,"phone",phone,"password","BuyerPassword123!","nickname","测试"),null,200).path("accessToken").asText();
        conversation=call("POST","/api/v1/buyer/ai/conversations",Map.of(),token,200).path("id").asLong();
    }
    @AfterAll static void close(){provider.stop(0);handlers.shutdownNow();}
    @Test void ragGeneratesOneCallWithGroundedSourcesAndNativeMissingUsage()throws Exception{
        long request=accept();String frames=finish(stream(request));
        assertTrue(frames.contains("草莓冷藏保存"));assertTrue(frames.contains("event:sources"));assertEquals(1,calls.get());assertEquals("COMPLETED",db.one("SELECT status FROM ai_request WHERE id=?",request).text("status"));
        assertEquals("LLM",db.one("SELECT answer_mode FROM ai_request WHERE id=?",request).text("answer_mode"));assertNull(db.one("SELECT prompt_tokens FROM ai_request WHERE id=?",request).number("prompt_tokens"));
    }
    @Test void externalPromptAndPersistedQuestionMaskKnownPrivateValues()throws Exception{
        String question="草莓如何冷藏保鲜？资料"+label+" 电话13812345678 江苏省南京市中山路1号 Key sk-private-example Bearer token123";
        long request=call("POST","/api/v1/buyer/ai/conversations/"+conversation+"/messages",Map.of("clientRequestKey","privacy","text",question),token,200).path("id").asLong();
        finish(stream(request));String external=capturedInput.get();assertNotNull(external);assertEquals(1,calls.get());
        String persisted=db.one("SELECT m.content FROM ai_message m JOIN ai_request r ON r.question_id=m.id WHERE r.id=?",request).text("content");
        for(String secret:List.of("13812345678","中山路1号","sk-private-example","token123")){assertFalse(external.contains(secret),secret);assertFalse(persisted.contains(secret),secret);}
        assertTrue(external.contains("草莓"));
    }
    @Test void stopCancelsActualProviderAndCannotBeOverwrittenByLateChunks()throws Exception{
        mode.set(2);long request=accept();MvcResult stream=stream(request);assertTrue(started.await(5,TimeUnit.SECONDS));
        call("POST","/api/v1/buyer/ai/requests/"+request+"/stop",Map.of(),token,200);String frames=finish(stream);assertTrue(cancelled.await(5,TimeUnit.SECONDS));
        assertEquals(1,count(frames,"event:done"));assertEquals("CANCELLED",db.one("SELECT status FROM ai_request WHERE id=?",request).text("status"));assertEquals(1,calls.get());
    }
    @Test void crossChunkRuleBlocksBeforeSensitiveTermIsVisible()throws Exception{
        mode.set(1);rules.save(admin,null,new RuleInput("跨片段"+label,"OUTPUT","LITERAL",List.of("禁止词"),99,"内容已拦截",true,null));
        long request=accept();String frames=finish(stream(request));assertFalse(frames.contains("禁止词"));assertEquals("BLOCKED",db.one("SELECT status FROM ai_request WHERE id=?",request).text("status"));
        assertFalse(db.one("SELECT m.content FROM ai_message m JOIN ai_request r ON r.answer_id=m.id WHERE r.id=?",request).text("content").contains("禁止词"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM ai_rule_hit WHERE request_id=? AND stage='OUTPUT'",request));
    }
    @Test void deletingDuringNativeStreamErasesContentAndPreservesAnonymousAttempt()throws Exception{
        mode.set(2);long request=accept();MvcResult stream=stream(request);assertTrue(started.await(5,TimeUnit.SECONDS));
        long fact=db.one("SELECT id FROM ai_attempt_fact WHERE request_id=?",request).id();
        call("DELETE","/api/v1/buyer/ai/conversations/"+conversation,null,token,200);finish(stream);assertTrue(cancelled.await(5,TimeUnit.SECONDS));
        assertEquals(0,db.count("SELECT COUNT(*) FROM ai_message WHERE conversation_id=?",conversation));assertEquals(0,db.count("SELECT COUNT(*) FROM ai_request WHERE id=?",request));assertNull(db.one("SELECT request_id FROM ai_attempt_fact WHERE id=?",fact).number("request_id"));
    }
    @Test void independentStreamCapabilityRejectsEmptyAndFailedStreams(){
        mode.set(3);assertFalse(models.test(admin,configId,"stream").get(0).success());
        mode.set(4);assertFalse(models.test(admin,configId,"stream").get(0).success());
        mode.set(0);assertTrue(models.test(admin,configId,"stream").get(0).success());
        assertEquals(3,calls.get());
    }
    @Test void changedKnowledgeStopsAnActiveAnswerAndRetainsTheOriginalSnapshot()throws Exception{
        mode.set(5);long request=accept();MvcResult stream=stream(request);assertTrue(started.await(5,TimeUnit.SECONDS));
        var doc=knowledge.detail(document);knowledge.enabled(admin,document,new EnabledInput(false,doc.version()));
        String frames=finish(stream);assertEquals("FAILED",db.one("SELECT status FROM ai_request WHERE id=?",request).text("status"));
        assertTrue(frames.contains("AI_KNOWLEDGE_CHANGED"));assertEquals(1,count(frames,"event:done"));
    }
    @Test void firstContentTimeoutFinishesInBasicModeAndDoesNotRetryProvider()throws Exception{
        mode.set(6);long request=accept();String frames=finish(stream(request));
        assertEquals("DEGRADED",db.one("SELECT status FROM ai_request WHERE id=?",request).text("status"));
        assertEquals(1,calls.get());assertEquals(1,count(frames,"event:done"));
        assertFalse(frames.contains("草莓冷藏保存"));
    }
    private long accept()throws Exception{return call("POST","/api/v1/buyer/ai/conversations/"+conversation+"/messages",Map.of("clientRequestKey","first","text","草莓如何冷藏保鲜？资料"+label),token,200).path("id").asLong();}
    private MvcResult stream(long id)throws Exception{var result=mvc.perform(MockMvcRequestBuilders.get("/api/v1/buyer/ai/requests/"+id+"/stream").header("Authorization","Bearer "+token)).andReturn();assertEquals(200,result.getResponse().getStatus());return result;}
    private String finish(MvcResult result)throws Exception{result.getAsyncResult(10000);var end=mvc.perform(MockMvcRequestBuilders.asyncDispatch(result)).andReturn();assertEquals(200,end.getResponse().getStatus(),end.getResponse().getContentAsString());return end.getResponse().getContentAsString(StandardCharsets.UTF_8);}
    private JsonNode call(String method,String path,Object payload,String token,int expected)throws Exception{var request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create(path));if(token!=null)request.header("Authorization","Bearer "+token);if(payload!=null)request.contentType("application/json").content(json.writeValueAsBytes(payload));var result=mvc.perform(request).andReturn();assertEquals(expected,result.getResponse().getStatus(),result.getResponse().getContentAsString());return json.readTree(result.getResponse().getContentAsString()).path("data");}
    private int count(String text,String term){return(text.length()-text.replace(term,"").length())/term.length();}
    private static void generate(HttpExchange exchange)throws java.io.IOException{
        calls.incrementAndGet();JsonNode input=codec.readTree(exchange.getRequestBody());capturedInput.set(input.toString());assertTrue(input.path("stream").asBoolean());
        if(mode.get()==4){exchange.sendResponseHeaders(500,-1);exchange.close();return;}
        exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);started.countDown();
        try(var out=exchange.getResponseBody()){
            if(mode.get()==3){out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));return;}
            if(mode.get()==5)try{Thread.sleep(250);}catch(InterruptedException ignored){return;}
            if(mode.get()==6)try{Thread.sleep(4500);}catch(InterruptedException ignored){return;}
            List<String> chunks=mode.get()==1?List.of("普通文字禁","止","词"):List.of("草莓","冷藏","保存");
            for(String text:chunks){out.write(frame(text).getBytes(StandardCharsets.UTF_8));out.flush();}
            if(mode.get()==2)for(int i=0;i<250;i++){out.write(frame("安全文字".repeat(500)).getBytes(StandardCharsets.UTF_8));out.flush();try{Thread.sleep(20);}catch(InterruptedException ignored){return;}}
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        }catch(java.io.IOException error){cancelled.countDown();}finally{exchange.close();}
    }
    private static String frame(String text)throws java.io.IOException{return "data: "+codec.writeValueAsString(Map.of("id","mock-stream","object","chat.completion.chunk","created",1,"model","mock","choices",List.of(Map.of("index",0,"delta",Map.of("role","assistant","content",text)))))+"\n\n";}
}
