package com.fairyshalltear.freshfruit.review;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fairyshalltear.freshfruit.review.entity.ProductReview;
import com.fairyshalltear.freshfruit.review.mapper.ProductReviewMapper;
import com.fairyshalltear.freshfruit.account.entity.BuyerAccount;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.catalog.*;
import static com.fairyshalltear.freshfruit.catalog.CatalogDtos.*;
import static com.fairyshalltear.freshfruit.catalog.CatalogErrors.*;
@Service
public class ReviewService {
    private final ProductReviewMapper reviews;private final BuyerAccountMapper buyers;
    private final ProductService products;private final CategoryService categories;
    public ReviewService(ProductReviewMapper reviews,BuyerAccountMapper buyers,ProductService products,CategoryService categories) {
        this.reviews=reviews;this.buyers=buyers;this.products=products;this.categories=categories;
    }
    public void visible(Long id) {
        var p=products.require(id);
        if(p.getStatus()!=1||!categories.enabled(p.getCategoryId())||products.skuRows(id).stream().noneMatch(s->s.getStatus()==1))throw notFound();
    }
    public ReviewSummary summary(Long id) {
        Map<String,Object> row=reviews.selectMaps(new QueryWrapper<ProductReview>().eq("product_id",id).eq("status",1)
                .select("COUNT(*) AS review_count","AVG(rating) AS average_rating")).get(0);
        Object count=value(row,"review_count"),average=value(row,"average_rating");
        return new ReviewSummary(((Number)count).longValue(),average==null?null:new BigDecimal(average.toString()).setScale(1,RoundingMode.HALF_UP));
    }
    @Transactional(readOnly=true) public Page<Review> list(Long id,Query q) {
        visible(id);long total=reviews.selectCount(new LambdaQueryWrapper<ProductReview>().eq(ProductReview::getProductId,id).eq(ProductReview::getStatus,1));
        List<ProductReview> rows=reviews.selectList(new LambdaQueryWrapper<ProductReview>().eq(ProductReview::getProductId,id).eq(ProductReview::getStatus,1)
                .orderByDesc(ProductReview::getCreatedAt).orderByDesc(ProductReview::getId).last("LIMIT "+q.size()+" OFFSET "+((long)(q.page()-1)*q.size())));
        Map<Long,BuyerAccount> owners=new HashMap<>();
        if(!rows.isEmpty())buyers.selectBatchIds(rows.stream().map(ProductReview::getBuyerId).distinct().toList()).forEach(b->owners.put(b.getId(),b));
        return new Page<>(rows.stream().map(r->{BuyerAccount b=owners.get(r.getBuyerId());String nickname=b==null?null:b.getNickname();
            return new Review(r.getId(),r.getRating(),r.getContent(),r.getSpecificationSnapshot(),r.getCreatedAt(),
                    nickname==null||nickname.isBlank()?"买家":nickname);}).toList(),total,q.page(),q.size());
    }
    private Object value(Map<String,Object> row,String key) {for(var entry:row.entrySet())if(entry.getKey().equalsIgnoreCase(key))return entry.getValue();return null;}
}
