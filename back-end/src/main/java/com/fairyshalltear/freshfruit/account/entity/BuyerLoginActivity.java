package com.fairyshalltear.freshfruit.account.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("buyer_login_activity")
public class BuyerLoginActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long buyerId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long value) { buyerId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
