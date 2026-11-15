package com.fairyshalltear.freshfruit.order;

import static com.fairyshalltear.freshfruit.order.OrderTypes.*;
import static com.fairyshalltear.freshfruit.order.OrderDtos.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fairyshalltear.freshfruit.account.entity.UserAddress;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.account.mapper.UserAddressMapper;
import com.fairyshalltear.freshfruit.cart.entity.CartItem;
import com.fairyshalltear.freshfruit.cart.mapper.CartItemMapper;
import com.fairyshalltear.freshfruit.catalog.CatalogDtos.Card;
import com.fairyshalltear.freshfruit.catalog.CatalogService;
import com.fairyshalltear.freshfruit.catalog.entity.FruitProduct;
import com.fairyshalltear.freshfruit.catalog.entity.ProductSku;
import com.fairyshalltear.freshfruit.catalog.mapper.FruitProductMapper;
import com.fairyshalltear.freshfruit.catalog.mapper.ProductSkuMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.inventory.entity.SkuInventory;
import com.fairyshalltear.freshfruit.inventory.mapper.SkuInventoryMapper;
import com.fairyshalltear.freshfruit.order.entity.*;
import com.fairyshalltear.freshfruit.order.mapper.*;
import com.fairyshalltear.freshfruit.review.mapper.ProductReviewMapper;
import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.inventory.entity.InventoryMovement;
import com.fairyshalltear.freshfruit.inventory.mapper.InventoryMovementMapper;

@Service
public class OrderService {
    private static final Logger log=LoggerFactory.getLogger(OrderService.class);
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final CartItemMapper carts; private final UserAddressMapper addresses; private final BuyerAccountMapper buyers; private final ProductSkuMapper skus;
    private final FruitProductMapper products; private final SkuInventoryMapper stocks; private final CatalogService catalog;
    private final ShopOrderMapper orders; private final OrderItemMapper orderItems; private final OrderStatusEventMapper events;
    private final PaymentRecordMapper payments; private final ShipmentMapper shipments; private final TrackingProvider tracking;
    private final ProductReviewMapper reviews; private final AuditService audit; private final InventoryMovementMapper movements;
    private final com.fairyshalltear.freshfruit.inventory.StockMovementService stockChanges;
    private final TransactionTemplate expirationTransaction;

    public OrderService(CartItemMapper carts, UserAddressMapper addresses, BuyerAccountMapper buyers, ProductSkuMapper skus,
            FruitProductMapper products, SkuInventoryMapper stocks, CatalogService catalog, ShopOrderMapper orders,
            OrderItemMapper orderItems, OrderStatusEventMapper events, PaymentRecordMapper payments,
            ShipmentMapper shipments, TrackingProvider tracking, ProductReviewMapper reviews,
            AuditService audit, InventoryMovementMapper movements, PlatformTransactionManager transactionManager, com.fairyshalltear.freshfruit.inventory.StockMovementService stockChanges) {
        this.carts=carts; this.addresses=addresses; this.buyers=buyers; this.skus=skus; this.products=products; this.stocks=stocks;
        this.catalog=catalog; this.orders=orders; this.orderItems=orderItems; this.events=events; this.payments=payments;
        this.shipments=shipments; this.tracking=tracking; this.reviews=reviews;this.audit=audit;this.movements=movements;
        this.stockChanges=stockChanges;
        this.expirationTransaction=new TransactionTemplate(transactionManager);
        this.expirationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly=true)
    public CheckoutPreview preview(Long buyerId, CheckoutRequest request) {
        UserAddress address=requireAddress(buyerId,request.addressId());
        List<Purchase> purchases=resolvePurchases(buyerId,request.items(),false);
        List<CheckoutLine> lines=new ArrayList<>(); BigDecimal subtotal=ZERO; boolean valid=!purchases.isEmpty();
        for(Purchase p:purchases) {
            ProductSku sku=skus.selectById(p.skuId); FruitProduct product=sku==null?null:products.selectById(sku.getProductId());
            Card card=product==null?null:catalog.visibleCards(List.of(product.getId())).stream().findFirst().orElse(null);
            SkuInventory stock=stocks.selectById(p.skuId); int available=stock==null?0:stock.getOnHand()-stock.getReserved();
            String reason=unavailable(sku,product,card,available,p.quantity);
            boolean priceChanged=p.acceptedPrice!=null&&sku!=null&&p.acceptedPrice.compareTo(sku.getPrice())!=0;
            BigDecimal price=sku==null?ZERO:sku.getPrice(); BigDecimal line=price.multiply(BigDecimal.valueOf(p.quantity)).setScale(2);
            if(reason==null&&!priceChanged)subtotal=subtotal.add(line); else valid=false;
            lines.add(new CheckoutLine(p.skuId,product==null?"商品已失效":product.getName(),sku==null?"规格已失效":sku.getSpecification(),
                    card==null?null:card.mainImage(),p.quantity,price,line,available,reason==null&&!priceChanged,reason,priceChanged));
        }
        String message=valid?"商品校验通过，请确认后提交订单":"存在不可购买、库存不足或价格变化的商品";
        return new CheckoutPreview(lines,address.getId(),address.getRecipientName(),address.getPhone(),formatAddress(address),subtotal.setScale(2),ZERO,subtotal.setScale(2),valid,message);
    }

