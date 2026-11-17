package com.fairyshalltear.freshfruit.ai.knowledge;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Service;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.model.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;

@Service
public class AiKnowledgeRetriever implements DocumentRetriever {
    public record Hit(Document document,Source source,double keywordScore,double vectorScore,boolean exactFaq){}
    public record Result(List<Hit> hits,String mode){public List<Document> documents(){return hits.stream().map(Hit::document).toList();}}
    private final AiMapper db;private final AiJson json;private final AiCatalogService catalog;
    private final AiModelService models;private final AiModelFactory factory;
    public AiKnowledgeRetriever(AiMapper db,AiJson json,AiCatalogService catalog,AiModelService models,AiModelFactory factory){this.db=db;this.json=json;this.catalog=catalog;this.models=models;this.factory=factory;}
    @Override public List<Document> retrieve(Query query){return search(query.text(),true).documents();}
    public Result search(String question,boolean useVector){
        AiRow profile=useVector && db.count("SELECT COUNT(*) FROM ai_model_config WHERE is_active=1 AND embedding_key_cipher IS NOT NULL")>0?db.optional("SELECT * FROM ai_embedding_profile WHERE status='ACTIVE' ORDER BY id DESC LIMIT 1").orElse(null):null;
        float[] queryVector=null;
        if(profile!=null)try{queryVector=factory.embed(factory.clients(models.settings(profile.number("config_id"))),question);}catch(Exception ignored){}
        final float[] vector=queryVector;
        List<AiRow> rows=db.rows("SELECT d.id AS document_id,d.type,d.product_id,d.origin_kind,r.id AS revision_id,r.revision_no,r.title,r.standard_question,r.aliases,r.product_version,k.id AS chunk_id,k.content,e.vector_json "
            +"FROM ai_knowledge_document d JOIN ai_knowledge_revision r ON r.id=d.active_revision_id JOIN ai_knowledge_chunk k ON k.revision_id=r.id "
            +"LEFT JOIN ai_chunk_embedding e ON e.chunk_id=k.id AND e.profile_id=? WHERE d.enabled=1 ORDER BY d.id,k.ordinal LIMIT 2000",profile==null?-1:profile.id());
        List<Hit> candidates=new ArrayList<>();
        for(AiRow row:rows){
            if(row.number("product_id")!=null){
                Optional<AiRow> product=catalog.product(row.number("product_id"));
                if(product.isEmpty() || !Objects.equals(product.get().number("version"),row.number("product_version")))continue;
            }
            String content=row.text("content");
            boolean exact=row.text("type").equals("FAQ") && (normalize(question).equals(normalize(row.text("standard_question"))) || json.strings(row.get("aliases")).stream().map(AiKnowledgeRetriever::normalize).anyMatch(normalize(question)::equals));
            double lexical=exact?1:keyword(question,row.text("title")+" "+Objects.toString(row.text("standard_question"),"")+" "+content);
            double semantic=0;
            if(vector!=null && row.get("vector_json")!=null)try{semantic=cosine(vector,json.read(row.get("vector_json"),float[].class));}catch(Exception ignored){}
            if(!exact && lexical<.20 && semantic<.62)continue;
            long docId=row.number("document_id");
            Map<String,Object> metadata=new HashMap<>();metadata.put("documentId",docId);metadata.put("revision",row.number("revision_no"));metadata.put("type",row.text("type"));metadata.put("title",row.text("title"));
            Document document=new Document("chunk:"+row.number("chunk_id"),row.text("title")+"\n"+content,metadata);
            Source source=new Source(0,"KNOWLEDGE",docId,row.number("revision_no"),row.text("title"),truncate(content,500),row.number("product_id")==null?null:"/products/"+row.number("product_id"));
            candidates.add(new Hit(document,source,lexical,semantic,exact));
        }
        // Current product facts remain available while an updated revision is still indexing.
        for(AiRow product:catalog.products()){
            if(!question.contains(product.text("name")))continue;
            String facts=catalog.facts(product);
            Document document=new Document("product:"+product.id(),facts,Map.of("productId",product.id(),"version",product.number("version"),"type","PRODUCT"));
            candidates.add(new Hit(document,new Source(0,"PRODUCT",product.id(),product.number("version"),product.text("name"),truncate(facts,500),"/products/"+product.id()),1,0,false));
        }
        List<Hit> keywordRank=candidates.stream().filter(h->h.keywordScore()>=.20).sorted(Comparator.comparingDouble(Hit::keywordScore).reversed()).toList();
        List<Hit> vectorRank=candidates.stream().filter(h->h.vectorScore()>=.62).sorted(Comparator.comparingDouble(Hit::vectorScore).reversed()).toList();
        Map<String,Double> scores=new HashMap<>();rank(keywordRank,scores);rank(vectorRank,scores);
        candidates.sort(Comparator.<Hit>comparingInt(h->h.exactFaq()?0:1).thenComparing(Comparator.comparingDouble((Hit h)->scores.getOrDefault(h.document().getId(),0d)).reversed()));
        List<Hit> result=new ArrayList<>();Set<String> seen=new HashSet<>();int chars=0;
        for(Hit hit:candidates){
            if(result.size()==6 || chars>=4000)break;
            if(!seen.add(hit.document().getId()))continue;
            String excerpt=Objects.toString(hit.document().getText(),"");if(excerpt.length()>4000-chars)excerpt=excerpt.substring(0,4000-chars);chars+=excerpt.length();
            Document bounded=new Document(hit.document().getId(),excerpt,hit.document().getMetadata());
            Source s=hit.source();result.add(new Hit(bounded,new Source(result.size()+1,s.type(),s.id(),s.version(),s.title(),s.excerpt(),s.path()),hit.keywordScore(),hit.vectorScore(),hit.exactFaq()));
        }
        return new Result(result,vector==null?"KEYWORD":"HYBRID");
    }
    private static void rank(List<Hit> rank,Map<String,Double> scores){for(int i=0;i<rank.size();i++)scores.merge(rank.get(i).document().getId(),1d/(60+i+1),Double::sum);}
    public static double keyword(String question,String body){
        Set<String> tokens=grams(normalize(question));if(tokens.isEmpty())return 0;
        String normalized=normalize(body);long count=tokens.stream().filter(normalized::contains).count();
        return count/(double)tokens.size();
    }
    private static Set<String> grams(String text){Set<String> tokens=new HashSet<>();for(int i=0;i+1<text.length();i++)tokens.add(text.substring(i,i+2));return tokens;}
    public static String normalize(String text){return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\p{P}\\s]","");}
    public static double cosine(float[] a,float[] b){if(a.length!=b.length)return 0;double dot=0,x=0,y=0;for(int i=0;i<a.length;i++){if(!Float.isFinite(a[i])||!Float.isFinite(b[i]))return 0;dot+=a[i]*b[i];x+=a[i]*a[i];y+=b[i]*b[i];}return x==0||y==0?0:dot/Math.sqrt(x*y);}
    private static String truncate(String text,int n){return text.length()<=n?text:text.substring(0,n);}
}
