package com.fairyshalltear.freshfruit.inventory;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.fairyshalltear.freshfruit.inventory.entity.*;
import com.fairyshalltear.freshfruit.inventory.mapper.*;
import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.catalog.CatalogDtos.SkuRequest;
import static com.fairyshalltear.freshfruit.catalog.CatalogErrors.*;
@Service
public class InventoryService {
    private final SkuInventoryMapper stocks;private final InventoryMovementMapper movements;private final AuditService audit;
    public InventoryService(SkuInventoryMapper stocks,InventoryMovementMapper movements,AuditService audit) {
        this.stocks=stocks;this.movements=movements;this.audit=audit;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void initialize(Long admin,Long sku,SkuRequest r) {
        SkuInventory s=new SkuInventory();s.setSkuId(sku);s.setOnHand(r.onHand());s.setReserved(0);
        s.setLowStockThreshold(r.lowStockThreshold());s.setVersion(0L);stocks.insert(s);
        if(r.onHand()>0)movement(admin,sku,0,r.onHand(),"INITIAL","新增商品初始库存");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void adjust(Long admin, SkuInventory s,SkuRequest r,String reason) {
        version(r.inventoryVersion(),s.getVersion());
        if(r.onHand()<s.getReserved())throw conflict("实物库存不能低于预留库存");
        if(r.status()==0 && s.getReserved()>0)throw conflict("规格仍有预留库存，不能停用");
        int before=s.getOnHand();
        if(before!=r.onHand() || !s.getLowStockThreshold().equals(r.lowStockThreshold())) {
            s.setOnHand(r.onHand());s.setLowStockThreshold(r.lowStockThreshold());s.setVersion(s.getVersion()+1);
            s.setUpdatedAt(LocalDateTime.now());stocks.updateById(s);
        }
        if(before!=r.onHand())movement(admin,s.getSkuId(),before,r.onHand(),"PRODUCT_EDIT_ADJUSTMENT",
                reason==null||reason.isBlank()?"商品编辑调整":reason.trim());
    }
    private void movement(Long admin,Long sku,int before,int after,String type,String reason) {
        Long log=audit.record(admin,"SKU_STOCK_ADJUSTMENT","SKU",sku);
        InventoryMovement m=new InventoryMovement();m.setSkuId(sku);m.setAdminId(admin);m.setOperationLogId(log);
        m.setType(type);m.setBeforeOnHand(before);m.setAfterOnHand(after);m.setQuantityDelta(after-before);m.setReason(reason);movements.insert(m);
    }
}
