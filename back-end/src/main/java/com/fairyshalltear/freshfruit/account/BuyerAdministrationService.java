package com.fairyshalltear.freshfruit.account;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fairyshalltear.freshfruit.account.dto.BuyerAdminDtos.*;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.common.api.ApiException;

@Service
public class BuyerAdministrationService {
    private final BuyerAccountMapper buyers;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public BuyerAdministrationService(BuyerAccountMapper buyers, PasswordEncoder passwords, AuditService audit) {
        this.buyers = buyers;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<BuyerSummary> list(Query query) {
        int page = query.page() == null ? 1 : query.page();
        int size = query.size() == null ? 12 : query.size();
        if (page < 1 || size < 1 || size > 100) throw invalid("分页参数无效");
        if (query.status() != null && query.status() != 0 && query.status() != 1) throw invalid("账户状态无效");
        LambdaQueryWrapper<BuyerAccount> filter = filter(query);
        long total = buyers.selectCount(filter);
        List<BuyerSummary> rows = buyers.selectList(filter(query)
                        .orderByDesc(BuyerAccount::getCreatedAt).orderByDesc(BuyerAccount::getId)
                        .last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)))
                .stream().map(this::summary).toList();
        return new Page<>(rows, total, page, size);
    }

    @Transactional(readOnly = true)
    public BuyerDetail get(Long buyerId) {
        return detail(require(buyerId));
    }

    @Transactional
    public BuyerDetail setStatus(Long adminId, Long buyerId, int status) {
        if (status != 0 && status != 1) throw invalid("账户状态只能为启用或禁用");
        BuyerAccount buyer = requireLocked(buyerId);
        if (buyer.getStatus() != status) {
            buyer.setStatus(status);
            buyer.setCredentialVersion(version(buyer) + 1);
            buyers.updateById(buyer);
            audit.record(adminId, status == 1 ? "BUYER_ENABLE" : "BUYER_DISABLE", "BUYER", buyerId);
        }
        return detail(buyer);
    }

    @Transactional
    public void resetPassword(Long adminId, Long buyerId, String temporaryPassword) {
        validatePassword(temporaryPassword);
        BuyerAccount buyer = requireLocked(buyerId);
        buyer.setPasswordHash(passwords.encode(temporaryPassword));
        buyer.setCredentialVersion(version(buyer) + 1);
        buyers.updateById(buyer);
        audit.record(adminId, "BUYER_PASSWORD_RESET", "BUYER", buyerId);
    }

    private LambdaQueryWrapper<BuyerAccount> filter(Query query) {
        LambdaQueryWrapper<BuyerAccount> filter = new LambdaQueryWrapper<>();
        if (query.status() != null) filter.eq(BuyerAccount::getStatus, query.status());
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = "%" + query.keyword().trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            filter.and(w -> w.apply("username LIKE {0} ESCAPE '!'", keyword)
                    .or().apply("phone LIKE {0} ESCAPE '!'", keyword)
                    .or().apply("nickname LIKE {0} ESCAPE '!'", keyword));
        }
        return filter;
    }

    private BuyerAccount require(Long id) {
        BuyerAccount buyer = buyers.selectById(id);
        if (buyer == null) throw new ApiException("NOT_FOUND", "买家账户不存在", HttpStatus.NOT_FOUND);
        return buyer;
    }

    private BuyerAccount requireLocked(Long id) {
        BuyerAccount buyer = buyers.selectByIdForUpdate(id);
        if (buyer == null) throw new ApiException("NOT_FOUND", "买家账户不存在", HttpStatus.NOT_FOUND);
        return buyer;
    }

    private long version(BuyerAccount buyer) {
        return buyer.getCredentialVersion() == null ? 0 : buyer.getCredentialVersion();
    }

    private BuyerSummary summary(BuyerAccount buyer) {
        return new BuyerSummary(buyer.getId(), buyer.getUsername(), buyer.getPhone(), buyer.getNickname(),
                buyer.getAvatarUrl(), buyer.getStatus(), buyer.getCreatedAt());
    }

    private BuyerDetail detail(BuyerAccount buyer) {
        return new BuyerDetail(buyer.getId(), buyer.getUsername(), buyer.getPhone(), buyer.getNickname(),
                buyer.getAvatarUrl(), buyer.getStatus(), buyer.getCreatedAt());
    }

    private void validatePassword(String password) {
        if (password == null) throw invalid("临时密码不能为空");
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (length < 8 || length > 72 || password.isBlank()) throw invalid("临时密码长度须为 8 至 72 字节");
    }

    private ApiException invalid(String message) {
        return new ApiException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }
}
