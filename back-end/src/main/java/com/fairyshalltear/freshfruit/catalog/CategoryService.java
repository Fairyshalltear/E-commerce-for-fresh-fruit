package com.fairyshalltear.freshfruit.catalog;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fairyshalltear.freshfruit.catalog.entity.*;
import com.fairyshalltear.freshfruit.catalog.mapper.*;
import com.fairyshalltear.freshfruit.audit.AuditService;
import static com.fairyshalltear.freshfruit.catalog.CatalogErrors.*;
import static com.fairyshalltear.freshfruit.catalog.CatalogDtos.*;
@Service
public class CategoryService {
    private final ProductCategoryMapper categories;
    private final FruitProductMapper products;
    private final AuditService audit;
    public CategoryService(ProductCategoryMapper categories,FruitProductMapper products,AuditService audit) {
        this.categories=categories;this.products=products;this.audit=audit;
    }
    public List<ProductCategory> lockTree() { return categories.lockTree(); }
    public List<Category> tree(boolean publicly) {
        List<ProductCategory> all=categories.selectList(new LambdaQueryWrapper<ProductCategory>()
                .orderByAsc(ProductCategory::getSortOrder).orderByAsc(ProductCategory::getId));
        return all.stream().filter(c->c.getParentId()==null && (!publicly || c.getStatus()==1))
                .map(c->new Category(c.getId(),null,c.getName(),c.getSortOrder(),c.getStatus(),all.stream()
                    .filter(x->c.getId().equals(x.getParentId()) && (!publicly || x.getStatus()==1)).map(this::dto).toList())).toList();
    }
    public Category get(Long id) { return dto(require(id)); }
    public ProductCategory require(Long id) {
        ProductCategory c=categories.selectById(id);if(c==null)throw notFound();return c;
    }
    public ProductCategory leaf(Long id, boolean enabled) {
        ProductCategory c=require(id);
        if(c.getParentId()==null)throw invalid("商品必须选择二级分类");
        ProductCategory parent=require(c.getParentId());
        if(parent.getParentId()!=null)throw conflict("分类层级无效");
        if(enabled && (c.getStatus()!=1 || parent.getStatus()!=1))throw conflict("所属分类已停用");
        return c;
    }
    public boolean enabled(Long id) {
        ProductCategory c=categories.selectById(id);if(c==null||c.getParentId()==null||c.getStatus()!=1)return false;
        ProductCategory p=categories.selectById(c.getParentId());return p!=null && p.getParentId()==null && p.getStatus()==1;
    }
    public List<Category> path(Long id) { ProductCategory c=require(id);return List.of(dto(require(c.getParentId())),dto(c)); }
    @Transactional public Category save(Long admin,Long id,CategoryRequest r) {
        lockTree();
        ProductCategory c=id==null?new ProductCategory():require(id);
        if(r.parentId()!=null) {
            if(r.parentId().equals(id))throw invalid("分类不能以自身为父级");
            if(require(r.parentId()).getParentId()!=null)throw invalid("最多支持两级分类");
            if(id!=null && categories.selectCount(new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getParentId,id))>0)
                throw conflict("含子分类的一级分类不能下移");
        } else if(id!=null && c.getParentId()!=null && products.selectCount(new LambdaQueryWrapper<FruitProduct>().eq(FruitProduct::getCategoryId,id))>0)
            throw conflict("有关联商品的二级分类不能提升为一级");
        String name=r.name().trim();if(name.isEmpty())throw invalid("分类名称不能为空");
        LambdaQueryWrapper<ProductCategory> q=new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getName,name);
        if(r.parentId()==null)q.isNull(ProductCategory::getParentId);else q.eq(ProductCategory::getParentId,r.parentId());
        if(id!=null)q.ne(ProductCategory::getId,id);
        if(categories.selectCount(q)>0)throw conflict("同一级下分类名称已存在");
        if(id!=null && Objects.equals(c.getName(),name) && Objects.equals(c.getParentId(),r.parentId())
            && c.getSortOrder().equals(r.sortOrder()) && c.getStatus().equals(r.status()))return dto(c);
        c.setName(name);c.setParentId(r.parentId());c.setSortOrder(r.sortOrder());c.setStatus(r.status());c.setUpdatedAt(LocalDateTime.now());
        if(id==null)categories.insert(c);else categories.updateById(c);
        audit.record(admin,id==null?"CATEGORY_CREATE":"CATEGORY_UPDATE","CATEGORY",c.getId());return dto(c);
    }
    @Transactional public void delete(Long admin,Long id) {
        lockTree();require(id);
        if(categories.selectCount(new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getParentId,id))>0
            || products.selectCount(new LambdaQueryWrapper<FruitProduct>().eq(FruitProduct::getCategoryId,id))>0)
            throw conflict("分类存在子分类或关联商品，请停用或先处理关联数据");
        audit.record(admin,"CATEGORY_DELETE","CATEGORY",id);categories.deleteById(id);
    }
    private Category dto(ProductCategory c) { return new Category(c.getId(),c.getParentId(),c.getName(),c.getSortOrder(),c.getStatus(),List.of()); }
}
