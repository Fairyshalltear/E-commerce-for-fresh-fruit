package com.fairyshalltear.freshfruit.catalog;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="CATALOG_MIGRATION_DB_URL",matches=".+")
class MySqlMigrationTest {
 @Test void upgradePreservesV2DataAndIsRepeatable()throws Exception {
  String url=System.getenv("CATALOG_MIGRATION_DB_URL"),user=System.getenv("CATALOG_TEST_DB_USERNAME"),password=System.getenv("CATALOG_TEST_DB_PASSWORD");
  Flyway.configure().dataSource(url,user,password).target("2").load().migrate();
  try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
   s.executeUpdate("INSERT INTO buyer_account(username,phone,password_hash,nickname) VALUES('migration_buyer','13800000001','hash','升级验证')");
   s.executeUpdate("INSERT INTO user_address(buyer_id,recipient_name,phone,province,city,district,detail_address,address_tag,is_default) VALUES(1,'验证','13800000001','省','市','区','路','HOME',1)");
  }
  var flyway=Flyway.configure().dataSource(url,user,password).load();assertEquals(2,flyway.migrate().migrationsExecuted);assertEquals(0,flyway.migrate().migrationsExecuted);flyway.validate();
  try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
   try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM user_address WHERE buyer_id=1")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
   try(ResultSet r=s.executeQuery("SELECT credential_version FROM buyer_account WHERE id=1")){assertTrue(r.next());assertEquals(0,r.getLong(1));}
   try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='cart_item'")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
   s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('同名根',NULL)");
   assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('同名根',NULL)"));
   assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO product_category(name,parent_id) VALUES('孤儿',99999)"));
  }
 }
}
