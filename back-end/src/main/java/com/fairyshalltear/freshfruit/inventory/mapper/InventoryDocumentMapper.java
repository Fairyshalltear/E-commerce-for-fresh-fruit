package com.fairyshalltear.freshfruit.inventory.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.inventory.entity.InventoryDocument;
import org.apache.ibatis.annotations.*;
@Mapper
public interface InventoryDocumentMapper extends BaseMapper<InventoryDocument> {
    @Select("SELECT * FROM inventory_document WHERE id=#{id} FOR UPDATE")
    InventoryDocument lock(@Param("id") Long id);
}
