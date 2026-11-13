package com.fairyshalltear.freshfruit.catalog;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fairyshalltear.freshfruit.account.mapper.BuyerAccountMapper;
import com.fairyshalltear.freshfruit.catalog.entity.*;
import com.fairyshalltear.freshfruit.catalog.mapper.*;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import static com.fairyshalltear.freshfruit.catalog.CatalogDtos.*;
import static com.fairyshalltear.freshfruit.catalog.CatalogErrors.*;
@Service
public class FavoriteService {
    private final UserFavoriteMapper favorites;private final BuyerAccountMapper buyers;
    private final FruitProductMapper products;private final CatalogService catalog;
    public FavoriteService(UserFavoriteMapper favorites,BuyerAccountMapper buyers,FruitProductMapper products,CatalogService catalog) {
        this.favorites=favorites;this.buyers=buyers;this.products=products;this.catalog=catalog;
    }
    private void buyer(Long id,boolean lock) {
        var b=lock?buyers.selectByIdForUpdate(id):buyers.selectById(id);
        if(b==null||b.getStatus()!=1)throw new ApiException("UNAUTHORIZED","请先登录或重新登录",HttpStatus.UNAUTHORIZED);
    }
    private LambdaQueryWrapper<UserFavorite> owned(Long buyer,Long product) {
        return new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getBuyerId,buyer).eq(UserFavorite::getProductId,product);
    }
    public FavoriteState state(Long buyer,Long product) {buyer(buyer,false);return new FavoriteState(favorites.selectCount(owned(buyer,product))>0);}
    @Transactional public FavoriteState add(Long buyer,Long product) {
        buyer(buyer,true);var p=products.lock(product);if(p==null)throw notFound();
        var cards=catalog.visibleCards(List.of(product));if(cards.isEmpty())throw notFound();
        if(favorites.selectCount(owned(buyer,product))==0) {
            UserFavorite f=new UserFavorite();f.setBuyerId(buyer);f.setProductId(product);
            f.setProductNameSnapshot(p.getName());f.setMainImageSnapshot(cards.get(0).mainImage());favorites.insert(f);
        }
        return new FavoriteState(true);
    }
    @Transactional public void remove(Long buyer,Long product) {buyer(buyer,true);favorites.delete(owned(buyer,product));}
    @Transactional(readOnly=true) public Page<Favorite> list(Long buyer,Query q) {
        buyer(buyer,false);long total=favorites.selectCount(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getBuyerId,buyer));
        var rows=favorites.selectList(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getBuyerId,buyer)
                .orderByDesc(UserFavorite::getCreatedAt).orderByDesc(UserFavorite::getId)
                .last("LIMIT "+q.size()+" OFFSET "+((long)(q.page()-1)*q.size())));
        Map<Long,Card> cards=new HashMap<>();catalog.visibleCards(rows.stream().map(UserFavorite::getProductId).toList()).forEach(c->cards.put(c.id(),c));
        Map<Long,FruitProduct> all=new HashMap<>();if(!rows.isEmpty())products.selectBatchIds(rows.stream().map(UserFavorite::getProductId).toList()).forEach(p->all.put(p.getId(),p));
        return new Page<>(rows.stream().map(f->{
            Card c=cards.get(f.getProductId());FruitProduct p=all.get(f.getProductId());
            String reason=c!=null?null:p==null||p.getDeleted()==1?"DELETED":p.getStatus()==0?"UNLISTED":"CATEGORY_DISABLED";
            return new Favorite(f.getProductId(),f.getProductNameSnapshot(),f.getMainImageSnapshot(),c!=null,reason,c);
        }).toList(),total,q.page(),q.size());
    }
}
