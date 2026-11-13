package com.fairyshalltear.freshfruit.inventory.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.inventory.entity.InventoryMovement;
import org.apache.ibatis.annotations.*;
@Mapper
public interface InventoryMovementMapper extends BaseMapper<InventoryMovement> {
    @Select("SELECT * FROM inventory_movement WHERE id = #{id} FOR UPDATE")
    InventoryMovement lock(@Param("id") Long id);
}
