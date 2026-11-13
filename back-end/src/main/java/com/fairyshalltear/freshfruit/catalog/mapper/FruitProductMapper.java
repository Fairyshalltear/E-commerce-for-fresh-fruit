package com.fairyshalltear.freshfruit.catalog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.catalog.entity.FruitProduct;
import org.apache.ibatis.annotations.*;
@Mapper
public interface FruitProductMapper extends BaseMapper<FruitProduct> {
    @Select("SELECT * FROM fruit_product WHERE id = #{id} FOR UPDATE")
    FruitProduct lock(@Param("id") Long id);
}
