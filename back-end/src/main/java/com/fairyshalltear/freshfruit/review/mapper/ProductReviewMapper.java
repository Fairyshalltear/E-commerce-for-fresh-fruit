package com.fairyshalltear.freshfruit.review.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.review.entity.ProductReview;
import org.apache.ibatis.annotations.*;
@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {
    @Select("SELECT * FROM product_review WHERE id = #{id} FOR UPDATE")
    ProductReview lock(@Param("id") Long id);
}
