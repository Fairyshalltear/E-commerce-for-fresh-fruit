package com.fairyshalltear.freshfruit.order.mapper;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.order.entity.PaymentRecord;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
    @Select("SELECT * FROM payment_record WHERE order_id=#{orderId} AND idempotency_key=#{key}") PaymentRecord byRequest(@Param("orderId") Long orderId,@Param("key") String key);
}
