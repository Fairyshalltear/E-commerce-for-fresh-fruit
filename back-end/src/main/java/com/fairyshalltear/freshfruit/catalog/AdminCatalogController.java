package com.fairyshalltear.freshfruit.catalog;
import java.util.List;
import java.util.function.Supplier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;
import com.fairyshalltear.freshfruit.common.api.*;
import com.fairyshalltear.freshfruit.security.AdminPermissionService;
import static com.fairyshalltear.freshfruit.catalog.CatalogDtos.*;
@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminCatalogController {
    private static final Logger log=LoggerFactory.getLogger(AdminCatalogController.class);
    private final AdminPermissionService permissions;
    private final ProductService products;private final CatalogService catalog;private final CategoryService categories;private final ProductImageService images;
    public AdminCatalogController(ProductService products,CatalogService catalog,CategoryService categories,ProductImageService images,AdminPermissionService permissions) {
        this.products=products;this.catalog=catalog;this.categories=categories;this.images=images;this.permissions=permissions;
    }
    @GetMapping("/products") public ApiResponse<Page<AdminCard>> list(@Valid @ModelAttribute Query query) {return ApiResponse.success(catalog.adminList(query));}
    @GetMapping("/products/{id}") public ApiResponse<AdminDetail> get(@PathVariable @Positive Long id){return ApiResponse.success(products.detail(id));}
    @PostMapping("/products") public ApiResponse<AdminDetail> create(JwtAuthenticationToken jwt,@Valid @RequestBody ProductRequest r) {
        requireInitialStockPermission(jwt,r);
        return mutate(jwt,"PRODUCT_CREATE",null,()->products.save(subject(jwt),null,r));
    }
    @PutMapping("/products/{id}") public ApiResponse<AdminDetail> update(JwtAuthenticationToken jwt,@PathVariable @Positive Long id,@Valid @RequestBody ProductRequest r) {
        requireInitialStockPermission(jwt,r);
        return mutate(jwt,"PRODUCT_UPDATE",id,()->products.save(subject(jwt),id,r));
    }
    @PatchMapping("/products/{id}/status") public ApiResponse<AdminDetail> status(JwtAuthenticationToken jwt,@PathVariable @Positive Long id,@Valid @RequestBody StatusRequest r) {
        return mutate(jwt,"PRODUCT_STATUS",id,()->products.status(subject(jwt),id,r));
    }
    @DeleteMapping("/products/{id}") public ApiResponse<Void> delete(JwtAuthenticationToken jwt,@PathVariable @Positive Long id,@RequestParam @Min(0) Long version) {
        return mutate(jwt,"PRODUCT_DELETE",id,()->{products.delete(subject(jwt),id,version);return null;});
    }
    @PostMapping("/product-images") public ApiResponse<Image> upload(JwtAuthenticationToken jwt,@RequestParam("file") MultipartFile file) {
        return mutate(jwt,"IMAGE_UPLOAD",null,()->images.upload(subject(jwt),file));
    }
    @GetMapping("/categories") public ApiResponse<List<Category>> tree(){return ApiResponse.success(categories.tree(false));}
    @GetMapping("/categories/{id}") public ApiResponse<Category> category(@PathVariable @Positive Long id){return ApiResponse.success(categories.get(id));}
    @PostMapping("/categories") public ApiResponse<Category> addCategory(JwtAuthenticationToken jwt,@Valid @RequestBody CategoryRequest r) {
        return mutate(jwt,"CATEGORY_CREATE",null,()->categories.save(subject(jwt),null,r));
    }
    @PutMapping("/categories/{id}") public ApiResponse<Category> updateCategory(JwtAuthenticationToken jwt,@PathVariable @Positive Long id,@Valid @RequestBody CategoryRequest r) {
        return mutate(jwt,"CATEGORY_UPDATE",id,()->categories.save(subject(jwt),id,r));
    }
    @DeleteMapping("/categories/{id}") public ApiResponse<Void> deleteCategory(JwtAuthenticationToken jwt,@PathVariable @Positive Long id) {
        return mutate(jwt,"CATEGORY_DELETE",id,()->{categories.delete(subject(jwt),id);return null;});
    }
    private Long subject(JwtAuthenticationToken jwt){return Long.valueOf(jwt.getToken().getSubject());}
    private void requireInitialStockPermission(JwtAuthenticationToken jwt,ProductRequest request){
        if(request.skus().stream().anyMatch(sku->sku.id()==null&&sku.onHand()!=null&&sku.onHand()>0))
            permissions.requireOperation(subject(jwt),"INVENTORY","INVENTORY_ADJUST");
    }
    private <T> ApiResponse<T> mutate(JwtAuthenticationToken jwt,String operation,Long target,Supplier<T> action) {
        try{return ApiResponse.success(action.get());}
        catch(RuntimeException ex){log.warn("catalog operation={} admin={} target={} result=FAILED code={}",operation,subject(jwt),target,
                ex instanceof ApiException api?api.code():ex.getClass().getSimpleName());throw ex;}
    }
}
