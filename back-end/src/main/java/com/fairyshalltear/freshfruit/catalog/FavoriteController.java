package com.fairyshalltear.freshfruit.catalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;
import static com.fairyshalltear.freshfruit.catalog.CatalogDtos.*;
@RestController
@RequestMapping("/api/v1/buyer/favorites")
@Validated
public class FavoriteController {
    private final FavoriteService favorites;
    public FavoriteController(FavoriteService favorites){this.favorites=favorites;}
    @GetMapping public ApiResponse<Page<Favorite>> list(JwtAuthenticationToken jwt,@Valid @ModelAttribute Query q) {return ApiResponse.success(favorites.list(subject(jwt),q));}
    @GetMapping("/{id}") public ApiResponse<FavoriteState> state(JwtAuthenticationToken jwt,@PathVariable @Positive Long id){return ApiResponse.success(favorites.state(subject(jwt),id));}
    @PutMapping("/{id}") public ApiResponse<FavoriteState> add(JwtAuthenticationToken jwt,@PathVariable @Positive Long id){return ApiResponse.success(favorites.add(subject(jwt),id));}
    @DeleteMapping("/{id}") public ApiResponse<Void> remove(JwtAuthenticationToken jwt,@PathVariable @Positive Long id){favorites.remove(subject(jwt),id);return ApiResponse.success(null);}
    private Long subject(JwtAuthenticationToken jwt){return Long.valueOf(jwt.getToken().getSubject());}
}
