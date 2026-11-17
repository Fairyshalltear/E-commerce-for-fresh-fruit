package com.fairyshalltear.freshfruit.ai.knowledge;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.audit.AuditService;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static com.fairyshalltear.freshfruit.ai.AiErrors.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiKnowledgeService {
    private final AiMapper db;private final AiJson json;private final AuditService audit;
    public AiKnowledgeService(AiMapper db,AiJson json,AuditService audit){this.db=db;this.json=json;this.audit=audit;}
    public Page<Knowledge> list(int page,int size,String type,String keyword){
        List<Object> args=new ArrayList<>();String where=" WHERE 1=1";
        if(type!=null&&!type.isBlank()){where+=" AND type=?";args.add(type);}
        if(keyword!=null&&!keyword.isBlank()){where+=" AND title LIKE ? ESCAPE '!'";args.add("%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        long total=db.count("SELECT COUNT(*) FROM ai_knowledge_document"+where,args.toArray());
        args.add(size);args.add((long)(page-1)*size);
        return new Page<>(db.rows("SELECT * FROM ai_knowledge_document"+where+" ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",args.toArray()).stream().map(r->view(r,false)).toList(),total,page,size);
    }
    public Knowledge detail(long id){return view(row(id,false),true);}
    @Transactional
    public Knowledge save(long admin,Long id,KnowledgeInput input){
        if(input.type().equals("FAQ") && (input.standardQuestion()==null||input.standardQuestion().isBlank()))throw invalid("FAQ需要标准问法");
        if(input.type().equals("PRODUCT") && input.productId()==null)throw invalid("商品知识需要关联商品");
        if(input.productId()!=null)db.one("SELECT id FROM fruit_product WHERE id=? AND deleted=0",input.productId());
        if(id==null)id=db.insert("ai_knowledge_document",values("type",input.type(),"product_id",input.productId(),"title",input.title().strip(),
            "draft_body",input.body(),"draft_question",input.standardQuestion(),"draft_aliases",json.write(input.aliases()==null?List.of():input.aliases()),
            "source_label",input.sourceLabel()==null?"管理员知识资料":input.sourceLabel(),"created_by",admin));
        else{
            AiRow existing=row(id,true);
            if(!existing.text("origin_kind").equals("MANUAL"))throw invalid("自动商品资料请在商品编辑页维护");
            if(input.version()==null || input.version()!=existing.number("version").longValue())throw conflict("知识已更新，请刷新后重试");
            if(!existing.text("type").equals(input.type()) || !Objects.equals(existing.number("product_id"),input.productId()))throw invalid("已有文档不能更改类型或关联商品，请新建文档");
            db.update("UPDATE ai_knowledge_document SET title=?,draft_body=?,draft_question=?,draft_aliases=?,source_label=?,version=version+1,index_status=CASE WHEN index_status='PENDING' THEN 'DRAFT' ELSE index_status END,updated_at=? WHERE id=?",
                input.title().strip(),input.body(),input.standardQuestion(),json.write(input.aliases()==null?List.of():input.aliases()),input.sourceLabel()==null?"管理员知识资料":input.sourceLabel(),LocalDateTime.now(),id);
        }
        db.update("UPDATE ai_index_task SET status='CANCELLED',lease_until=NULL WHERE document_id=? AND status IN ('PENDING','RUNNING')",id);
        audit.record(admin,"AI_KNOWLEDGE_DRAFT","AI_KNOWLEDGE",id);return detail(id);
    }
    @Transactional
    public Knowledge publish(long admin,long id,long version){
        AiRow r=row(id,true);check(r,version);
        if(!r.text("origin_kind").equals("MANUAL"))throw invalid("商品资料自动同步，无需手动发布");
        long revisionNo=db.count("SELECT COUNT(*) FROM ai_knowledge_revision WHERE document_id=?",id)+1;
        Long productVersion=r.number("product_id")==null?null:db.one("SELECT version FROM fruit_product WHERE id=? AND deleted=0",r.number("product_id")).number("version");
        long revision=db.insert("ai_knowledge_revision",values("document_id",id,"revision_no",revisionNo,"title",r.text("title"),"body",r.text("draft_body"),
            "standard_question",r.text("draft_question"),"aliases",r.text("draft_aliases"),"source_label",r.text("source_label"),"product_version",productVersion,"created_by",admin));
        db.update("UPDATE ai_knowledge_document SET enabled=1,latest_revision_id=?,version=version+1,index_status='PENDING',index_error=NULL,updated_at=? WHERE id=?",revision,LocalDateTime.now(),id);
        queue(id,revision,version+1,activeProfile(),"PUBLISH");
        audit.record(admin,"AI_KNOWLEDGE_PUBLISH","AI_KNOWLEDGE",id);return detail(id);
    }
    @Transactional
    public Knowledge enabled(long admin,long id,EnabledInput input){
        AiRow r=row(id,true);check(r,input.version());
        if(input.enabled() && r.number("active_revision_id")==null)throw invalid("请先发布知识");
        db.update("UPDATE ai_knowledge_document SET enabled=?,version=version+1,updated_at=? WHERE id=?",input.enabled()?1:0,LocalDateTime.now(),id);
        db.update("UPDATE ai_index_task SET status='CANCELLED',lease_until=NULL WHERE document_id=? AND status IN ('PENDING','RUNNING')",id);
        audit.record(admin,"AI_KNOWLEDGE_ENABLE","AI_KNOWLEDGE",id);return detail(id);
    }
    @Transactional
    public Knowledge retry(long admin,long id,long version){
        AiRow r=row(id,true);check(r,version);
        if(!r.flag("enabled")||r.number("latest_revision_id")==null)throw invalid("停用或未发布的知识不能重试索引");
        if(db.count("SELECT COUNT(*) FROM ai_index_task WHERE document_id=? AND status IN ('PENDING','RUNNING')",id)>0)throw conflict("已有索引任务");
        db.update("UPDATE ai_knowledge_document SET index_status='PENDING',index_error=NULL WHERE id=?",id);
        queue(id,r.number("latest_revision_id"),version,activeProfile(),"PUBLISH");
        audit.record(admin,"AI_KNOWLEDGE_RETRY","AI_KNOWLEDGE",id);return detail(id);
    }
    public void queue(long document,long revision,long version,Long profile,String kind){db.insert("ai_index_task",values("document_id",document,"revision_id",revision,"expected_version",version,"profile_id",profile,"kind",kind));}
    public Long activeProfile(){if(db.count("SELECT COUNT(*) FROM ai_model_config WHERE is_active=1 AND embedding_key_cipher IS NOT NULL")==0)return null;return db.optional("SELECT id FROM ai_embedding_profile WHERE status='ACTIVE' ORDER BY id DESC LIMIT 1").map(AiRow::id).orElse(null);}
    private AiRow row(long id,boolean lock){return db.one("SELECT * FROM ai_knowledge_document WHERE id=?"+(lock?" FOR UPDATE":""),id);}
    private void check(AiRow r,long version){if(r.number("version")!=version)throw conflict("知识版本已变化，请刷新后重试");}
    private Knowledge view(AiRow r,boolean revisions){
        List<Map<String,Object>> list=revisions?db.rows("SELECT id,revision_no,title,source_label,product_version,created_at FROM ai_knowledge_revision WHERE document_id=? ORDER BY revision_no DESC",r.id()).stream()
            .map(v->values("id",v.id(),"version",v.number("revision_no"),"title",v.text("title"),"sourceLabel",v.text("source_label"),"productVersion",v.number("product_version"),"createdAt",v.time("created_at"))).toList():List.of();
        return new Knowledge(r.id(),r.text("type"),r.text("origin_kind"),r.number("product_id"),r.text("title"),r.text("draft_body"),r.text("draft_question"),json.strings(r.get("draft_aliases")),
            r.text("source_label"),r.flag("enabled"),r.number("version"),r.number("active_revision_id"),r.number("latest_revision_id"),r.text("index_status"),r.text("index_error"),r.time("updated_at"),list);
    }
}
