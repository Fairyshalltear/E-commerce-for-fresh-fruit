package com.fairyshalltear.freshfruit.review;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.order.entity.OrderItem;
import com.fairyshalltear.freshfruit.order.entity.ShopOrder;
import com.fairyshalltear.freshfruit.order.mapper.OrderItemMapper;
import com.fairyshalltear.freshfruit.order.mapper.ShopOrderMapper;
import com.fairyshalltear.freshfruit.review.ReviewDtos.SubmitRequest;
import com.fairyshalltear.freshfruit.review.mapper.ProductReviewDeletionAuditMapper;
import com.fairyshalltear.freshfruit.review.mapper.ProductReviewMapper;

class ReviewManagementServiceTest {
    @Test
    void locksOrderBeforeOrderItem() {
        ProductReviewMapper reviews=mock(ProductReviewMapper.class);
        ProductReviewDeletionAuditMapper audits=mock(ProductReviewDeletionAuditMapper.class);
        BuyerAccountMapper buyers=mock(BuyerAccountMapper.class);
        ShopOrderMapper orders=mock(ShopOrderMapper.class);
        OrderItemMapper items=mock(OrderItemMapper.class);
        AuditService adminAudit=mock(AuditService.class);

        OrderItem snapshot=new OrderItem();snapshot.id=12L;snapshot.orderId=34L;
        OrderItem lockedItem=new OrderItem();lockedItem.id=12L;lockedItem.orderId=34L;lockedItem.productId=56L;lockedItem.skuId=78L;
        lockedItem.specificationSnapshot="500g";
        ShopOrder order=new ShopOrder();order.id=34L;order.buyerId=90L;order.status="COMPLETED";
        when(items.selectById(12L)).thenReturn(snapshot);
        when(orders.lock(34L)).thenReturn(order);
        when(items.lockOwned(12L,34L)).thenReturn(lockedItem);

        ReviewManagementService service=new ReviewManagementService(reviews,audits,buyers,orders,items,adminAudit,new ObjectMapper());
        service.submit(90L,new SubmitRequest(12L,5,"fresh"));

        InOrder lockOrder=inOrder(orders,items);
        lockOrder.verify(orders).lock(34L);
        lockOrder.verify(items).lockOwned(12L,34L);
    }
}
