package com.fairyshalltear.freshfruit.aftersales.mapper;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.aftersales.entity.AfterSaleRequest;

@Mapper
public interface AfterSaleRequestMapper extends BaseMapper<AfterSaleRequest> {
    @Select("SELECT * FROM after_sale_request WHERE id=#{id} FOR UPDATE") AfterSaleRequest lock(@Param("id") Long id);
    @Select("SELECT * FROM after_sale_request WHERE id=#{id} AND buyer_id=#{buyerId}") AfterSaleRequest owned(@Param("id") Long id,@Param("buyerId") Long buyerId);
}
