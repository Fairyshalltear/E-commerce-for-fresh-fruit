package com.fairyshalltear.freshfruit.ai.knowledge;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiCatalogSynchronizer {
    private final AiMapper db;private final AiCatalogService catalog;private final AiKnowledgeService knowledge;
    public AiCatalogSynchronizer(AiMapper db,AiCatalogService catalog,AiKnowledgeService knowledge){this.db=db;this.catalog=catalog;this.knowledge=knowledge;}
    @Transactional
    public void sync(long productId){
        AiRow product=db.one("SELECT * FROM fruit_product WHERE id=?",productId);
        Optional<AiRow> existing=db.optional("SELECT * FROM ai_knowledge_document WHERE catalog_key=? FOR UPDATE","PRODUCT:"+productId);
        boolean visible=catalog.visible(productId);
        if(existing.isEmpty() && !visible)return;
        if(existing.isPresent()){
            AiRow d=existing.get();
            if(!visible){
                if(d.flag("enabled"))db.update("UPDATE ai_knowledge_document SET enabled=0,version=version+1,updated_at=? WHERE id=?",LocalDateTime.now(),d.id());
                db.update("UPDATE ai_index_task SET status='CANCELLED',lease_until=NULL WHERE document_id=? AND status IN ('PENDING','RUNNING')",d.id());return;
            }
            if(d.flag("enabled") && d.number("latest_revision_id")!=null && db.count("SELECT COUNT(*) FROM ai_knowledge_revision WHERE id=? AND product_version=?",d.number("latest_revision_id"),product.number("version"))==1)return;
        }
        String body=catalog.baseFacts(product);long documentId;
        if(existing.isEmpty())documentId=db.insert("ai_knowledge_document",values("type","PRODUCT","origin_kind","CATALOG","catalog_key","PRODUCT:"+productId,"product_id",productId,
            "title",product.text("name"),"draft_body",body,"source_label","当前商品资料","enabled",1));
        else documentId=existing.get().id();
        long version=existing.map(d->d.number("version")).orElse(0L)+1;
        long revision=db.insert("ai_knowledge_revision",values("document_id",documentId,"revision_no",db.count("SELECT COUNT(*) FROM ai_knowledge_revision WHERE document_id=?",documentId)+1,
            "title",product.text("name"),"body",body,"source_label","当前商品资料","product_version",product.number("version")));
        db.update("UPDATE ai_knowledge_document SET title=?,draft_body=?,enabled=1,version=?,latest_revision_id=?,index_status='PENDING',index_error=NULL,updated_at=? WHERE id=?",
            product.text("name"),body,version,revision,LocalDateTime.now(),documentId);
        knowledge.queue(documentId,revision,version,knowledge.activeProfile(),"PUBLISH");
    }
    @Transactional
    public void syncAll(){for(AiRow p:db.rows("SELECT id FROM fruit_product ORDER BY id"))sync(p.id());}
}
