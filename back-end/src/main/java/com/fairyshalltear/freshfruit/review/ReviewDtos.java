package com.fairyshalltear.freshfruit.review;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public final class ReviewDtos {
    private ReviewDtos() {}
    public record SubmitRequest(@NotNull @Positive Long orderItemId,@NotNull @Min(1) @Max(5) Integer rating,@Size(max=2000) String content) {}
    public record DeleteRequest(@NotBlank @Size(max=500) String reason) {}
    public record AdminQuery(@Min(1) Integer page,@Min(1) @Max(100) Integer size,Boolean deleted) {public AdminQuery{if(page==null)page=1;if(size==null)size=20;}}
    public record AdminReview(Long id,Long productId,Long skuId,Long orderItemId,Long buyerId,String buyerNickname,
            Integer rating,String content,String specification,LocalDateTime createdAt,boolean deleted,
            Long deletedBy,String deletionReason,LocalDateTime deletedAt) {}
    public record Page<T>(List<T> items,long total,int page,int size) {}
}
