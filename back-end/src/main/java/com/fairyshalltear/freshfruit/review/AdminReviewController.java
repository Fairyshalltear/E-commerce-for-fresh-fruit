package com.fairyshalltear.freshfruit.review;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;
import static com.fairyshalltear.freshfruit.review.ReviewDtos.*;

@RestController @RequestMapping("/api/v1/admin/reviews") @Validated
public class AdminReviewController {
    private final ReviewManagementService reviews;public AdminReviewController(ReviewManagementService reviews){this.reviews=reviews;}
    @GetMapping public ApiResponse<Page<AdminReview>> list(@Valid @ModelAttribute AdminQuery query){return ApiResponse.success(reviews.adminList(query));}
    @PostMapping("/{id}/delete") public ApiResponse<Void> delete(Authentication auth,@PathVariable @Positive Long id,@Valid @RequestBody DeleteRequest request){reviews.delete(subject(auth),id,request.reason());return ApiResponse.success(null);}
    private Long subject(Authentication auth){return Long.valueOf(((JwtAuthenticationToken)auth).getToken().getSubject());}
}
