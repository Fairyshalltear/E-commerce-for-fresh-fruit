package com.fairyshalltear.freshfruit.account;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fairyshalltear.freshfruit.account.dto.CreateDeliveryAddressRequest;
import com.fairyshalltear.freshfruit.account.dto.DeliveryAddress;
import com.fairyshalltear.freshfruit.account.dto.UpdateDeliveryAddressRequest;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.entity.UserAddress;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.account.mapper.UserAddressMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;

@Service
public class DeliveryAddressService {
    private static final long MAX_ADDRESSES = 5;

    private final BuyerAccountMapper buyers;
    private final UserAddressMapper addresses;

    public DeliveryAddressService(BuyerAccountMapper buyers, UserAddressMapper addresses) {
        this.buyers = buyers;
        this.addresses = addresses;
    }

    public List<DeliveryAddress> list(Long buyerId) {
        return addresses.selectList(new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getBuyerId, buyerId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getId))
                .stream().map(this::toDto).toList();
    }

    public DeliveryAddress get(Long buyerId, Long addressId) {
        return toDto(requireOwnedAddress(buyerId, addressId));
    }

    @Transactional
    public DeliveryAddress create(Long buyerId, CreateDeliveryAddressRequest request) {
        lockActiveBuyer(buyerId);
        long count = addresses.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getBuyerId, buyerId));
        if (count >= MAX_ADDRESSES) {
            throw new ApiException("ADDRESS_LIMIT_REACHED", "每位买家最多可保存 5 个地址", HttpStatus.CONFLICT);
        }
        boolean makeDefault = Boolean.TRUE.equals(request.defaultAddress());
        if (makeDefault) {
            clearDefault(buyerId);
        }
        UserAddress address = new UserAddress();
        address.setBuyerId(buyerId);
        apply(address, request.recipientName(), request.phone(), request.province(), request.city(),
                request.district(), request.detailAddress(), request.addressTag());
        address.setIsDefault(makeDefault ? 1 : 0);
        addresses.insert(address);
        return toDto(address);
    }

    @Transactional
    public DeliveryAddress update(Long buyerId, Long addressId, UpdateDeliveryAddressRequest request) {
        lockActiveBuyer(buyerId);
        UserAddress address = requireOwnedAddress(buyerId, addressId);
        apply(address, request.recipientName(), request.phone(), request.province(), request.city(),
                request.district(), request.detailAddress(), request.addressTag());
        addresses.updateById(address);
        return toDto(address);
    }

    @Transactional
    public void delete(Long buyerId, Long addressId) {
        lockActiveBuyer(buyerId);
        UserAddress address = requireOwnedAddress(buyerId, addressId);
        addresses.deleteById(address.getId());
    }

    @Transactional
    public DeliveryAddress setDefault(Long buyerId, Long addressId) {
        lockActiveBuyer(buyerId);
        UserAddress address = requireOwnedAddress(buyerId, addressId);
        clearDefault(buyerId);
        address.setIsDefault(1);
        addresses.updateById(address);
        return toDto(address);
    }

    private BuyerAccount lockActiveBuyer(Long buyerId) {
        BuyerAccount buyer = buyers.selectByIdForUpdate(buyerId);
        if (buyer == null || buyer.getStatus() == null || buyer.getStatus() != 1) {
            throw new ApiException("UNAUTHORIZED", "请先登录或重新登录", HttpStatus.UNAUTHORIZED);
        }
        return buyer;
    }

    private UserAddress requireOwnedAddress(Long buyerId, Long addressId) {
        UserAddress address = addresses.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getBuyerId, buyerId));
        if (address == null) {
            throw new ApiException("NOT_FOUND", "收货地址不存在", HttpStatus.NOT_FOUND);
        }
        return address;
    }

    private void clearDefault(Long buyerId) {
        addresses.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getBuyerId, buyerId)
                .set(UserAddress::getIsDefault, 0));
    }

    private void apply(UserAddress address, String recipientName, String phone, String province, String city,
            String district, String detailAddress, String addressTag) {
        address.setRecipientName(recipientName.trim());
        address.setPhone(phone.trim());
        address.setProvince(province.trim());
        address.setCity(city.trim());
        address.setDistrict(district.trim());
        address.setDetailAddress(detailAddress.trim());
        address.setAddressTag(addressTag);
    }

    private DeliveryAddress toDto(UserAddress address) {
        return new DeliveryAddress(address.getId(), address.getRecipientName(), address.getPhone(),
                address.getProvince(), address.getCity(), address.getDistrict(), address.getDetailAddress(),
                address.getAddressTag(), Integer.valueOf(1).equals(address.getIsDefault()));
    }
}
