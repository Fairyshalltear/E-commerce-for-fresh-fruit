package com.fairyshalltear.freshfruit.cart.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.cart.entity.CartItem;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
    @Select("SELECT * FROM cart_item WHERE buyer_id = #{buyerId} AND sku_id = #{skuId} FOR UPDATE")
    CartItem lockByBuyerAndSku(@Param("buyerId") Long buyerId, @Param("skuId") Long skuId);

    @Select("SELECT * FROM cart_item WHERE buyer_id = #{buyerId} AND id = #{itemId} FOR UPDATE")
    CartItem lockOwned(@Param("buyerId") Long buyerId, @Param("itemId") Long itemId);

    @Select("SELECT * FROM cart_item WHERE buyer_id = #{buyerId} ORDER BY id FOR UPDATE")
    List<CartItem> lockAllByBuyer(@Param("buyerId") Long buyerId);
}
