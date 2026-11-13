package com.fairyshalltear.freshfruit.catalog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.catalog.entity.ProductImage;
import org.apache.ibatis.annotations.*;
@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImage> {
    @Select("SELECT * FROM product_image WHERE id = #{id} FOR UPDATE")
    ProductImage lock(@Param("id") Long id);
}
