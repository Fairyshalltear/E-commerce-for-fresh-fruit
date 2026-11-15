package com.fairyshalltear.freshfruit.catalog;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fairyshalltear.freshfruit.catalog.entity.*;
import com.fairyshalltear.freshfruit.catalog.mapper.*;
import com.fairyshalltear.freshfruit.inventory.entity.SkuInventory;
import com.fairyshalltear.freshfruit.inventory.mapper.SkuInventoryMapper;
import com.fairyshalltear.freshfruit.inventory.InventoryService;
import com.fairyshalltear.freshfruit.audit.AuditService;
import static com.fairyshalltear.freshfruit.catalog.CatalogDtos.*;
import static com.fairyshalltear.freshfruit.catalog.CatalogErrors.*;
@Service
public class ProductService {
    private final FruitProductMapper products;private final ProductSkuMapper skus;private final SkuInventoryMapper stocks;
    private final CategoryService categories;private final ProductImageService images;private final InventoryService inventory;private final AuditService audit;
    public ProductService(FruitProductMapper products,ProductSkuMapper skus,SkuInventoryMapper stocks,CategoryService categories,
                          ProductImageService images,InventoryService inventory,AuditService audit) {
        this.products=products;this.skus=skus;this.stocks=stocks;this.categories=categories;
        this.images=images;this.inventory=inventory;this.audit=audit;
    }
    public FruitProduct require(Long id) {
        FruitProduct p=products.selectById(id);if(p==null||p.getDeleted()==1)throw notFound();return p;
    }
    public List<ProductSku> skuRows(Long id) {
        return skus.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId,id)
                .orderByAsc(ProductSku::getSortOrder).orderByAsc(ProductSku::getId));
    }
    @Transactional(readOnly=true) public AdminDetail detail(Long id) {
        FruitProduct p=require(id);List<ProductSku> rows=skuRows(id);Map<Long,SkuInventory> by=new HashMap<>();
        if(!rows.isEmpty())stocks.selectBatchIds(rows.stream().map(ProductSku::getId).toList()).forEach(s->by.put(s.getSkuId(),s));
        List<AdminSku> dto=rows.stream().map(s->{SkuInventory st=by.get(s.getId());int available=st.getOnHand()-st.getReserved();
            return new AdminSku(s.getId(),s.getSpecification(),s.getPrice(),st.getOnHand(),st.getReserved(),available,
                    st.getLowStockThreshold(),s.getStatus()==1 && available<st.getLowStockThreshold(),s.getStatus(),st.getVersion());}).toList();
        List<ProductImage> ir=images.rows(id);
        return new AdminDetail(id,p.getName(),p.getCategoryId(),p.getDescription(),p.getOrigin(),p.getTaste(),p.getStorageMethod(),
                p.getStatus(),p.getIsRecommended()==1,p.getRecommendSort(),p.getVersion(),
                ir.stream().filter(i->i.getActive()==1).map(ProductImageService::dto).toList(),
                ir.stream().filter(i->i.getActive()==0).map(ProductImageService::dto).toList(),dto);
    }
    @Transactional public AdminDetail save(Long admin,Long id,ProductRequest r) {
        categories.lockTree();
        categories.leaf(r.categoryId(),false);
        FruitProduct p=id==null?new FruitProduct():products.lock(id);
        if(id!=null) { if(p==null||p.getDeleted()==1)throw notFound();version(r.version(),p.getVersion()); }
        else {p.setStatus(0);p.setDeleted(0);p.setVersion(0L);}
        Set<String> names=new HashSet<>();Set<Long> ids=new HashSet<>();
        for(SkuRequest item:r.skus()) {
            if(item.id()==null && item.onHand()==null)throw invalid("新规格必须设置初始库存");
            if(item.specification().isBlank()||!names.add(item.specification().trim()))throw invalid("规格名称不能为空或重复");
            if(item.id()!=null&&!ids.add(item.id()))throw invalid("规格 ID 重复");
        }
        List<ProductSku> old=id==null?List.of():skuRows(id);
        Map<Long,ProductSku> oldBy=new HashMap<>();Map<Long,SkuInventory> locked=new HashMap<>();
        for(ProductSku s:old.stream().sorted(Comparator.comparing(ProductSku::getId)).toList()) {
            oldBy.put(s.getId(),s);locked.put(s.getId(),stocks.lock(s.getId()));
        }
        for(SkuRequest item:r.skus())if(item.id()!=null&&!oldBy.containsKey(item.id()))throw invalid("规格不属于当前商品");
        for(ProductSku s:old)if(!ids.contains(s.getId())) {
            if(locked.get(s.getId()).getReserved()>0)throw conflict("被移除规格仍有预留库存");
            if(names.contains(s.getSpecification()))throw conflict("规格名称已保留，请恢复原规格");
        }
        if(p.getStatus()==1) {
            categories.leaf(r.categoryId(),true);
            if(r.imageIds().isEmpty()||r.skus().stream().noneMatch(s->s.status()==1))throw conflict("已上架商品至少需要一张图片及一个启用规格");
        }
        p.setName(r.name().trim());p.setCategoryId(r.categoryId());p.setDescription(text(r.description()));
        p.setOrigin(text(r.origin()));p.setTaste(text(r.taste()));p.setStorageMethod(text(r.storageMethod()));
        p.setIsRecommended(r.recommended()?1:0);p.setRecommendSort(r.recommendSort());p.setUpdatedAt(LocalDateTime.now());
        if(id==null)products.insert(p);else {p.setVersion(p.getVersion()+1);products.updateById(p);}
        // Temporary unique names allow swaps without replacing stable SKU identifiers.
        for(ProductSku s:old)if(ids.contains(s.getId())) {s.setSpecification("~"+UUID.randomUUID());skus.updateById(s);}
        for(ProductSku s:old)if(!ids.contains(s.getId())) {s.setStatus(0);s.setUpdatedAt(LocalDateTime.now());skus.updateById(s);}
        for(int index=0;index<r.skus().size();index++) {
            SkuRequest item=r.skus().get(index);ProductSku s=item.id()==null?new ProductSku():oldBy.get(item.id());
            s.setProductId(p.getId());s.setSpecification(item.specification().trim());s.setPrice(item.price());
            s.setStatus(item.status());s.setSortOrder(index);s.setUpdatedAt(LocalDateTime.now());
            if(item.id()==null) {skus.insert(s);inventory.initialize(admin,s.getId(),item);}
            else {
                if(item.status()==0 && locked.get(s.getId()).getReserved()>0)throw conflict("规格仍有预留库存，不能停用");
                skus.updateById(s);
            }
        }
        images.bind(admin,p.getId(),r.imageIds());
        audit.record(admin,id==null?"PRODUCT_CREATE":"PRODUCT_UPDATE","PRODUCT",p.getId());
        return detail(p.getId());
    }
    @Transactional public AdminDetail status(Long admin,Long id,StatusRequest r) {
        categories.lockTree();FruitProduct p=products.lock(id);
        if(p==null||p.getDeleted()==1)throw notFound();version(r.version(),p.getVersion());
        if(p.getStatus().equals(r.status()))return detail(id);
        if(r.status()==1) {
            categories.leaf(p.getCategoryId(),true);
            if(images.rows(id).stream().noneMatch(i->i.getActive()==1)||skuRows(id).stream().noneMatch(s->s.getStatus()==1))
                throw conflict("上架需要至少一张图片和一个有效启用规格");
            p.setPublishedAt(LocalDateTime.now());
        }
        p.setStatus(r.status());p.setVersion(p.getVersion()+1);p.setUpdatedAt(LocalDateTime.now());products.updateById(p);
        audit.record(admin,r.status()==1?"PRODUCT_PUBLISH":"PRODUCT_UNLIST","PRODUCT",id);return detail(id);
    }
    @Transactional public void delete(Long admin,Long id,Long expected) {
        categories.lockTree();FruitProduct p=products.lock(id);if(p==null||p.getDeleted()==1)throw notFound();version(expected,p.getVersion());
        for(ProductSku s:skuRows(id).stream().sorted(Comparator.comparing(ProductSku::getId)).toList())
            if(stocks.lock(s.getId()).getReserved()>0)throw conflict("商品仍有预留库存，不能删除");
        p.setDeleted(1);p.setStatus(0);p.setVersion(p.getVersion()+1);p.setUpdatedAt(LocalDateTime.now());products.updateById(p);
        audit.record(admin,"PRODUCT_DELETE","PRODUCT",id);
    }
    private String text(String value){return value==null?"":value.trim();}
}
