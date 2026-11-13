package com.fairyshalltear.freshfruit.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_address")
public class UserAddress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long buyerId;
    private String recipientName;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private String addressTag;
    private Integer isDefault;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getDetailAddress() { return detailAddress; }
    public void setDetailAddress(String detailAddress) { this.detailAddress = detailAddress; }
    public String getAddressTag() { return addressTag; }
    public void setAddressTag(String addressTag) { this.addressTag = addressTag; }
    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
}