    @Transactional
    public OrderDetail submit(Long buyerId, CheckoutRequest request) {
        BuyerAccount buyer=buyers.selectByIdForUpdate(buyerId);
        if(buyer==null||buyer.getStatus()==null||buyer.getStatus()!=1)throw new ApiException("UNAUTHORIZED","请先登录或重新登录",HttpStatus.UNAUTHORIZED);
        ShopOrder existing=orders.byRequest(buyerId,request.requestKey());
        if(existing!=null)return detail(requireOwned(buyerId,existing.id));
        UserAddress address=requireAddress(buyerId,request.addressId());
        List<Purchase> purchases=new ArrayList<>(resolvePurchases(buyerId,request.items(),true));
        if(purchases.isEmpty())throw bad("CART_SELECTION_REQUIRED","请选择要结算的商品");
        purchases.sort(Comparator.comparing(p->p.skuId));
        Map<Long,ProductSku> skuById=new HashMap<>(); Map<Long,FruitProduct> productById=new HashMap<>(); Map<Long,SkuInventory> stockById=new HashMap<>();
        for(Purchase p:purchases) {
            ProductSku sku=skus.lock(p.skuId); if(sku==null)throw conflict("商品规格已失效");
            SkuInventory stock=stocks.lock(p.skuId); FruitProduct product=products.lock(sku.getProductId());
            Card card=product==null?null:catalog.visibleCards(List.of(product.getId())).stream().findFirst().orElse(null);
            String reason=unavailable(sku,product,card,stock==null?0:stock.getOnHand()-stock.getReserved(),p.quantity);
            if(reason!=null)throw conflict("商品不可购买或库存不足");
            if(p.acceptedPrice!=null&&p.acceptedPrice.compareTo(sku.getPrice())!=0)throw conflict("商品价格已变化，请返回购物车确认新价格");
            skuById.put(sku.getId(),sku);productById.put(product.getId(),product);stockById.put(sku.getId(),stock);
        }
        BigDecimal subtotal=ZERO;
        for(Purchase p:purchases)subtotal=subtotal.add(skuById.get(p.skuId).getPrice().multiply(BigDecimal.valueOf(p.quantity)));
        subtotal=subtotal.setScale(2);
        ShopOrder order=new ShopOrder(); order.orderNo="FF"+System.currentTimeMillis()+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        order.buyerId=buyerId;order.idempotencyKey=request.requestKey();order.status=PENDING_PAYMENT;order.subtotal=subtotal;order.shippingFee=ZERO;order.totalAmount=subtotal;order.refundedAmount=ZERO;
        order.recipientName=address.getRecipientName();order.phone=address.getPhone();order.province=address.getProvince();order.city=address.getCity();order.district=address.getDistrict();order.detailAddress=address.getDetailAddress();order.createdAt=LocalDateTime.now();orders.insert(order);
        for(Purchase p:purchases) {
            ProductSku sku=skuById.get(p.skuId);FruitProduct product=productById.get(sku.getProductId());Card card=catalog.visibleCards(List.of(product.getId())).stream().findFirst().orElse(null);
            OrderItem item=new OrderItem();item.orderId=order.id;item.productId=product.getId();item.skuId=sku.getId();item.productNameSnapshot=product.getName();item.specificationSnapshot=sku.getSpecification();item.imageSnapshot=card==null?null:card.mainImage();item.unitPrice=sku.getPrice();item.quantity=p.quantity;item.shippedQuantity=0;item.refundPendingQuantity=0;item.refundedQuantity=0;item.restockedQuantity=0;item.lineAmount=sku.getPrice().multiply(BigDecimal.valueOf(p.quantity)).setScale(2);orderItems.insert(item);
            SkuInventory stock=stockById.get(sku.getId());stock.setReserved(stock.getReserved()+p.quantity);stock.setVersion(stock.getVersion()+1);stocks.updateById(stock);
        }
        event(order,null,PENDING_PAYMENT,"CREATED","BUYER",buyerId,"买家提交订单");
        for(Purchase p:purchases)if(p.cartItemId!=null)carts.deleteById(p.cartItemId);
        return detail(order);
    }

