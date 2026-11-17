package com.fairyshalltear.freshfruit.ai.chat;

import com.fairyshalltear.freshfruit.ai.AiDtos.Context;

public final class AiScenes {
    private AiScenes(){}
    public static String identify(String text,Context context){
        if(text.matches("(?s).*(退款|退货|售后|换货|AS[0-9]{13}[0-9A-F]{8}).*"))return "AFTER_SALE";
        if(text.matches("(?s).*(订单|物流|快递|到哪|发货|签收|FF[0-9]{13}[0-9A-F]{8}).*"))return "ORDER";
        if(text.matches("(?s).*(推荐|送礼|礼盒|预算|应季|适合长辈).*"))return "RECOMMEND";
        if(context.productId()!=null || text.matches("(?s).*(产地|口感|甜度|保鲜|保存|储存|怎么吃).*"))return "PRODUCT";
        if(context.afterSaleId()!=null)return "AFTER_SALE";
        if((context.budget()!=null || context.giftBox()!=null || context.useCase()!=null || context.month()!=null)
            && text.matches("(?s).*(改成|换成|便宜|[0-9]+元|[0-9]+份|[0-9]+月).*"))return "RECOMMEND";
        return "GENERAL";
    }
    public static String topic(String scene){return switch(scene){case "ORDER"->"订单与物流查询";case "AFTER_SALE"->"售后与退款咨询";case "RECOMMEND"->"水果与礼盒推荐";case "PRODUCT"->"水果商品咨询";default->"其他问题";};}
    public static Context explicit(String text){
        java.math.BigDecimal budget=null;Integer month=null,quantity=null;Boolean gift=null;String use=null;
        var price=java.util.regex.Pattern.compile("(?:预算|不超过|最多|以内|以下)?\\s*([0-9]{1,8}(?:\\.[0-9]{1,2})?)\\s*元").matcher(text);
        if(price.find())budget=new java.math.BigDecimal(price.group(1));
        var months=java.util.regex.Pattern.compile("(?<![0-9])([1-9]|1[0-2])月").matcher(text);if(months.find())month=Integer.valueOf(months.group(1));
        var portions=java.util.regex.Pattern.compile("(?<![0-9])([1-9][0-9]{0,2})份").matcher(text);if(portions.find())quantity=Integer.valueOf(portions.group(1));
        if(text.contains("礼盒"))gift=!(text.contains("不要礼盒")||text.contains("不用礼盒")||text.contains("不限礼盒"));
        if(text.contains("长辈")||text.contains("老人"))use="ELDER_GIFT";
        else if(text.contains("商务"))use="BUSINESS_GIFT";
        else if(text.contains("亲友"))use="FRIEND_GIFT";
        else if(text.contains("家庭"))use="FAMILY";
        else if(text.contains("日常"))use="DAILY";
        String taste=null;
        for(String value:java.util.List.of("酸甜","清甜","香甜","甜","酸"))if(text.contains(value)){taste=value;break;}
        return new Context(null,null,null,budget,month,gift,use,taste,quantity);
    }
    public static Context merge(Context existing,Context change){
        if(existing==null)existing=Context.empty();if(change==null)return existing;
        return new Context(change.productId()==null?(change.orderId()!=null||change.afterSaleId()!=null?null:existing.productId()):change.productId(),change.orderId()==null?(change.productId()!=null?null:existing.orderId()):change.orderId(),
            change.afterSaleId()==null?(change.productId()!=null || change.orderId()!=null && !change.orderId().equals(existing.orderId())?null:existing.afterSaleId()):change.afterSaleId(),change.budget()==null?existing.budget():change.budget(),
            change.month()==null?existing.month():change.month(),change.giftBox()==null?existing.giftBox():change.giftBox(),
            change.useCase()==null?existing.useCase():change.useCase(),change.taste()==null?existing.taste():change.taste(),change.quantity()==null?existing.quantity():change.quantity());
    }
    public static Context selected(Context existing,Context change){
        Context merged=merge(existing,change);
        if(change==null || change.budget()==null && change.month()==null && change.giftBox()==null && change.useCase()==null && change.taste()==null && change.quantity()==null)return merged;
        return new Context(merged.productId(),merged.orderId(),merged.afterSaleId(),change.budget(),change.month(),change.giftBox(),change.useCase(),change.taste(),change.quantity());
    }
}
