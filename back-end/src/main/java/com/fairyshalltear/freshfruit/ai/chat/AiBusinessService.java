package com.fairyshalltear.freshfruit.ai.chat;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.ai.knowledge.AiKnowledgeRetriever;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static com.fairyshalltear.freshfruit.ai.AiErrors.*;

@Service
public class AiBusinessService {
    public static final String POLICY="已付款且未发货的订单可申请退款；确认收货后7天内可申请退货退款，资格、数量与金额以本人订单及售后页面为准。当前系统不支持换货。物流和退款为模拟流程，记录不代表真实承运进度或支付到账。";
    public record Answer(String text,String mode,List<Card> cards,List<Source> sources,String denialReason){
        public Answer(String text,String mode,List<Card> cards,List<Source> sources){this(text,mode,cards,sources,null);}
    }
    private final AiMapper db;private final AiCatalogService catalog;
    public AiBusinessService(AiMapper db,AiCatalogService catalog){this.db=db;this.catalog=catalog;}
    public Context validate(AiActor actor,Context context){
        if(context==null)return Context.empty();
        if(context.productId()!=null)catalog.product(context.productId()).orElseThrow(AiErrors::missing);
        if(context.orderId()!=null)order(actor,context.orderId());
        if(context.afterSaleId()!=null){AiRow sale=sale(actor,context.afterSaleId());if(context.orderId()!=null&&!context.orderId().equals(sale.number("order_id")))throw invalid("售后申请与所选订单不一致");}
        return context;
    }
    public Context confirmed(AiActor actor,String text,Context existing){
        if(existing.productId()!=null && !catalog.visible(existing.productId()))existing=new Context(null,existing.orderId(),existing.afterSaleId(),existing.budget(),existing.month(),existing.giftBox(),existing.useCase(),existing.taste(),existing.quantity());
        Context context=AiScenes.merge(existing,AiScenes.explicit(text));
        List<AiRow> named=catalog.products().stream().filter(p->text.contains(p.text("name"))).toList();
        if(named.size()==1)context=AiScenes.merge(context,new Context(named.get(0).id(),null,null,null,null,null,null,null,null));
        if(!actor.guest()){
            Matcher order=Pattern.compile("FF[0-9]{13}[0-9A-Fa-f]{8}").matcher(text);
            if(order.find()){
                var record=db.optional("SELECT id FROM shop_order WHERE buyer_id=? AND order_no=?",actor.buyerId(),order.group().toUpperCase(Locale.ROOT));
                if(record.isPresent())context=AiScenes.merge(context,new Context(null,record.get().id(),null,null,null,null,null,null,null));
            }
            Matcher sale=Pattern.compile("AS[0-9]{13}[0-9A-Fa-f]{8}").matcher(text);
            if(sale.find()){
                var record=db.optional("SELECT id,order_id FROM after_sale_request WHERE buyer_id=? AND request_no=?",actor.buyerId(),sale.group().toUpperCase(Locale.ROOT));
                if(record.isPresent())context=AiScenes.merge(context,new Context(null,record.get().number("order_id"),record.get().id(),null,null,null,null,null,null));
            }
        }
        return context;
    }
    public Optional<Answer> direct(AiActor actor,String scene,String question,Context context){
        if(scene.equals("ORDER"))return Optional.of(orderAnswer(actor,question,context));
        if(scene.equals("AFTER_SALE"))return Optional.of(saleAnswer(actor,question,context));
        if(scene.equals("RECOMMEND")){
            if(context.budget()==null && context.giftBox()==null && context.useCase()==null && context.month()==null)
                return Optional.of(new Answer("请选择预算、月份及送礼用途；如需多份礼盒，请一并选择份数。我会按当前在售商品和库存筛选。","CATALOG",List.of(),List.of()));
            var cards=catalog.recommend(context);
            return Optional.of(new Answer(cards.isEmpty()?"目前没有同时符合这些条件的在售水果。可以调整预算、月份或用途后重新查询。":"为你找到以下应季水果。预算按每个候选规格单价乘份数计算，价格和库存以当前商品页为准。","CATALOG",cards,List.of()));
        }
        if(context.productId()!=null && scene.equals("PRODUCT")){
            AiRow product=catalog.product(context.productId()).orElseThrow(AiErrors::missing);
            String text=catalog.facts(product);
            return Optional.of(new Answer(text,"CATALOG",List.of(catalog.card(product,catalog.skus(product.id()).stream().findFirst().orElse(null),"查看当前商品与规格")),
                List.of(new Source(1,"PRODUCT",product.id(),product.number("version"),product.text("name"),truncate(text,500),"/products/"+product.id()))));
        }
        return Optional.empty();
    }
    public Answer basic(AiKnowledgeRetriever.Result result){
        var faq=result.hits().stream().filter(AiKnowledgeRetriever.Hit::exactFaq).findFirst();
        if(faq.isPresent())return new Answer(faq.get().document().getText(),"FAQ",List.of(),List.of(faq.get().source()));
        return new Answer("当前使用基础咨询服务。你可以选择商品查看产地、口感和储存资料，查询本人订单或售后，设置推荐条件，或使用已发布FAQ的标准问法。这个问题暂时缺少可靠依据，请补充信息。","DEGRADED",List.of(),List.of());
    }
    private Answer orderAnswer(AiActor actor,String question,Context context){
        if(actor.guest())return new Answer("登录后可查询本人订单状态和模拟物流。","BUSINESS",List.of(),List.of());
        Long id=context.orderId();
        Matcher number=Pattern.compile("FF[0-9]{13}[0-9A-Fa-f]{8}").matcher(question);
        if(number.find())id=db.optional("SELECT id FROM shop_order WHERE buyer_id=? AND order_no=?",actor.buyerId(),number.group().toUpperCase(Locale.ROOT)).map(AiRow::id).orElse(-1L);
        if(id==null)return new Answer("请选择要查询的本人订单。","BUSINESS",orderCards(actor),List.of());
        AiRow order;
        try{order=order(actor,id);}catch(RuntimeException error){return new Answer("未找到可查询的本人订单，请从订单列表选择。","BUSINESS",List.of(),List.of(),"ORDER_UNAVAILABLE");}
        StringBuilder text=new StringBuilder("订单 "+order.text("order_no")+"\n状态："+orderStatus(order.text("status")));
        text.append("\n订单金额：￥").append(order.decimal("total_amount")).append("，已退款金额：￥").append(order.decimal("refunded_amount"));
        List<AiRow> shipments=db.rows("SELECT id,carrier,tracking_number FROM shipment WHERE order_id=?",id);
        if(shipments.isEmpty())text.append("\n尚未登记物流记录。");
        else{
            AiRow shipment=shipments.get(0);text.append("\n模拟物流：").append(shipment.text("carrier")).append("，运单号：").append(shipment.text("tracking_number"));
            var event=db.optional("SELECT description,occurred_at FROM shipment_event WHERE shipment_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",shipment.id());
            if(event.isPresent())text.append("\n最近节点：").append(event.get().text("description")).append("（").append(event.get().time("occurred_at")).append("）");
            else text.append("\n尚无物流进度节点。");
            text.append("\n这些是系统模拟记录，不代表真实承运进度，也未提供确定送达时间。");
        }
        text.append("\n查询时间：").append(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        return new Answer(text.toString(),"BUSINESS",List.of(orderCard(order)),List.of(new Source(1,"ORDER",id,null,"本人订单当前状态",truncate(text.toString(),500),orderPath(id))));
    }
    private Answer saleAnswer(AiActor actor,String question,Context context){
        if(question.contains("换货"))return new Answer(POLICY,"BUSINESS",List.of(),List.of(policySource()));
        boolean privateQuestion=question.matches("(?s).*(进度|到账|到哪|我的|那退款|AS[0-9]).*") || context.afterSaleId()!=null || context.orderId()!=null;
        if(!privateQuestion)return new Answer(POLICY,"BUSINESS",List.of(),List.of(policySource()));
        if(actor.guest())return new Answer("登录后可查询本人售后申请与模拟退款记录。\n"+POLICY,"BUSINESS",List.of(),List.of(policySource()));
        Long id=context.afterSaleId();
        Matcher number=Pattern.compile("AS[0-9]{13}[0-9A-Fa-f]{8}").matcher(question);
        if(number.find())id=db.optional("SELECT id FROM after_sale_request WHERE buyer_id=? AND request_no=?",actor.buyerId(),number.group().toUpperCase(Locale.ROOT)).map(AiRow::id).orElse(-1L);
        if(id==null && context.orderId()!=null){
            AiRow order=order(actor,context.orderId());
            List<AiRow> sales=db.rows("SELECT id,request_no,status,order_id FROM after_sale_request WHERE buyer_id=? AND order_id=? ORDER BY id DESC",actor.buyerId(),order.id());
            if(sales.isEmpty()){
                boolean refund=order.text("status").equals("PENDING_SHIPMENT");
                boolean returns=order.text("status").equals("COMPLETED") && order.time("completed_at")!=null && !order.time("completed_at").isBefore(LocalDateTime.now().minusDays(7));
                String eligibility=refund?"该订单当前处于待发货，可在售后页面申请退款。":returns?"该订单当前在确认收货7天内，可在售后页面申请退货退款。":"该订单当前不满足未发货退款或收货7天内退货的条件。";
                return new Answer(eligibility+"\n尚无售后申请或退款记录。具体数量及审核以售后页面为准。\n"+POLICY,"BUSINESS",List.of(orderCard(order)),List.of(policySource()));
            }
            return new Answer("请选择该订单的售后申请。","BUSINESS",sales.stream().map(this::saleCard).toList(),List.of());
        }
        if(id==null){var sales=db.rows("SELECT id,request_no,status,order_id FROM after_sale_request WHERE buyer_id=? ORDER BY id DESC LIMIT 10",actor.buyerId());return new Answer(sales.isEmpty()?"你当前没有售后申请或退款记录。":"请选择要查询的本人售后申请。","BUSINESS",sales.stream().map(this::saleCard).toList(),List.of());}
        AiRow sale;
        try{sale=sale(actor,id);}catch(RuntimeException error){return new Answer("未找到可查询的本人售后申请，请从售后列表选择。","BUSINESS",List.of(),List.of(),"AFTER_SALE_UNAVAILABLE");}
        StringBuilder text=new StringBuilder("售后 "+sale.text("request_no")+"\n类型："+(sale.text("type").equals("REFUND")?"退款":"退货退款")+"\n状态："+saleStatus(sale.text("status"))+"\n申请金额：￥"+sale.decimal("requested_amount"));
        var refund=db.optional("SELECT amount,result,created_at FROM refund_record WHERE request_id=?",id);
        if(refund.isPresent())text.append("\n模拟退款成功金额：￥").append(refund.get().decimal("amount")).append("\n记录时间：").append(refund.get().time("created_at")).append("\n此为系统模拟退款，不代表真实支付到账。");
        else text.append("\n尚未生成退款成功记录，无法承诺到账时间。");
        text.append("\n查询时间：").append(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        return new Answer(text.toString(),"BUSINESS",List.of(saleCard(sale)),List.of(new Source(1,"AFTER_SALE",id,null,"本人售后与模拟退款",truncate(text.toString(),500),orderPath(sale.number("order_id")))));
    }
    private AiRow order(AiActor actor,long id){if(actor.guest())throw missing();return db.one("SELECT id,order_no,status,total_amount,refunded_amount,completed_at FROM shop_order WHERE buyer_id=? AND id=?",actor.buyerId(),id);}
    private AiRow sale(AiActor actor,long id){if(actor.guest())throw missing();return db.one("SELECT id,request_no,type,status,requested_amount,order_id FROM after_sale_request WHERE buyer_id=? AND id=?",actor.buyerId(),id);}
    private List<Card> orderCards(AiActor actor){return db.rows("SELECT id,order_no,status,total_amount FROM shop_order WHERE buyer_id=? ORDER BY id DESC LIMIT 10",actor.buyerId()).stream().map(this::orderCard).toList();}
    private Card orderCard(AiRow order){return new Card("ORDER",order.id(),order.text("order_no"),orderStatus(order.text("status")),order.decimal("total_amount").toPlainString(),null,orderPath(order.id()),new Context(null,order.id(),null,null,null,null,null,null,null));}
    private Card saleCard(AiRow sale){return new Card("AFTER_SALE",sale.id(),sale.text("request_no"),saleStatus(sale.text("status")),null,null,orderPath(sale.number("order_id")),new Context(null,sale.number("order_id"),sale.id(),null,null,null,null,null,null));}
    private Source policySource(){return new Source(1,"POLICY",null,null,"当前系统售后限制",POLICY,null);}
    private String orderPath(long id){return "/account/orders?orderId="+id;}
    private static String orderStatus(String value){return switch(value){case "PENDING_PAYMENT"->"待付款";case "PENDING_SHIPMENT"->"待发货";case "PENDING_RECEIPT"->"待收货";case "COMPLETED"->"已完成";case "CANCELLED"->"已取消";default->"未知状态";};}
    private static String saleStatus(String value){return switch(value){case "PENDING_REVIEW"->"待审核";case "WAITING_RETURN"->"等待寄回";case "WAITING_RECEIPT"->"等待收货";case "COMPLETED"->"已完成";case "REJECTED"->"已拒绝";default->"未知状态";};}
    private static String truncate(String text,int n){return text.length()<=n?text:text.substring(0,n);}
}
