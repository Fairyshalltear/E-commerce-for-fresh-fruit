package com.fairyshalltear.freshfruit.inventory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.fairyshalltear.freshfruit.inventory.entity.SkuInventory;
import com.fairyshalltear.freshfruit.inventory.mapper.SkuInventoryMapper;
import com.fairyshalltear.freshfruit.catalog.CatalogDtos.SkuRequest;
@Service
public class InventoryService {
    private final SkuInventoryMapper stocks;private final StockMovementService movements;
    public InventoryService(SkuInventoryMapper stocks,StockMovementService movements){this.stocks=stocks;this.movements=movements;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void initialize(Long admin,Long sku,SkuRequest r){
        SkuInventory s=new SkuInventory();s.setSkuId(sku);s.setOnHand(r.onHand());s.setReserved(0);s.setLowStockThreshold(0);s.setVersion(0L);stocks.insert(s);
        if(r.onHand()>0)movements.record(admin,sku,0,r.onHand(),"INITIAL","新增商品初始库存",null,null,null,null,null,r.specification());
    }
}
