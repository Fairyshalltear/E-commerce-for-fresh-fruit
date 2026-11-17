package com.fairyshalltear.freshfruit.ai;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.catalog.ProductConsultation;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiCatalogService {
    public static final String VISIBLE="p.deleted=0 AND p.status=1 AND c.status=1 AND parent.status=1 AND parent.parent_id IS NULL AND EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.id AND s.status=1)";
    public static final String JOIN=" FROM fruit_product p JOIN product_category c ON c.id=p.category_id JOIN product_category parent ON parent.id=c.parent_id ";
    private final AiMapper db;
    public AiCatalogService(AiMapper db){this.db=db;}
    public boolean visible(long id){return db.count("SELECT COUNT(*)"+JOIN+"WHERE p.id=? AND "+VISIBLE,id)==1;}
    public Optional<AiRow> product(long id){return db.optional("SELECT p.*"+JOIN+"WHERE p.id=? AND "+VISIBLE,id);}
    public List<AiRow> products(){return db.rows("SELECT p.*"+JOIN+"WHERE "+VISIBLE+" ORDER BY p.id LIMIT 2000");}
    public List<AiRow> skus(long id){return db.rows("SELECT s.id,s.specification,s.price,(i.on_hand-i.reserved) AS available FROM product_sku s JOIN sku_inventory i ON i.sku_id=s.id WHERE s.product_id=? AND s.status=1 ORDER BY s.price,s.id",id);}
    public String baseFacts(AiRow p){
        StringBuilder text=new StringBuilder(p.text("name"));
        for(var pair:List.of(new String[]{"origin","产地"},new String[]{"taste","口感"},new String[]{"storage_method","储存方法"},new String[]{"sweetness_description","甜度描述"}))
            text.append("\n").append(pair[1]).append("：").append(blank(p.text(pair[0]))?"未提供":p.text(pair[0]));
        List<Integer> months=ProductConsultation.months(p.text("seasonal_months"));
        List<String> uses=ProductConsultation.uses(p.text("use_cases"));
        text.append("\n应季月份：").append(months.isEmpty()?"未提供":String.join("、",months.stream().map(month->month+"月").toList()))
            .append("\n水果礼盒：").append(p.flag("is_gift_box")?"是":"否").append("\n适用场景：")
            .append(uses.isEmpty()?"未提供":String.join("、",uses.stream().map(use->switch(use){
                case "DAILY"->"日常食用";case "ELDER_GIFT"->"长辈送礼";case "FRIEND_GIFT"->"亲友送礼";
                case "BUSINESS_GIFT"->"商务送礼";case "FAMILY"->"家庭分享";default->"其他用途";
            }).toList()));
        text.append("\n说明：").append(blank(p.text("description"))?"未提供":p.text("description"));
        return text.toString();
    }
    public String facts(AiRow p){
        StringBuilder text=new StringBuilder(baseFacts(p));
        for(AiRow sku:skus(p.id()))text.append("\n规格：").append(sku.text("specification")).append("，价格：￥").append(sku.decimal("price")).append("，可售库存：").append(sku.integer("available"));
        return text.toString();
    }
    public List<Card> recommend(Context context){
        int month=context.month()==null?LocalDate.now(ZoneId.of("Asia/Shanghai")).getMonthValue():context.month();
        int quantity=context.quantity()==null?1:context.quantity();
        List<Card> result=new ArrayList<>();
        List<AiRow> sorted=new ArrayList<>(products());
        sorted.sort(Comparator.<AiRow>comparingInt(p->!blank(context.taste()) && Objects.toString(p.text("taste"),"").contains(context.taste())?0:1).thenComparingLong(AiRow::id));
        for(AiRow p:sorted){
            if(context.giftBox()!=null && context.giftBox()!=p.flag("is_gift_box"))continue;
            if(!ProductConsultation.months(p.text("seasonal_months")).contains(month))continue;
            if(context.useCase()!=null && !ProductConsultation.uses(p.text("use_cases")).contains(context.useCase()))continue;
            Optional<AiRow> sku=skus(p.id()).stream().filter(s->s.integer("available")>=quantity)
                .filter(s->context.budget()==null || s.decimal("price").multiply(BigDecimal.valueOf(quantity)).compareTo(context.budget())<=0).findFirst();
            if(sku.isEmpty())continue;
            result.add(card(p,sku.get(),"符合"+month+"月应季条件；"+sku.get().text("specification")+" × "+quantity));
            if(result.size()==3)break;
        }
        return result;
    }
    public Card card(AiRow p,AiRow sku,String description){
        String image=db.optional("SELECT storage_key FROM product_image WHERE product_id=? AND active=1 ORDER BY sort_order,id LIMIT 1",p.id())
            .map(r->"/api/v1/public/product-images/"+r.text("storage_key")).orElse(null);
        return new Card("PRODUCT",p.id(),p.text("name"),description,sku==null?null:sku.decimal("price").toPlainString(),image,
            "/products/"+p.id(),new Context(p.id(),null,null,null,null,null,null,null,null));
    }
    public Map<String,Object> projection(long id){
        AiRow p=product(id).orElseThrow(AiErrors::missing);
        return values("id",p.id(),"version",p.number("version"),"facts",facts(p),"card",card(p,skus(id).stream().findFirst().orElse(null),"当前商品资料"));
    }
    private boolean blank(String text){return text==null || text.isBlank();}
}
