package com.fairyshalltear.freshfruit.catalog;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import com.fairyshalltear.freshfruit.audit.entity.AdminOperationLog;
import com.fairyshalltear.freshfruit.audit.mapper.AdminOperationLogMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:test-schema.sql",
 "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
 "security.admin.initial-username=testadmin","security.admin.initial-password=TestAdminPassword123!"
})
@AutoConfigureMockMvc
class CatalogIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
 @MockitoSpyBean AdminOperationLogMapper logs;
 @MockitoSpyBean com.fairyshalltear.freshfruit.catalog.mapper.ProductImageMapper imageRecords;
 String admin,buyer,secondBuyer,label;long root,leaf;
 static final String IMAGE_DIR=System.getProperty("java.io.tmpdir")+"/freshfruit-catalog-images-"+UUID.randomUUID();
 @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
  r.add("freshfruit.product-images.storage-dir",()->IMAGE_DIR);
  String mysql=System.getenv("CATALOG_TEST_DB_URL");
  if(mysql!=null) {
   r.add("spring.datasource.url",()->mysql);r.add("spring.datasource.username",()->System.getenv("CATALOG_TEST_DB_USERNAME"));
   r.add("spring.datasource.password",()->System.getenv("CATALOG_TEST_DB_PASSWORD"));
   r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
   r.add("spring.flyway.enabled",()->true);r.add("spring.sql.init.mode",()->"never");
  }
 }
 @BeforeEach void setup() throws Exception {
  label=UUID.randomUUID().toString().substring(0,8);
  admin=call("POST","/api/v1/admin/auth/login",Map.of("username","testadmin","password","TestAdminPassword123!"),null,200).path("accessToken").asText();
  buyer=register();secondBuyer=register();
  root=category(null,"根"+label);leaf=category(root,"叶"+label);
 }
 String register()throws Exception {
  String suffix=UUID.randomUUID().toString().replace("-","");
  String phone="139"+String.format("%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100000000L));
  return call("POST","/api/v1/auth/register",Map.of("username","buyer"+suffix.substring(0,12),"phone",phone,"password","BuyerPassword123!","nickname","测试买家"),null,200).path("accessToken").asText();
 }
 long category(Long parent,String name)throws Exception {
  Map<String,Object> p=new HashMap<>();p.put("name",name);p.put("parentId",parent);p.put("status",1);p.put("sortOrder",0);
  return call("POST","/api/v1/admin/categories",p,admin,200).path("id").asLong();
 }
 JsonNode call(String method,String path,Object payload,String token,int expected)throws Exception {
  MockHttpServletRequestBuilder r=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create(path));
  if(token!=null)r.header("Authorization","Bearer "+token);
  if(payload!=null)r.contentType("application/json").content(json.writeValueAsBytes(payload));
  MvcResult result=mvc.perform(r).andReturn();
  assertEquals(expected,result.getResponse().getStatus(),result.getResponse().getContentAsString());
  String body=result.getResponse().getContentAsString();return body.isEmpty()?json.nullNode():json.readTree(body).path("data");
 }
 byte[] picture(String format)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray();}
 JsonNode upload(byte[] content,String mime,int expected)throws Exception {
  MvcResult result=mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/admin/product-images")
    .file(new MockMultipartFile("file","image",mime,content)).header("Authorization","Bearer "+admin)).andReturn();
  assertEquals(expected,result.getResponse().getStatus(),result.getResponse().getContentAsString());return json.readTree(result.getResponse().getContentAsString()).path("data");
 }
 ObjectNode payload(long category,List<Long> imageIds) {
  ObjectNode p=json.createObjectNode();p.put("name","苹果"+label);p.put("categoryId",category);p.put("description","清甜水果"+label);
  p.put("origin","山东");p.put("taste","清甜爽脆");p.put("storageMethod","冷藏");p.put("recommended",false);p.put("recommendSort",0);
  ArrayNode images=p.putArray("imageIds");imageIds.forEach(images::add);
  ArrayNode skus=p.putArray("skus");
  skus.addObject().put("specification","500g").put("price","19.90").put("onHand",12).put("status",1);
  skus.addObject().put("specification","1kg").put("price","39.90").put("onHand",20).put("status",1);
  return p;
 }
 JsonNode product(boolean publish)throws Exception {
  long image=upload(picture("png"),"image/png",200).path("id").asLong();
  JsonNode p=call("POST","/api/v1/admin/products",payload(leaf,List.of(image)),admin,200);
  return publish?call("PATCH","/api/v1/admin/products/"+p.path("id").asLong()+"/status",Map.of("status",1,"version",p.path("version").asLong()),admin,200):p;
 }
 ObjectNode edit(JsonNode detail) {
  ObjectNode p=payload(detail.path("categoryId").asLong(),List.of());p.put("name",detail.path("name").asText());p.put("version",detail.path("version").asLong());
  ArrayNode im=p.putArray("imageIds");detail.path("images").forEach(i->im.add(i.path("id").asLong()));
  ArrayNode sk=p.putArray("skus");detail.path("skus").forEach(s->{ObjectNode out=sk.addObject();for(String field:List.of("id","specification","price","status"))out.set(field,s.get(field));});
  return p;
 }
 String productPath(JsonNode p){return "/api/v1/admin/products/"+p.path("id").asLong();}
 String publicPath(JsonNode p){return "/api/v1/public/products/"+p.path("id").asLong();}
 long scalar(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
 @Test void categoriesValidateHierarchyNamesAndReferences()throws Exception {
  call("POST","/api/v1/admin/categories",Map.of("name","third","parentId",leaf,"status",1,"sortOrder",0),admin,400);
  Map<String,Object> duplicate=new HashMap<>();duplicate.put("name","根"+label);duplicate.put("parentId",null);duplicate.put("status",1);duplicate.put("sortOrder",0);
  call("POST","/api/v1/admin/categories",duplicate,admin,409);
  call("PUT","/api/v1/admin/categories/"+root,Map.of("name","self","parentId",root,"status",1,"sortOrder",0),admin,400);
  long another=category(null,"另根"+label);
  call("PUT","/api/v1/admin/categories/"+root,Map.of("name","move","parentId",another,"status",1,"sortOrder",0),admin,409);
  call("DELETE","/api/v1/admin/categories/"+root,null,admin,409);
  JsonNode p=product(false);Map<String,Object> promote=new HashMap<>(duplicate);promote.put("name","提升");call("PUT","/api/v1/admin/categories/"+leaf,promote,admin,409);
  call("DELETE",productPath(p)+"?version="+p.path("version").asLong(),null,admin,200);
  call("DELETE","/api/v1/admin/categories/"+leaf,null,admin,409);
  long free=category(root,"可删除"+label);call("DELETE","/api/v1/admin/categories/"+free,null,admin,200);
 }
 @Test void multiSkuSearchUsesInclusivePriceAndLiteralKeyword()throws Exception {
  JsonNode p=product(true);
  JsonNode result=call("GET","/api/v1/public/products?categoryId="+root+"&keyword="+label+"&minPrice=30.00&maxPrice=50.00",null,null,200);
  assertEquals(1,result.path("total").asLong());assertEquals("19.90",result.path("items").get(0).path("minPrice").asText());
  assertEquals(1,call("GET","/api/v1/public/products?categoryId="+leaf+"&minPrice=39.90&maxPrice=39.90",null,null,200).path("total").asLong());
  assertEquals(0,call("GET","/api/v1/public/products?categoryId="+leaf+"&minPrice=25&maxPrice=30",null,null,200).path("total").asLong());
  ObjectNode e=edit(p);e.put("name","苹果%"+label);call("PUT",productPath(p),e,admin,200);
  assertEquals(1,call("GET","/api/v1/public/products?categoryId="+leaf+"&keyword=%25",null,null,200).path("total").asLong());
  assertEquals(0,call("GET","/api/v1/public/products?categoryId=999999",null,null,200).path("total").asLong());
 }
 @Test void queryValidationAndPaginationDoNotProduceServerErrors()throws Exception {
  for(String query:List.of("page=0","size=101","sort=INVALID","minPrice=20&maxPrice=10","minPrice=-1","maxPrice=1.001","page=hello"))
   call("GET","/api/v1/public/products?"+query,null,null,400);
  product(true);JsonNode empty=call("GET","/api/v1/public/products?categoryId="+leaf+"&page=3&size=1",null,null,200);
  assertEquals(1,empty.path("total").asLong());assertTrue(empty.path("items").isEmpty());
 }
 @Test void productLifecycleHidesDetailsAndPreservesFavorites()throws Exception {
  JsonNode p=product(true);long id=p.path("id").asLong();
  String favorite="/api/v1/buyer/favorites/"+id;call("PUT",favorite,null,buyer,200);
  p=call("PATCH",productPath(p)+"/status",Map.of("status",0,"version",p.path("version").asLong()),admin,200);
  call("GET",publicPath(p),null,null,404);call("GET",publicPath(p)+"/reviews",null,null,404);
  JsonNode f=call("GET","/api/v1/buyer/favorites",null,buyer,200).path("items").get(0);
  assertFalse(f.path("available").asBoolean());assertTrue(f.path("product").isNull());assertEquals("UNLISTED",f.path("unavailableReason").asText());
  p=call("PATCH",productPath(p)+"/status",Map.of("status",1,"version",p.path("version").asLong()),admin,200);
  assertTrue(call("GET","/api/v1/buyer/favorites",null,buyer,200).path("items").get(0).path("available").asBoolean());
  call("DELETE",productPath(p)+"?version="+p.path("version").asLong(),null,admin,200);
  assertEquals("DELETED",call("GET","/api/v1/buyer/favorites",null,buyer,200).path("items").get(0).path("unavailableReason").asText());
  call("DELETE",favorite,null,buyer,200);call("DELETE",favorite,null,buyer,200);
  assertEquals(0,call("GET","/api/v1/buyer/favorites",null,buyer,200).path("total").asLong());
  assertEquals(2,scalar("SELECT COUNT(*) FROM product_sku WHERE product_id=?",id));
 }
 @Test void categoryDisablingIsInheritedAndReversible()throws Exception {
  JsonNode p=product(true);Map<String,Object> body=new HashMap<>();body.put("name","根"+label);body.put("parentId",null);body.put("sortOrder",0);body.put("status",0);
  call("PUT","/api/v1/admin/categories/"+root,body,admin,200);call("GET",publicPath(p),null,null,404);
  assertEquals(1,call("GET",productPath(p),null,admin,200).path("status").asInt());
  body.put("status",1);call("PUT","/api/v1/admin/categories/"+root,body,admin,200);call("GET",publicPath(p),null,null,200);
 }
 @Test void stockVersionsMovementsAndStableSkuIds()throws Exception {
  JsonNode p=product(false);long sku=p.path("skus").get(0).path("id").asLong();
  ObjectNode body=edit(p);((ObjectNode)body.path("skus").get(0)).put("price","20.00");
  JsonNode saved=call("PUT",productPath(p),body,admin,200);
  assertEquals(sku,saved.path("skus").get(0).path("id").asLong());assertEquals(12,saved.path("skus").get(0).path("onHand").asInt());
  long count=scalar("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku);
  call("PUT",productPath(saved),edit(saved),admin,200);assertEquals(count,scalar("SELECT COUNT(*) FROM inventory_movement WHERE sku_id=?",sku));
  ((ObjectNode)body.path("skus").get(0)).put("onHand",20);call("PUT",productPath(saved),body,admin,400);
  call("PUT",productPath(p),edit(p),admin,409);
 }
 @Test void reservedStockRollbackAndWriteProtection()throws Exception {
  JsonNode p=product(false);long sku=p.path("skus").get(0).path("id").asLong();
  db.update("UPDATE sku_inventory SET reserved=4,version=version+1 WHERE sku_id=?",sku);long logsBefore=scalar("SELECT COUNT(*) FROM admin_operation_log");
  ObjectNode bad=edit(p);bad.put("name","不能保存");((ObjectNode)bad.path("skus").get(0)).put("onHand",3);
  call("PUT",productPath(p),bad,admin,400);assertEquals(p.path("name").asText(),call("GET",productPath(p),null,admin,200).path("name").asText());assertEquals(logsBefore+1,scalar("SELECT COUNT(*) FROM admin_operation_log"));
  ((ObjectNode)bad.path("skus").get(0)).remove("onHand");((ObjectNode)bad.path("skus").get(0)).put("reserved",0);call("PUT",productPath(p),bad,admin,400);
  ObjectNode body=edit(p);body.put("description","库存变化后可修改描述");JsonNode saved=call("PUT",productPath(p),body,admin,200);assertEquals(4,saved.path("skus").get(0).path("reserved").asInt());
  call("DELETE",productPath(saved)+"?version="+saved.path("version").asLong(),null,admin,409);
 }
 @Test void retiredSkuCanBeRestoredAndNamesSwappedWithoutNewIds()throws Exception {
  JsonNode p=product(false);ObjectNode body=edit(p);
  ((ObjectNode)body.path("skus").get(0)).put("specification","1kg");((ObjectNode)body.path("skus").get(1)).put("specification","500g");
  JsonNode swapped=call("PUT",productPath(p),body,admin,200);assertEquals(p.path("skus").get(0).path("id"),swapped.path("skus").get(0).path("id"));
  ObjectNode retired=edit(swapped);((ArrayNode)retired.path("skus")).remove(1);
  JsonNode saved=call("PUT",productPath(p),retired,admin,200);assertEquals(2,saved.path("skus").size());assertEquals(0,saved.path("skus").get(1).path("status").asInt());
  ObjectNode restore=edit(saved);((ObjectNode)restore.path("skus").get(1)).put("status",1);call("PUT",productPath(p),restore,admin,200);
 }
 @Test void invalidSkuOwnershipAndPriceAreRejectedAtomically()throws Exception {
  JsonNode p=product(false),other=product(false);ObjectNode body=edit(p);
  ((ObjectNode)body.path("skus").get(0)).put("id",other.path("skus").get(0).path("id").asLong());call("PUT",productPath(p),body,admin,400);
  body=edit(p);((ObjectNode)body.path("skus").get(0)).put("price","0");call("PUT",productPath(p),body,admin,400);
  ((ObjectNode)body.path("skus").get(0)).put("price","1.001");call("PUT",productPath(p),body,admin,400);
  assertEquals(p.path("version"),call("GET",productPath(p),null,admin,200).path("version"));
 }
 @Test void publicationRequiresImagesAndSkusButAllowsSoldOut()throws Exception {
  JsonNode noImage=call("POST","/api/v1/admin/products",payload(leaf,List.of()),admin,200);
  call("PATCH",productPath(noImage)+"/status",Map.of("status",1,"version",0),admin,409);
  long image=upload(picture("png"),"image/png",200).path("id").asLong();ObjectNode soldout=payload(leaf,List.of(image));soldout.path("skus").forEach(sku->((ObjectNode)sku).put("onHand",0));
  JsonNode p=call("POST","/api/v1/admin/products",soldout,admin,200);p=call("PATCH",productPath(p)+"/status",Map.of("status",1,"version",p.path("version").asLong()),admin,200);
  assertTrue(call("GET","/api/v1/public/products?categoryId="+leaf,null,null,200).path("items").get(0).path("soldOut").asBoolean());
  ObjectNode missing=edit(p);missing.putArray("imageIds");call("PUT",productPath(p),missing,admin,409);
  call("PUT","/api/v1/buyer/favorites/"+p.path("id").asLong(),null,buyer,200);
 }
 @Test void imageDecodingOrderingOwnershipAndRetainedSnapshots()throws Exception {
  JsonNode png=upload(picture("png"),"image/png",200);upload(picture("jpg"),"image/jpeg",200);
  byte[] webp=Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
  upload(webp,"image/webp",200);
  upload("not an image".getBytes(),"image/png",400);upload(picture("png"),"image/jpeg",400);upload(new byte[5*1024*1024+1],"image/png",413);
  long first=png.path("id").asLong(),second=upload(picture("png"),"image/png",200).path("id").asLong();
  JsonNode p=call("POST","/api/v1/admin/products",payload(leaf,List.of(first,second)),admin,200);
  p=call("PATCH",productPath(p)+"/status",Map.of("status",1,"version",0),admin,200);call("PUT","/api/v1/buyer/favorites/"+p.path("id").asLong(),null,buyer,200);
  ObjectNode edit=edit(p);edit.putArray("imageIds").add(second);JsonNode saved=call("PUT",productPath(p),edit,admin,200);
  assertEquals(second,saved.path("images").get(0).path("id").asLong());assertEquals(1,saved.path("retainedImages").size());
  mvc.perform(MockMvcRequestBuilders.get(png.path("url").asText())).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  call("POST","/api/v1/admin/products",payload(leaf,List.of(first)),admin,400);
  call("GET","/api/v1/public/product-images/not-a-file.png",null,null,404);
 }
 @Test void favoritesAreIsolatedAndIdempotent()throws Exception {
  JsonNode p=product(true);String path="/api/v1/buyer/favorites/"+p.path("id").asLong();
  call("PUT",path,null,buyer,200);call("PUT",path,null,buyer,200);assertTrue(call("GET",path,null,buyer,200).path("favorited").asBoolean());
  assertFalse(call("GET",path,null,secondBuyer,200).path("favorited").asBoolean());call("DELETE",path,null,secondBuyer,200);
  assertEquals(1,call("GET","/api/v1/buyer/favorites",null,buyer,200).path("total").asLong());
 }
 @Test void reviewSummaryIsPublishedOnlyPrivateAndReadOnly()throws Exception {
  JsonNode p=product(true);long id=p.path("id").asLong(),sku=p.path("skus").get(0).path("id").asLong();
  assertTrue(call("GET",publicPath(p),null,null,200).path("reviewSummary").path("averageRating").isNull());
  long buyerId=scalar("SELECT MAX(id) FROM buyer_account");
  for(int rating:List.of(4,5,1))db.update("INSERT INTO product_review(product_id,sku_id,buyer_id,order_item_id,rating,content,specification_snapshot,status) VALUES(?,?,?,?,?,?,?,?)",
    id,sku,buyerId,Math.abs(UUID.randomUUID().getMostSignificantBits()),rating,"<script>alert(1)</script>","500g",rating==1?0:1);
  JsonNode summary=call("GET",publicPath(p),null,null,200).path("reviewSummary");assertEquals(2,summary.path("count").asLong());assertEquals(4.5,summary.path("averageRating").asDouble());
  JsonNode rows=call("GET",publicPath(p)+"/reviews?size=1",null,null,200);assertEquals(2,rows.path("total").asLong());assertEquals(1,rows.path("items").size());
  JsonNode review=rows.path("items").get(0);assertFalse(review.has("buyerId"));assertFalse(review.has("orderItemId"));assertFalse(review.has("phone"));assertTrue(review.path("content").asText().contains("<script>"));
  call("POST",publicPath(p)+"/reviews",Map.of("rating",5),buyer,405);
 }
 @Test void permissionsSeparateAnonymousBuyersAndAdministrators()throws Exception {
  call("GET","/api/v1/public/products",null,null,200);call("GET","/api/v1/admin/products",null,null,401);call("GET","/api/v1/admin/products",null,buyer,403);
  call("GET","/api/v1/buyer/favorites",null,null,401);call("GET","/api/v1/buyer/favorites",null,admin,403);
  call("POST","/api/v1/admin/products",payload(leaf,List.of()),buyer,403);
 }
 @Test void productStatusRequiresModuleAndSeparateSensitivePermission()throws Exception {
  JsonNode product=product(false);long productId=product.path("id").asLong();
  JsonNode staff=call("POST","/api/v1/admin/staff",Map.of("username","statusstaff"+label,"displayName","上下架店员",
    "password","StaffPassword123!","modulePermissions",List.of(),"sensitivePermissions",List.of()),admin,200);
  long staffId=staff.path("id").asLong();String username=staff.path("username").asText();
  String staffToken=call("POST","/api/v1/admin/auth/login",Map.of("username",username,"password","StaffPassword123!"),null,200).path("accessToken").asText();
  Map<String,Object> statusRequest=Map.of("status",1,"version",product.path("version").asLong());
  call("PATCH",productPath(product)+"/status",statusRequest,staffToken,403);
  call("PUT","/api/v1/admin/staff/"+staffId,Map.of("displayName","上下架店员","modulePermissions",List.of("PRODUCTS"),"sensitivePermissions",List.of()),admin,200);
  staffToken=call("POST","/api/v1/admin/auth/login",Map.of("username",username,"password","StaffPassword123!"),null,200).path("accessToken").asText();
  call("PATCH",productPath(product)+"/status",statusRequest,staffToken,403);
  assertEquals(0,scalar("SELECT status FROM fruit_product WHERE id=?",productId));
  call("PUT","/api/v1/admin/staff/"+staffId,Map.of("displayName","上下架店员","modulePermissions",List.of("PRODUCTS"),"sensitivePermissions",List.of("PRODUCT_STATUS_CHANGE")),admin,200);
  staffToken=call("POST","/api/v1/admin/auth/login",Map.of("username",username,"password","StaffPassword123!"),null,200).path("accessToken").asText();
  call("PATCH",productPath(product)+"/status",statusRequest,staffToken,200);
  assertEquals(2,scalar("SELECT COUNT(*) FROM admin_operation_log WHERE admin_id=? AND result='DENIED'",staffId));
 }
 @Test void directInventoryAdjustmentRequiresSeparatePermissionAndIsAudited()throws Exception {
  JsonNode product=product(false);long skuId=product.path("skus").get(0).path("id").asLong();
  JsonNode staff=call("POST","/api/v1/admin/staff",Map.of("username","inventorystaff"+label,"displayName","库存店员",
    "password","StaffPassword123!","modulePermissions",List.of("INVENTORY"),"sensitivePermissions",List.of()),admin,200);
  long staffId=staff.path("id").asLong();String username=staff.path("username").asText();
  String staffToken=call("POST","/api/v1/admin/auth/login",Map.of("username",username,"password","StaffPassword123!"),null,200).path("accessToken").asText();
  long version=scalar("SELECT version FROM sku_inventory WHERE sku_id=?",skuId);
  long threshold=scalar("SELECT low_stock_threshold FROM sku_inventory WHERE sku_id=?",skuId);
  call("PATCH","/api/v1/admin/inventory/stocks/"+skuId+"/threshold",Map.of("lowStockThreshold",threshold+3,"version",version),staffToken,403);
  assertEquals(threshold,scalar("SELECT low_stock_threshold FROM sku_inventory WHERE sku_id=?",skuId));
  call("PUT","/api/v1/admin/staff/"+staffId,Map.of("displayName","库存店员","modulePermissions",List.of("INVENTORY"),"sensitivePermissions",List.of("INVENTORY_ADJUST")),admin,200);
  staffToken=call("POST","/api/v1/admin/auth/login",Map.of("username",username,"password","StaffPassword123!"),null,200).path("accessToken").asText();
  call("PATCH","/api/v1/admin/inventory/stocks/"+skuId+"/threshold",Map.of("lowStockThreshold",threshold+3,"version",version),staffToken,200);
  assertEquals(threshold+3,scalar("SELECT low_stock_threshold FROM sku_inventory WHERE sku_id=?",skuId));
  assertEquals(1,scalar("SELECT COUNT(*) FROM admin_operation_log WHERE admin_id=? AND result='DENIED'",staffId));
 }
 @Test void recommendationsPrioritizeManualRankAndSupplementWithoutDuplicates()throws Exception {
  db.update("UPDATE fruit_product SET is_recommended=0");
  List<Long> ids=new ArrayList<>();
  for(int i=0;i<9;i++) {
   JsonNode p=product(true);ids.add(p.path("id").asLong());
   if(i<2){ObjectNode body=edit(p);body.put("recommended",true);body.put("recommendSort",i);call("PUT",productPath(p),body,admin,200);}
  }
  JsonNode result=call("GET","/api/v1/public/products/recommendations",null,null,200);assertEquals(8,result.size());
  assertEquals(ids.get(0).longValue(),result.get(0).path("id").asLong());assertEquals(ids.get(1).longValue(),result.get(1).path("id").asLong());
  Set<Long> unique=new HashSet<>();result.forEach(p->unique.add(p.path("id").asLong()));assertEquals(8,unique.size());
 }
 @Test void adminPaginationCombinesFiltersWithoutDuplicatingSkus()throws Exception {
  JsonNode first=product(false),second=product(false),third=product(false);
  ObjectNode recommended=edit(second);recommended.put("recommended",true);
  second=call("PUT",productPath(second),recommended,admin,200);
  String base="/api/v1/admin/products?keyword="+label+"&categoryId="+root;
  JsonNode page=call("GET",base+"&size=1&page=1",null,admin,200);
  JsonNode next=call("GET",base+"&size=1&page=2",null,admin,200);
  assertEquals(3,page.path("total").asLong());assertEquals(3,next.path("total").asLong());
  assertNotEquals(page.path("items").get(0).path("product").path("id"),next.path("items").get(0).path("product").path("id"));
  JsonNode selected=call("GET",base+"&status=0&recommended=true",null,admin,200);
  assertEquals(1,selected.path("total").asLong());assertEquals(second.path("id"),selected.path("items").get(0).path("product").path("id"));
  assertEquals(32,selected.path("items").get(0).path("product").path("availableStock").asInt());
  call("PATCH",productPath(second)+"/status",Map.of("status",1,"version",second.path("version").asLong()),admin,200);
  assertEquals(0,call("GET",base+"&status=0&recommended=true",null,admin,200).path("total").asLong());
  call("DELETE",productPath(third)+"?version="+third.path("version").asLong(),null,admin,200);
  assertEquals(2,call("GET",base,null,admin,200).path("total").asLong());
  assertTrue(call("GET",base+"&size=1&page=5",null,admin,200).path("items").isEmpty());
 }
 @Test void concurrentEditsRejectTheStaleVersion()throws Exception {
  JsonNode p=product(false);ObjectNode a=edit(p),b=edit(p);((ObjectNode)a.path("skus").get(0)).put("price","30.00");((ObjectNode)b.path("skus").get(0)).put("price","40.00");
  ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
  try {
   List<Future<Integer>> responses=new ArrayList<>();
   for(ObjectNode body:List.of(a,b))responses.add(pool.submit(()->{start.await();return mvc.perform(MockMvcRequestBuilders.put(productPath(p)).header("Authorization","Bearer "+admin).contentType("application/json").content(json.writeValueAsBytes(body))).andReturn().getResponse().getStatus();}));
   start.countDown();List<Integer> statuses=new ArrayList<>();for(var f:responses)statuses.add(f.get(20,TimeUnit.SECONDS));
   Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
  }finally{pool.shutdownNow();}
 }
 @Test void concurrentFavoriteRequestsDoNotDuplicate()throws Exception {
  JsonNode p=product(true);ExecutorService pool=Executors.newFixedThreadPool(2);
  try {List<Future<?>> futures=new ArrayList<>();for(int i=0;i<2;i++)futures.add(pool.submit(()->{try{call("PUT","/api/v1/buyer/favorites/"+p.path("id").asLong(),null,buyer,200);}catch(Exception ex){throw new RuntimeException(ex);}}));
   for(var f:futures)f.get(20,TimeUnit.SECONDS);assertEquals(1,scalar("SELECT COUNT(*) FROM user_favorite WHERE product_id=?",p.path("id").asLong()));
  }finally{pool.shutdownNow();}
 }
 @Test void categoryDeletionAndProductCreationCannotLeaveAnOrphan()throws Exception {
  long empty=category(root,"并发分类"+label);
  ObjectNode payload=payload(empty,List.of());ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
  try {
   Future<Integer> deletion=pool.submit(()->{start.await();return mvc.perform(MockMvcRequestBuilders.delete("/api/v1/admin/categories/"+empty).header("Authorization","Bearer "+admin)).andReturn().getResponse().getStatus();});
   Future<Integer> creation=pool.submit(()->{start.await();return mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/products").header("Authorization","Bearer "+admin).contentType("application/json").content(json.writeValueAsBytes(payload))).andReturn().getResponse().getStatus();});
   start.countDown();int deleted=deletion.get(20,TimeUnit.SECONDS),created=creation.get(20,TimeUnit.SECONDS);
   assertTrue((deleted==200 && created==404)||(deleted==409 && created==200),deleted+" / "+created);
   assertEquals(0,scalar("SELECT COUNT(*) FROM fruit_product p LEFT JOIN product_category c ON c.id=p.category_id WHERE c.id IS NULL"));
  }finally{pool.shutdownNow();}
 }
 @Test void foreignAdminImageAndFirstLevelAssignmentAreRejected()throws Exception {
  long image=upload(picture("png"),"image/png",200).path("id").asLong();
  call("POST","/api/v1/admin/products",payload(root,List.of(image)),admin,400);
  String name="otheradmin"+label;
  db.update("INSERT INTO admin_account(username,password_hash) VALUES(?,?)",name,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("OtherAdminPassword123!"));
  String other=call("POST","/api/v1/admin/auth/login",Map.of("username",name,"password","OtherAdminPassword123!"),null,200).path("accessToken").asText();
  call("POST","/api/v1/admin/products",payload(leaf,List.of(image)),other,400);
 }
 @Test void failedAuditPersistenceRollsBackProductAndStock()throws Exception {
  JsonNode p=product(false);ObjectNode body=edit(p);body.put("name","回滚");((ObjectNode)body.path("skus").get(0)).put("price","99.00");
  long stock=scalar("SELECT on_hand FROM sku_inventory WHERE sku_id=?",p.path("skus").get(0).path("id").asLong());
  doThrow(new org.springframework.dao.DataIntegrityViolationException("test audit failure")).when(logs).insert(any(AdminOperationLog.class));
  try{call("PUT",productPath(p),body,admin,409);}finally{reset(logs);}
  JsonNode current=call("GET",productPath(p),null,admin,200);assertEquals(p.path("name"),current.path("name"));assertEquals(stock,current.path("skus").get(0).path("onHand").asLong());
 }
 @Test void failedImageMetadataInsertCleansFileAndCorruptImageIsRejected()throws Exception {
  upload(Arrays.copyOf(picture("png"),12),"image/png",400);
  long filesBefore;
  try(var files=java.nio.file.Files.list(java.nio.file.Path.of(IMAGE_DIR))){filesBefore=files.count();}
  long rowsBefore=scalar("SELECT COUNT(*) FROM product_image");
  doThrow(new org.springframework.dao.DataIntegrityViolationException("test image metadata failure"))
   .when(imageRecords).insert(any(com.fairyshalltear.freshfruit.catalog.entity.ProductImage.class));
  try{upload(picture("png"),"image/png",409);}finally{reset(imageRecords);}
  assertEquals(rowsBefore,scalar("SELECT COUNT(*) FROM product_image"));
  try(var files=java.nio.file.Files.list(java.nio.file.Path.of(IMAGE_DIR))){assertEquals(filesBefore,files.count());}
 }
 @Test void reservedSkuCannotBeRemovedAndLowStockClearsAtThreshold()throws Exception {
  JsonNode p=product(true);long sku=p.path("skus").get(0).path("id").asLong();
  db.update("UPDATE sku_inventory SET reserved=4 WHERE sku_id=?",sku);
  ObjectNode removed=edit(p);((ArrayNode)removed.path("skus")).remove(0);call("PUT",productPath(p),removed,admin,409);
  ObjectNode disabled=edit(p);((ObjectNode)disabled.path("skus").get(0)).put("status",0);call("PUT",productPath(p),disabled,admin,409);
  ObjectNode allDisabled=edit(p);allDisabled.path("skus").forEach(s->((ObjectNode)s).put("status",0));call("PUT",productPath(p),allDisabled,admin,409);
  call("PATCH","/api/v1/admin/inventory/stocks/"+sku+"/threshold",Map.of("lowStockThreshold",9,"version",p.path("skus").get(0).path("inventoryVersion").asLong()),admin,200);
  p=call("GET",productPath(p),null,admin,200);assertTrue(p.path("skus").get(0).path("lowStock").asBoolean());
  call("PATCH","/api/v1/admin/inventory/stocks/"+sku+"/threshold",Map.of("lowStockThreshold",8,"version",p.path("skus").get(0).path("inventoryVersion").asLong()),admin,200);
  p=call("GET",productPath(p),null,admin,200);assertFalse(p.path("skus").get(0).path("lowStock").asBoolean());assertEquals(4,p.path("skus").get(0).path("reserved").asInt());
  long records=scalar("SELECT COUNT(*) FROM admin_operation_log");
  call("PATCH",productPath(p)+"/status",Map.of("status",1,"version",p.path("version").asLong()),admin,200);assertEquals(records+1,scalar("SELECT COUNT(*) FROM admin_operation_log"));
 }
 @Test void productLimitsAndReviewIsolationAreEnforced()throws Exception {
  ObjectNode invalid=payload(leaf,List.of());invalid.put("name","x".repeat(101));call("POST","/api/v1/admin/products",invalid,admin,400);
  invalid=payload(leaf,List.of());invalid.putArray("imageIds");for(int i=1;i<=11;i++)((ArrayNode)invalid.path("imageIds")).add(i);call("POST","/api/v1/admin/products",invalid,admin,400);
  invalid=payload(leaf,List.of());for(int i=0;i<20;i++)((ArrayNode)invalid.path("skus")).add(invalid.path("skus").get(0).deepCopy());call("POST","/api/v1/admin/products",invalid,admin,400);
  JsonNode a=product(true),b=product(true);long owner=scalar("SELECT MAX(id) FROM buyer_account");
  db.update("INSERT INTO product_review(product_id,sku_id,buyer_id,order_item_id,rating,content,specification_snapshot,status) VALUES(?,?,?,?,?,?,?,1)",
   b.path("id").asLong(),b.path("skus").get(0).path("id").asLong(),owner,Math.abs(UUID.randomUUID().getMostSignificantBits()),5,"其他商品评价","500g");
  assertEquals(0,call("GET",publicPath(a)+"/reviews",null,null,200).path("total").asLong());
  assertEquals(0,call("GET",publicPath(a),null,null,200).path("reviewSummary").path("count").asLong());
 }
}
