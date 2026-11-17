package com.fairyshalltear.freshfruit.ai.knowledge;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.model.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiIndexWorker {
    private final AiMapper db;private final AiJson json;private final AiModelService models;private final AiModelFactory factory;
    private final TransactionTemplate tx;private final boolean enabled;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ai-knowledge-index");t.setDaemon(true);return t;});
    private final AtomicBoolean working=new AtomicBoolean();
    public AiIndexWorker(AiMapper db,AiJson json,AiModelService models,AiModelFactory factory,PlatformTransactionManager transactions,
        @Value("${freshfruit.ai.jobs-enabled:true}") boolean enabled){this.db=db;this.json=json;this.models=models;this.factory=factory;this.tx=new TransactionTemplate(transactions);this.enabled=enabled;}
    @Scheduled(fixedDelay=3000)
    public void poll(){if(enabled && working.compareAndSet(false,true))executor.execute(()->{try{runOne();}finally{working.set(false);}});}
    public boolean runOne(){
        LocalDateTime now=LocalDateTime.now();
        db.update("UPDATE ai_index_task SET status='PENDING',lease_until=NULL WHERE status='RUNNING' AND lease_until<?",now);
        Optional<AiRow> next=db.optional("SELECT * FROM ai_index_task WHERE status='PENDING' ORDER BY id LIMIT 1");
        if(next.isEmpty())return false;AiRow task=next.get();
        if(db.update("UPDATE ai_index_task SET status='RUNNING',attempts=attempts+1,lease_until=?,updated_at=? WHERE id=? AND status='PENDING'",now.plusMinutes(3),now,task.id())!=1)return true;
        try{
            AiRow revision=db.one("SELECT * FROM ai_knowledge_revision WHERE id=?",task.number("revision_id"));
            List<AiRow> chunks=tx.execute(status->{
                db.one("SELECT id FROM ai_knowledge_revision WHERE id=? FOR UPDATE",revision.id());
                List<AiRow> existing=db.rows("SELECT * FROM ai_knowledge_chunk WHERE revision_id=? ORDER BY ordinal",revision.id());
                if(!existing.isEmpty())return existing;
                int ordinal=0;for(String content:split(revision.text("body")))db.insert("ai_knowledge_chunk",values("revision_id",revision.id(),"ordinal",ordinal++,"content",content));
                return db.rows("SELECT * FROM ai_knowledge_chunk WHERE revision_id=? ORDER BY ordinal",revision.id());
            });
            Long profileId=task.number("profile_id");
            if(profileId!=null){
                AiRow profile=db.one("SELECT * FROM ai_embedding_profile WHERE id=?",profileId);
                if(!Set.of("BUILDING","READY","ACTIVE").contains(profile.text("status")))throw new IllegalStateException();
                var clients=factory.clients(models.settings(profile.number("config_id")));
                for(AiRow chunk:chunks){
                    if(!renew(task.id(),task.integer("attempts")+1,profileId))return true;
                    if(db.count("SELECT COUNT(*) FROM ai_chunk_embedding WHERE chunk_id=? AND profile_id=?",chunk.id(),profileId)>0)continue;
                    float[] vector=factory.embed(clients,revision.text("title")+"\n"+chunk.text("content"));
                    db.update("INSERT INTO ai_chunk_embedding(chunk_id,profile_id,vector_json) VALUES(?,?,?) ON DUPLICATE KEY UPDATE vector_json=VALUES(vector_json)",chunk.id(),profileId,json.write(vector));
                }
            }
            tx.executeWithoutResult(status->{
                AiRow document=db.one("SELECT * FROM ai_knowledge_document WHERE id=? FOR UPDATE",task.number("document_id"));
                AiRow currentTask=db.one("SELECT * FROM ai_index_task WHERE id=? FOR UPDATE",task.id());
                // A profile switch can requeue an in-flight publish under another model.
                if(!currentTask.text("status").equals("RUNNING") || currentTask.integer("attempts")!=task.integer("attempts")+1 || !Objects.equals(currentTask.number("profile_id"),profileId))return;
                boolean valid=document.flag("enabled") && document.number("version").equals(task.number("expected_version"))
                    && (task.text("kind").equals("PUBLISH")?Objects.equals(document.number("latest_revision_id"),revision.id()):Objects.equals(document.number("active_revision_id"),revision.id()));
                if(profileId!=null && db.count("SELECT COUNT(*) FROM ai_embedding_profile WHERE id=? AND status IN ('BUILDING','READY','ACTIVE')",profileId)!=1)valid=false;
                if(db.update("UPDATE ai_index_task SET status=?,lease_until=NULL,last_error=NULL,updated_at=? WHERE id=? AND status='RUNNING'",valid?"DONE":"CANCELLED",LocalDateTime.now(),task.id())!=1)return;
                if(valid && task.text("kind").equals("PUBLISH"))db.update("UPDATE ai_knowledge_document SET active_revision_id=?,index_status=?,index_error=NULL,updated_at=? WHERE id=?",revision.id(),profileId==null?"KEYWORD_ONLY":"READY",LocalDateTime.now(),document.id());
            });
        }catch(Exception error){
            tx.executeWithoutResult(status->{
                if(db.update("UPDATE ai_index_task SET status='FAILED',lease_until=NULL,last_error='索引失败，请检查向量配置并重试',updated_at=? WHERE id=? AND status='RUNNING' AND attempts=?",LocalDateTime.now(),task.id(),task.integer("attempts")+1)==1 && task.text("kind").equals("PUBLISH"))
                    db.update("UPDATE ai_knowledge_document SET index_status='FAILED',index_error='索引失败，原有效版本继续服务' WHERE id=? AND enabled=1 AND version=? AND latest_revision_id=?",task.number("document_id"),task.number("expected_version"),task.number("revision_id"));
            });
        }
        return true;
    }
    private boolean renew(long task,int attempt,Long profile){return db.update("UPDATE ai_index_task SET lease_until=? WHERE id=? AND status='RUNNING' AND attempts=? AND (profile_id=? OR (profile_id IS NULL AND ? IS NULL))",LocalDateTime.now().plusMinutes(3),task,attempt,profile,profile)==1;}
    public static List<String> split(String body){
        List<String> result=new ArrayList<>();
        for(String paragraph:body.split("\\n\\s*\\n")){
            String text=paragraph.strip();if(text.isEmpty())continue;
            int start=0;while(start<text.length()){
                int end=Math.min(text.length(),start+700);
                if(end<text.length()){
                    int sentence=Math.max(text.lastIndexOf('。',end-1),text.lastIndexOf('\n',end-1));
                    if(sentence>start+400)end=sentence+1;
                    if(Character.isHighSurrogate(text.charAt(end-1)))end--;
                }
                result.add(text.substring(start,end));if(end==text.length())break;start=end-80;if(Character.isLowSurrogate(text.charAt(start)))start++;
            }
        }
        return result;
    }
    @PreDestroy public void close(){executor.shutdownNow();}
}
