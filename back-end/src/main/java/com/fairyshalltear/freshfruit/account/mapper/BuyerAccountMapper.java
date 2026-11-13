package com.fairyshalltear.freshfruit.account.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;

@Mapper
public interface BuyerAccountMapper extends BaseMapper<BuyerAccount> {
    @Select("SELECT * FROM buyer_account WHERE id = #{id} FOR UPDATE")
    BuyerAccount selectByIdForUpdate(@Param("id") Long id);
}
