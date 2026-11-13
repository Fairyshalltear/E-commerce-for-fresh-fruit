package com.fairyshalltear.freshfruit.audit.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.audit.entity.AdminOperationLog;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {
    @Select("SELECT * FROM admin_operation_log WHERE id = #{id} FOR UPDATE")
    AdminOperationLog lock(@Param("id") Long id);
}
