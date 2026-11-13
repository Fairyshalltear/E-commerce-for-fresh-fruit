package com.fairyshalltear.freshfruit.catalog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.catalog.entity.UserFavorite;
import org.apache.ibatis.annotations.*;
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
    @Select("SELECT * FROM user_favorite WHERE id = #{id} FOR UPDATE")
    UserFavorite lock(@Param("id") Long id);
}
