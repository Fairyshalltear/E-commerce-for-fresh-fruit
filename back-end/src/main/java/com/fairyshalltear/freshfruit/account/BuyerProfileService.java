package com.fairyshalltear.freshfruit.account;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fairyshalltear.freshfruit.account.dto.BuyerProfile;
import com.fairyshalltear.freshfruit.account.dto.ChangeBuyerPasswordRequest;
import com.fairyshalltear.freshfruit.account.dto.UpdateBuyerProfileRequest;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;

@Service
public class BuyerProfileService {
    private final BuyerAccountMapper buyers;
    private final PasswordEncoder passwordEncoder;

    public BuyerProfileService(BuyerAccountMapper buyers, PasswordEncoder passwordEncoder) {
        this.buyers = buyers;
        this.passwordEncoder = passwordEncoder;
    }

    public BuyerProfile getProfile(Long buyerId) {
        return toProfile(requireActiveBuyer(buyerId));
    }

    @Transactional
    public BuyerProfile updateProfile(Long buyerId, UpdateBuyerProfileRequest request) {
        BuyerAccount buyer = requireActiveBuyer(buyerId);
        if (request.username() != null) {
            throw new ApiException("VALIDATION_ERROR", "用户名不可修改", HttpStatus.BAD_REQUEST,
                    Map.of("username", "用户名不可修改"));
        }
        if (request.phone() != null && !request.phone().equals(buyer.getPhone())
                && buyers.selectCount(new LambdaQueryWrapper<BuyerAccount>()
                        .eq(BuyerAccount::getPhone, request.phone())
                        .ne(BuyerAccount::getId, buyerId)) > 0) {
            throw phoneConflict();
        }

        if (request.nickname() != null) {
            buyer.setNickname(request.nickname().trim());
        }
        if (request.phone() != null) {
            buyer.setPhone(request.phone());
        }
        try {
            buyers.updateById(buyer);
        } catch (DuplicateKeyException exception) {
            throw phoneConflict();
        }
        return toProfile(buyer);
    }

    @Transactional
    public void changePassword(Long buyerId, ChangeBuyerPasswordRequest request) {
        BuyerAccount buyer = requireActiveBuyer(buyerId);
        validatePasswordLength(request.newPassword());
        if (!passwordEncoder.matches(request.currentPassword(), buyer.getPasswordHash())) {
            throw new ApiException("INVALID_CURRENT_PASSWORD", "当前密码不正确", HttpStatus.BAD_REQUEST,
                    Map.of("currentPassword", "当前密码不正确"));
        }
        buyer.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        buyers.updateById(buyer);
    }

    private BuyerAccount requireActiveBuyer(Long buyerId) {
        BuyerAccount buyer = buyers.selectById(buyerId);
        if (buyer == null || buyer.getStatus() == null || buyer.getStatus() != 1) {
            throw new ApiException("UNAUTHORIZED", "请先登录或重新登录", HttpStatus.UNAUTHORIZED);
        }
        return buyer;
    }

    private BuyerProfile toProfile(BuyerAccount buyer) {
        return new BuyerProfile(buyer.getId(), buyer.getUsername(), buyer.getPhone(),
                buyer.getNickname(), buyer.getAvatarUrl());
    }

    private ApiException phoneConflict() {
        return new ApiException("CONFLICT", "手机号已被使用", HttpStatus.CONFLICT,
                Map.of("phone", "手机号已被使用"));
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException("VALIDATION_ERROR", "密码 UTF-8 编码不能超过 72 字节", HttpStatus.BAD_REQUEST,
                    Map.of("newPassword", "密码 UTF-8 编码不能超过 72 字节"));
        }
    }
}
