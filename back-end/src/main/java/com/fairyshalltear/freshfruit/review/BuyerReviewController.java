package com.fairyshalltear.freshfruit.review;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;
import static com.fairyshalltear.freshfruit.review.ReviewDtos.*;

@RestController @RequestMapping("/api/v1/buyer/reviews") @Validated
public class BuyerReviewController {
    private final ReviewManagementService reviews;public BuyerReviewController(ReviewManagementService reviews){this.reviews=reviews;}
    @PostMapping public ApiResponse<Void> submit(Authentication auth,@Valid @RequestBody SubmitRequest request){reviews.submit(subject(auth),request);return ApiResponse.success(null);}
    private Long subject(Authentication auth){return Long.valueOf(((JwtAuthenticationToken)auth).getToken().getSubject());}
}
