package com.fairyshalltear.freshfruit.inventory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import com.fasterxml.jackson.databind.*;
import com.fairyshalltear.freshfruit.audit.mapper.AdminOperationLogMapper;
import com.fairyshalltear.freshfruit.audit.entity.AdminOperationLog;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:inventory;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql","security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","security.admin.initial-username=inventoryadmin","security.admin.initial-password=InventoryPassword123!"})
@AutoConfigureMockMvc
class InventoryIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;
    @MockitoSpyBean AdminOperationLogMapper audit;
    String token,label;long root,category,product,sku,second,admin;
    static final String BASE="/api/v1/admin/inventory";
    @BeforeEach void setup()throws Exception{
        label=UUID.randomUUID().toString().substring(0,8);
        token=request("POST","/api/v1/admin/auth/login",Map.of("username","inventoryadmin","password","InventoryPassword123!"),null,200).path("accessToken").asText();
        admin=number("SELECT id FROM admin_account WHERE username='inventoryadmin'");
        db.update("INSERT INTO product_category(name) VALUES(?)","根"+label);root=number("SELECT MAX(id) FROM product_category");
        db.update("INSERT INTO product_category(name,parent_id) VALUES(?,?)","叶"+label,root);category=number("SELECT MAX(id) FROM product_category");
        db.update("INSERT INTO fruit_product(category_id,name,status) VALUES(?,?,0)",category,"库存苹果"+label);product=number("SELECT MAX(id) FROM fruit_product");
        sku=newSku("1kg/袋",30);second=newSku("500g/盒",10);
    }
    long newSku(String spec,int qty){db.update("INSERT INTO product_sku(product_id,specification,price) VALUES(?,?,10)",product,spec);long id=number("SELECT MAX(id) FROM product_sku");db.update("INSERT INTO sku_inventory(sku_id,on_hand) VALUES(?,?)",id,qty);return id;}
    long number(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
    JsonNode request(String method,String url,Object body,String auth,int expected)throws Exception{
        var r=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create(url));if(auth!=null)r.header("Authorization","Bearer "+auth);if(body!=null)r.contentType("application/json").content(json.writeValueAsBytes(body));
        MvcResult result=mvc.perform(r).andReturn();assertEquals(expected,result.getResponse().getStatus(),result.getResponse().getContentAsString());return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
    JsonNode call(String method,String path,Object body,int expected)throws Exception{return request(method,BASE+path,body,token,expected);}
    Map<String,Object> body(String type,List<Map<String,Object>> lines){return new HashMap<>(Map.of("type",type,"supplier","果园合作社","referenceNo","采购参考"+label,"reason","库存核对","items",lines));}
    JsonNode draft(String type,long id,int qty)throws Exception{return call("POST","/documents",body(type,List.of(Map.of("skuId",id,"STOCKTAKE".equals(type)?"countedQuantity":"quantity",qty))),200);}
    JsonNode post(JsonNode d,int expected)throws Exception{return call("POST","/documents/"+d.path("id").asLong()+"/post",Map.of("version",d.path("version").asLong()),expected);}
    @Test void schemaAndPermissionValidation()throws Exception{
        call("GET","/documents/999999",null,404);call("GET","/stocks?page=0",null,400);call("GET","/stocks?size=101",null,400);call("GET","/movements?from=2026-11-16T00:00:00&to=2026-11-15T00:00:00",null,400);
        request("GET",BASE+"/stocks",null,null,401);
        String user="inventorybuyer"+label;String buyer=request("POST","/api/v1/auth/register",Map.of("username",user,"phone","139"+String.format("%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100000000L)),"password","BuyerPassword123!"),null,200).path("accessToken").asText();
        request("POST",BASE+"/documents",body("PURCHASE_IN",List.of(Map.of("skuId",sku,"quantity",1))),buyer,403);
        call("POST","/documents",body("PURCHASE_IN",List.of(Map.of("skuId",sku,"quantity",1),Map.of("skuId",sku,"quantity",2))),400);
        call("POST","/documents",body("PURCHASE_IN",List.of(Map.of("skuId",sku,"quantity",1.5))),400);
        Map<String,Object> spoof=body("PURCHASE_IN",List.of(Map.of("skuId",sku,"quantity",1)));spoof.put("createdBy",admin);call("POST","/documents",spoof,400);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO sku_inventory(sku_id,on_hand,reserved) VALUES(999999,0,1)"));
    }
    @Test void purchaseDraftAndPostingAreAtomicImmutableAndIdempotent()throws Exception{
        db.update("UPDATE sku_inventory SET reserved=8 WHERE sku_id=?",sku);
        JsonNode d=call("POST","/documents",body("PURCHASE_IN",List.of(Map.of("skuId",sku,"quantity",20),Map.of("skuId",second,"quantity",5))),200);
        assertEquals(30,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));assertEquals(0,number("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku));
        JsonNode posted=post(d,200);assertEquals("POSTED",posted.path("status").asText());assertEquals(50,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));assertEquals(8,number("SELECT reserved FROM sku_inventory WHERE sku_id=?",sku));
        post(d,200);assertEquals(1,number("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku));
        Map<String,Object> edit=body("PURCHASE_IN",List.of(Map.of("skuId",sku,"quantity",10)));edit.put("version",posted.path("version").asLong());call("PUT","/documents/"+d.path("id").asLong(),edit,409);call("DELETE","/documents/"+d.path("id").asLong()+"?version=1",null,409);
        db.update("UPDATE fruit_product SET name='后来改名' WHERE id=?",product);JsonNode detail=call("GET","/documents/"+d.path("id").asLong(),null,200);assertTrue(detail.path("items").get(0).path("productName").asText().contains(label));
    }
    @Test void receiptEligibilityAndOverflowRollback()throws Exception{
        JsonNode d=draft("PURCHASE_IN",sku,1);db.update("UPDATE product_sku SET status=0 WHERE id=?",sku);post(d,409);assertEquals(30,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));
        db.update("UPDATE product_sku SET status=1 WHERE id=?",sku);db.update("UPDATE sku_inventory SET on_hand=? WHERE sku_id=?",Integer.MAX_VALUE,sku);post(d,409);
        db.update("UPDATE fruit_product SET deleted=1 WHERE id=?",product);call("POST","/documents",body("PURCHASE_IN",List.of(Map.of("skuId",sku,"quantity",1))),409);
    }
    @Test void supplierReturnPreservesReservationsAndRollsBackEveryLine()throws Exception{
        db.update("UPDATE sku_inventory SET reserved=8 WHERE sku_id=?",sku);
        JsonNode d=call("POST","/documents",body("SUPPLIER_RETURN_OUT",List.of(Map.of("skuId",sku,"quantity",5),Map.of("skuId",second,"quantity",11))),200);post(d,409);assertEquals(30,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));assertEquals(0,number("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku));
        db.update("UPDATE fruit_product SET deleted=1 WHERE id=?",product);db.update("UPDATE product_sku SET status=0 WHERE id=?",sku);
        JsonNode boundary=draft("SUPPLIER_RETURN_OUT",sku,22);post(boundary,200);assertEquals(8,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));assertEquals(8,number("SELECT reserved FROM sku_inventory WHERE sku_id=?",sku));
    }
    @Test void draftsAreVersionedAndCanBeDeleted()throws Exception{
        JsonNode d=draft("PURCHASE_IN",sku,3);Map<String,Object> edit=body("PURCHASE_IN",List.of(Map.of("skuId",sku,"quantity",4)));edit.put("version",0);
        JsonNode next=call("PUT","/documents/"+d.path("id").asLong(),edit,200);call("PUT","/documents/"+d.path("id").asLong(),edit,409);post(d,409);
        call("DELETE","/documents/"+d.path("id").asLong()+"?version="+next.path("version").asLong(),null,200);call("GET","/documents/"+d.path("id").asLong(),null,404);assertEquals(30,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));
    }
    @Test void countsRecordLossGainAndZeroWithoutOverwritingReservations()throws Exception{
        db.update("UPDATE sku_inventory SET reserved=8 WHERE sku_id=?",sku);post(draft("STOCKTAKE",sku,27),200);assertEquals(-3,number("SELECT quantity_delta FROM inventory_movement WHERE sku_id=?",sku));
        post(draft("STOCKTAKE",sku,35),200);assertEquals(35,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));
        JsonNode zero=post(draft("STOCKTAKE",sku,35),200);assertEquals(0,zero.path("items").get(0).path("quantityDelta").asInt());assertEquals(2,number("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku));
        post(draft("STOCKTAKE",sku,7),409);post(draft("STOCKTAKE",sku,8),200);assertEquals(8,number("SELECT reserved FROM sku_inventory WHERE sku_id=?",sku));
    }
    @Test void staleCountPreservesInputsAndRequiresExplicitRefresh()throws Exception{
        JsonNode d=draft("STOCKTAKE",sku,27);post(draft("PURCHASE_IN",sku,20),200);post(d,409);
        JsonNode unchanged=call("GET","/documents/"+d.path("id").asLong(),null,200);assertEquals(30,unchanged.path("items").get(0).path("beforeOnHand").asInt());assertEquals(27,unchanged.path("items").get(0).path("countedQuantity").asInt());assertEquals(50,unchanged.path("items").get(0).path("currentStock").path("onHand").asInt());
        JsonNode refreshed=call("POST","/documents/"+d.path("id").asLong()+"/refresh-snapshot",Map.of("version",0),200);assertEquals(50,refreshed.path("items").get(0).path("beforeOnHand").asInt());assertEquals(27,refreshed.path("items").get(0).path("countedQuantity").asInt());post(refreshed,200);
    }
    @Test void lowStockFiltersTotalsAndThresholdUpdates()throws Exception{
        call("PATCH","/stocks/"+sku+"/threshold",Map.of("lowStockThreshold",5,"version",0),200);db.update("UPDATE sku_inventory SET reserved=26,version=version+1 WHERE sku_id=?",sku);
        JsonNode low=call("GET","/stocks?stockState=LOW&keyword="+label,null,200);assertEquals(1,low.path("total").asLong());assertEquals(4,low.path("items").get(0).path("availableStock").asInt());
        JsonNode summary=call("GET","/summary?categoryId="+root+"&size=1",null,200);assertEquals(2,summary.path("skuCount").asLong());assertEquals(40,summary.path("onHand").asLong());
        call("PATCH","/stocks/"+sku+"/threshold",Map.of("lowStockThreshold",4,"version",0),409);
        call("PATCH","/stocks/"+sku+"/threshold",Map.of("lowStockThreshold",4,"version",2),200);assertEquals(0,call("GET","/stocks?stockState=LOW&keyword="+label,null,200).path("total").asLong());assertEquals(0,number("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku));
        call("PATCH","/stocks/"+sku+"/threshold",Map.of("lowStockThreshold",0,"version",3),200);
        assertEquals(0,call("GET","/stocks?page=100&categoryId="+category,null,200).path("items").size());
    }
    @Test void auditFailureRollsBackStockAndDocument()throws Exception{
        JsonNode d=draft("PURCHASE_IN",sku,20);doThrow(new org.springframework.dao.DataIntegrityViolationException("audit unavailable")).when(audit).insert(any(AdminOperationLog.class));
        try{post(d,409);}finally{reset(audit);}assertEquals(30,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));assertEquals("DRAFT",call("GET","/documents/"+d.path("id").asLong(),null,200).path("status").asText());assertEquals(0,number("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku));
    }
    @Test void concurrentConfirmationPostsOnlyOnce()throws Exception{
        JsonNode d=draft("PURCHASE_IN",sku,20);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{List<Future<?>> fs=new ArrayList<>();for(int i=0;i<2;i++)fs.add(pool.submit(()->{try{start.await();post(d,200);}catch(Exception e){throw new RuntimeException(e);}}));start.countDown();for(var f:fs)f.get(20,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        assertEquals(50,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));assertEquals(1,number("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku));
    }
    @Test void competingSupplierReturnsCannotConsumeTheSameStock()throws Exception{
        JsonNode a=draft("SUPPLIER_RETURN_OUT",sku,20),b=draft("SUPPLIER_RETURN_OUT",sku,20);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{List<Future<Integer>> fs=new ArrayList<>();for(JsonNode d:List.of(a,b))fs.add(pool.submit(()->{start.await();return mvc.perform(MockMvcRequestBuilders.post(BASE+"/documents/"+d.path("id").asLong()+"/post").header("Authorization","Bearer "+token).contentType("application/json").content("{\"version\":0}")).andReturn().getResponse().getStatus();}));start.countDown();List<Integer> codes=new ArrayList<>();for(var f:fs)codes.add(f.get(20,TimeUnit.SECONDS));Collections.sort(codes);assertEquals(List.of(200,409),codes);}finally{pool.shutdownNow();}assertEquals(10,number("SELECT on_hand FROM sku_inventory WHERE sku_id=?",sku));
    }
    @Test void movementsFilterSourcesAndPreserveHistoricalRecords()throws Exception {
        JsonNode d=post(draft("PURCHASE_IN",sku,3),200);String no=d.path("documentNo").asText();
        JsonNode rows=call("GET","/movements?sourceNo="+no+"&direction=IN",null,200);
        assertEquals(1,rows.path("total").asLong());assertEquals(d.path("id").asLong(),rows.path("items").get(0).path("sourceId").asLong());assertEquals("DOCUMENT",rows.path("items").get(0).path("sourceType").asText());
        db.update("UPDATE fruit_product SET name='已改名' WHERE id=?",product);
        JsonNode renamed=call("GET","/movements?sourceNo="+no,null,200);assertTrue(renamed.path("items").get(0).path("productName").asText().contains(label));
        db.update("INSERT INTO admin_operation_log(admin_id,operation_type,target_type,target_id,result) VALUES(?,'SKU_STOCK_ADJUSTMENT','SKU',?,'SUCCESS')",admin,sku);long log=number("SELECT MAX(id) FROM admin_operation_log");
        db.update("INSERT INTO inventory_movement(sku_id,operation_log_id,admin_id,type,before_on_hand,after_on_hand,quantity_delta,reason) VALUES(?,?,?,'PRODUCT_EDIT_ADJUSTMENT',12,20,8,'历史核对')",sku,log,admin);
        JsonNode old=call("GET","/movements?skuId="+sku+"&type=PRODUCT_EDIT_ADJUSTMENT",null,200);assertEquals(1,old.path("total").asLong());assertTrue(old.path("items").get(0).path("sourceId").isNull());
        assertEquals(0,call("GET","/movements?skuId="+sku+"&page=99",null,200).path("items").size());
        call("POST","/documents",body("STOCKTAKE",List.of(Map.of("skuId",sku,"countedQuantity",10,"beforeOnHand",30))),400);
    }

}
