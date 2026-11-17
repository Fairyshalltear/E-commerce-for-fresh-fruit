package com.fairyshalltear.freshfruit.ai.chat;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.model.*;
import com.fairyshalltear.freshfruit.ai.safety.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static com.fairyshalltear.freshfruit.ai.AiErrors.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiConversationService {
    public record TerminalEvent(long requestId){}
    private static final Set<String> ACTIVE=Set.of("ACCEPTED","RUNNING");
    private final AiMapper db;private final AiJson json;private final AiBusinessService business;private final AiModelService models;
    private final AiRuleService rules;private final AiUsageService usage;private final ApplicationEventPublisher events;
    private final SecureRandom random=new SecureRandom();
    public AiConversationService(AiMapper db,AiJson json,AiBusinessService business,AiModelService models,AiRuleService rules,AiUsageService usage,ApplicationEventPublisher events){this.db=db;this.json=json;this.business=business;this.models=models;this.rules=rules;this.usage=usage;this.events=events;}
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException(error);}}
    public AiActor guestActor(String token,String ip){
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))throw new ApiException("UNAUTHORIZED","访客会话已失效，请重新开始",HttpStatus.UNAUTHORIZED);
        String hash=hash(token);
        if(db.count("SELECT COUNT(*) FROM ai_conversation WHERE guest_token_hash=? AND deleted_at IS NULL AND expires_at>?",hash,LocalDateTime.now())==0)throw new ApiException("UNAUTHORIZED","访客会话已失效，请重新开始",HttpStatus.UNAUTHORIZED);
        return new AiActor("GUEST",null,hash,ip);
    }
    @Transactional
    public GuestSession guest(String ip){
        usage.frequency("GUEST_SESSION:"+ip,"SESSION:"+ip,true,Parameters.defaults());
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var actor=new AiActor("GUEST",null,hash(token),ip);var conversation=create(actor,Context.empty());
        return new GuestSession(token,conversation.id(),conversation.expiresAt());
    }
    @Transactional
    public Conversation create(AiActor actor,Context context){
        Context safe=business.validate(actor,context);
        LocalDateTime now=LocalDateTime.now(),expires=now.plusDays(actor.guest()?1:180);
        if(actor.guest())expires=db.optional("SELECT MIN(expires_at) AS expires FROM ai_conversation WHERE guest_token_hash=? AND deleted_at IS NULL",actor.guestHash()).map(r->r.time("expires")).orElse(expires);
        long id=db.insert("ai_conversation",values("owner_kind",actor.kind(),"buyer_id",actor.buyerId(),"guest_token_hash",actor.guestHash(),"context_json",json.write(safe),"expires_at",expires));
        return conversation(owned(actor,id,false));
    }
    public Page<Conversation> list(AiActor actor,int page,int size){
        if(actor.guest())throw missing();
        long total=db.count("SELECT COUNT(*) FROM ai_conversation WHERE buyer_id=? AND deleted_at IS NULL AND expires_at>?",actor.buyerId(),LocalDateTime.now());
        var rows=db.rows("SELECT * FROM ai_conversation WHERE buyer_id=? AND deleted_at IS NULL AND expires_at>? ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",actor.buyerId(),LocalDateTime.now(),size,(long)(page-1)*size);
        return new Page<>(rows.stream().map(this::conversation).toList(),total,page,size);
    }
    public ConversationDetail detail(AiActor actor,long id){return new ConversationDetail(conversation(owned(actor,id,false)),messages(id));}
    public AiRow owned(AiActor actor,long id,boolean lock){
        String ownership=actor.guest()?"owner_kind='GUEST' AND guest_token_hash=?":"owner_kind='BUYER' AND buyer_id=?";
        return db.one("SELECT * FROM ai_conversation WHERE id=? AND "+ownership+" AND deleted_at IS NULL AND expires_at>?"+(lock?" FOR UPDATE":""),id,actor.guest()?actor.guestHash():actor.buyerId(),LocalDateTime.now());
    }
    public AiRow requestRow(AiActor actor,long id){AiRow request=db.one("SELECT * FROM ai_request WHERE id=?",id);owned(actor,request.number("conversation_id"),false);return request;}
    public Request request(AiActor actor,long id){return requestView(requestRow(actor,id));}
    @Transactional
    public Request accept(AiActor actor,long conversationId,MessageInput input){
        AiRow conversation=owned(actor,conversationId,true);
        String key=input.clientRequestKey().strip();String payload=hash(json.write(input));
        var existing=db.optional("SELECT * FROM ai_request WHERE conversation_id=? AND client_request_key=?",conversationId,key);
        if(existing.isPresent()){if(!payload.equals(existing.get().text("payload_hash")))throw conflict("相同请求标识的内容发生变化");return requestView(existing.get());}
        if(conversation.number("active_request_id")!=null)throw conflict("请等待当前回答完成或先停止");
        Context context=business.validate(actor,business.confirmed(actor,input.text(),AiScenes.selected(context(conversation),input.context())));
        String scene=AiScenes.identify(input.text(),context);
        var settings=models.active().orElse(null);Parameters limits=settings==null?Parameters.defaults():settings.parameters();
        usage.frequency(actor.identity(),actor.ip(),actor.guest(),limits);
        if(!usage.reserve(actor.identity(),actor.guest()?limits.guestDaily():limits.buyerDaily()))throw new ApiException("AI_RATE_LIMIT","今日提问额度已用完，请明天再试",HttpStatus.TOO_MANY_REQUESTS);
        List<Rule> snapshot=rules.snapshot();var blocked=AiRuleService.match(snapshot,"INPUT",input.text());
        long sequence=conversation.number("next_sequence"),epoch=conversation.number("epoch")+1;
        String safeText=blocked.isPresent()?"[输入已拦截]":AiPrivacy.mask(input.text().strip());
        long question=db.insert("ai_message",values("conversation_id",conversationId,"sequence_no",sequence+1,"role","USER","content",safeText));
        long answer=db.insert("ai_message",values("conversation_id",conversationId,"sequence_no",sequence+2,"role","ASSISTANT","content",""));
        long request=db.insert("ai_request",values("conversation_id",conversationId,"question_id",question,"answer_id",answer,"client_request_key",key,"payload_hash",payload,
            "generation_epoch",epoch,"input_context",json.write(context),"config_id",settings==null?null:settings.id(),"rules_snapshot",json.write(snapshot),"scene",scene,"lease_until",LocalDateTime.now().plusSeconds(30)));
        db.update("UPDATE ai_conversation SET active_request_id=?,epoch=?,next_sequence=?,context_json=?,title=?,updated_at=?,expires_at=? WHERE id=?",request,epoch,sequence+2,json.write(context),AiScenes.topic(scene),LocalDateTime.now(),actor.guest()?conversation.time("expires_at"):LocalDateTime.now().plusDays(180),conversationId);
        db.insert("ai_question_fact",values("question_id",question,"conversation_id",conversationId,"owner_kind",actor.kind(),"buyer_id",actor.buyerId(),"scene",scene,"topic",AiScenes.topic(scene)));
        db.insert("ai_attempt_fact",values("request_id",request,"conversation_id",conversationId,"status","ACCEPTED","scene",scene));
        if(blocked.isPresent()){rules.hit(request,blocked.get(),"INPUT");finish(request,"BLOCKED",blocked.get().message(),"DEGRADED","INPUT_BLOCKED","问题触发输入规则",List.of(),List.of(),null,null);}
        return request(actor,request);
    }
    @Transactional
    public Request retry(AiActor actor,long originalId,RetryInput input){
        AiRow original=requestRow(actor,originalId);AiRow conversation=owned(actor,original.number("conversation_id"),true);
        String key=input.clientRequestKey().strip(),payload=hash("retry:"+originalId);
        var existing=db.optional("SELECT * FROM ai_request WHERE conversation_id=? AND client_request_key=?",conversation.id(),key);
        if(existing.isPresent()){if(!payload.equals(existing.get().text("payload_hash")))throw conflict("重试标识发生变化");return requestView(existing.get());}
        if(ACTIVE.contains(original.text("status")) || conversation.number("active_request_id")!=null)throw conflict("活动回答不能重试，请先停止");
        if(original.text("status").equals("BLOCKED"))throw conflict("已拦截的问题不能重试，请修改后重新发送");
        var settings=models.active().orElse(null);usage.frequency(actor.identity(),actor.ip(),actor.guest(),settings==null?Parameters.defaults():settings.parameters());
        long epoch=conversation.number("epoch")+1,sequence=conversation.number("next_sequence")+1;
        Context context=business.validate(actor,json.read(original.get("input_context"),Context.class));
        long answer=db.insert("ai_message",values("conversation_id",conversation.id(),"sequence_no",sequence,"role","ASSISTANT","content",""));
        long id=db.insert("ai_request",values("conversation_id",conversation.id(),"question_id",original.number("question_id"),"answer_id",answer,"client_request_key",key,"payload_hash",payload,"retry_of",originalId,
            "generation_epoch",epoch,"input_context",json.write(context),"config_id",settings==null?null:settings.id(),"rules_snapshot",json.write(rules.snapshot()),"scene",original.text("scene"),"lease_until",LocalDateTime.now().plusSeconds(30)));
        db.update("UPDATE ai_conversation SET active_request_id=?,epoch=?,next_sequence=?,updated_at=? WHERE id=?",id,epoch,sequence,LocalDateTime.now(),conversation.id());
        db.insert("ai_attempt_fact",values("request_id",id,"conversation_id",conversation.id(),"status","ACCEPTED","is_retry",1,"scene",original.text("scene")));
        var blocked=AiRuleService.match(rules.snapshot(),"INPUT",db.one("SELECT content FROM ai_message WHERE id=?",original.number("question_id")).text("content"));
        if(blocked.isPresent()){rules.hit(id,blocked.get(),"INPUT");finish(id,"BLOCKED",blocked.get().message(),"DEGRADED","INPUT_BLOCKED","问题触发输入规则",List.of(),List.of(),null,null);}
        return request(actor,id);
    }
    @Transactional
    public AiRow claim(AiActor actor,long id){
        AiRow request=requestRow(actor,id);owned(actor,request.number("conversation_id"),true);
        if(request.text("status").equals("RUNNING"))throw conflict("该回答已被订阅，请读取当前状态");
        if(!request.text("status").equals("ACCEPTED"))return request;
        if(db.update("UPDATE ai_request SET status='RUNNING',lease_until=? WHERE id=? AND status='ACCEPTED'",LocalDateTime.now().plusSeconds(120),id)!=1)throw conflict("该回答已被订阅");
        db.update("UPDATE ai_attempt_fact SET status='RUNNING' WHERE request_id=?",id);return db.one("SELECT * FROM ai_request WHERE id=?",id);
    }
    @Transactional
    public boolean checkpoint(long id,String text,boolean first){
        Optional<AiRow> optional=db.optional("SELECT * FROM ai_request WHERE id=?",id);if(optional.isEmpty())return false;AiRow r=optional.get();
        AiRow c=db.one("SELECT * FROM ai_conversation WHERE id=? FOR UPDATE",r.number("conversation_id"));
        if(!valid(r,c))return false;
        db.update("UPDATE ai_message SET content=? WHERE id=?",text,r.number("answer_id"));
        if(first)db.update("UPDATE ai_request SET first_content_at=? WHERE id=? AND first_content_at IS NULL",LocalDateTime.now(),id);
        db.update("UPDATE ai_request SET lease_until=? WHERE id=? AND status='RUNNING'",LocalDateTime.now().plusSeconds(120),id);return true;
    }
    @Transactional
    public boolean finish(long id,String status,String text,String mode,String code,String message,List<Card> cards,List<Source> sources,Long promptTokens,Long completionTokens){
        if(ACTIVE.contains(status))throw new IllegalArgumentException("终态无效");
        var optional=db.optional("SELECT * FROM ai_request WHERE id=?",id);if(optional.isEmpty())return false;AiRow r=optional.get();
        AiRow conversation=db.one("SELECT * FROM ai_conversation WHERE id=? FOR UPDATE",r.number("conversation_id"));
        if(!valid(r,conversation))return false;
        LocalDateTime now=LocalDateTime.now();
        if(db.update("UPDATE ai_request SET status=?,answer_mode=?,error_code=?,error_message=?,cards_json=?,prompt_tokens=?,completion_tokens=?,finished_at=? WHERE id=? AND status IN ('ACCEPTED','RUNNING')",
            status,mode,code,message,json.write(cards),promptTokens,completionTokens,now,id)!=1)return false;
        db.update("UPDATE ai_message SET content=? WHERE id=?",text,r.number("answer_id"));
        for(Source source:sources)db.insert("ai_answer_source",values("answer_id",r.number("answer_id"),"ordinal",source.ordinal(),"source_type",source.type(),"source_id",source.id(),"revision_no",source.version(),"title",source.title(),"excerpt",source.excerpt(),"target_path",source.path()));
        db.update("UPDATE ai_conversation SET active_request_id=NULL,updated_at=? WHERE id=? AND active_request_id=?",now,conversation.id(),id);
        Long first=r.time("first_content_at")==null?null:Duration.between(r.time("created_at"),r.time("first_content_at")).toMillis();
        db.update("UPDATE ai_attempt_fact SET status=?,mode=?,first_content_ms=?,total_ms=? WHERE request_id=?",status,mode,first,Duration.between(r.time("created_at"),now).toMillis(),id);
        terminal(id);return true;
    }
    @Transactional
    public Request stop(AiActor actor,long id){AiRow r=requestRow(actor,id);String content=db.one("SELECT content FROM ai_message WHERE id=?",r.number("answer_id")).text("content");finish(id,"CANCELLED",content,r.text("answer_mode"),"USER_STOPPED","已停止回答",List.of(),List.of(),r.number("prompt_tokens"),r.number("completion_tokens"));return request(actor,id);}
    @Transactional
    public void feedback(AiActor actor,long answer,FeedbackInput input){
        AiRow r=db.one("SELECT * FROM ai_request WHERE answer_id=?",answer);owned(actor,r.number("conversation_id"),true);
        if(!Set.of("COMPLETED","DEGRADED").contains(r.text("status")))throw conflict("只有完成的回答可以评价");
        db.update("INSERT INTO ai_feedback(answer_id,helpful,reason,updated_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE helpful=VALUES(helpful),reason=VALUES(reason),updated_at=VALUES(updated_at)",answer,input.helpful()?1:0,AiPrivacy.mask(input.reason()),LocalDateTime.now());
        db.update("UPDATE ai_attempt_fact SET helpful=? WHERE request_id=?",input.helpful()?1:0,r.id());
    }
    @Transactional
    public void delete(AiActor actor,long id){owned(actor,id,true);erase(id);}
    @Transactional
    public void erase(long id){
        AiRow c=db.one("SELECT * FROM ai_conversation WHERE id=? FOR UPDATE",id);if(c.time("deleted_at")!=null)return;
        if(c.number("active_request_id")!=null){AiRow r=db.one("SELECT * FROM ai_request WHERE id=?",c.number("active_request_id"));finish(r.id(),"CANCELLED","",r.text("answer_mode"),"CONVERSATION_DELETED","对话已删除",List.of(),List.of(),null,null);}
        db.update("UPDATE ai_conversation SET deleted_at=?,epoch=epoch+1,active_request_id=NULL,context_json=NULL,title='已删除对话' WHERE id=?",LocalDateTime.now(),id);
        db.update("UPDATE ai_question_fact SET question_id=NULL,conversation_id=NULL,buyer_id=NULL WHERE conversation_id=?",id);
        db.update("UPDATE ai_attempt_fact SET request_id=NULL,conversation_id=NULL WHERE conversation_id=?",id);
        db.update("DELETE FROM ai_feedback WHERE answer_id IN (SELECT id FROM ai_message WHERE conversation_id=?)",id);
        db.update("DELETE FROM ai_answer_source WHERE answer_id IN (SELECT id FROM ai_message WHERE conversation_id=?)",id);
        db.update("UPDATE ai_rule_hit SET request_id=NULL WHERE request_id IN (SELECT id FROM ai_request WHERE conversation_id=?)",id);
        db.update("UPDATE ai_request SET retry_of=NULL WHERE conversation_id=?",id);db.update("DELETE FROM ai_request WHERE conversation_id=?",id);db.update("DELETE FROM ai_message WHERE conversation_id=?",id);
        db.update("DELETE FROM ai_conversation WHERE id=?",id);
    }
    public List<Message> messages(long conversation){
        return db.rows("SELECT m.*,r.id AS request_id,r.status,r.answer_mode,r.scene,r.error_code,r.error_message,r.cards_json,f.helpful,f.reason FROM ai_message m LEFT JOIN ai_request r ON r.answer_id=m.id LEFT JOIN ai_feedback f ON f.answer_id=m.id WHERE m.conversation_id=? ORDER BY m.sequence_no",conversation)
            .stream().map(r->new Message(r.id(),r.number("sequence_no"),r.text("role"),r.text("content"),r.number("request_id"),r.text("status"),r.text("answer_mode"),r.text("scene"),r.text("error_code"),r.text("error_message"),
                json.list(r.get("cards_json"),Card.class),sources(r.id()),r.number("helpful")==null?null:r.flag("helpful"),r.text("reason"),r.time("created_at"))).toList();
    }
    public List<Source> sources(long answer){return db.rows("SELECT * FROM ai_answer_source WHERE answer_id=? ORDER BY ordinal",answer).stream().map(r->new Source(r.integer("ordinal"),r.text("source_type"),r.number("source_id"),r.number("revision_no"),r.text("title"),r.text("excerpt"),r.text("target_path"))).toList();}
    public Context context(AiRow c){return c.get("context_json")==null?Context.empty():json.read(c.get("context_json"),Context.class);}
    public Request requestView(AiRow r){return new Request(r.id(),r.number("conversation_id"),r.number("question_id"),r.number("answer_id"),r.text("status"),r.text("scene"),r.text("answer_mode"),r.text("error_code"),r.text("error_message"),r.time("created_at"),r.time("finished_at"));}
    private Conversation conversation(AiRow r){return new Conversation(r.id(),r.text("title"),r.text("owner_kind"),context(r),r.time("created_at"),r.time("updated_at"),r.time("expires_at"));}
    private boolean valid(AiRow request,AiRow conversation){return ACTIVE.contains(request.text("status"))&&conversation.time("deleted_at")==null&&request.number("generation_epoch").equals(conversation.number("epoch"))&&request.number("id").equals(conversation.number("active_request_id"));}
    private void terminal(long id){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){events.publishEvent(new TerminalEvent(id));}});else events.publishEvent(new TerminalEvent(id));}
}
