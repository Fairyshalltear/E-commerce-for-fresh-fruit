package com.fairyshalltear.freshfruit.ai;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.fairyshalltear.freshfruit.ai.chat.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:ai-recovery;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql",
 "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
 "security.admin.initial-username=testadmin","security.admin.initial-password=TestAdminPassword123!",
 "freshfruit.ai.jobs-enabled=true"
})
class AiRecoveryIntegrationTest {
    @Autowired AiMapper db;
    @Autowired AiConversationService conversations;
    @Autowired AiStreamService streams;
    @Test void expiredUnsubscribedAndRestartedRequestsReleaseOccupancyAndRetentionAnonymizes(){
        GuestSession session=conversations.guest("recovery-test");
        AiActor actor=conversations.guestActor(session.token(),"recovery-test");
        Request first=conversations.accept(actor,session.conversationId(),new MessageInput("before-subscription","水果咨询",null));
        db.update("UPDATE ai_request SET lease_until=? WHERE id=?",LocalDateTime.now().minusMinutes(1),first.id());streams.maintain();
        assertEquals("AI_NOT_SUBSCRIBED",conversations.request(actor,first.id()).errorCode());
        Request next=conversations.accept(actor,session.conversationId(),new MessageInput("after-restart","查询水果",null));
        conversations.claim(actor,next.id());assertTrue(conversations.checkpoint(next.id(),"安全片段",true));
        db.update("UPDATE ai_request SET lease_until=? WHERE id=?",LocalDateTime.now().minusMinutes(1),next.id());streams.maintain();
        assertEquals("PROCESS_RESTART",conversations.request(actor,next.id()).errorCode());
        assertFalse(conversations.finish(next.id(),"COMPLETED","迟到内容","LLM",null,null,List.of(),List.of(),null,null));
        assertNull(db.one("SELECT active_request_id FROM ai_conversation WHERE id=?",session.conversationId()).number("active_request_id"));
        assertTrue(conversations.detail(actor,session.conversationId()).messages().stream().anyMatch(m->m.content().equals("安全片段")));
        long questions=db.count("SELECT COUNT(*) FROM ai_question_fact");
        db.update("UPDATE ai_conversation SET expires_at=? WHERE id=?",LocalDateTime.now().minusMinutes(1),session.conversationId());streams.clean();
        assertEquals(0,db.count("SELECT COUNT(*) FROM ai_conversation WHERE id=?",session.conversationId()));
        assertEquals(0,db.count("SELECT COUNT(*) FROM ai_message WHERE conversation_id=?",session.conversationId()));
        assertEquals(questions,db.count("SELECT COUNT(*) FROM ai_question_fact"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM ai_question_fact WHERE conversation_id IS NOT NULL"));
    }
}
