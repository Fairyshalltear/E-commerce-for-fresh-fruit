package com.fairyshalltear.freshfruit.ai.admin;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.chat.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.ai.safety.AiPrivacy;
import com.fairyshalltear.freshfruit.audit.AuditService;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiRecordService {
    private final AiMapper db;private final AiConversationService conversations;private final AuditService audit;
    public AiRecordService(AiMapper db,AiConversationService conversations,AuditService audit){this.db=db;this.conversations=conversations;this.audit=audit;}
    public Page<Map<String,Object>> list(int page,int size,String ownerKind,String scene,String status,LocalDate from,LocalDate to,Long buyerId,String keyword,Boolean helpful){
        if(from!=null && to!=null && to.isBefore(from))throw AiErrors.invalid("结束日期不能早于开始日期");
        List<Object> args=new ArrayList<>();String where=" WHERE c.deleted_at IS NULL AND c.expires_at>?";args.add(LocalDateTime.now());
        if(ownerKind!=null&&!ownerKind.isBlank()){where+=" AND c.owner_kind=?";args.add(ownerKind);}
        if(scene!=null&&!scene.isBlank()){where+=" AND EXISTS(SELECT 1 FROM ai_request r WHERE r.conversation_id=c.id AND r.scene=?)";args.add(scene);}
        if(status!=null&&!status.isBlank()){where+=" AND EXISTS(SELECT 1 FROM ai_request r WHERE r.conversation_id=c.id AND r.status=?)";args.add(status);}
        if(buyerId!=null){where+=" AND c.buyer_id=?";args.add(buyerId);}
        if(helpful!=null){where+=" AND EXISTS(SELECT 1 FROM ai_request r JOIN ai_feedback f ON f.answer_id=r.answer_id WHERE r.conversation_id=c.id AND f.helpful=?)";args.add(helpful?1:0);}
        if(keyword!=null&&!keyword.isBlank()){
            String safe=AiPrivacy.external(keyword.strip()).replace("!","!!").replace("%","!%").replace("_","!_");
            where+=" AND EXISTS(SELECT 1 FROM ai_message m WHERE m.conversation_id=c.id AND m.content LIKE ? ESCAPE '!')";args.add("%"+safe+"%");
        }
        if(from!=null){where+=" AND c.updated_at>=?";args.add(from.atStartOfDay());}if(to!=null){where+=" AND c.updated_at<?";args.add(to.plusDays(1).atStartOfDay());}
        long total=db.count("SELECT COUNT(*) FROM ai_conversation c"+where,args.toArray());args.add(size);args.add((long)(page-1)*size);
        var rows=db.rows("SELECT c.id,c.title,c.owner_kind,c.buyer_id,c.created_at,c.updated_at,(SELECT COUNT(*) FROM ai_message m WHERE m.conversation_id=c.id AND m.role='USER') AS question_count,(SELECT r.status FROM ai_request r WHERE r.conversation_id=c.id ORDER BY r.id DESC LIMIT 1) AS last_status FROM ai_conversation c"+where+" ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?",args.toArray());
        return new Page<>(rows.stream().map(r->values("id",r.id(),"title",AiPrivacy.external(r.text("title")),"ownerKind",r.text("owner_kind"),"buyerId",r.number("buyer_id"),"questionCount",r.number("question_count"),"status",r.text("last_status"),"createdAt",r.time("created_at"),"updatedAt",r.time("updated_at"))).toList(),total,page,size);
    }
    public Map<String,Object> detail(long admin,long id){
        AiRow conversation=db.one("SELECT id,title,owner_kind,created_at,updated_at FROM ai_conversation WHERE id=? AND deleted_at IS NULL AND expires_at>?",id,LocalDateTime.now());
        audit.recordRequestOutcome(admin,"AI_CONVERSATION_VIEW","AI_CONVERSATION",id,"SUCCESS",null);
        List<Map<String,Object>> messages=conversations.messages(id).stream().map(m->values("id",m.id(),"sequence",m.sequence(),"role",m.role(),"content",AiPrivacy.external(m.content()),"requestId",m.requestId(),"status",m.status(),"mode",m.mode(),"scene",m.scene(),"errorCode",m.errorCode(),"errorMessage",AiPrivacy.external(m.errorMessage()),"helpful",m.helpful(),"feedbackReason",AiPrivacy.external(m.feedbackReason()),"createdAt",m.createdAt(),
            "sources",m.sources().stream().map(s->values("type",s.type(),"title",AiPrivacy.external(s.title()),"version",s.version(),"excerpt",AiPrivacy.external(s.excerpt()))).toList())).toList();
        var attempts=db.rows("SELECT r.id,r.question_id,r.answer_id,r.retry_of,r.status,r.scene,r.answer_mode,r.retrieval_mode,r.config_id,r.prompt_tokens,r.completion_tokens,r.error_code,r.created_at,r.finished_at,c.chat_model,f.total_ms,f.first_content_ms FROM ai_request r LEFT JOIN ai_model_config c ON c.id=r.config_id LEFT JOIN ai_attempt_fact f ON f.request_id=r.id WHERE r.conversation_id=? ORDER BY r.id",id).stream()
            .map(r->values("id",r.id(),"questionId",r.number("question_id"),"answerId",r.number("answer_id"),"retryOf",r.number("retry_of"),"status",r.text("status"),"scene",r.text("scene"),"mode",r.text("answer_mode"),"retrievalMode",r.text("retrieval_mode"),"configId",r.number("config_id"),"model",r.text("chat_model"),"promptTokens",r.number("prompt_tokens"),"completionTokens",r.number("completion_tokens"),"totalMs",r.number("total_ms"),"firstContentMs",r.number("first_content_ms"),"errorCode",r.text("error_code"),"createdAt",r.time("created_at"),"finishedAt",r.time("finished_at"))).toList();
        var hits=db.rows("SELECT h.rule_id,h.rule_version,h.stage,h.summary,h.created_at FROM ai_rule_hit h JOIN ai_request r ON r.id=h.request_id WHERE r.conversation_id=? ORDER BY h.id",id).stream()
            .map(r->values("ruleId",r.number("rule_id"),"version",r.number("rule_version"),"stage",r.text("stage"),"summary",r.text("summary"),"createdAt",r.time("created_at"))).toList();
        return values("id",id,"title",conversation.text("title"),"ownerKind",conversation.text("owner_kind"),"createdAt",conversation.time("created_at"),"updatedAt",conversation.time("updated_at"),"messages",messages,"attempts",attempts,"ruleHits",hits);
    }
}
