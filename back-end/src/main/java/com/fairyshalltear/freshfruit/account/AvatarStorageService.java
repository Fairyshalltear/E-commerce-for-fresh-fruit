package com.fairyshalltear.freshfruit.account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;

@Service
public class AvatarStorageService {
    private static final Pattern STORED_FILENAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");
    private static final String PUBLIC_PREFIX = "/api/v1/public/avatars/";
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    private final BuyerAccountMapper buyers;
    private final Path root;

    public AvatarStorageService(BuyerAccountMapper buyers,
            @Value("${freshfruit.avatar.storage-dir:./uploads/avatars}") String storageDirectory) {
        this.buyers = buyers;
        this.root = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void createStorageDirectory() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize avatar storage directory", exception);
        }
    }

    public String storeUpload(Long buyerId, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw invalidAvatar("请选择头像图片");
        }
        if (upload.getSize() > MAX_AVATAR_BYTES) {
            throw new ApiException("PAYLOAD_TOO_LARGE", "头像图片不能超过 5 MiB", HttpStatus.PAYLOAD_TOO_LARGE);
        }

        String extension = switch (upload.getContentType() == null ? "" : upload.getContentType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw invalidAvatar("仅支持 JPEG、PNG 或 WebP 图片");
        };
        byte[] content;
        try {
            content = upload.getBytes();
        } catch (IOException exception) {
            throw invalidAvatar("无法读取头像图片");
        }
        if (!matchesSignature(content, extension)) {
            throw invalidAvatar("头像图片内容与文件类型不匹配");
        }
        return saveAvatar(buyerId, content, extension);
    }

    public String saveAvatar(Long buyerId, byte[] content, String extension) {
        String filename = UUID.randomUUID() + "." + extension;
        Path newFile = resolveGeneratedPath(filename);
        try {
            Files.write(newFile, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            BuyerAccount buyer = buyers.selectById(buyerId);
            if (buyer == null || buyer.getStatus() == null || buyer.getStatus() != 1) {
                throw new ApiException("UNAUTHORIZED", "请先登录或重新登录", HttpStatus.UNAUTHORIZED);
            }
            String oldAvatarUrl = buyer.getAvatarUrl();
            int updated = buyers.update(null, new LambdaUpdateWrapper<BuyerAccount>()
                    .eq(BuyerAccount::getId, buyerId)
                    .eq(BuyerAccount::getStatus, 1)
                    .set(BuyerAccount::getAvatarUrl, PUBLIC_PREFIX + filename));
            if (updated != 1) {
                throw new ApiException("UNAUTHORIZED", "请先登录或重新登录", HttpStatus.UNAUTHORIZED);
            }
            cleanupAfterCompletion(newFile, oldAvatarUrl);
            return PUBLIC_PREFIX + filename;
        } catch (IOException exception) {
            deleteQuietly(newFile);
            throw new ApiException("AVATAR_STORAGE_ERROR", "头像保存失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RuntimeException exception) {
            deleteQuietly(newFile);
            throw exception;
        }
    }

    public Path resolveAvatar(String filename) {
        if (filename == null || !STORED_FILENAME.matcher(filename).matches()) {
            throw notFound();
        }
        Path resolved = resolveGeneratedPath(filename);
        if (!Files.isRegularFile(resolved)) {
            throw notFound();
        }
        return resolved;
    }

    private Path resolveGeneratedPath(String filename) {
        Path resolved = root.resolve(filename).normalize();
        if (!resolved.startsWith(root)) {
            throw notFound();
        }
        return resolved;
    }

    private void cleanupAfterCompletion(Path newFile, String oldAvatarUrl) {
        Runnable cleanupOld = () -> deleteAvatarReference(oldAvatarUrl);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        cleanupOld.run();
                    } else {
                        deleteQuietly(newFile);
                    }
                }
            });
        } else {
            cleanupOld.run();
        }
    }

    private void deleteAvatarReference(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        String filename = avatarUrl.substring(PUBLIC_PREFIX.length());
        if (STORED_FILENAME.matcher(filename).matches()) {
            deleteQuietly(resolveGeneratedPath(filename));
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A stale unreferenced file is safer than failing an otherwise successful profile update.
        }
    }

    private ApiException notFound() {
        return new ApiException("NOT_FOUND", "头像不存在", HttpStatus.NOT_FOUND);
    }

    private ApiException invalidAvatar(String message) {
        return new ApiException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    private boolean matchesSignature(byte[] content, String extension) {
        return switch (extension) {
            case "jpg" -> content.length >= 3 && (content[0] & 0xff) == 0xff
                    && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
            case "png" -> content.length >= 8
                    && content[0] == (byte) 0x89 && content[1] == 0x50 && content[2] == 0x4e
                    && content[3] == 0x47 && content[4] == 0x0d && content[5] == 0x0a
                    && content[6] == 0x1a && content[7] == 0x0a;
            case "webp" -> content.length >= 12 && content[0] == 'R' && content[1] == 'I'
                    && content[2] == 'F' && content[3] == 'F' && content[8] == 'W'
                    && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            default -> false;
        };
    }
}
