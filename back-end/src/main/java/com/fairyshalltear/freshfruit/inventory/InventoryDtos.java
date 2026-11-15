package com.fairyshalltear.freshfruit.inventory;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public final class InventoryDtos {
    private InventoryDtos() {}
    public record Page<T>(List<T> items,long total,int page,int size) {}
    public record StockQuery(@Size(max=100) String keyword,@Positive Long categoryId,
        @Min(0) @Max(1) Integer status,@Min(0) @Max(1) Integer skuStatus,
        @Min(0) @Max(1) Integer deleted,@Pattern(regexp="NORMAL|LOW|ZERO") String stockState,
        @Positive Long skuId,@Pattern(regexp="PURCHASE_IN|SUPPLIER_RETURN_OUT|STOCKTAKE") String type,
        @Min(1) Integer page,@Min(1) @Max(100) Integer size) {
        public StockQuery {if(page==null)page=1;if(size==null)size=20;}
    }
    public record Stock(Long skuId,Long productId,String productName,String specification,Long categoryId,
        String categoryName,int productStatus,int skuStatus,int deleted,int onHand,int reserved,
        int availableStock,int lowStockThreshold,boolean lowStock,long inventoryVersion) {}
    public record Summary(long skuCount,long onHand,long reserved,long availableStock,long lowStockCount) {}
    public record Threshold(@NotNull @Min(0) Integer lowStockThreshold,@NotNull @Min(0) Long version) {
        @JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("不允许写入字段: "+key);}
    }
    public record Line(@NotNull @Positive Long skuId,@Min(1) Integer quantity,@Min(0) Integer countedQuantity) {
        @JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("不允许写入字段: "+key);}
    }
    public record Draft(@NotNull @Pattern(regexp="PURCHASE_IN|SUPPLIER_RETURN_OUT|STOCKTAKE") String type,
        @Size(max=100) String supplier,@Size(max=100) String referenceNo,@Size(max=200) String reason,
        @Min(0) Long version,@NotNull @Size(min=1,max=100) List<@NotNull @Valid Line> items) {
        @JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("不允许写入字段: "+key);}
    }
    public record Version(@NotNull @Min(0) Long version) {
        @JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("不允许写入字段: "+key);}
    }
    public record DocumentQuery(@Pattern(regexp="PURCHASE_IN|SUPPLIER_RETURN_OUT|STOCKTAKE") String type,
        @Pattern(regexp="DRAFT|POSTED") String status,@Size(max=100) String keyword,
        LocalDateTime from,LocalDateTime to,@Min(1) Integer page,@Min(1) @Max(100) Integer size) {
        public DocumentQuery {if(page==null)page=1;if(size==null)size=20;}
    }
    public record ItemView(Long id,Long skuId,String productName,String specification,Integer quantity,
        Integer countedQuantity,Integer beforeOnHand,Integer beforeReserved,Integer beforeAvailable,
        Long inventoryVersion,Integer afterOnHand,Integer quantityDelta,Stock currentStock) {}
    public record DocumentView(Long id,String documentNo,String type,String status,String supplier,
        String referenceNo,String reason,long version,Long createdBy,String creatorName,Long postedBy,
        String posterName,LocalDateTime createdAt,LocalDateTime updatedAt,LocalDateTime postedAt,List<ItemView> items) {}
    public record MovementQuery(@Size(max=100) String keyword,@Positive Long skuId,
        @Pattern(regexp="IN|OUT") String direction,
        @Pattern(regexp="INITIAL|PRODUCT_EDIT_ADJUSTMENT|ORDER_DISPATCH|RETURN_RESTOCK|PURCHASE_IN|SUPPLIER_RETURN_OUT|STOCKTAKE_GAIN|STOCKTAKE_LOSS") String type,
        @Size(max=100) String sourceNo,LocalDateTime from,LocalDateTime to,
        @Min(1) Integer page,@Min(1) @Max(100) Integer size) {
        public MovementQuery {if(page==null)page=1;if(size==null)size=20;}
    }
    public record Movement(Long id,Long skuId,String productName,String specification,String type,
        int beforeOnHand,int afterOnHand,int quantityDelta,String reason,Long adminId,String adminName,
        LocalDateTime createdAt,String sourceType,Long sourceId,Long sourceItemId,String sourceNo) {}
}
