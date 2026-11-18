package com.fairyshalltear.freshfruit.catalog;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fairyshalltear.freshfruit.catalog.entity.*;
import com.fairyshalltear.freshfruit.catalog.mapper.*;
import com.fairyshalltear.freshfruit.inventory.entity.SkuInventory;
import com.fairyshalltear.freshfruit.inventory.mapper.SkuInventoryMapper;
import com.fairyshalltear.freshfruit.review.ReviewService;
import static com.fairyshalltear.freshfruit.catalog.CatalogDtos.*;
import static com.fairyshalltear.freshfruit.catalog.CatalogErrors.*;
@Service
public class CatalogService {
    private final FruitProductMapper products;private final ProductCategoryMapper categories;
    private final ProductSkuMapper skus;private final ProductImageMapper images;private final SkuInventoryMapper stocks;
    private final ProductService management;private final CategoryService categoryService;private final ReviewService reviews;
    public CatalogService(FruitProductMapper products,ProductCategoryMapper categories,ProductSkuMapper skus,
                          ProductImageMapper images,SkuInventoryMapper stocks,ProductService management,CategoryService categoryService,ReviewService reviews) {
        this.products=products;this.categories=categories;this.skus=skus;this.images=images;this.stocks=stocks;
        this.management=management;this.categoryService=categoryService;this.reviews=reviews;
    }
    private QueryWrapper<FruitProduct> filter(Query r,boolean publicly) {
        if(r.minPrice()!=null&&r.maxPrice()!=null&&r.minPrice().compareTo(r.maxPrice())>0)throw invalid("最低价不能大于最高价");
        QueryWrapper<FruitProduct> q=new QueryWrapper<FruitProduct>().eq("deleted",0);
        if(publicly) {
            q.eq("status",1).inSql("category_id","SELECT c.id FROM product_category c JOIN product_category p ON p.id=c.parent_id WHERE c.status=1 AND p.status=1 AND p.parent_id IS NULL")
                .apply("EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=fruit_product.id AND s.status=1)");
        } else {
            if(r.status()!=null)q.eq("status",r.status());if(r.recommended()!=null)q.eq("is_recommended",r.recommended()?1:0);
        }
        if(r.keyword()!=null&&!r.keyword().isBlank()) {
            String keyword="%"+r.keyword().trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            if(publicly)q.and(w->w.apply("name LIKE {0} ESCAPE '!'",keyword).or().apply("description LIKE {0} ESCAPE '!'",keyword));
            else q.apply("name LIKE {0} ESCAPE '!'",keyword);
        }
        if(r.categoryId()!=null)q.apply("category_id IN (SELECT id FROM product_category WHERE id={0} OR parent_id={0})",r.categoryId());
        if(r.minPrice()!=null||r.maxPrice()!=null) {
            String sql="EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=fruit_product.id AND s.status=1";
            List<Object> args=new ArrayList<>();
            if(r.minPrice()!=null){sql+=" AND s.price >= {"+args.size()+"}";args.add(r.minPrice());}
            if(r.maxPrice()!=null){sql+=" AND s.price <= {"+args.size()+"}";args.add(r.maxPrice());}
            q.apply(sql+")",args.toArray());
        }
        return q;
    }
    private QueryWrapper<FruitProduct> sort(QueryWrapper<FruitProduct> q,Query r,boolean publicly) {
        if(!publicly)q.orderByDesc("updated_at");
        else if(r.sort().equals("NEWEST"))q.orderByDesc("published_at");
        else {
            String minimum="(SELECT MIN(s.price) FROM product_sku s WHERE s.product_id=fruit_product.id AND s.status=1)";
            if(r.sort().equals("PRICE_ASC"))q.orderByAsc(minimum);else q.orderByDesc(minimum);
        }
        return q.orderByDesc("id").last("LIMIT "+r.size()+" OFFSET "+((long)(r.page()-1)*r.size()));
    }
    @Transactional(readOnly=true) public Page<Card> search(Query q) {
        long count=products.selectCount(filter(q,true));var rows=products.selectList(sort(filter(q,true),q,true));
        return new Page<>(cards(rows),count,q.page(),q.size());
    }
    @Transactional(readOnly=true) public Page<AdminCard> adminList(Query q) {
        long count=products.selectCount(filter(q,false));var rows=products.selectList(sort(filter(q,false),q,false));
        Map<Long,Card> by=new HashMap<>();cards(rows).forEach(c->by.put(c.id(),c));
        return new Page<>(rows.stream().map(p->new AdminCard(by.get(p.getId()),p.getStatus(),p.getIsRecommended()==1,
                p.getRecommendSort(),p.getVersion(),p.getUpdatedAt())).toList(),count,q.page(),q.size());
    }
    @Transactional(readOnly=true) public List<Card> recommendations() {
        Query q=new Query(null,null,null,null,"NEWEST",1,8,null,null);
        List<FruitProduct> rows=products.selectList(filter(q,true).eq("is_recommended",1)
                .orderByAsc("recommend_sort").orderByDesc("id").last("LIMIT 8"));
        return cards(rows);
    }
    @Transactional(readOnly=true) public PublicDetail detail(Long id) {
        reviews.visible(id);AdminDetail p=management.detail(id);
        return new PublicDetail(id,p.name(),p.categoryId(),categoryService.path(p.categoryId()),p.description(),p.origin(),p.taste(),p.storageMethod(),p.images(),
                p.skus().stream().filter(s->s.status()==1).map(s->new Sku(s.id(),s.specification(),s.price(),s.availableStock())).toList(),reviews.summary(id),
                p.sweetnessDescription(),p.seasonalMonths(),p.giftBox(),p.useCases());
    }
    public List<Card> visibleCards(Collection<Long> ids) {
        if(ids.isEmpty())return List.of();
        Query q=new Query(null,null,null,null,null,1,100,null,null);
        return cards(products.selectList(filter(q,true).in("id",ids)));
    }
    private List<Card> cards(List<FruitProduct> rows) {
        if(rows.isEmpty())return List.of();
        List<Long> ids=rows.stream().map(FruitProduct::getId).toList();
        Map<Long,String> categoryNames=new HashMap<>();
        categories.selectBatchIds(rows.stream().map(FruitProduct::getCategoryId).distinct().toList()).forEach(c->categoryNames.put(c.getId(),c.getName()));
        List<ProductSku> skuRows=skus.selectList(new LambdaQueryWrapper<ProductSku>().in(ProductSku::getProductId,ids).eq(ProductSku::getStatus,1));
        Map<Long,SkuInventory> inventory=new HashMap<>();
        if(!skuRows.isEmpty())stocks.selectBatchIds(skuRows.stream().map(ProductSku::getId).toList()).forEach(s->inventory.put(s.getSkuId(),s));
        Map<Long,String> main=new HashMap<>();
        images.selectList(new LambdaQueryWrapper<ProductImage>().in(ProductImage::getProductId,ids).eq(ProductImage::getActive,1)
                .orderByAsc(ProductImage::getSortOrder).orderByAsc(ProductImage::getId)).forEach(i->main.putIfAbsent(i.getProductId(),ProductImageService.PREFIX+i.getStorageKey()));
        Map<Long,List<ProductSku>> grouped=new HashMap<>();skuRows.forEach(s->grouped.computeIfAbsent(s.getProductId(),key->new ArrayList<>()).add(s));
        return rows.stream().map(p->{
            var ss=grouped.getOrDefault(p.getId(),List.of());
            BigDecimal min=ss.stream().map(ProductSku::getPrice).min(BigDecimal::compareTo).orElse(null);
            BigDecimal max=ss.stream().map(ProductSku::getPrice).max(BigDecimal::compareTo).orElse(null);
            long available=ss.stream().mapToLong(s->{var inv=inventory.get(s.getId());return inv==null?0:inv.getOnHand()-inv.getReserved();}).sum();
            return new Card(p.getId(),p.getName(),p.getCategoryId(),categoryNames.get(p.getCategoryId()),main.get(p.getId()),min,max,available,available==0);
        }).toList();
    }
}
