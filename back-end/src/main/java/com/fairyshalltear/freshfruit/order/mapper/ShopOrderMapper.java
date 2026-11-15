package com.fairyshalltear.freshfruit.order.mapper;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.order.entity.ShopOrder;

@Mapper
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {
    @Select("SELECT * FROM shop_order WHERE id=#{id} FOR UPDATE") ShopOrder lock(@Param("id") Long id);
    @Select("SELECT * FROM shop_order WHERE buyer_id=#{buyerId} AND idempotency_key=#{key}") ShopOrder byRequest(@Param("buyerId") Long buyerId,@Param("key") String key);
    @Select("SELECT * FROM shop_order WHERE id=#{id} AND buyer_id=#{buyerId}") ShopOrder owned(@Param("id") Long id,@Param("buyerId") Long buyerId);
}
