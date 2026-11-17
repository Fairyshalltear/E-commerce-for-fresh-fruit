package com.fairyshalltear.freshfruit.ai;

import java.nio.charset.StandardCharsets;
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
import com.fairyshalltear.freshfruit.ai.chat.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:ai-chat;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql",
 "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
 "security.admin.initial-username=testadmin","security.admin.initial-password=TestAdminPassword123!","freshfruit.ai.jobs-enabled=false"
})
@AutoConfigureMockMvc
class AiChatIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired AiMapper db;@Autowired AiConversationService conversations;
    String token,label;long buyer;
    @BeforeEach void setup()throws Exception{
        label=UUID.randomUUID().toString().substring(0,8);String phone="139"+String.format("%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100000000L));
        JsonNode user=call("POST","/api/v1/auth/register",Map.of("username","buyer"+label,"phone",phone,"password","BuyerPassword123!","nickname","测试"),null,null,200);
        token=user.path("accessToken").asText();buyer=jdbc.queryForObject("SELECT id FROM buyer_account WHERE username=?",Long.class,"buyer"+label);
    }
    @Test void guestTokenOwnershipExpiryAndPrivateContextAreEnforced()throws Exception{
        JsonNode guest=call("POST","/api/v1/public/ai/guest-session",Map.of(),null,null,200);long id=guest.path("conversationId").asLong();String visitor=guest.path("token").asText();
        call("GET","/api/v1/public/ai/conversations/"+id,null,null,visitor,200);
        call("GET","/api/v1/public/ai/conversations/"+id,null,null,null,401);
        call("GET","/api/v1/buyer/ai/conversations/"+id,null,token,null,404);
        call("POST","/api/v1/public/ai/conversations/"+id+"/messages",Map.of("clientRequestKey","private","text","查订单","context",Map.of("orderId",1)),null,visitor,404);
        JsonNode accepted=call("POST","/api/v1/public/ai/conversations/"+id+"/messages",Map.of("clientRequestKey","query","text","我的订单到哪了"),null,visitor,200);
        String frames=stream(accepted.path("id").asLong(),null,visitor);assertTrue(frames.contains("登录后可查询本人订单"));assertEquals(1,count(frames,"event:done"));
        jdbc.update("UPDATE ai_conversation SET expires_at=? WHERE id=?",LocalDateTime.now().minusSeconds(1),id);
        call("GET","/api/v1/public/ai/conversations/"+id,null,null,visitor,401);
    }
    @Test void twoStageSseIsIdempotentAndFeedbackDeletionKeepAnonymousFacts()throws Exception{
        long conversation=newConversation();Map<String,Object> input=Map.of("clientRequestKey","question","text","可以换货吗？");
        JsonNode accepted=call("POST",messages(conversation),input,token,null,200);long id=accepted.path("id").asLong();
        assertEquals(id,call("POST",messages(conversation),input,token,null,200).path("id").asLong());
        assertEquals(1,db.count("SELECT count_value FROM ai_usage_counter WHERE scope_key=?","BUYER:"+buyer));
        call("POST",messages(conversation),Map.of("clientRequestKey","question","text","另一问题"),token,null,409);
        call("POST",messages(conversation),Map.of("clientRequestKey","next","text","你好"),token,null,409);
        String frames=stream(id,token,null);assertTrue(frames.contains("不支持换货"));assertEquals(1,count(frames,"event:done"));assertTrue(frames.contains("event:sources"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM ai_question_fact WHERE conversation_id=?",conversation));assertEquals(1,db.count("SELECT COUNT(*) FROM ai_attempt_fact WHERE conversation_id=?",conversation));
        long answer=accepted.path("answerId").asLong();call("PUT","/api/v1/buyer/ai/answers/"+answer+"/feedback",Map.of("helpful",true),token,null,200);
        call("PUT","/api/v1/buyer/ai/answers/"+answer+"/feedback",Map.of("helpful",false),token,null,200);assertEquals(1,db.count("SELECT COUNT(*) FROM ai_feedback WHERE answer_id=?",answer));
        long fact=db.one("SELECT id FROM ai_attempt_fact WHERE request_id=?",id).id();
        call("DELETE","/api/v1/buyer/ai/conversations/"+conversation,null,token,null,200);call("GET","/api/v1/buyer/ai/requests/"+id,null,token,null,404);
        assertEquals(0,db.count("SELECT COUNT(*) FROM ai_message WHERE conversation_id=?",conversation));assertNull(db.one("SELECT * FROM ai_attempt_fact WHERE id=?",fact).number("request_id"));assertEquals(0,db.one("SELECT * FROM ai_attempt_fact WHERE id=?",fact).integer("helpful"));
    }
    @Test void stopAndRetryOnlyAddAttemptAndLateCheckpointCannotOverwriteTerminal()throws Exception{
        long conversation=newConversation();JsonNode first=call("POST",messages(conversation),Map.of("clientRequestKey","first","text","草莓怎么保存？"),token,null,200);long id=first.path("id").asLong();
        call("POST","/api/v1/buyer/ai/requests/"+id+"/stop",Map.of(),token,null,200);assertFalse(conversations.checkpoint(id,"迟到内容",true));
        assertFalse(conversations.finish(id,"COMPLETED","迟到完成","LLM",null,null,List.of(),List.of(),null,null));
        call("PUT","/api/v1/buyer/ai/answers/"+first.path("answerId").asLong()+"/feedback",Map.of("helpful",true),token,null,409);
        JsonNode retry=call("POST","/api/v1/buyer/ai/requests/"+id+"/retry",Map.of("clientRequestKey","retry"),token,null,200);
        assertEquals(first.path("questionId"),retry.path("questionId"));assertNotEquals(first.path("answerId"),retry.path("answerId"));
        stream(retry.path("id").asLong(),token,null);assertEquals(1,db.count("SELECT COUNT(*) FROM ai_question_fact WHERE conversation_id=?",conversation));assertEquals(2,db.count("SELECT COUNT(*) FROM ai_attempt_fact WHERE conversation_id=?",conversation));
        assertEquals(3,db.count("SELECT COUNT(*) FROM ai_message WHERE conversation_id=?",conversation));
    }
    @Test void buyerOrderTemplatesExcludePrivateFieldsAndBindExplicitOrderAcrossTurns()throws Exception{
        String number="FF1234567890123ABCDEF12";String wrong="FF1234567890123ABCDEF13";
        jdbc.update("INSERT INTO shop_order(order_no,buyer_id,idempotency_key,status,subtotal,total_amount,refunded_amount,recipient_name,phone,province,city,district,detail_address) VALUES(?,?,?,'PENDING_SHIPMENT',100,100,20,'私人姓名','13812345678','省','市','区','秘密地址')",number,buyer,label);
        long order=jdbc.queryForObject("SELECT id FROM shop_order WHERE order_no=?",Long.class,number);
        long conversation=newConversation();JsonNode first=call("POST",messages(conversation),Map.of("clientRequestKey","order","text","我的订单"+number+"到哪了？"),token,null,200);
        String frames=stream(first.path("id").asLong(),token,null);assertTrue(frames.contains("待发货"));assertTrue(frames.contains("100.00"));for(String secret:List.of("私人姓名","13812345678","秘密地址"))assertFalse(frames.contains(secret));
        assertEquals(order,call("GET","/api/v1/buyer/ai/conversations/"+conversation,null,token,null,200).path("conversation").path("context").path("orderId").asLong());
        JsonNode follow=call("POST",messages(conversation),Map.of("clientRequestKey","sale","text","那退款呢？"),token,null,200);assertTrue(stream(follow.path("id").asLong(),token,null).contains("尚无售后申请"));
        JsonNode unknown=call("POST",messages(conversation),Map.of("clientRequestKey","unknown","text","订单"+wrong),token,null,200);assertTrue(stream(unknown.path("id").asLong(),token,null).contains("未找到可查询的本人订单"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM ai_rule_hit WHERE request_id=? AND stage='BUSINESS'",unknown.path("id").asLong()));
        String saleNumber="AS1234567890123ABCDEF12";
        jdbc.update("INSERT INTO after_sale_request(request_no,order_id,buyer_id,type,status,reason,requested_amount) VALUES(?,?,?,'REFUND','COMPLETED','测试',20)",saleNumber,order,buyer);
        long sale=db.one("SELECT id FROM after_sale_request WHERE request_no=?",saleNumber).id();
        jdbc.update("INSERT INTO refund_record(request_id,order_id,amount,result,created_at) VALUES(?,?,20,'SIMULATED_SUCCESS',?)",sale,order,LocalDateTime.of(2020,1,2,10,15));
        JsonNode refund=call("POST",messages(conversation),Map.of("clientRequestKey","refund","text","查询售后"+saleNumber+"退款进度"),token,null,200);
        String refundFrames=stream(refund.path("id").asLong(),token,null);assertTrue(refundFrames.contains("模拟退款成功金额：￥20.00"));assertTrue(refundFrames.contains("2020-01-02T10:15"));assertTrue(refundFrames.contains("不代表真实支付到账"));
        String owner=call("POST","/api/v1/admin/auth/login",Map.of("username","testadmin","password","TestAdminPassword123!"),null,null,200).path("accessToken").asText();
        JsonNode record=call("GET","/api/v1/admin/ai/conversations/"+conversation,null,owner,null,200);
        for(String secret:List.of(number,saleNumber,"私人姓名","13812345678","秘密地址"))assertFalse(record.toString().contains(secret),secret);
        assertEquals(1,db.count("SELECT COUNT(*) FROM admin_operation_log WHERE operation_type='AI_CONVERSATION_VIEW' AND target_id=?",conversation));
        call("DELETE","/api/v1/buyer/ai/conversations/"+conversation,null,token,null,200);
        call("GET","/api/v1/admin/ai/conversations/"+conversation,null,owner,null,404);
    }
    @Test void explicitNewRequirementsReplaceOldFiltersAndTextOverridesStaleClientContext()throws Exception{
        long id=newConversation();
        JsonNode first=call("POST",messages(id),Map.of("clientRequestKey","filters","text","按条件推荐","context",Map.of("budget",100,"month",11,"giftBox",true,"useCase","ELDER_GIFT")),token,null,200);stream(first.path("id").asLong(),token,null);
        JsonNode next=call("POST",messages(id),Map.of("clientRequestKey","change","text","改成80元","context",Map.of("budget",100,"month",11,"giftBox",true,"useCase","ELDER_GIFT")),token,null,200);stream(next.path("id").asLong(),token,null);
        JsonNode context=call("GET","/api/v1/buyer/ai/conversations/"+id,null,token,null,200).path("conversation").path("context");assertEquals(80,context.path("budget").asInt());
        JsonNode clear=call("POST",messages(id),Map.of("clientRequestKey","clear","text","按条件推荐","context",Map.of("budget",90,"month",12,"quantity",1)),token,null,200);stream(clear.path("id").asLong(),token,null);
        context=call("GET","/api/v1/buyer/ai/conversations/"+id,null,token,null,200).path("conversation").path("context");assertTrue(context.path("giftBox").isNull());assertTrue(context.path("useCase").isNull());assertEquals(12,context.path("month").asInt());
    }
    private long newConversation()throws Exception{return call("POST","/api/v1/buyer/ai/conversations",Map.of(),token,null,200).path("id").asLong();}
    private String messages(long id){return "/api/v1/buyer/ai/conversations/"+id+"/messages";}
    private String stream(long id,String token,String guest)throws Exception{
        String prefix=guest==null?"buyer":"public";
        var request=MockMvcRequestBuilders.get("/api/v1/"+prefix+"/ai/requests/"+id+"/stream");if(token!=null)request.header("Authorization","Bearer "+token);if(guest!=null)request.header("X-AI-Guest-Token",guest);
        MvcResult result=mvc.perform(request).andReturn();assertEquals(200,result.getResponse().getStatus(),result.getResponse().getContentAsString());result.getAsyncResult(10000);
        var end=mvc.perform(MockMvcRequestBuilders.asyncDispatch(result)).andReturn();assertEquals(200,end.getResponse().getStatus(),end.getResponse().getContentAsString());return end.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
    private JsonNode call(String method,String path,Object payload,String token,String guest,int expected)throws Exception{
        var request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create(path));if(token!=null)request.header("Authorization","Bearer "+token);if(guest!=null)request.header("X-AI-Guest-Token",guest);if(payload!=null)request.contentType("application/json").content(json.writeValueAsBytes(payload));
        var result=mvc.perform(request).andReturn();assertEquals(expected,result.getResponse().getStatus(),result.getResponse().getContentAsString());return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
    private int count(String text,String token){return (text.length()-text.replace(token,"").length())/token.length();}
}