    @Transactional
    public OrderDetail pay(Long buyerId,Long orderId,PaymentRequest request) {
        ShopOrder order=requireOwnedLocked(buyerId,orderId);
        PaymentRecord prior=payments.byRequest(orderId,request.requestKey());
        if(prior!=null) {
            if(!prior.channel.equals(request.channel())||!prior.result.equals(request.result()))throw conflict("支付请求编号已用于其他结果");
            return detail(order);
        }
        if(!PENDING_PAYMENT.equals(order.status))throw conflict("订单当前状态不可支付");
        PaymentRecord payment=new PaymentRecord();payment.orderId=orderId;payment.idempotencyKey=request.requestKey();payment.channel=request.channel();payment.result=request.result();payment.amount=order.totalAmount;payment.createdAt=LocalDateTime.now();payments.insert(payment);
        if("SUCCESS".equals(request.result())) {order.status=PENDING_SHIPMENT;order.paidAt=LocalDateTime.now();orders.updateById(order);event(order,PENDING_PAYMENT,PENDING_SHIPMENT,"PAYMENT_SUCCEEDED","BUYER",buyerId,"模拟支付成功");}
        return detail(order);
    }

    @Transactional
    public OrderDetail cancel(Long buyerId,Long orderId) {
        ShopOrder order=requireOwnedLocked(buyerId,orderId);
        if(!PENDING_PAYMENT.equals(order.status))throw conflict("仅待付款订单可取消");
        List<OrderItem> lines=orderItems.lockByOrder(orderId); adjustReserved(lines,-1);
        order.status=CANCELLED;order.cancelledAt=LocalDateTime.now();orders.updateById(order);event(order,PENDING_PAYMENT,CANCELLED,"CANCELLED","BUYER",buyerId,"买家取消订单");
        return detail(order);
    }

    @Transactional
    public OrderDetail confirmReceipt(Long buyerId,Long orderId) {
        ShopOrder order=requireOwnedLocked(buyerId,orderId);
        if(!PENDING_RECEIPT.equals(order.status))throw conflict("仅待收货订单可确认收货");
        order.status=COMPLETED;order.completedAt=LocalDateTime.now();orders.updateById(order);event(order,PENDING_RECEIPT,COMPLETED,"RECEIVED","BUYER",buyerId,"买家确认收货");
        return detail(order);
    }

    public int expireUnpaid(int timeoutMinutes) {
        return expireUnpaid(LocalDateTime.now(),timeoutMinutes);
    }

    int expireUnpaid(LocalDateTime now,int timeoutMinutes) {
        LocalDateTime cutoff=now.minusMinutes(timeoutMinutes);
        List<ShopOrder> due=orders.selectList(new QueryWrapper<ShopOrder>().eq("status",PENDING_PAYMENT).le("created_at",cutoff).orderByAsc("created_at","id").last("LIMIT 100"));
        int expired=0;
        for(ShopOrder candidate:due) {
            try {
                Boolean changed=expirationTransaction.execute(status->expireUnpaidOrder(candidate.id,cutoff));
                if(Boolean.TRUE.equals(changed))expired++;
            } catch(RuntimeException exception) {
                log.error("Unable to expire unpaid order {}",candidate.id,exception);
            }
        }
        return expired;
    }

    private boolean expireUnpaidOrder(Long orderId,LocalDateTime cutoff) {
        ShopOrder order=orders.lock(orderId);
        if(order==null||!PENDING_PAYMENT.equals(order.status)||order.createdAt.isAfter(cutoff))return false;
        adjustReserved(orderItems.lockByOrder(order.id),-1);
        order.status=CANCELLED;order.cancelledAt=LocalDateTime.now();orders.updateById(order);
        event(order,PENDING_PAYMENT,CANCELLED,"PAYMENT_TIMEOUT","SYSTEM",null,"未付款超时自动取消");
        return true;
    }

