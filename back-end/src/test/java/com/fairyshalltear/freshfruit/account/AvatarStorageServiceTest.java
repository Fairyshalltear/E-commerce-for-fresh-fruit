package com.fairyshalltear.freshfruit.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;

class AvatarStorageServiceTest {
    @TempDir
    Path storageDirectory;

    private BuyerAccountMapper buyers;
    private AvatarStorageService storage;

    @BeforeEach
    void setUp() {
        buyers = mock(BuyerAccountMapper.class);
        storage = new AvatarStorageService(buyers, storageDirectory.toString());
        storage.createStorageDirectory();
    }

    @Test
    void storesNewAvatarAndRemovesPreviousOwnedFile() throws Exception {
        BuyerAccount buyer = new BuyerAccount();
        buyer.setId(42L);
        buyer.setStatus(1);
        buyer.setAvatarUrl("/api/v1/public/avatars/00000000-0000-0000-0000-000000000001.png");
        Path previousFile = storageDirectory.resolve("00000000-0000-0000-0000-000000000001.png");
        Files.write(previousFile, new byte[] {1});
        when(buyers.selectById(42L)).thenReturn(buyer);
        when(buyers.update(isNull(), any(Wrapper.class))).thenReturn(1);

        String avatarUrl = storage.saveAvatar(42L, new byte[] {2, 3}, "png");

        assertTrue(avatarUrl.matches("/api/v1/public/avatars/[0-9a-f-]{36}\\.png"));
        assertTrue(Files.isRegularFile(storage.resolveAvatar(avatarUrl.substring(avatarUrl.lastIndexOf('/') + 1))));
        assertFalse(Files.exists(previousFile));
    }

    @Test
    void removesNewFileWhenProfileReferenceCannotBeUpdated() throws Exception {
        BuyerAccount buyer = new BuyerAccount();
        buyer.setId(42L);
        buyer.setStatus(1);
        when(buyers.selectById(42L)).thenReturn(buyer);
        when(buyers.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThrows(ApiException.class, () -> storage.saveAvatar(42L, new byte[] {2, 3}, "png"));
        try (var files = Files.list(storageDirectory)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    @Test
    void onlyResolvesGeneratedFilenamesInsideStorageRoot() {
        ApiException exception = assertThrows(ApiException.class, () -> storage.resolveAvatar("../secret.png"));
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.NOT_FOUND, exception.status());
    }
}
