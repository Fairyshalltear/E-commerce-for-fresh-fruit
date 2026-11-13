package com.fairyshalltear.freshfruit.catalog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.catalog.entity.ProductCategory;
import org.apache.ibatis.annotations.*;
@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {
    @Select("SELECT * FROM product_category WHERE id = #{id} FOR UPDATE")
    ProductCategory lock(@Param("id") Long id);
    @Select("SELECT * FROM product_category ORDER BY id FOR UPDATE")
    java.util.List<ProductCategory> lockTree();
}
