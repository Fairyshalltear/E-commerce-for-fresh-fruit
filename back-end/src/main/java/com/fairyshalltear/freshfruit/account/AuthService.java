package com.fairyshalltear.freshfruit.account;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fairyshalltear.freshfruit.account.dto.AccountSummary;
import com.fairyshalltear.freshfruit.account.dto.AdminLoginRequest;
import com.fairyshalltear.freshfruit.account.dto.AuthSession;
import com.fairyshalltear.freshfruit.account.dto.BuyerLoginRequest;
import com.fairyshalltear.freshfruit.account.dto.RegisterRequest;
import com.fairyshalltear.freshfruit.account.entity.AdminAccount;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.entity.BuyerLoginActivity;
import com.fairyshalltear.freshfruit.account.mapper.AdminAccountMapper;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.account.mapper.BuyerLoginActivityMapper;
import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.security.JwtTokenService;

@Service
public class AuthService {
    private final BuyerAccountMapper buyers;
    private final BuyerLoginActivityMapper loginActivities;
    private final AdminAccountMapper admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public AuthService(BuyerAccountMapper buyers, BuyerLoginActivityMapper loginActivities, AdminAccountMapper admins,
            PasswordEncoder passwordEncoder, JwtTokenService tokens, AuditService audit, JdbcTemplate jdbc) {
        this.buyers = buyers;
        this.loginActivities = loginActivities;
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public AuthSession register(RegisterRequest request) {
        validatePasswordLength(request.password());
        if (buyers.selectCount(new LambdaQueryWrapper<BuyerAccount>()
                .eq(BuyerAccount::getUsername, request.username())) > 0) {
            throw conflict("username", "用户名已被使用");
        }
        if (buyers.selectCount(new LambdaQueryWrapper<BuyerAccount>()
                .eq(BuyerAccount::getPhone, request.phone())) > 0) {
            throw conflict("phone", "手机号已被使用");
        }

        BuyerAccount buyer = new BuyerAccount();
        buyer.setUsername(request.username());
        buyer.setPhone(request.phone());
        buyer.setPasswordHash(passwordEncoder.encode(request.password()));
        buyer.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname().trim());
        buyer.setStatus(1);
        try {
            buyers.insert(buyer);
        } catch (DuplicateKeyException exception) {
            throw new ApiException("CONFLICT", "用户名或手机号已被使用", HttpStatus.CONFLICT);
        }
        return tokens.issueBuyer(buyer.getId(), buyer.getUsername(), buyer.getNickname(),
                buyer.getCredentialVersion() == null ? 0 : buyer.getCredentialVersion());
    }

    public AuthSession loginBuyer(BuyerLoginRequest request) {
        validatePasswordLength(request.password());
        BuyerAccount buyer = buyers.selectOne(new LambdaQueryWrapper<BuyerAccount>()
                .eq(BuyerAccount::getUsername, request.usernameOrPhone())
                .or()
                .eq(BuyerAccount::getPhone, request.usernameOrPhone())
                .last("LIMIT 1"));
        if (buyer == null || buyer.getStatus() == null || buyer.getStatus() != 1
                || !passwordEncoder.matches(request.password(), buyer == null ? "" : buyer.getPasswordHash())) {
            throw unauthorized();
        }
        BuyerLoginActivity activity = new BuyerLoginActivity();
        activity.setBuyerId(buyer.getId());
        loginActivities.insert(activity);
        return tokens.issueBuyer(buyer.getId(), buyer.getUsername(), buyer.getNickname(),
                buyer.getCredentialVersion() == null ? 0 : buyer.getCredentialVersion());
    }

    public AuthSession loginAdmin(AdminLoginRequest request) {
        validatePasswordLength(request.password());
        AdminAccount admin = admins.selectOne(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getUsername, request.username()).last("LIMIT 1"));
        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1
                || !passwordEncoder.matches(request.password(), admin == null ? "" : admin.getPasswordHash())) {
            audit.recordAuthentication(admin == null ? null : admin.getId(), request.username(), false,
                    "INVALID_CREDENTIALS");
            throw unauthorized();
        }
        audit.recordAuthentication(admin.getId(), admin.getUsername(), true, null);
        String displayName = admin.getDisplayName() == null ? admin.getUsername() : admin.getDisplayName();
        return tokens.issueAdmin(admin.getId(), admin.getUsername(), displayName, admin.getAccountType(),
                admin.getCredentialVersion() == null ? 0 : admin.getCredentialVersion());
    }

    public AccountSummary buyerSummary(Long id) {
        BuyerAccount buyer = buyers.selectById(id);
        if (buyer == null || buyer.getStatus() == null || buyer.getStatus() != 1) {
            throw unauthorized();
        }
        return new AccountSummary(buyer.getId(), buyer.getUsername(), buyer.getNickname(), "BUYER");
    }

    public AccountSummary adminSummary(Long id) {
        AdminAccount admin = admins.selectById(id);
        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1) {
            throw unauthorized();
        }
        String displayName = admin.getDisplayName() == null ? admin.getUsername() : admin.getDisplayName();
        var modules = jdbc.query("SELECT permission_key FROM admin_permission WHERE admin_id=? AND permission_scope='MODULE' ORDER BY permission_key",
                (rs, row) -> rs.getString(1), id);
        var operations = jdbc.query("SELECT permission_key FROM admin_permission WHERE admin_id=? AND permission_scope='OPERATION' ORDER BY permission_key",
                (rs, row) -> rs.getString(1), id);
        return new AccountSummary(admin.getId(), admin.getUsername(), displayName, admin.getAccountType(), modules, operations);
    }

    private ApiException conflict(String field, String message) {
        return new ApiException("CONFLICT", message, HttpStatus.CONFLICT, Map.of(field, message));
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException("VALIDATION_ERROR", "密码 UTF-8 编码不能超过 72 字节", HttpStatus.BAD_REQUEST,
                    Map.of("password", "密码 UTF-8 编码不能超过 72 字节"));
        }
    }

    private ApiException unauthorized() {
        return new ApiException("INVALID_CREDENTIALS", "账号或密码错误，或账户已停用", HttpStatus.UNAUTHORIZED);
    }
}
