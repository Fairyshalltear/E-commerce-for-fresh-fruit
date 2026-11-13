package com.fairyshalltear.freshfruit.catalog;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;
import com.fairyshalltear.freshfruit.review.ReviewService;
import static com.fairyshalltear.freshfruit.catalog.CatalogDtos.*;
@RestController
@RequestMapping("/api/v1/public")
@Validated
public class PublicCatalogController {
    private final CatalogService catalog;private final CategoryService categories;private final ReviewService reviews;private final ProductImageService images;
    public PublicCatalogController(CatalogService catalog,CategoryService categories,ReviewService reviews,ProductImageService images) {
        this.catalog=catalog;this.categories=categories;this.reviews=reviews;this.images=images;
    }
    @GetMapping("/categories") public ApiResponse<List<Category>> categories(){return ApiResponse.success(categories.tree(true));}
    @GetMapping("/products") public ApiResponse<Page<Card>> search(@Valid @ModelAttribute Query q){return ApiResponse.success(catalog.search(q));}
    @GetMapping("/products/recommendations") public ApiResponse<List<Card>> recommendations(){return ApiResponse.success(catalog.recommendations());}
    @GetMapping("/products/{id}") public ApiResponse<PublicDetail> detail(@PathVariable @Positive Long id){return ApiResponse.success(catalog.detail(id));}
    @GetMapping("/products/{id}/reviews") public ApiResponse<Page<Review>> reviews(@PathVariable @Positive Long id,@Valid @ModelAttribute Query q) {return ApiResponse.success(reviews.list(id,q));}
    @GetMapping("/product-images/{filename}") public ResponseEntity<Resource> image(@PathVariable String filename) {
        var path=images.read(filename);
        MediaType type=filename.endsWith(".jpg")?MediaType.IMAGE_JPEG:filename.endsWith(".png")?MediaType.IMAGE_PNG:MediaType.parseMediaType("image/webp");
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noCache()).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));
    }
}
