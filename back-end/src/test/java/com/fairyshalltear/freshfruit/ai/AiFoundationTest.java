package com.fairyshalltear.freshfruit.ai;

import java.net.InetAddress;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.fairyshalltear.freshfruit.ai.model.*;
import com.fairyshalltear.freshfruit.ai.safety.*;
import com.fairyshalltear.freshfruit.ai.knowledge.*;
import com.fairyshalltear.freshfruit.catalog.*;
import com.fairyshalltear.freshfruit.catalog.entity.FruitProduct;
import static org.junit.jupiter.api.Assertions.*;

class AiFoundationTest {
    @Test void encryptionIsRandomAuthenticatedAndRequiresCorrectMaster(){
        String key=Base64.getEncoder().encodeToString(new byte[32]);
        var crypto=new AiCredentials(key);String a=crypto.encrypt("sk-example-secret"),b=crypto.encrypt("sk-example-secret");
        assertNotEquals(a,b);assertFalse(a.contains("sk-example-secret"));assertEquals("sk-example-secret",crypto.decrypt(a));
        assertThrows(RuntimeException.class,()->new AiCredentials("").encrypt("secret"));
        byte[] other=new byte[32];other[0]=1;
        assertThrows(RuntimeException.class,()->new AiCredentials(Base64.getEncoder().encodeToString(other)).decrypt(a));
        assertThrows(RuntimeException.class,()->crypto.decrypt(a.substring(0,a.length()-4)+"AAAA"));
    }
    @Test void blockedOutputNeverReleasesCrossChunkTerm(){
        var rule=new AiDtos.Rule(1,"测试","OUTPUT","LITERAL",List.of("禁止词","很长的敏感文字"),1,"已拦截",true,0);
        var guard=new AiOutputGuard(List.of(rule));
        String safe=guard.append("这段安全文本禁止");
        assertFalse(safe.contains("禁止"));
        assertThrows(AiOutputGuard.Blocked.class,()->guard.append("词结束"));
        var tail=new AiOutputGuard(List.of(rule));String output=tail.append("中文🍎分片")+tail.finish();assertEquals("中文🍎分片",output);
        var unicode=new AiOutputGuard(List.of());assertEquals("",unicode.append("\uD83C"));assertEquals("🍎",unicode.append("\uDF4E"));
        var terminal=new AiOutputGuard(List.of(rule));terminal.append("普通内容很长的敏");
        assertThrows(AiOutputGuard.Blocked.class,()->terminal.append("感文字"));
    }
    @Test void rulePriorityAndCombinationsAreDeterministic(){
        var any=new AiDtos.Rule(1,"ANY","INPUT","ANY",List.of("退款","礼盒"),1,"低",true,0);
        var all=new AiDtos.Rule(2,"ALL","INPUT","ALL",List.of("退款","直接"),9,"高",true,0);
        assertEquals(2,AiRuleService.match(List.of(any,all),"INPUT","请直接退款").orElseThrow().id());
        assertEquals(1,AiRuleService.match(List.of(any,all),"INPUT","礼盒怎么选").orElseThrow().id());
        assertTrue(AiRuleService.match(List.of(any,all),"OUTPUT","直接退款").isEmpty());
    }
    @Test void endpointRejectsPrivateUrlsAndCredentials()throws Exception{
        var policy=new AiEndpointPolicy("api.openai.com",false);
        for(String url:List.of("http://api.openai.com/v1","https://127.0.0.1/v1","https://api.openai.com@localhost/v1","https://example.com/v1","file:///tmp/key","https://api.openai.com/v1?key=secret"))assertThrows(RuntimeException.class,()->policy.validate(url));
        for(String ip:List.of("127.0.0.1","10.1.2.3","172.16.1.1","192.168.1.1","169.254.169.254","100.64.0.1","fc00::1","0.0.0.0"))assertThrows(RuntimeException.class,()->policy.validateAddress(InetAddress.getByName(ip)));
        assertEquals("localhost",new AiEndpointPolicy("",true).validate("http://localhost:1234/v1").getHost());
    }
    @Test void optionalProductFieldsPreserveOrExplicitlyClear()throws Exception{
        var json=new ObjectMapper();
        String old="{\"name\":\"苹果\",\"categoryId\":1,\"imageIds\":[],\"skus\":[]}";
        CatalogDtos.ProductRequest oldRequest=json.readValue(old,CatalogDtos.ProductRequest.class);
        FruitProduct p=new FruitProduct();p.setSweetnessDescription("清甜");p.setSeasonalMonths("[9]");p.setUseCases("[\"DAILY\"]");p.setIsGiftBox(1);
        ProductConsultation.apply(p,oldRequest.consultationAttributes());assertEquals("清甜",p.getSweetnessDescription());assertEquals(1,p.getIsGiftBox());
        var clear=json.readValue(old.substring(0,old.length()-1)+",\"sweetnessDescription\":null,\"seasonalMonths\":[],\"useCases\":null,\"giftBox\":false}",CatalogDtos.ProductRequest.class);
        ProductConsultation.apply(p,clear.consultationAttributes());assertNull(p.getSweetnessDescription());assertEquals(List.of(),ProductConsultation.months(p.getSeasonalMonths()));assertNull(p.getUseCases());assertEquals(0,p.getIsGiftBox());
        ProductConsultation.apply(p,Map.of("seasonalMonths",List.of(12,1,12)));assertEquals(List.of(1,12),ProductConsultation.months(p.getSeasonalMonths()));
        for(Object value:List.of(List.of(1.1),List.of(13),"全部"))assertThrows(RuntimeException.class,()->ProductConsultation.apply(p,Map.of("seasonalMonths",value)));
        assertThrows(RuntimeException.class,()->ProductConsultation.apply(p,Map.of("useCases",List.of("UNKNOWN"))));
        assertThrows(Exception.class,()->json.readValue(old.substring(0,old.length()-1)+",\"buyerId\":2}",CatalogDtos.ProductRequest.class));
    }
    @Test void knowledgeChunkingPreservesUnicodeAndVectorCompatibility(){
        String body="草莓应冷藏保存。".repeat(200)+"🍎";
        List<String> chunks=AiIndexWorker.split(body);assertTrue(chunks.size()>1);assertTrue(chunks.stream().allMatch(c->c.length()<=700));assertTrue(chunks.get(chunks.size()-1).endsWith("🍎"));
        assertEquals(1,AiKnowledgeRetriever.cosine(new float[]{1,0},new float[]{1,0}),.001);
        assertEquals(0,AiKnowledgeRetriever.cosine(new float[]{1},new float[]{1,0}));
        assertEquals(0,AiKnowledgeRetriever.cosine(new float[]{Float.NaN},new float[]{1}));
    }
    @Test void fixedTemplateVariablesAndPrivacyMasking(){
        AiModelService.validateTemplate(AiModelSettings.DEFAULT_PROMPT);
        assertThrows(RuntimeException.class,()->AiModelService.validateTemplate("{buyerId}"));
        String masked=AiPrivacy.mask("电话13812345678 邮箱a@example.com Key sk-abcdefghijk Bearer token123 江苏省南京市中山路1号");
        for(String privateText:List.of("13812345678","a@example.com","sk-abcdefghijk","token123","中山路1号"))assertFalse(masked.contains(privateText),masked);
    }
}
