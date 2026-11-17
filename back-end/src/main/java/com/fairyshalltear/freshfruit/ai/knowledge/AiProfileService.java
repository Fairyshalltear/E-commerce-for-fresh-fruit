package com.fairyshalltear.freshfruit.ai.knowledge;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.model.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.audit.AuditService;
import static com.fairyshalltear.freshfruit.ai.AiErrors.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiProfileService {
    private final AiMapper db;private final AiKnowledgeService knowledge;private final AiModelService models;
    private final AuditService audit;private final boolean jobsEnabled;
    public AiProfileService(AiMapper db,AiKnowledgeService knowledge,AiModelService models,AuditService audit,@Value("${freshfruit.ai.jobs-enabled:true}") boolean jobsEnabled){this.db=db;this.knowledge=knowledge;this.models=models;this.audit=audit;this.jobsEnabled=jobsEnabled;}
    public List<Map<String,Object>> list(){return db.rows("SELECT * FROM ai_embedding_profile ORDER BY id DESC LIMIT 30").stream().map(r->values("id",r.id(),"configId",r.number("config_id"),"model",r.text("model"),"dimensions",r.integer("dimensions"),"status",r.text("status"),"createdAt",r.time("created_at"))).toList();}
    @Transactional
    public long build(long admin,long configId){
        db.one("SELECT id FROM admin_account WHERE account_type='OWNER' ORDER BY id LIMIT 1 FOR UPDATE");
        var s=models.settingsForBuild(configId);
        if(!s.enabled() || s.embeddingCipher()==null)throw invalid("请先激活包含向量服务的配置");
        if(db.count("SELECT COUNT(*) FROM ai_embedding_profile WHERE status IN ('BUILDING','READY')")>0)throw conflict("已有向量代次正在构建，请先完成或取消");
        long id=db.insert("ai_embedding_profile",values("config_id",configId,"model",s.embeddingModel(),"dimensions",s.dimensions()));
        catchUp(id);
        audit.record(admin,"AI_PROFILE_BUILD","AI_PROFILE",id);return id;
    }
    @Scheduled(fixedDelay=10000)
    public void maintain(){if(!jobsEnabled)return;for(AiRow profile:db.rows("SELECT id FROM ai_embedding_profile WHERE status IN ('BUILDING','READY')"))catchUp(profile.id());}
    @Transactional
    public void catchUp(long profileId){
        for(AiRow d:db.rows("SELECT * FROM ai_knowledge_document WHERE enabled=1 AND active_revision_id IS NOT NULL ORDER BY id")){
            long revision=d.number("active_revision_id");
            if(complete(revision,profileId))continue;
            if(db.count("SELECT COUNT(*) FROM ai_index_task WHERE revision_id=? AND profile_id=? AND expected_version=? AND status IN ('PENDING','RUNNING','FAILED')",revision,profileId,d.number("version"))==0)
                knowledge.queue(d.id(),revision,d.number("version"),profileId,"REBUILD");
        }
        if(db.count("SELECT COUNT(*) FROM ai_index_task t JOIN ai_knowledge_document d ON d.id=t.document_id WHERE t.profile_id=? AND t.status='FAILED' AND d.enabled=1 AND d.active_revision_id=t.revision_id AND d.version=t.expected_version",profileId)>0)db.update("UPDATE ai_embedding_profile SET status='FAILED' WHERE id=? AND status IN ('BUILDING','READY')",profileId);
        else if(allComplete(profileId))db.update("UPDATE ai_embedding_profile SET status='READY' WHERE id=? AND status='BUILDING'",profileId);
        else db.update("UPDATE ai_embedding_profile SET status='BUILDING' WHERE id=? AND status='READY'",profileId);
    }
    @Transactional
    public void switchTo(long admin,long id){
        db.one("SELECT id FROM admin_account WHERE account_type='OWNER' ORDER BY id LIMIT 1 FOR UPDATE");
        AiRow p=db.one("SELECT * FROM ai_embedding_profile WHERE id=? FOR UPDATE",id);
        if(!Set.of("READY","BUILDING").contains(p.text("status")))throw invalid("此代次不能切换");
        db.rows("SELECT id FROM ai_knowledge_document WHERE enabled=1 ORDER BY id FOR UPDATE");
        if(!allComplete(id))throw conflict("有效知识的新向量尚未全部就绪，请等待追赶索引完成");
        db.update("UPDATE ai_embedding_profile SET status='RETIRED' WHERE status='ACTIVE'");
        db.update("UPDATE ai_embedding_profile SET status='ACTIVE',activated_at=? WHERE id=?",LocalDateTime.now(),id);
        db.update("UPDATE ai_index_task SET profile_id=?,status='PENDING',lease_until=NULL WHERE kind='PUBLISH' AND status IN ('PENDING','RUNNING') AND (profile_id IS NULL OR profile_id<>?)",id,id);
        audit.record(admin,"AI_PROFILE_SWITCH","AI_PROFILE",id);
    }
    @Transactional
    public void cancel(long admin,long id){
        if(db.update("UPDATE ai_embedding_profile SET status='FAILED' WHERE id=? AND status IN ('BUILDING','READY')",id)!=1)throw conflict("此代次不能取消");
        db.update("UPDATE ai_index_task SET status='CANCELLED',lease_until=NULL WHERE profile_id=? AND status IN ('PENDING','RUNNING')",id);
        audit.record(admin,"AI_PROFILE_CANCEL","AI_PROFILE",id);
    }
    private boolean allComplete(long profile){return db.rows("SELECT active_revision_id FROM ai_knowledge_document WHERE enabled=1 AND active_revision_id IS NOT NULL").stream().allMatch(d->complete(d.number("active_revision_id"),profile));}
    private boolean complete(long revision,long profile){long chunks=db.count("SELECT COUNT(*) FROM ai_knowledge_chunk WHERE revision_id=?",revision);return chunks>0 && chunks==db.count("SELECT COUNT(*) FROM ai_knowledge_chunk k JOIN ai_chunk_embedding e ON e.chunk_id=k.id AND e.profile_id=? WHERE k.revision_id=?",profile,revision);}
}
