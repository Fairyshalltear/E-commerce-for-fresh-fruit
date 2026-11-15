package com.fairyshalltear.freshfruit.inventory;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.inventory.entity.*;
import com.fairyshalltear.freshfruit.inventory.mapper.*;
import com.fairyshalltear.freshfruit.audit.AuditService;
@Service
public class StockMovementService {
    private final SkuInventoryMapper stocks;
    private final InventoryMovementMapper movements;
    private final AuditService audit;
    public StockMovementService(SkuInventoryMapper stocks,InventoryMovementMapper movements,AuditService audit){this.stocks=stocks;this.movements=movements;this.audit=audit;}
    // Caller holds the SKU inventory lock and the owning business transaction.
    @Transactional(propagation=Propagation.MANDATORY)
    public void change(Long admin,SkuInventory stock,long after,int reserved,String type,String reason,
            String sourceType,Long sourceId,Long sourceItemId,String sourceNo,String name,String specification){
        if(after<reserved||reserved<0||after>Integer.MAX_VALUE)throw new ApiException("INVENTORY_CONFLICT","库存不足、预留异常或数量超出支持范围",HttpStatus.CONFLICT);
        int before=stock.getOnHand();
        stock.setOnHand((int)after);stock.setReserved(reserved);stock.setVersion(stock.getVersion()+1);stock.setUpdatedAt(LocalDateTime.now());stocks.updateById(stock);
        if(before!=after)record(admin,stock.getSkuId(),before,(int)after,type,reason,sourceType,sourceId,sourceItemId,sourceNo,name,specification);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Long admin,Long sku,int before,int after,String type,String reason,
            String sourceType,Long sourceId,Long sourceItemId,String sourceNo,String name,String specification){
        InventoryMovement m=new InventoryMovement();m.setSkuId(sku);m.setAdminId(admin);
        m.setOperationLogId(audit.record(admin,type,"SKU",sku));m.setType(type);m.setBeforeOnHand(before);m.setAfterOnHand(after);
        m.setQuantityDelta(after-before);m.setReason(reason);m.setCreatedAt(LocalDateTime.now());
        m.setSourceType(sourceType);m.setSourceId(sourceId);m.setSourceItemId(sourceItemId);m.setSourceNo(sourceNo);
        m.setProductNameSnapshot(name);m.setSpecificationSnapshot(specification);movements.insert(m);
    }
}
