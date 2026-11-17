package com.fairyshalltear.freshfruit.catalog;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="CATALOG_MIGRATION_DB_URL",matches=".+")
class MySqlMigrationTest {
 @Test void upgradePreservesV2AndV5InventoryAndIsRepeatable()throws Exception {
  String url=System.getenv("CATALOG_MIGRATION_DB_URL"),user=System.getenv("CATALOG_TEST_DB_USERNAME"),password=System.getenv("CATALOG_TEST_DB_PASSWORD");
  Flyway.configure().dataSource(url,user,password).target("2").load().migrate();
  try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
   s.executeUpdate("INSERT INTO buyer_account(username,phone,password_hash,nickname) VALUES('migration_buyer','13800000001','hash','升级验证')");
   s.executeUpdate("INSERT INTO user_address(buyer_id,recipient_name,phone,province,city,district,detail_address,address_tag,is_default) VALUES(1,'验证','13800000001','省','市','区','路','HOME',1)");
  }
  assertEquals(3,Flyway.configure().dataSource(url,user,password).target("5").load().migrate().migrationsExecuted);
  try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
   s.executeUpdate("INSERT INTO admin_account(username,password_hash,display_name) VALUES('migration_admin','hash','旧管理员')");
   s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('根',NULL),('叶',1)");
   s.executeUpdate("INSERT INTO fruit_product(category_id,name) VALUES(2,'历史苹果')");
   s.executeUpdate("INSERT INTO product_sku(product_id,specification,price) VALUES(1,'500g',10)");
   s.executeUpdate("INSERT INTO sku_inventory(sku_id,on_hand,reserved,low_stock_threshold,version) VALUES(1,30,8,5,7)");
   s.executeUpdate("INSERT INTO admin_operation_log(admin_id,operation_type,target_type,target_id,result) VALUES(1,'INITIAL','SKU',1,'SUCCESS')");
   s.executeUpdate("INSERT INTO shop_order(order_no,buyer_id,idempotency_key,status,subtotal,total_amount,recipient_name,phone,province,city,district,detail_address) VALUES('MIG-ORDER',1,'old-1','COMPLETED',50,50,'人','13800000001','省','市','区','路'),('MIG-AMB',1,'old-2','COMPLETED',100,100,'人','13800000001','省','市','区','路')");
   s.executeUpdate("INSERT INTO order_item(order_id,product_id,sku_id,product_name_snapshot,specification_snapshot,unit_price,quantity,shipped_quantity,line_amount) VALUES(1,1,1,'历史苹果','500g',10,5,5,50),(2,1,1,'历史苹果','500g',10,5,5,50),(2,1,1,'历史苹果','500g',10,5,5,50)");
   s.executeUpdate("INSERT INTO after_sale_request(request_no,order_id,buyer_id,type,status,reason,requested_amount) VALUES('MIG-RETURN',1,1,'RETURN','COMPLETED','退货',20)");
   s.executeUpdate("INSERT INTO after_sale_item(request_id,order_item_id,quantity,refund_quantity,restock_quantity,unit_price) VALUES(1,1,2,2,1,10)");
   s.executeUpdate("INSERT INTO inventory_movement(sku_id,operation_log_id,admin_id,type,before_on_hand,after_on_hand,quantity_delta,reason) VALUES(1,1,1,'INITIAL',0,30,30,'初始库存'),(1,1,1,'ORDER_DISPATCH',30,25,-5,'订单发货 MIG-ORDER'),(1,1,1,'RETURN_RESTOCK',25,26,1,'退货入库 MIG-RETURN'),(1,1,1,'ORDER_DISPATCH',26,21,-5,'订单发货 MIG-MISSING'),(1,1,1,'ORDER_DISPATCH',21,16,-5,'订单发货 MIG-AMB')");
  }
  assertEquals(3,Flyway.configure().dataSource(url,user,password).target("8").load().migrate().migrationsExecuted);
  try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
   s.executeUpdate("INSERT INTO admin_account(username,password_hash,display_name,account_type) VALUES('legacy_staff','hash','旧店员','STAFF')");
   s.executeUpdate("INSERT INTO admin_permission(admin_id,permission_scope,permission_key) VALUES(2,'MODULE','PRODUCTS')");
   assertEquals(0,scalar(s,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fruit_product' AND column_name='sweetness_description'"));
  }
  assertEquals(2,Flyway.configure().dataSource(url,user,password).target("10").load().migrate().migrationsExecuted);
  try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
   s.executeUpdate("INSERT INTO ai_model_config(name,chat_model,embedding_model,prompt_template,chat_key_cipher) VALUES('旧模型配置','legacy-chat','legacy-embedding','旧提示词','migration-cipher')");
   s.executeUpdate("INSERT INTO ai_embedding_profile(config_id,model,dimensions,status) VALUES(1,'legacy-embedding',1024,'RETIRED')");
  }
  var flyway=Flyway.configure().dataSource(url,user,password).load();assertEquals(1,flyway.migrate().migrationsExecuted);assertEquals(0,flyway.migrate().migrationsExecuted);flyway.validate();
  try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
   try(ResultSet r=s.executeQuery("SELECT account_type,credential_version FROM admin_account WHERE id=1")){assertTrue(r.next());assertEquals("OWNER",r.getString(1));assertEquals(0,r.getLong(2));}
   assertEquals(1,scalar(s,"SELECT COUNT(*) FROM user_address WHERE buyer_id=1"));assertEquals(0,scalar(s,"SELECT credential_version FROM buyer_account WHERE id=1"));
   assertEquals(30,scalar(s,"SELECT on_hand FROM sku_inventory WHERE sku_id=1"));assertEquals(8,scalar(s,"SELECT reserved FROM sku_inventory WHERE sku_id=1"));assertEquals(7,scalar(s,"SELECT version FROM sku_inventory WHERE sku_id=1"));
   assertEquals(5,scalar(s,"SELECT COUNT(*) FROM inventory_movement"));assertEquals(1,scalar(s,"SELECT COUNT(*) FROM admin_operation_log"));assertEquals(2,scalar(s,"SELECT COUNT(*) FROM shop_order"));assertEquals(1,scalar(s,"SELECT COUNT(*) FROM after_sale_request"));
   assertEquals(2,scalar(s,"SELECT COUNT(*) FROM inventory_movement WHERE source_id=1 AND source_item_id=1"));
   assertEquals(3,scalar(s,"SELECT COUNT(*) FROM inventory_movement WHERE source_id IS NULL"));assertEquals(0,scalar(s,"SELECT COUNT(*) FROM inventory_movement WHERE product_name_snapshot IS NOT NULL"));
   assertEquals(2,scalar(s,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('inventory_document','inventory_document_item')"));
   assertEquals(1,scalar(s,"SELECT COUNT(*) FROM admin_operation_log WHERE id=1 AND operation_type='INITIAL' AND target_id=1"));
   assertEquals(4,scalar(s,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('admin_permission','buyer_login_activity','storefront_carousel','storefront_announcement')"));
   assertTrue(scalar(s,"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name IN ('idx_inventory_movement_time','idx_inventory_movement_source','uk_inventory_document_sku','idx_admin_log_actor_time','idx_admin_log_retention','idx_buyer_login_activity_time_buyer','idx_storefront_carousel_public','idx_storefront_announcement_public','idx_payment_result_time')")>=9);
   assertEquals(1,scalar(s,"SELECT COUNT(*) FROM fruit_product WHERE id=1 AND sweetness_description IS NULL AND seasonal_months IS NULL AND use_cases IS NULL AND is_gift_box=0"));
   assertEquals(1,scalar(s,"SELECT COUNT(*) FROM admin_permission WHERE admin_id=2 AND permission_key='PRODUCTS'"));assertEquals(0,scalar(s,"SELECT COUNT(*) FROM admin_permission WHERE permission_key='AI'"));
   s.executeUpdate("INSERT INTO admin_permission(admin_id,permission_scope,permission_key) VALUES(2,'MODULE','AI')");assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO admin_permission(admin_id,permission_scope,permission_key) VALUES(2,'MODULE','UNKNOWN')"));
   assertEquals(17,scalar(s,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND LEFT(table_name,3)='ai_'"));
   assertEquals(1,scalar(s,"SELECT COUNT(*) FROM ai_model_config WHERE id=1 AND deleted_at IS NULL AND name='旧模型配置' AND chat_model='legacy-chat' AND chat_key_cipher='migration-cipher'"));
   assertEquals(1,scalar(s,"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name='idx_ai_model_visible' AND column_name='deleted_at'"));
   assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM ai_model_config WHERE id=1"));
   s.executeUpdate("UPDATE ai_model_config SET deleted_at=NOW() WHERE id=1");
   assertEquals(0,scalar(s,"SELECT COUNT(*) FROM ai_model_config WHERE deleted_at IS NULL"));
   assertEquals(1,scalar(s,"SELECT COUNT(*) FROM ai_embedding_profile p JOIN ai_model_config m ON m.id=p.config_id WHERE p.id=1 AND m.chat_model='legacy-chat'"));
   s.executeUpdate("INSERT INTO ai_conversation(owner_kind,guest_token_hash,expires_at) VALUES('GUEST',REPEAT('a',64),DATE_ADD(NOW(),INTERVAL 1 DAY))");
   s.executeUpdate("INSERT INTO ai_message(conversation_id,sequence_no,role,content) VALUES(1,1,'USER','问题'),(1,2,'ASSISTANT','回答')");
   String attempt="INSERT INTO ai_request(conversation_id,question_id,answer_id,client_request_key,payload_hash,generation_epoch,lease_until) VALUES(1,1,2,'key',REPEAT('a',64),0,DATE_ADD(NOW(),INTERVAL 30 SECOND))";
   s.executeUpdate(attempt);assertThrows(SQLException.class,()->s.executeUpdate(attempt));
   s.executeUpdate("INSERT INTO ai_feedback(answer_id,helpful) VALUES(2,1)");assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO ai_feedback(answer_id,helpful) VALUES(2,0)"));
   s.executeUpdate("INSERT INTO ai_knowledge_document(type,title,draft_body) VALUES('FAQ','验证','正文')");
   s.executeUpdate("INSERT INTO ai_knowledge_revision(document_id,revision_no,title,body,source_label) VALUES(1,1,'验证','正文','测试')");assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO ai_knowledge_revision(document_id,revision_no,title,body,source_label) VALUES(1,1,'重复','正文','测试')"));
   assertThrows(SQLException.class,()->s.executeUpdate("UPDATE ai_knowledge_document SET active_revision_id=99999 WHERE id=1"));
   s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('同名根',NULL)");assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('同名根',NULL)"));assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('孤儿',99999)"));
  }
 }
 private long scalar(Statement s,String sql)throws SQLException{try(ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}}
}
