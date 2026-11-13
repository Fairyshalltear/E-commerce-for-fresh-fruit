package com.fairyshalltear.freshfruit.cart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.cart.entity.CartItem;
import com.fairyshalltear.freshfruit.cart.mapper.CartItemMapper;
import com.fairyshalltear.freshfruit.catalog.CatalogService;
import com.fairyshalltear.freshfruit.catalog.CatalogDtos.Card;
import com.fairyshalltear.freshfruit.catalog.entity.FruitProduct;
import com.fairyshalltear.freshfruit.catalog.entity.ProductSku;
import com.fairyshalltear.freshfruit.catalog.mapper.FruitProductMapper;
import com.fairyshalltear.freshfruit.catalog.mapper.ProductSkuMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.inventory.entity.SkuInventory;
import com.fairyshalltear.freshfruit.inventory.mapper.SkuInventoryMapper;

import static com.fairyshalltear.freshfruit.cart.CartDtos.*;

@Service
public class CartService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final BuyerAccountMapper buyers;
    private final CartItemMapper items;
    private final ProductSkuMapper skus;
    private final FruitProductMapper products;
    private final SkuInventoryMapper inventory;
    private final CatalogService catalog;

    public CartService(BuyerAccountMapper buyers, CartItemMapper items, ProductSkuMapper skus,
            FruitProductMapper products, SkuInventoryMapper inventory, CatalogService catalog) {
        this.buyers = buyers;
        this.items = items;
        this.skus = skus;
        this.products = products;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public Cart get(Long buyerId) {
        requireBuyer(buyerId, false);
        return build(items.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getBuyerId, buyerId)
                .orderByDesc(CartItem::getCreatedAt).orderByDesc(CartItem::getId)));
    }

    @Transactional
    public Cart add(Long buyerId, AddRequest request) {
        requireBuyer(buyerId, true);
        ProductSku sku = skus.lock(request.skuId());
        SkuInventory stock = sku == null ? null : inventory.lock(sku.getId());
        FruitProduct product = sku == null ? null : products.lock(sku.getProductId());
        Card visible = product == null ? null : visibleCard(product.getId());
        requirePurchasable(sku, stock, product, visible);

        CartItem existing = items.lockByBuyerAndSku(buyerId, sku.getId());
        int nextQuantity = request.quantity() + (existing == null ? 0 : existing.getQuantity());
        requireStock(nextQuantity, available(stock));
        if (existing == null) {
            CartItem item = new CartItem();
            item.setBuyerId(buyerId);
            item.setSkuId(sku.getId());
            item.setQuantity(request.quantity());
            item.setSelected(1);
            item.setAcceptedUnitPrice(sku.getPrice());
            items.insert(item);
        } else {
            existing.setQuantity(nextQuantity);
            existing.setAcceptedUnitPrice(sku.getPrice());
            items.updateById(existing);
        }
        return get(buyerId);
    }

    @Transactional
    public Cart updateQuantity(Long buyerId, Long itemId, int quantity) {
        requireBuyer(buyerId, true);
        CartItem item = requireOwned(buyerId, itemId);
        ProductSku sku = skus.lock(item.getSkuId());
        SkuInventory stock = sku == null ? null : inventory.lock(sku.getId());
        FruitProduct product = sku == null ? null : products.lock(sku.getProductId());
        Card visible = product == null ? null : visibleCard(product.getId());
        requirePurchasable(sku, stock, product, visible);
        requireStock(quantity, available(stock));
        item.setQuantity(quantity);
        items.updateById(item);
        return get(buyerId);
    }

    @Transactional
    public Cart remove(Long buyerId, Long itemId) {
        requireBuyer(buyerId, true);
        CartItem item = items.lockOwned(buyerId, itemId);
        if (item != null) items.deleteById(item.getId());
        return get(buyerId);
    }

    @Transactional
    public Cart select(Long buyerId, Long itemId, boolean selected) {
        requireBuyer(buyerId, true);
        CartItem item = requireOwned(buyerId, itemId);
        item.setSelected(selected ? 1 : 0);
        items.updateById(item);
        return get(buyerId);
    }

    @Transactional
    public Cart selectAll(Long buyerId, boolean selected) {
        requireBuyer(buyerId, true);
        items.update(null, new LambdaUpdateWrapper<CartItem>().eq(CartItem::getBuyerId, buyerId)
                .set(CartItem::getSelected, selected ? 1 : 0));
        return get(buyerId);
    }

    @Transactional
    public Cart acceptPrice(Long buyerId, Long itemId, BigDecimal expectedPrice) {
        requireBuyer(buyerId, true);
        CartItem item = requireOwned(buyerId, itemId);
        ProductSku sku = skus.lock(item.getSkuId());
        SkuInventory stock = sku == null ? null : inventory.lock(sku.getId());
        FruitProduct product = sku == null ? null : products.lock(sku.getProductId());
        Card visible = product == null ? null : visibleCard(product.getId());
        requirePurchasable(sku, stock, product, visible);
        if (sku.getPrice().compareTo(expectedPrice) != 0) {
            throw new ApiException("PRICE_CHANGED", "商品价格已再次变化，请确认最新价格", HttpStatus.CONFLICT);
        }
        item.setAcceptedUnitPrice(sku.getPrice());
        items.updateById(item);
        return get(buyerId);
    }

    @Transactional(readOnly = true)
    public CheckoutValidation validateCheckout(Long buyerId) {
        requireBuyer(buyerId, false);
        Cart cart = get(buyerId);
        List<Item> selected = cart.items().stream().filter(Item::selected).toList();
        if (selected.isEmpty()) {
            throw new ApiException("CART_SELECTION_REQUIRED", "请先选择要结算的商品", HttpStatus.BAD_REQUEST);
        }
        List<Long> attention = selected.stream()
                .filter(item -> !item.purchasable() || item.priceChanged()).map(Item::id).toList();
        boolean valid = attention.isEmpty();
        return new CheckoutValidation(valid, cart, attention,
                valid ? "商品与库存校验通过，运费为零；尚未创建订单" : "请处理不可购买或价格已变化的商品后重新校验");
    }

    private Cart build(List<CartItem> rows) {
        if (rows.isEmpty()) return new Cart(List.of(), 0, ZERO, ZERO, ZERO);

        List<Long> skuIds = rows.stream().map(CartItem::getSkuId).distinct().toList();
        Map<Long, ProductSku> skuById = new HashMap<>();
        skus.selectBatchIds(skuIds).forEach(sku -> skuById.put(sku.getId(), sku));
        List<Long> productIds = skuById.values().stream().map(ProductSku::getProductId).distinct().toList();
        Map<Long, FruitProduct> productById = new HashMap<>();
        if (!productIds.isEmpty()) products.selectBatchIds(productIds).forEach(p -> productById.put(p.getId(), p));
        Map<Long, SkuInventory> stockBySku = new HashMap<>();
        inventory.selectBatchIds(skuIds).forEach(stock -> stockBySku.put(stock.getSkuId(), stock));
        Map<Long, Card> visibleByProduct = new HashMap<>();
        catalog.visibleCards(productIds).forEach(card -> visibleByProduct.put(card.id(), card));

        List<Item> result = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        long selectedCount = 0;
        for (CartItem row : rows) {
            ProductSku sku = skuById.get(row.getSkuId());
            FruitProduct product = sku == null ? null : productById.get(sku.getProductId());
            SkuInventory stock = stockBySku.get(row.getSkuId());
            Card card = product == null ? null : visibleByProduct.get(product.getId());
            int available = available(stock);
            String reason = unavailableReason(sku, stock, product, card, row.getQuantity());
            boolean purchasable = reason == null;
            BigDecimal currentPrice = sku == null ? BigDecimal.ZERO : sku.getPrice();
            boolean priceChanged = sku != null && row.getAcceptedUnitPrice().compareTo(currentPrice) != 0;
            BigDecimal lineAmount = currentPrice.multiply(BigDecimal.valueOf(row.getQuantity()));
            boolean selectedItem = Integer.valueOf(1).equals(row.getSelected());
            if (selectedItem) {
                selectedCount++;
                if (purchasable) subtotal = subtotal.add(lineAmount);
            }
            result.add(new Item(row.getId(), row.getSkuId(), product == null ? "商品已失效" : product.getName(),
                    sku == null ? "规格已失效" : sku.getSpecification(), card == null ? null : card.mainImage(),
                    row.getQuantity(), selectedItem, currentPrice, row.getAcceptedUnitPrice(), available,
                    purchasable, priceChanged, reason, lineAmount));
        }
        subtotal = subtotal.setScale(2);
        return new Cart(List.copyOf(result), selectedCount, subtotal, ZERO, subtotal);
    }

    private Card visibleCard(Long productId) {
        return catalog.visibleCards(List.of(productId)).stream().findFirst().orElse(null);
    }

    private void requirePurchasable(ProductSku sku, SkuInventory stock, FruitProduct product, Card visible) {
        String reason = unavailableReason(sku, stock, product, visible, 1);
        if (reason != null) {
            String message = "OUT_OF_STOCK".equals(reason) ? "商品当前库存不足" : "商品当前不可购买";
            throw new ApiException("ITEM_UNAVAILABLE", message, HttpStatus.CONFLICT);
        }
    }

    private String unavailableReason(ProductSku sku, SkuInventory stock, FruitProduct product,
            Card visible, int quantity) {
        if (sku == null || product == null || product.getDeleted() == null || product.getDeleted() != 0
                || product.getStatus() == null || product.getStatus() != 1 || visible == null
                || sku.getStatus() == null || sku.getStatus() != 1) return "ITEM_UNAVAILABLE";
        if (stock == null || available(stock) < quantity) return "OUT_OF_STOCK";
        return null;
    }

    private int available(SkuInventory stock) {
        return stock == null ? 0 : stock.getOnHand() - stock.getReserved();
    }

    private void requireStock(int quantity, int available) {
        if (quantity > available) {
            throw new ApiException("INSUFFICIENT_STOCK", "购买数量超过当前可售库存", HttpStatus.CONFLICT);
        }
    }

    private BuyerAccount requireBuyer(Long buyerId, boolean lock) {
        BuyerAccount buyer = lock ? buyers.selectByIdForUpdate(buyerId) : buyers.selectById(buyerId);
        if (buyer == null || buyer.getStatus() == null || buyer.getStatus() != 1) {
            throw new ApiException("UNAUTHORIZED", "请先登录或重新登录", HttpStatus.UNAUTHORIZED);
        }
        return buyer;
    }

    private CartItem requireOwned(Long buyerId, Long itemId) {
        CartItem item = items.lockOwned(buyerId, itemId);
        if (item == null) throw new ApiException("NOT_FOUND", "购物车商品不存在", HttpStatus.NOT_FOUND);
        return item;
    }
}
