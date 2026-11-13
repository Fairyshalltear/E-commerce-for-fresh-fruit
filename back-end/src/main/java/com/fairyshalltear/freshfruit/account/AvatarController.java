package com.fairyshalltear.freshfruit.account;

import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

import org.springframework.http.CacheControl;

@RestController
public class AvatarController {
    private final AvatarStorageService avatars;

    public AvatarController(AvatarStorageService avatars) {
        this.avatars = avatars;
    }

    @PostMapping(path = "/api/v1/buyer/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AvatarReference> upload(Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        String avatarUrl = avatars.storeUpload(subjectId(authentication), file);
        return ApiResponse.success(new AvatarReference(avatarUrl));
    }

    @GetMapping("/api/v1/public/avatars/{filename}")
    public ResponseEntity<Resource> get(@PathVariable String filename) {
        Path path = avatars.resolveAvatar(filename);
        String extension = filename.substring(filename.lastIndexOf('.') + 1);
        MediaType contentType = switch (extension) {
            case "jpg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> throw new ApiException("NOT_FOUND", "头像不存在", HttpStatus.NOT_FOUND);
        };
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }

    private Long subjectId(Authentication authentication) {
        return Long.valueOf(((JwtAuthenticationToken) authentication).getToken().getSubject());
    }

    public record AvatarReference(String avatarUrl) {
    }
}
