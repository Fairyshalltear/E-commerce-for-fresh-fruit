package com.fairyshalltear.freshfruit.aftersales;

import static com.fairyshalltear.freshfruit.aftersales.AfterSaleDtos.*;
import static com.fairyshalltear.freshfruit.order.OrderTypes.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fairyshalltear.freshfruit.aftersales.entity.*;
import com.fairyshalltear.freshfruit.aftersales.mapper.*;
import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.inventory.entity.*;
import com.fairyshalltear.freshfruit.inventory.mapper.*;
import com.fairyshalltear.freshfruit.order.entity.*;
import com.fairyshalltear.freshfruit.order.mapper.*;

@Service
public class AfterSaleService {
    private static final String PENDING_REVIEW="PENDING_REVIEW",WAITING_RETURN="WAITING_RETURN",WAITING_RECEIPT="WAITING_RECEIPT",COMPLETED="COMPLETED",REJECTED="REJECTED";
    private final AfterSaleRequestMapper requests;private final AfterSaleItemMapper requestItems;private final RefundRecordMapper refunds;
    private final ShopOrderMapper orders;private final OrderItemMapper orderItems;private final SkuInventoryMapper stocks;
    private final InventoryMovementMapper movements;private final AuditService audit;
    private final OrderStatusEventMapper statusEvents;
    private final com.fairyshalltear.freshfruit.inventory.StockMovementService stockChanges;
    public AfterSaleService(AfterSaleRequestMapper requests,AfterSaleItemMapper requestItems,RefundRecordMapper refunds,
            ShopOrderMapper orders,OrderItemMapper orderItems,SkuInventoryMapper stocks,InventoryMovementMapper movements,AuditService audit,OrderStatusEventMapper statusEvents,com.fairyshalltear.freshfruit.inventory.StockMovementService stockChanges){
        this.stockChanges=stockChanges;this.requests=requests;this.requestItems=requestItems;this.refunds=refunds;this.orders=orders;this.orderItems=orderItems;this.stocks=stocks;this.movements=movements;this.audit=audit;this.statusEvents=statusEvents;
    }

