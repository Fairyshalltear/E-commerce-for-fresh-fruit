package com.fairyshalltear.freshfruit.inventory.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.inventory.entity.SkuInventory;
import org.apache.ibatis.annotations.*;
@Mapper
public interface SkuInventoryMapper extends BaseMapper<SkuInventory> {
    @Select("SELECT * FROM sku_inventory WHERE sku_id = #{id} FOR UPDATE")
    SkuInventory lock(@Param("id") Long id);
}