    @Transactional
    public OrderDetail dispatch(Long adminId,Long orderId,String carrier,String trackingNumber) {
        ShopOrder order=orders.lock(orderId);if(order==null)throw notFound();
        if(!PENDING_SHIPMENT.equals(order.status))throw conflict("仅待发货订单可发货");
        List<OrderItem> lines=orderItems.lockByOrder(orderId);
        for(OrderItem item:lines.stream().sorted(Comparator.comparing(i->i.skuId)).toList()) {
            int quantity=item.quantity-item.shippedQuantity-item.refundedQuantity-item.refundPendingQuantity;
            if(quantity<=0)continue;
            SkuInventory stock=stocks.lock(item.skuId);
            if(stock==null||stock.getOnHand()<quantity||stock.getReserved()<quantity)throw conflict("订单库存预留状态异常");
            stockChanges.change(adminId,stock,(long)stock.getOnHand()-quantity,stock.getReserved()-quantity,"ORDER_DISPATCH","订单发货 "+order.orderNo,"ORDER",order.id,item.id,order.orderNo,item.productNameSnapshot,item.specificationSnapshot);
            item.shippedQuantity=item.shippedQuantity+quantity;orderItems.updateById(item);
        }
        Shipment shipment=new Shipment();shipment.orderId=orderId;shipment.carrier=carrier.trim();shipment.trackingNumber=trackingNumber.trim();shipment.createdAt=LocalDateTime.now();shipments.insert(shipment);
        order.status=PENDING_RECEIPT;order.shippedAt=LocalDateTime.now();orders.updateById(order);
        event(order,PENDING_SHIPMENT,PENDING_RECEIPT,"DISPATCHED","ADMIN",adminId,"管理员登记发货");audit.record(adminId,"ORDER_DISPATCH","ORDER",orderId);
        return detail(order);
    }

    @Transactional
    public OrderDetail addTrackingEvent(Long adminId,Long orderId,String description,LocalDateTime occurredAt) {
        ShopOrder order=orders.lock(orderId);if(order==null)throw notFound();
        if(!PENDING_RECEIPT.equals(order.status))throw conflict("仅已发货订单可添加物流节点");
        Shipment shipment=shipments.selectOne(new QueryWrapper<Shipment>().eq("order_id",orderId));if(shipment==null)throw conflict("物流单不存在");
        tracking.appendManualEvent(shipment.id,description.trim(),occurredAt);
        audit.record(adminId,"TRACKING_EVENT_ADD","ORDER",orderId);
        return detail(order);
    }

    @Transactional(readOnly=true)
    public Page<OrderSummary> buyerOrders(Long buyerId,OrderQuery q) {return list(buyerId,q,true);}
    @Transactional(readOnly=true)
    public Page<OrderSummary> adminOrders(OrderQuery q) {return list(null,q,false);}
    @Transactional(readOnly=true)
    public OrderDetail buyerDetail(Long buyerId,Long orderId) {return detail(requireOwned(buyerId,orderId));}
    @Transactional(readOnly=true)
    public OrderDetail adminDetail(Long orderId) {ShopOrder order=orders.selectById(orderId);if(order==null)throw notFound();return detail(order);}

