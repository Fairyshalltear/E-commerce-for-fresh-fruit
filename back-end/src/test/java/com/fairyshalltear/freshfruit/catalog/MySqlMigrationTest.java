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
  var flyway=Flyway.configure().dataSource(url,user,password).load();assertEquals(3,flyway.migrate().migrationsExecuted);assertEquals(0,flyway.migrate().migrationsExecuted);flyway.validate();
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
   s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('同名根',NULL)");assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('同名根',NULL)"));assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('孤儿',99999)"));
  }
 }
 private long scalar(Statement s,String sql)throws SQLException{try(ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}}
}