    @Transactional
    public RequestView submit(Long buyerId,Long orderId,CreateRequest input){
        ShopOrder order=orders.lock(orderId);if(order==null||!buyerId.equals(order.buyerId))throw missing();
        boolean isRefund="REFUND".equals(input.type());
        if(isRefund&&!PENDING_SHIPMENT.equals(order.status))throw conflict("待发货订单才可申请退款");
        if(!isRefund&&(!COMPLETED.equals(order.status)||order.completedAt==null||order.completedAt.isBefore(LocalDateTime.now().minusDays(7))))throw conflict("仅可在确认收货后 7 天内申请退货退款");
        Map<Long,Integer> requested=new TreeMap<>();for(RequestedItem line:input.items())if(requested.put(line.orderItemId(),line.quantity())!=null)throw bad("售后商品不可重复");
        List<OrderItem> locked=new ArrayList<>();BigDecimal amount=BigDecimal.ZERO;
        for(var entry:requested.entrySet()){
            OrderItem line=orderItems.lockOwned(entry.getKey(),orderId);if(line==null)throw missing();
            int available=line.quantity-line.refundedQuantity-line.refundPendingQuantity;
            if(isRefund)available=Math.min(available,line.quantity-line.shippedQuantity-line.refundedQuantity-line.refundPendingQuantity);
            if(entry.getValue()>available)throw conflict("申请数量超过该商品可申请数量");
            line.refundPendingQuantity+=entry.getValue();orderItems.updateById(line);locked.add(line);
            amount=amount.add(line.unitPrice.multiply(BigDecimal.valueOf(entry.getValue())));
        }
        AfterSaleRequest request=new AfterSaleRequest();request.requestNo="AS"+System.currentTimeMillis()+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);request.orderId=orderId;request.buyerId=buyerId;request.type=input.type();request.status=PENDING_REVIEW;request.reason=input.reason().trim();request.description=input.description()==null?"":input.description().trim();request.requestedAmount=amount.setScale(2);request.createdAt=LocalDateTime.now();request.updatedAt=request.createdAt;requests.insert(request);
        for(OrderItem line:locked){AfterSaleItem ri=new AfterSaleItem();ri.requestId=request.id;ri.orderItemId=line.id;ri.quantity=requested.get(line.id);ri.refundQuantity=0;ri.restockQuantity=0;ri.unitPrice=line.unitPrice;requestItems.insert(ri);}
        return view(request,order);
    }

    @Transactional(readOnly=true)
    public Page<RequestView> buyerList(Long buyerId){List<AfterSaleRequest> rows=requests.selectList(new QueryWrapper<AfterSaleRequest>().eq("buyer_id",buyerId).orderByDesc("created_at","id"));return new Page<>(rows.stream().map(r->view(r,orders.selectById(r.orderId))).toList(),rows.size(),1,rows.size());}
    @Transactional(readOnly=true)
    public RequestView buyerDetail(Long buyerId,Long id){AfterSaleRequest r=requests.owned(id,buyerId);if(r==null)throw missing();return view(r,orders.selectById(r.orderId));}
    @Transactional(readOnly=true)
    public Page<RequestView> adminList(Query q){QueryWrapper<AfterSaleRequest>w=new QueryWrapper<>();if(q.status()!=null)w.eq("status",q.status());if(q.type()!=null)w.eq("type",q.type());long total=requests.selectCount(w);List<AfterSaleRequest>rows=requests.selectList(w.clone().orderByDesc("created_at","id").last("LIMIT "+q.size()+" OFFSET "+((long)(q.page()-1)*q.size())));return new Page<>(rows.stream().map(r->view(r,orders.selectById(r.orderId))).toList(),total,q.page(),q.size());}
    @Transactional(readOnly=true)
    public RequestView adminDetail(Long id){AfterSaleRequest r=requests.selectById(id);if(r==null)throw missing();return view(r,orders.selectById(r.orderId));}

    @Transactional
    public RequestView review(Long adminId,Long id,ReviewRequest input){
        AfterSaleRequest request=requests.lock(id);if(request==null)throw missing();if(!PENDING_REVIEW.equals(request.status))throw conflict("售后申请已处理");
        ShopOrder order=orders.lock(request.orderId);if(order==null)throw missing();
        request.reviewedBy=adminId;request.reviewedAt=LocalDateTime.now();request.reviewReason=input.reason().trim();
        List<AfterSaleItem> lines=requestItems.lockByRequest(id);
        if(!input.approved()){
            restorePending(lines);request.status=REJECTED;request.updatedAt=LocalDateTime.now();requests.updateById(request);audit.record(adminId,"AFTER_SALE_REJECT","AFTER_SALE",id);return view(request,order);
        }
        if("REFUND".equals(request.type)){
            completeRefund(request,order,lines,lines.stream().mapToInt(i->i.quantity).sum(),adminId,true);
            request.status=COMPLETED;
        }else request.status=WAITING_RETURN;
        request.updatedAt=LocalDateTime.now();requests.updateById(request);audit.record(adminId,"AFTER_SALE_APPROVE","AFTER_SALE",id);return view(request,order);
    }

    @Transactional
    public RequestView submitReturnShipment(Long buyerId,Long id,ReturnShipmentRequest input){AfterSaleRequest r=requests.lock(id);if(r==null||!buyerId.equals(r.buyerId))throw missing();if(!WAITING_RETURN.equals(r.status))throw conflict("当前售后申请不可登记退货物流");r.returnCarrier=input.carrier().trim();r.returnTrackingNumber=input.trackingNumber().trim();r.status=WAITING_RECEIPT;r.updatedAt=LocalDateTime.now();requests.updateById(r);return view(r,orders.selectById(r.orderId));}

    @Transactional
    public RequestView receive(Long adminId,Long id,ReceiveRequest input){
        AfterSaleRequest r=requests.lock(id);if(r==null)throw missing();if(!WAITING_RECEIPT.equals(r.status))throw conflict("售后申请尚未等待收货或已处理");
        ShopOrder order=orders.lock(r.orderId);List<AfterSaleItem> lines=requestItems.lockByRequest(id);
        Map<Long,ReceivedItem> received=new HashMap<>();for(ReceivedItem i:input.items())if(received.put(i.orderItemId(),i)!=null)throw bad("退货明细不可重复");
        BigDecimal amount=BigDecimal.ZERO;int totalRefund=0;
        for(AfterSaleItem line:lines){ReceivedItem rcv=received.remove(line.orderItemId);if(rcv==null)throw bad("请提交全部退货明细的处理数量");if(rcv.refundQuantity()>line.quantity||rcv.restockQuantity()>line.quantity)throw bad("退款或入库数量超过获批数量");line.refundQuantity=rcv.refundQuantity();line.restockQuantity=rcv.restockQuantity();totalRefund+=line.refundQuantity;amount=amount.add(line.unitPrice.multiply(BigDecimal.valueOf(line.refundQuantity)));}
        if(!received.isEmpty()||totalRefund<1)throw bad("退货退款数量无效");
        completeRefund(r,order,lines,totalRefund,adminId,false,amount);
        Map<Long,Long> skuByLine=new HashMap<>();for(AfterSaleItem line:lines)skuByLine.put(line.orderItemId,orderItems.selectById(line.orderItemId).skuId);
        for(AfterSaleItem line:lines.stream().sorted(Comparator.comparing(i->skuByLine.get(i.orderItemId))).toList()){requestItems.updateById(line);if(line.restockQuantity>0){OrderItem orderLine=orderItems.lockOwned(line.orderItemId,r.orderId);SkuInventory stock=stocks.lock(orderLine.skuId);stockChanges.change(adminId,stock,(long)stock.getOnHand()+line.restockQuantity,stock.getReserved(),"RETURN_RESTOCK","退货入库 "+r.requestNo,"AFTER_SALE",r.id,line.id,r.requestNo,orderLine.productNameSnapshot,orderLine.specificationSnapshot);orderLine.restockedQuantity+=line.restockQuantity;orderItems.updateById(orderLine);}}
        r.status=COMPLETED;r.receivedAt=LocalDateTime.now();r.updatedAt=r.receivedAt;requests.updateById(r);audit.record(adminId,"AFTER_SALE_RECEIVE","AFTER_SALE",id);return view(r,order);
    }

    private void completeRefund(AfterSaleRequest request,ShopOrder order,List<AfterSaleItem> lines,int quantity,Long adminId,boolean releaseReserved){completeRefund(request,order,lines,quantity,adminId,releaseReserved,request.requestedAmount);}
    private void completeRefund(AfterSaleRequest request,ShopOrder order,List<AfterSaleItem> lines,int quantity,Long adminId,boolean releaseReserved,BigDecimal amount){
        for(AfterSaleItem ri:lines){OrderItem line=orderItems.lockOwned(ri.orderItemId,request.orderId);int refundQty="REFUND".equals(request.type)?ri.quantity:ri.refundQuantity;line.refundPendingQuantity-=ri.quantity;line.refundedQuantity+=refundQty;orderItems.updateById(line);ri.refundQuantity=refundQty;requestItems.updateById(ri);
            if(releaseReserved&&refundQty>0){SkuInventory stock=stocks.lock(line.skuId);if(stock==null||stock.getReserved()<refundQty)throw conflict("订单预留库存状态异常");stock.setReserved(stock.getReserved()-refundQty);stock.setVersion(stock.getVersion()+1);stocks.updateById(stock);}}
        order.refundedAmount=order.refundedAmount.add(amount).setScale(2);orders.updateById(order);
        RefundRecord record=new RefundRecord();record.requestId=request.id;record.orderId=request.orderId;record.amount=amount.setScale(2);record.result="SIMULATED_SUCCESS";record.createdAt=LocalDateTime.now();refunds.insert(record);
        if(releaseReserved&&PENDING_SHIPMENT.equals(order.status)){
            boolean fulfilled=orderItems.selectList(new QueryWrapper<OrderItem>().eq("order_id",order.id)).stream().allMatch(i->i.quantity-i.shippedQuantity-i.refundedQuantity-i.refundPendingQuantity==0);
            if(fulfilled){order.status=CANCELLED;order.cancelledAt=LocalDateTime.now();orders.updateById(order);com.fairyshalltear.freshfruit.order.entity.OrderStatusEvent event=new com.fairyshalltear.freshfruit.order.entity.OrderStatusEvent();event.orderId=order.id;event.fromStatus=PENDING_SHIPMENT;event.toStatus=CANCELLED;event.eventType="REFUNDED";event.actorType="ADMIN";event.actorId=adminId;event.note="未发货退款完成";event.createdAt=LocalDateTime.now();statusEvents.insert(event);}
        }
        audit.record(adminId,"SIMULATED_REFUND","ORDER",order.id);
    }
    private void restorePending(List<AfterSaleItem> lines){for(AfterSaleItem ri:lines){OrderItem line=orderItems.lockById(ri.orderItemId);line.refundPendingQuantity-=ri.quantity;orderItems.updateById(line);}}
    private RequestView view(AfterSaleRequest r,ShopOrder order){List<ItemView> lines=requestItems.selectList(new QueryWrapper<AfterSaleItem>().eq("request_id",r.id).orderByAsc("id")).stream().map(i->{OrderItem oi=orderItems.selectById(i.orderItemId);return new ItemView(i.orderItemId,oi==null?"商品已失效":oi.productNameSnapshot,oi==null?"":oi.specificationSnapshot,i.quantity,i.refundQuantity,i.restockQuantity,i.unitPrice);}).toList();return new RequestView(r.id,r.requestNo,r.orderId,order==null?"":order.orderNo,r.type,r.status,r.reason,r.description,r.requestedAmount,r.reviewReason,r.returnCarrier,r.returnTrackingNumber,r.createdAt,r.reviewedAt,r.receivedAt,lines);}
    private ApiException missing(){return new ApiException("NOT_FOUND","售后申请或订单不存在",HttpStatus.NOT_FOUND);}
    private ApiException conflict(String msg){return new ApiException("AFTER_SALE_CONFLICT",msg,HttpStatus.CONFLICT);}
    private ApiException bad(String msg){return new ApiException("VALIDATION_ERROR",msg,HttpStatus.BAD_REQUEST);}
}