    private Page<OrderSummary> list(Long buyerId,OrderQuery q,boolean own) {
        QueryWrapper<ShopOrder> w=new QueryWrapper<>();if(own)w.eq("buyer_id",buyerId);
        String status=OrderTypes.status(q.status());if(status!=null)w.eq("status",status);
        if(q.from()!=null)w.ge("created_at",q.from());if(q.to()!=null)w.le("created_at",q.to());
        if(q.keyword()!=null&&!q.keyword().isBlank()){String k="%"+q.keyword().trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";w.apply("(order_no LIKE {0} ESCAPE '!' OR buyer_id IN (SELECT id FROM buyer_account WHERE username LIKE {0} ESCAPE '!' OR phone LIKE {0} ESCAPE '!'))",k);}
        long total=orders.selectCount(w);List<ShopOrder> rows=orders.selectList(w.clone().orderByDesc("created_at","id").last("LIMIT "+q.size()+" OFFSET "+((long)(q.page()-1)*q.size())));
        List<OrderSummary> result=rows.stream().map(o->new OrderSummary(o.id,o.orderNo,o.status,orderItems.selectCount(new QueryWrapper<OrderItem>().eq("order_id",o.id)).intValue(),o.totalAmount,o.createdAt)).toList();
        return new Page<>(result,total,q.page(),q.size());
    }

    private OrderDetail detail(ShopOrder order) {
        List<OrderItem> lines=orderItems.selectList(new QueryWrapper<OrderItem>().eq("order_id",order.id).orderByAsc("id"));
        List<OrderItemView> itemViews=lines.stream().map(i->new OrderItemView(i.id,i.productId,i.skuId,i.productNameSnapshot,i.specificationSnapshot,i.imageSnapshot,i.quantity,i.shippedQuantity,i.refundedQuantity,i.unitPrice,i.lineAmount,reviews.selectCount(new QueryWrapper<com.fairyshalltear.freshfruit.review.entity.ProductReview>().eq("order_item_id",i.id).eq("status",1))>0)).toList();
        List<StatusEventView> history=events.byOrder(order.id).stream().map(e->new StatusEventView(e.fromStatus,e.toStatus,e.eventType,e.actorType,e.note,e.createdAt)).toList();
        Shipment shipment=shipments.selectOne(new QueryWrapper<Shipment>().eq("order_id",order.id));
        List<TrackingEventView> trackingEvents=shipment==null?List.of():tracking.eventsForOrder(order.id).stream().map(e->new TrackingEventView(e.description,e.occurredAt)).toList();
        return new OrderDetail(order.id,order.orderNo,order.status,order.recipientName,order.phone,order.province,order.city,order.district,order.detailAddress,order.subtotal,order.shippingFee,order.totalAmount,order.createdAt,order.paidAt,order.shippedAt,order.completedAt,shipment==null?null:shipment.carrier,shipment==null?null:shipment.trackingNumber,itemViews,history,trackingEvents);
    }

    private List<Purchase> resolvePurchases(Long buyerId,List<PurchaseItem> requested,boolean lockCart) {
        if(requested!=null&&!requested.isEmpty()) {
            Map<Long,Integer> quantities=new TreeMap<>();
            for(PurchaseItem item:requested)quantities.merge(item.skuId(),item.quantity(),Integer::sum);
            return quantities.entrySet().stream().map(e->new Purchase(e.getKey(),e.getValue(),null,null)).toList();
        }
        List<CartItem> rows=lockCart?carts.lockAllByBuyer(buyerId):carts.selectList(new LambdaQueryWrapper<CartItem>().eq(CartItem::getBuyerId,buyerId).orderByAsc(CartItem::getSkuId));
        return rows.stream().filter(i->Integer.valueOf(1).equals(i.getSelected())).map(i->new Purchase(i.getSkuId(),i.getQuantity(),i.getAcceptedUnitPrice(),i.getId())).toList();
    }

    private UserAddress requireAddress(Long buyerId,Long addressId) {UserAddress a=addresses.selectById(addressId);if(a==null||!buyerId.equals(a.getBuyerId()))throw notFound();return a;}
    private String formatAddress(UserAddress a) {return a.getProvince()+a.getCity()+a.getDistrict()+a.getDetailAddress();}
    private String unavailable(ProductSku sku,FruitProduct p,Card card,int available,int quantity) {if(sku==null||p==null||p.getDeleted()!=0||p.getStatus()!=1||sku.getStatus()!=1||card==null)return "ITEM_UNAVAILABLE";return available<quantity?"OUT_OF_STOCK":null;}
    private ShopOrder requireOwned(Long buyerId,Long id) {ShopOrder o=orders.owned(id,buyerId);if(o==null)throw notFound();return o;}
    private ShopOrder requireOwnedLocked(Long buyerId,Long id) {ShopOrder o=orders.lock(id);if(o==null||!buyerId.equals(o.buyerId))throw notFound();return o;}
    private void adjustReserved(List<OrderItem> lines,int sign) {List<OrderItem> sorted=lines.stream().sorted(Comparator.comparing(i->i.skuId)).toList();for(OrderItem i:sorted){SkuInventory stock=stocks.lock(i.skuId);int qty=i.quantity-i.shippedQuantity-i.refundedQuantity-i.refundPendingQuantity;if(stock==null||qty<0||stock.getReserved()<qty)throw conflict("订单预留库存状态异常");stock.setReserved(stock.getReserved()+sign*qty);stock.setVersion(stock.getVersion()+1);stocks.updateById(stock);}}
    private void event(ShopOrder order,String from,String to,String type,String actor,Long actorId,String note) {OrderStatusEvent e=new OrderStatusEvent();e.orderId=order.id;e.fromStatus=from;e.toStatus=to;e.eventType=type;e.actorType=actor;e.actorId=actorId;e.note=note;e.createdAt=LocalDateTime.now();events.insert(e);}
    private ApiException bad(String code,String message) {return new ApiException(code,message,HttpStatus.BAD_REQUEST);}
    private ApiException conflict(String message) {return new ApiException("ORDER_CONFLICT",message,HttpStatus.CONFLICT);}
    private ApiException notFound() {return new ApiException("NOT_FOUND","订单或收货地址不存在",HttpStatus.NOT_FOUND);}
    private record Purchase(Long skuId,int quantity,BigDecimal acceptedPrice,Long cartItemId) {}
}
