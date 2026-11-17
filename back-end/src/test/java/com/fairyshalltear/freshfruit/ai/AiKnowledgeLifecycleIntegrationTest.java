package com.fairyshalltear.freshfruit.ai;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.fairyshalltear.freshfruit.ai.knowledge.*;
import com.fairyshalltear.freshfruit.ai.model.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:ai-knowledge-lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql",
 "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
 "security.admin.initial-username=testadmin","security.admin.initial-password=TestAdminPassword123!",
 "freshfruit.ai.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 "freshfruit.ai.jobs-enabled=false","freshfruit.ai.allow-local-endpoints=true"
})
class AiKnowledgeLifecycleIntegrationTest {
    static final HttpServer provider;
    static final AtomicBoolean fail=new AtomicBoolean();
    static final ObjectMapper codec=new ObjectMapper();
    static {
        try {
            provider=HttpServer.create(new InetSocketAddress("localhost",0),0);
            provider.createContext("/v1/embeddings",exchange->{
                var input=codec.readTree(exchange.getRequestBody());
                int dimensions=input.path("dimensions").asInt(64);
                List<Double> vector=new ArrayList<>();
                for(int i=0;i<dimensions;i++)vector.add(i==0?1d:0d);
                byte[] body=codec.writeValueAsBytes(fail.get()?Map.of("error","mock failure"):
                    Map.of("object","list","model","mock-vector","data",List.of(Map.of("index",0,"object","embedding","embedding",vector))));
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(fail.get()?500:200,body.length);
                try(var out=exchange.getResponseBody()){out.write(body);}finally{exchange.close();}
            });
            provider.start();
        } catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    @Autowired AiMapper db;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired AiKnowledgeService knowledge;
    @Autowired AiKnowledgeRetriever retriever;
    @Autowired AiIndexWorker index;
    @Autowired AiProfileService profiles;
    @Autowired AiModelService models;
    @Autowired AiCatalogService catalog;
    @Autowired AiCatalogSynchronizer synchronizer;
    long admin;String label;
    @BeforeEach void setup(){
        fail.set(false);label=UUID.randomUUID().toString().substring(0,8);
        admin=db.one("SELECT id FROM admin_account ORDER BY id LIMIT 1").id();
        db.update("UPDATE ai_knowledge_document SET enabled=0");
        db.update("UPDATE ai_index_task SET status='CANCELLED'");
        db.update("UPDATE ai_embedding_profile SET status='RETIRED'");
        db.update("UPDATE ai_model_config SET is_active=0");
        db.update("UPDATE fruit_product SET status=0");
    }
    @AfterAll static void close(){provider.stop(0);}
    @Test void incompatibleProfilesCatchUpNewRevisionsAndRetargetPendingFirstPublication(){
        Knowledge doc=published("旧答案");
        long config=model(64),old=profiles.build(admin,config);
        drain();profiles.catchUp(old);profiles.switchTo(admin,old);
        assertEquals("HYBRID",retriever.search(question(),true).mode());
        long nextConfig=model(128),next=profiles.build(admin,nextConfig);
        doc=knowledge.save(admin,doc.id(),input("新答案",doc.version()));
        knowledge.publish(admin,doc.id(),doc.version());
        assertThrows(RuntimeException.class,()->profiles.switchTo(admin,next));
        drain();profiles.catchUp(next);drain();profiles.catchUp(next);
        Knowledge pending=knowledge.save(admin,null,new KnowledgeInput("新资料"+label,"FRUIT","待发布水果资料",null,List.of(),"测试",null,null));
        knowledge.publish(admin,pending.id(),pending.version());
        profiles.switchTo(admin,next);drain();
        long revision=knowledge.detail(pending.id()).activeRevision();
        assertTrue(db.count("SELECT COUNT(*) FROM ai_chunk_embedding e JOIN ai_knowledge_chunk k ON k.id=e.chunk_id WHERE k.revision_id=? AND e.profile_id=?",revision,next)>0);
        assertEquals(128,db.one("SELECT dimensions FROM ai_embedding_profile WHERE status='ACTIVE'").integer("dimensions"));
        assertTrue(retriever.search(question(),true).hits().stream().anyMatch(h->h.document().getText().contains("新答案")));
        assertEquals(0,AiKnowledgeRetriever.cosine(new float[64],new float[128]));
    }
    @Test void failedPublicationPreservesOldRevisionAndExpiredLeaseCanRetry(){
        Knowledge doc=published("旧标准答案");long profile=profiles.build(admin,model(64));drain();profiles.catchUp(profile);profiles.switchTo(admin,profile);
        long oldRevision=knowledge.detail(doc.id()).activeRevision();
        doc=knowledge.save(admin,doc.id(),input("新标准答案",knowledge.detail(doc.id()).version()));
        doc=knowledge.publish(admin,doc.id(),doc.version());fail.set(true);drain();
        assertEquals("FAILED",knowledge.detail(doc.id()).indexStatus());assertEquals(oldRevision,knowledge.detail(doc.id()).activeRevision());
        assertTrue(retriever.search(question(),false).hits().stream().anyMatch(h->h.document().getText().contains("旧标准答案")));
        fail.set(false);knowledge.retry(admin,doc.id(),doc.version());
        db.update("UPDATE ai_index_task SET status='RUNNING',lease_until=? WHERE document_id=? AND status='PENDING'",LocalDateTime.now().minusMinutes(1),doc.id());
        drain();assertNotEquals(oldRevision,knowledge.detail(doc.id()).activeRevision());
        doc=knowledge.save(admin,doc.id(),input("已停用答案",doc.version()));doc=knowledge.publish(admin,doc.id(),doc.version());
        knowledge.enabled(admin,doc.id(),new EnabledInput(false,doc.version()));drain();
        long documentId=doc.id();
        assertTrue(retriever.search(question(),false).hits().stream().noneMatch(h->h.source().id().equals(documentId)));
    }
    @Test void catalogVersionInvalidatesOldStorageAndDisabledCategoryRemovesCards(){
        long product=product("验收水果"+label,50,8,true,List.of(11),List.of("ELDER_GIFT"));synchronizer.sync(product);drain();
        assertTrue(retriever.search("验收水果"+label,false).hits().stream().anyMatch(h->h.document().getText().contains("旧储存资料")));
        db.update("UPDATE fruit_product SET storage_method='新储存资料',version=version+1 WHERE id=?",product);synchronizer.sync(product);
        var hits=retriever.search("验收水果"+label,false).hits();
        assertTrue(hits.stream().noneMatch(h->h.document().getText().contains("旧储存资料")));assertTrue(hits.stream().anyMatch(h->h.document().getText().contains("新储存资料")));
        db.update("UPDATE product_category SET status=0 WHERE id=(SELECT category_id FROM fruit_product WHERE id=?)",product);
        assertFalse(catalog.visible(product));assertTrue(retriever.search("验收水果"+label,false).hits().isEmpty());
        assertTrue(catalog.recommend(new Context(null,null,null,new java.math.BigDecimal("100"),11,true,"ELDER_GIFT",null,1)).isEmpty());
    }
    @Test void recommendationsRespectTotalBudgetQuantityUnknownFieldsAndStock(){
        long eligible=product("礼盒"+label,50,2,true,List.of(11),List.of("ELDER_GIFT"));
        product("未知月份"+label,10,10,true,List.of(),List.of("ELDER_GIFT"));
        product("售罄"+label,10,0,true,List.of(11),List.of("ELDER_GIFT"));
        Context requirement=new Context(null,null,null,new java.math.BigDecimal("100"),11,true,"ELDER_GIFT",null,2);
        assertEquals(List.of(eligible),catalog.recommend(requirement).stream().map(Card::id).toList());
        assertTrue(catalog.recommend(new Context(null,null,null,new java.math.BigDecimal("99.99"),11,true,"ELDER_GIFT",null,2)).isEmpty());
        assertTrue(catalog.recommend(new Context(null,null,null,new java.math.BigDecimal("200"),11,true,"ELDER_GIFT",null,3)).isEmpty());
    }
    private String question(){return "独有问法"+label;}
    private KnowledgeInput input(String body,Long version){return new KnowledgeInput("验收FAQ"+label,"FAQ",body,question(),List.of(),"测试依据",null,version);}
    private Knowledge published(String body){var d=knowledge.save(admin,null,input(body,null));d=knowledge.publish(admin,d.id(),d.version());drain();return knowledge.detail(d.id());}
    private long model(int dimensions){String base="http://localhost:"+provider.getAddress().getPort()+"/v1";long id=((Number)models.save(admin,new ModelInput(null,"mock"+label,base,"mock","sk-local-test",false,base,"mock-vector","sk-local-vector",false,false,dimensions,"{question}",null)).get("id")).longValue();models.activate(admin,id);return id;}
    private void drain(){int n=0;while(index.runOne())assertTrue(++n<100,"任务不能无限循环");}
    private long fixtureInsert(String table,Map<String,Object> fields){
        var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();
        String sql="INSERT INTO "+table+"("+String.join(",",fields.keySet())+") VALUES("+String.join(",",Collections.nCopies(fields.size(),"?"))+")";
        jdbc.update(connection->{var statement=connection.prepareStatement(sql,new String[]{"id"});int index=1;for(Object value:fields.values())statement.setObject(index++,value);return statement;},holder);
        return holder.getKey().longValue();
    }
    private long product(String name,int price,int stock,boolean gift,List<Integer> months,List<String> uses){
        long root=fixtureInsert("product_category",values("name","根"+UUID.randomUUID(),"status",1));
        long leaf=fixtureInsert("product_category",values("name","叶"+UUID.randomUUID(),"parent_id",root,"status",1));
        long p=fixtureInsert("fruit_product",values("category_id",leaf,"name",name,"status",1,"storage_method","旧储存资料","is_gift_box",gift?1:0,"seasonal_months",months.toString(),"use_cases",codec.valueToTree(uses).toString()));
        long sku=fixtureInsert("product_sku",values("product_id",p,"specification","一份","price",price,"status",1));
        jdbc.update("INSERT INTO sku_inventory(sku_id,on_hand,reserved) VALUES(?,?,0)",sku,stock);return p;
    }
}
