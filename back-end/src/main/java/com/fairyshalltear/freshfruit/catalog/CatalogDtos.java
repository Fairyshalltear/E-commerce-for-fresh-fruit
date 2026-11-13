package com.fairyshalltear.freshfruit.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class CatalogDtos {
    private CatalogDtos() {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record Category(Long id, Long parentId, String name, int sortOrder, int status, List<Category> children) {}
    public record CategoryRequest(@NotBlank @Size(max=50) String name, @Positive Long parentId,
                                  @NotNull @Min(0) Integer sortOrder, @NotNull @Min(0) @Max(1) Integer status) {}
    public record SkuRequest(@Positive Long id, @NotBlank @Size(max=100) String specification,
                            @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal price,
                            @NotNull @Min(0) Integer onHand, @NotNull @Min(0) Integer lowStockThreshold,
                            @NotNull @Min(0) @Max(1) Integer status, @Min(0) Long inventoryVersion) {
        @JsonAnySetter public void unknown(String key, Object value) { throw new IllegalArgumentException("不允许写入字段: " + key); }
    }
    public record ProductRequest(@NotBlank @Size(max=100) String name, @NotNull @Positive Long categoryId,
                                 @Size(max=5000) String description, @Size(max=500) String origin,
                                 @Size(max=500) String taste, @Size(max=500) String storageMethod,
                                 @NotNull Boolean recommended, @NotNull @Min(0) Integer recommendSort,
                                 @NotNull @Size(max=10) List<@NotNull @Positive Long> imageIds,
                                 @NotNull @Size(min=1,max=20) List<@NotNull @Valid SkuRequest> skus,
                                 @Min(0) Long version, @Size(max=200) String adjustmentReason) {
        @JsonAnySetter public void unknown(String key, Object value) { throw new IllegalArgumentException("不允许写入字段: " + key); }
    }
    public record StatusRequest(@NotNull @Min(0) @Max(1) Integer status, @NotNull @Min(0) Long version) {}
    public record Query(@Size(max=100) String keyword, @Positive Long categoryId,
                        @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal minPrice,
                        @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal maxPrice,
                        @Pattern(regexp="NEWEST|PRICE_ASC|PRICE_DESC") String sort,
                        @Min(1) Integer page, @Min(1) @Max(100) Integer size,
                        @Min(0) @Max(1) Integer status, Boolean recommended) {
        public Query { if(page==null)page=1; if(size==null)size=12; if(sort==null)sort="NEWEST"; }
    }
    public record Image(Long id, String url) {}
    public record Card(Long id, String name, Long categoryId, String categoryName, String mainImage,
                       @JsonSerialize(using=ToStringSerializer.class) BigDecimal minPrice,
                       @JsonSerialize(using=ToStringSerializer.class) BigDecimal maxPrice,
                       long availableStock, boolean soldOut) {}
    public record AdminCard(Card product, int status, boolean recommended, int recommendSort, long version, LocalDateTime updatedAt) {}
    public record Sku(Long id, String specification, @JsonSerialize(using=ToStringSerializer.class) BigDecimal price, int availableStock) {}
    public record AdminSku(Long id, String specification, @JsonSerialize(using=ToStringSerializer.class) BigDecimal price,
                           int onHand, int reserved, int availableStock, int lowStockThreshold,
                           boolean lowStock, int status, long inventoryVersion) {}
    public record ReviewSummary(long count, BigDecimal averageRating) {}
    public record PublicDetail(Long id, String name, Long categoryId, List<Category> categoryPath,
                               String description, String origin, String taste, String storageMethod,
                               List<Image> images, List<Sku> skus, ReviewSummary reviewSummary) {}
    public record AdminDetail(Long id, String name, Long categoryId, String description, String origin, String taste,
                              String storageMethod, int status, boolean recommended, int recommendSort,
                              long version, List<Image> images, List<Image> retainedImages, List<AdminSku> skus) {}
    public record Favorite(Long productId, String nameSnapshot, String mainImageSnapshot,
                           boolean available, String unavailableReason, Card product) {}
    public record FavoriteState(boolean favorited) {}
    public record Review(Long id, int rating, String content, String specification, LocalDateTime createdAt, String nickname) {}
}
