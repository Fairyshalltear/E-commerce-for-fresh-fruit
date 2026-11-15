package com.fairyshalltear.freshfruit.review;

import static com.fairyshalltear.freshfruit.review.ReviewDtos.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.order.entity.OrderItem;
import com.fairyshalltear.freshfruit.order.entity.ShopOrder;
import com.fairyshalltear.freshfruit.order.mapper.OrderItemMapper;
import com.fairyshalltear.freshfruit.order.mapper.ShopOrderMapper;
import com.fairyshalltear.freshfruit.review.entity.*;
import com.fairyshalltear.freshfruit.review.mapper.*;

@Service
public class ReviewManagementService {
    private final ProductReviewMapper reviews;private final ProductReviewDeletionAuditMapper audits;private final BuyerAccountMapper buyers;
    private final ShopOrderMapper orders;private final OrderItemMapper items;private final AuditService adminAudit;private final ObjectMapper json;
    public ReviewManagementService(ProductReviewMapper reviews,ProductReviewDeletionAuditMapper audits,BuyerAccountMapper buyers,
            ShopOrderMapper orders,OrderItemMapper items,AuditService adminAudit,ObjectMapper json){this.reviews=reviews;this.audits=audits;this.buyers=buyers;this.orders=orders;this.items=items;this.adminAudit=adminAudit;this.json=json;}

    @Transactional
    public void submit(Long buyerId,SubmitRequest request){
        OrderItem itemSnapshot=items.selectById(request.orderItemId());if(itemSnapshot==null)throw missing();
        ShopOrder order=orders.lock(itemSnapshot.orderId);if(order==null||!buyerId.equals(order.buyerId))throw missing();
        if(!"COMPLETED".equals(order.status))throw conflict("仅已完成订单可以评价");
        OrderItem item=items.lockOwned(request.orderItemId(),order.id);if(item==null)throw missing();
        if(reviews.selectCount(new QueryWrapper<ProductReview>().eq("order_item_id",item.id))>0)throw conflict("该订单商品已评价");
        ProductReview review=new ProductReview();review.setOrderItemId(item.id);review.setBuyerId(buyerId);review.setProductId(item.productId);review.setSkuId(item.skuId);review.setRating(request.rating());review.setContent(request.content()==null?"":request.content().trim());review.setSpecificationSnapshot(item.specificationSnapshot);review.setStatus(1);review.setCreatedAt(LocalDateTime.now());reviews.insert(review);
    }

    @Transactional(readOnly=true)
    public Page<AdminReview> adminList(AdminQuery q){QueryWrapper<ProductReview>w=new QueryWrapper<>();if(q.deleted()!=null)w.eq("status",q.deleted()?0:1);long total=reviews.selectCount(w);List<ProductReview> rows=reviews.selectList(w.clone().orderByDesc("created_at","id").last("LIMIT "+q.size()+" OFFSET "+((long)(q.page()-1)*q.size())));Map<Long,BuyerAccount> owners=new HashMap<>();if(!rows.isEmpty())buyers.selectBatchIds(rows.stream().map(ProductReview::getBuyerId).distinct().toList()).forEach(b->owners.put(b.getId(),b));return new Page<>(rows.stream().map(r->{BuyerAccount b=owners.get(r.getBuyerId());return new AdminReview(r.getId(),r.getProductId(),r.getSkuId(),r.getOrderItemId(),r.getBuyerId(),b==null?"买家":b.getNickname(),r.getRating(),r.getContent(),r.getSpecificationSnapshot(),r.getCreatedAt(),r.getStatus()!=1,r.getDeletedBy(),r.getDeletionReason(),r.getDeletedAt());}).toList(),total,q.page(),q.size());}

    @Transactional
    public void delete(Long adminId,Long reviewId,String reason){ProductReview review=reviews.lock(reviewId);if(review==null)throw missing();if(review.getStatus()!=1)throw conflict("评价已删除");String normalized=reason.trim();if(normalized.isBlank())throw new ApiException("VALIDATION_ERROR","删除原因不能为空",HttpStatus.BAD_REQUEST);
        String snapshot;try{snapshot=json.writeValueAsString(Map.of("productId",review.getProductId(),"skuId",review.getSkuId(),"buyerId",review.getBuyerId(),"orderItemId",review.getOrderItemId(),"rating",review.getRating(),"content",review.getContent(),"specification",review.getSpecificationSnapshot(),"createdAt",review.getCreatedAt()));}catch(JsonProcessingException e){throw new IllegalStateException("Could not snapshot review",e);}
        ProductReviewDeletionAudit audit=new ProductReviewDeletionAudit();audit.reviewId=reviewId;audit.adminId=adminId;audit.reason=normalized;audit.reviewSnapshot=snapshot;audit.createdAt=LocalDateTime.now();audits.insert(audit);
        review.setStatus(0);review.setDeletedAt(audit.createdAt);review.setDeletedBy(adminId);review.setDeletionReason(normalized);reviews.updateById(review);adminAudit.record(adminId,"REVIEW_DELETE","PRODUCT_REVIEW",reviewId);
    }
    private ApiException missing(){return new ApiException("NOT_FOUND","评价或订单不存在",HttpStatus.NOT_FOUND);}
    private ApiException conflict(String message){return new ApiException("REVIEW_CONFLICT",message,HttpStatus.CONFLICT);}
}
