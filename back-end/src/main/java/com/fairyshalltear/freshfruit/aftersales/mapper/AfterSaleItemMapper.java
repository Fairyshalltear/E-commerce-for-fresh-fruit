package com.fairyshalltear.freshfruit.aftersales.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.aftersales.entity.AfterSaleItem;

@Mapper
public interface AfterSaleItemMapper extends BaseMapper<AfterSaleItem> {
    @Select("SELECT * FROM after_sale_item WHERE request_id=#{requestId} ORDER BY id FOR UPDATE") List<AfterSaleItem> lockByRequest(@Param("requestId") Long requestId);
    @Select("SELECT COALESCE(SUM(i.quantity),0) FROM after_sale_item i JOIN after_sale_request r ON r.id=i.request_id WHERE i.order_item_id=#{orderItemId} AND r.status IN ('PENDING_REVIEW','WAITING_RETURN','WAITING_RECEIPT','COMPLETED')") int reservedQuantity(@Param("orderItemId") Long orderItemId);
}
