package com.fairyshalltear.freshfruit.ai.model;

import java.util.*;
import java.util.regex.*;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.audit.AuditService;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static com.fairyshalltear.freshfruit.ai.AiErrors.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiModelService {
    private final AiUsageService usage;
    private final AiMapper db; private final AiCredentials credentials; private final AiEndpointPolicy policy;
    private final AiModelFactory factory; private final AuditService audit;
    public AiModelService(AiMapper db,AiCredentials credentials,AiEndpointPolicy policy,AiModelFactory factory,AuditService audit,AiUsageService usage) {
        this.db=db;this.credentials=credentials;this.policy=policy;this.factory=factory;this.audit=audit;this.usage=usage;
    }
    public List<Map<String,Object>> list() { return db.rows("SELECT * FROM ai_model_config WHERE deleted_at IS NULL ORDER BY id DESC LIMIT 50").stream().map(this::view).toList(); }
    public Map<String,Object> detail(long id) { return view(row(id)); }
    // Historical requests and retired indices retain their immutable configuration snapshot.
    public AiModelSettings settings(long id) { return AiModelSettings.from(db.one("SELECT * FROM ai_model_config WHERE id=?",id)); }
    public AiModelSettings settingsForBuild(long id) { return AiModelSettings.from(lockedRow(id)); }
    public Optional<AiModelSettings> active() {
        // Hold the snapshot until request acceptance commits, so deletion cannot race its FK insert.
        String lock=TransactionSynchronizationManager.isActualTransactionActive()?" FOR UPDATE":"";
        return db.optional("SELECT * FROM ai_model_config WHERE is_active=1 AND deleted_at IS NULL ORDER BY id DESC LIMIT 1"+lock).map(AiModelSettings::from);
    }
    @Transactional
    public Map<String,Object> save(long admin,ModelInput input) {
        validateTemplate(input.promptTemplate());
        AiRow base=input.baseId()==null?null:lockedRow(input.baseId());
        String chatUrl=clean(input.chatBaseUrl()), embeddingUrl=clean(input.embeddingBaseUrl());
        if(!chatUrl.isBlank()) policy.validate(chatUrl);
        if(!embeddingUrl.isBlank()) policy.validate(embeddingUrl);
        String chatCipher=secret(input.chatKey(),input.clearChatKey(),base==null?null:base.text("chat_key_cipher"));
        String embeddingCipher=secret(input.embeddingKey(),input.clearEmbeddingKey(),base==null?null:base.text("embedding_key_cipher"));
        if(input.reuseChatKey()) {
            if(input.clearEmbeddingKey() || (input.embeddingKey()!=null && !input.embeddingKey().isBlank())) throw invalid("复用生成Key与设置/清除向量Key不能同时执行");
            embeddingCipher=chatCipher;
        }
        if(chatCipher!=null && chatUrl.isBlank()) throw invalid("设置生成Key时必须填写生成接口地址");
        if(embeddingCipher!=null && (embeddingUrl.isBlank() || clean(input.embeddingModel()).isBlank())) throw invalid("设置向量Key时必须填写向量接口和模型");
        Parameters p=input.parameters()==null?Parameters.defaults():input.parameters();
        long id=db.insert("ai_model_config",values("name",input.name().trim(),"chat_base_url",chatUrl,"chat_model",input.chatModel().trim(),
            "chat_key_cipher",chatCipher,"embedding_base_url",embeddingUrl,"embedding_model",clean(input.embeddingModel()),
            "embedding_key_cipher",embeddingCipher,"embedding_dimensions",input.embeddingDimensions(),"prompt_template",input.promptTemplate(),
            "temperature",p.temperature(),"max_output_tokens",p.maxOutputTokens(),"history_turns",p.historyTurns(),
            "connect_timeout_seconds",p.connectTimeoutSeconds(),"first_content_seconds",p.firstContentSeconds(),
            "idle_timeout_seconds",p.idleTimeoutSeconds(),"total_timeout_seconds",p.totalTimeoutSeconds(),
            "buyer_per_minute",p.buyerPerMinute(),"guest_per_minute",p.guestPerMinute(),"buyer_daily",p.buyerDaily(),
            "guest_daily",p.guestDaily(),"global_daily_calls",p.globalDailyCalls(),"created_by",admin));
        audit.record(admin,"AI_MODEL_DRAFT","AI_MODEL",id);return detail(id);
    }
    @Transactional
    public Map<String,Object> activate(long admin,long id) {
        // Serialize switches using the same database row, including concurrent first activation.
        db.one("SELECT id FROM admin_account WHERE account_type='OWNER' ORDER BY id LIMIT 1 FOR UPDATE");
        AiRow config=lockedRow(id);
        AiModelSettings s=AiModelSettings.from(config);
        if(s.chatCipher()==null) throw invalid("激活前请配置生成服务Key");
        credentials.decrypt(s.chatCipher());policy.validate(s.chatBaseUrl());
        if(s.embeddingCipher()!=null) { credentials.decrypt(s.embeddingCipher());policy.validate(s.embeddingBaseUrl()); }
        db.update("UPDATE ai_model_config SET is_active=0 WHERE is_active=1");
        db.update("UPDATE ai_model_config SET is_active=1,enabled=1 WHERE id=?",id);
        audit.record(admin,"AI_MODEL_ACTIVATE","AI_MODEL",id);return detail(id);
    }
    @Transactional
    public Map<String,Object> disable(long admin,long id) {
        db.one("SELECT id FROM admin_account WHERE account_type='OWNER' ORDER BY id LIMIT 1 FOR UPDATE");
        lockedRow(id);db.update("UPDATE ai_model_config SET is_active=0 WHERE id=?",id);
        // Immutable enabled snapshots remain usable by requests already holding this version.
        audit.record(admin,"AI_MODEL_DISABLE","AI_MODEL",id);return detail(id);
    }
    @Transactional
    public Map<String,Object> delete(long admin,long id) {
        // Activation and profile builds/switches use the same owner lock before the config lock.
        db.one("SELECT id FROM admin_account WHERE account_type='OWNER' ORDER BY id LIMIT 1 FOR UPDATE");
        AiRow config=lockedRow(id);
        Deletion deletion=deletion(config);
        if(deletion.mode().equals("BLOCKED")) throw conflict(deletion.reason());
        if(deletion.mode().equals("DELETE")) db.update("DELETE FROM ai_model_config WHERE id=?",id);
        else db.update("UPDATE ai_model_config SET deleted_at=? WHERE id=?",LocalDateTime.now(),id);
        audit.record(admin,deletion.mode().equals("DELETE")?"AI_MODEL_DELETE":"AI_MODEL_ARCHIVE","AI_MODEL",id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { factory.evict(id); }
        });
        return values("id",id,"mode",deletion.mode());
    }
    public List<Capability> test(long admin,long id,String capability) {
        if(!Set.of("chat","stream","intent","embedding","all").contains(capability)) throw invalid("未知测试能力");
        AiModelSettings stored=AiModelSettings.from(row(id));
        AiModelSettings s=new AiModelSettings(stored.id(),stored.chatBaseUrl(),stored.chatModel(),stored.chatCipher(),stored.embeddingBaseUrl(),
            stored.embeddingModel(),stored.embeddingCipher(),stored.dimensions(),stored.promptTemplate(),true,stored.parameters());
        List<Capability> result=new ArrayList<>();
        for(String part:List.of("chat","stream","intent","embedding")) {
            if(!capability.equals("all") && !capability.equals(part)) continue;
            long start=System.nanoTime();boolean success=false;Integer dimensions=null;String message;
            try {
                if(!part.equals("embedding") && !usage.reserve("GLOBAL:CHAT",s.parameters().globalDailyCalls())) throw unavailable("AI_CALL_LIMIT","全站今日调用额度已用完");
                AiModelFactory.Clients clients=factory.clients(s);
                if(part.equals("embedding")) { dimensions=factory.embed(clients,"草莓保鲜方法").length; }
                else if(part.equals("stream")) {
                    String text=factory.stream(clients,"用中文简短回答","请回答：连接成功")
                        .map(response->response.getResult()==null?"":Objects.toString(response.getResult().getOutput().getText(),""))
                        .reduce("",String::concat).block(java.time.Duration.ofSeconds(s.parameters().totalTimeoutSeconds()));
                    if(text==null || text.isBlank())throw new IllegalStateException();
                } else if(part.equals("intent")) {
                    Intent intent=clients.chat().prompt().system("只分类为GENERAL、PRODUCT、RECOMMEND、ORDER、AFTER_SALE，不输出私人信息")
                        .user("有什么适合送礼的水果？").call().entity(Intent.class);
                    if(intent==null || !Set.of("GENERAL","PRODUCT","RECOMMEND","ORDER","AFTER_SALE").contains(intent.scene())) throw new IllegalStateException();
                } else {
                    String text=clients.chat().prompt().system("用中文简短回答").user("请回答：连接成功").call().content();
                    if(text==null || text.isBlank()) throw new IllegalStateException();
                }
                success=true;message="能力测试通过";
            } catch(Exception error) { message="测试失败，请核对地址、凭据、模型名称、地区配额及能力协议"; }
            result.add(new Capability(part,success,(System.nanoTime()-start)/1_000_000,message,dimensions));
        }
        audit.recordRequestOutcome(admin,"AI_MODEL_TEST","AI_MODEL",id,result.stream().allMatch(Capability::success)?"SUCCESS":"FAILED","CAPABILITY_TEST");
        return result;
    }
    public record Intent(String scene) {}
    private String secret(String value,boolean clear,String previous) {
        if(clear && value!=null && !value.isBlank()) throw invalid("设置和清除Key不能同时执行");
        return clear?null:value==null || value.isBlank()?previous:credentials.encrypt(value);
    }
    public static void validateTemplate(String template) {
        Set<String> variables=Set.of("question","history","knowledge","products","policy");
        Matcher matcher=Pattern.compile("\\{([^{}]+)}").matcher(template);
        while(matcher.find()) if(!variables.contains(matcher.group(1))) throw invalid("提示词含未知变量："+matcher.group(1));
    }
    private AiRow row(long id) { return db.one("SELECT * FROM ai_model_config WHERE id=? AND deleted_at IS NULL",id); }
    private AiRow lockedRow(long id) { return db.one("SELECT * FROM ai_model_config WHERE id=? AND deleted_at IS NULL FOR UPDATE",id); }
    private record Deletion(String mode,String reason) {}
    private Deletion deletion(AiRow config) {
        long id=config.id();
        if(config.flag("is_active")) return new Deletion("BLOCKED","当前配置已激活，请先关闭在线模型或激活其他版本，再删除");
        if(db.count("SELECT COUNT(*) FROM ai_request WHERE config_id=? AND status IN ('ACCEPTED','RUNNING')",id)>0)
            return new Deletion("BLOCKED","此配置仍有回答处理中，请等待回答结束或停止后再删除");
        if(db.count("SELECT COUNT(*) FROM ai_embedding_profile WHERE config_id=? AND status IN ('BUILDING','READY','ACTIVE')",id)>0
            || db.count("SELECT COUNT(*) FROM ai_index_task t JOIN ai_embedding_profile p ON p.id=t.profile_id WHERE p.config_id=? AND t.status IN ('PENDING','RUNNING')",id)>0)
            return new Deletion("BLOCKED","此配置仍被向量索引使用，请先取消构建或切换到其他代次，再删除");
        boolean referenced=db.count("SELECT COUNT(*) FROM ai_request WHERE config_id=?",id)>0
            || db.count("SELECT COUNT(*) FROM ai_embedding_profile WHERE config_id=?",id)>0;
        return referenced?new Deletion("ARCHIVE","此配置已被历史回答或旧索引引用，删除后从管理列表隐藏，保留历史追溯数据")
            :new Deletion("DELETE","此配置未被回答或索引引用，删除后将永久移除");
    }
    private Map<String,Object> view(AiRow r) {
        AiModelSettings s=AiModelSettings.from(r);
        Deletion deletion=deletion(r);
        return values("id",r.id(),"name",r.text("name"),"chatBaseUrl",s.chatBaseUrl(),"chatModel",s.chatModel(),
            "hasChatKey",s.chatCipher()!=null,"maskedChatKey",s.chatCipher()==null?null:"••••••••",
            "embeddingBaseUrl",s.embeddingBaseUrl(),"embeddingModel",s.embeddingModel(),"embeddingDimensions",s.dimensions(),
            "hasEmbeddingKey",s.embeddingCipher()!=null,"maskedEmbeddingKey",s.embeddingCipher()==null?null:"••••••••",
            "promptTemplate",s.promptTemplate(),"parameters",s.parameters(),"active",r.flag("is_active"),
            "encryptionReady",credentials.available(),"createdAt",r.time("created_at"),
            "deletionMode",deletion.mode(),"deletionReason",deletion.reason());
    }
    private String clean(String s) {return s==null?"":s.trim();}
}
