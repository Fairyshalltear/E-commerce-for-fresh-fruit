package com.fairyshalltear.freshfruit.catalog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.catalog.entity.ProductSku;
import org.apache.ibatis.annotations.*;
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {
    @Select("SELECT * FROM product_sku WHERE id = #{id} FOR UPDATE")
    ProductSku lock(@Param("id") Long id);
}
