package com.fairyshalltear.freshfruit.inventory;

import static com.fairyshalltear.freshfruit.inventory.InventoryDtos.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.inventory.entity.*;
import com.fairyshalltear.freshfruit.inventory.mapper.*;

@Service
public class InventoryManagementService {
    private final JdbcTemplate db;
    private final SkuInventoryMapper stocks;
    private final InventoryDocumentMapper documents;
    private final InventoryDocumentItemMapper items;
    private final StockMovementService changes;
    private final AuditService audit;
    private static final String JOIN=" FROM sku_inventory i JOIN product_sku s ON s.id=i.sku_id JOIN fruit_product p ON p.id=s.product_id JOIN product_category c ON c.id=p.category_id ";
    private static final String LOW="(p.deleted=0 AND s.status=1 AND i.low_stock_threshold>0 AND i.on_hand-i.reserved<i.low_stock_threshold)";
    private static final String COLUMNS="i.sku_id,p.id product_id,p.name product_name,s.specification,p.category_id,c.name category_name,p.status product_status,s.status sku_status,p.deleted,i.on_hand,i.reserved,i.on_hand-i.reserved available_stock,i.low_stock_threshold,i.version inventory_version,"+LOW+" low_stock";
    private static final RowMapper<Stock> STOCK=(r,n)->new Stock(r.getLong("sku_id"),r.getLong("product_id"),r.getString("product_name"),r.getString("specification"),r.getLong("category_id"),r.getString("category_name"),r.getInt("product_status"),r.getInt("sku_status"),r.getInt("deleted"),r.getInt("on_hand"),r.getInt("reserved"),r.getInt("available_stock"),r.getInt("low_stock_threshold"),r.getBoolean("low_stock"),r.getLong("inventory_version"));
    public InventoryManagementService(JdbcTemplate db,SkuInventoryMapper stocks,InventoryDocumentMapper documents,
            InventoryDocumentItemMapper items,StockMovementService changes,AuditService audit){
        this.db=db;this.stocks=stocks;this.documents=documents;this.items=items;this.changes=changes;this.audit=audit;
    }
    private static final class Filter {
        final StringBuilder sql=new StringBuilder(" WHERE 1=1");final List<Object> args=new ArrayList<>();
        void add(String condition,Object...values){sql.append(" AND ").append(condition);Collections.addAll(args,values);}
    }
    private static String like(String value){return "%"+value.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private Filter filter(StockQuery q,boolean options){
        Filter f=new Filter();
        if(q.keyword()!=null&&!q.keyword().isBlank())f.add("(p.name LIKE ? ESCAPE '!' OR s.specification LIKE ? ESCAPE '!')",like(q.keyword()),like(q.keyword()));
        if(q.categoryId()!=null)f.add("(c.id=? OR c.parent_id=?)",q.categoryId(),q.categoryId());
        if(q.status()!=null)f.add("p.status=?",q.status());if(q.skuStatus()!=null)f.add("s.status=?",q.skuStatus());
        if(options){if("PURCHASE_IN".equals(q.type()))f.add("p.deleted=0 AND s.status=1");}
        else f.add("p.deleted=?",q.deleted()==null?0:q.deleted());
        if(q.skuId()!=null)f.add("i.sku_id=?",q.skuId());
        if("LOW".equals(q.stockState()))f.add(LOW);
        if("ZERO".equals(q.stockState()))f.add("i.on_hand=i.reserved");
        if("NORMAL".equals(q.stockState()))f.add("NOT "+LOW+" AND i.on_hand>i.reserved");
        return f;
    }
    @Transactional(readOnly=true)
    public Page<Stock> stockList(StockQuery q,boolean options){
        Filter f=filter(q,options);long total=count("SELECT COUNT(*)"+JOIN+f.sql,f.args);
        List<Object>a=new ArrayList<>(f.args);a.add(q.size());a.add((long)(q.page()-1)*q.size());
        return new Page<>(db.query("SELECT "+COLUMNS+JOIN+f.sql+" ORDER BY p.id DESC,s.id ASC LIMIT ? OFFSET ?",STOCK,a.toArray()),total,q.page(),q.size());
    }
    @Transactional(readOnly=true)
    public Summary summary(StockQuery q){Filter f=filter(q,false);return db.queryForObject("SELECT COUNT(*) sku_count,COALESCE(SUM(i.on_hand),0) on_hand,COALESCE(SUM(i.reserved),0) reserved,COALESCE(SUM(i.on_hand-i.reserved),0) available,COALESCE(SUM(CASE WHEN "+LOW+" THEN 1 ELSE 0 END),0) low_count"+JOIN+f.sql,(r,n)->new Summary(r.getLong("sku_count"),r.getLong("on_hand"),r.getLong("reserved"),r.getLong("available"),r.getLong("low_count")),f.args.toArray());}
    @Transactional(readOnly=true)
    public long alertCount(){return db.queryForObject("SELECT COUNT(*)"+JOIN+" WHERE "+LOW,Long.class);}
    private Stock current(Long sku){List<Stock> rows=db.query("SELECT "+COLUMNS+JOIN+" WHERE i.sku_id=?",STOCK,sku);if(rows.isEmpty())throw missing();return rows.get(0);}
    @Transactional
    public Stock threshold(Long admin,Long sku,Threshold input){SkuInventory s=stocks.lock(sku);if(s==null)throw missing();checkVersion(input.version(),s.getVersion());if(!Objects.equals(input.lowStockThreshold(),s.getLowStockThreshold())){s.setLowStockThreshold(input.lowStockThreshold());s.setVersion(s.getVersion()+1);s.setUpdatedAt(LocalDateTime.now());stocks.updateById(s);audit.record(admin,"INVENTORY_THRESHOLD","SKU",sku);}return current(sku);}
    private long count(String sql,List<Object> args){return db.queryForObject(sql,Long.class,args.toArray());}
    private static void range(LocalDateTime from,LocalDateTime to){if(from!=null&&to!=null&&from.isAfter(to))throw bad("开始时间不能晚于结束时间");}
    @Transactional(readOnly=true)
    public Page<Movement> movementList(MovementQuery q){
        range(q.from(),q.to());Filter f=new Filter();
        if(q.keyword()!=null&&!q.keyword().isBlank())f.add("(COALESCE(m.product_name_snapshot,p.name) LIKE ? ESCAPE '!' OR COALESCE(m.specification_snapshot,s.specification) LIKE ? ESCAPE '!')",like(q.keyword()),like(q.keyword()));
        if(q.skuId()!=null)f.add("m.sku_id=?",q.skuId());if(q.type()!=null)f.add("m.type=?",q.type());
        if("IN".equals(q.direction()))f.add("m.quantity_delta>0");if("OUT".equals(q.direction()))f.add("m.quantity_delta<0");
        if(q.sourceNo()!=null&&!q.sourceNo().isBlank())f.add("(m.source_no LIKE ? ESCAPE '!' OR m.reason LIKE ? ESCAPE '!')",like(q.sourceNo()),like(q.sourceNo()));
        if(q.from()!=null)f.add("m.created_at>=?",q.from());if(q.to()!=null)f.add("m.created_at<=?",q.to());
        String joins=" FROM inventory_movement m JOIN product_sku s ON s.id=m.sku_id JOIN fruit_product p ON p.id=s.product_id JOIN admin_account a ON a.id=m.admin_id";
        long total=count("SELECT COUNT(*)"+joins+f.sql,f.args);List<Object>a=new ArrayList<>(f.args);a.add(q.size());a.add((long)(q.page()-1)*q.size());
        List<Movement> rows=db.query("SELECT m.*,COALESCE(m.product_name_snapshot,p.name) display_name,COALESCE(m.specification_snapshot,s.specification) display_spec,a.display_name admin_name"+joins+f.sql+" ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?",(r,n)->new Movement(r.getLong("id"),r.getLong("sku_id"),r.getString("display_name"),r.getString("display_spec"),r.getString("type"),r.getInt("before_on_hand"),r.getInt("after_on_hand"),r.getInt("quantity_delta"),r.getString("reason"),r.getLong("admin_id"),r.getString("admin_name"),r.getTimestamp("created_at").toLocalDateTime(),r.getString("source_type"),nullableLong(r,"source_id"),nullableLong(r,"source_item_id"),r.getString("source_no")),a.toArray());
        return new Page<>(rows,total,q.page(),q.size());
    }
    private static Long nullableLong(ResultSet r,String field)throws SQLException{Object o=r.getObject(field);return o==null?null:((Number)o).longValue();}
    @Transactional(readOnly=true)
    public Page<DocumentView> documentList(DocumentQuery q){
        range(q.from(),q.to());QueryWrapper<InventoryDocument>w=new QueryWrapper<>();if(q.type()!=null)w.eq("type",q.type());if(q.status()!=null)w.eq("status",q.status());
        if(q.keyword()!=null&&!q.keyword().isBlank())w.and(x->x.like("document_no",q.keyword().trim()).or().like("supplier",q.keyword().trim()).or().like("reference_no",q.keyword().trim()));
        if(q.from()!=null)w.ge("created_at",q.from());if(q.to()!=null)w.le("created_at",q.to());
        long total=documents.selectCount(w);List<InventoryDocument> rows=documents.selectList(w.orderByDesc("created_at","id").last("LIMIT "+q.size()+" OFFSET "+((long)(q.page()-1)*q.size())));
        return new Page<>(rows.stream().map(this::view).toList(),total,q.page(),q.size());
    }
    @Transactional(readOnly=true)
    public DocumentView detail(Long id){InventoryDocument d=documents.selectById(id);if(d==null)throw missing();return view(d);}
    private List<InventoryDocumentItem> lines(Long id){return items.selectList(new QueryWrapper<InventoryDocumentItem>().eq("document_id",id).orderByAsc("sku_id"));}
    private DocumentView view(InventoryDocument d){
        List<ItemView> result=lines(d.id).stream().map(i->new ItemView(i.id,i.skuId,i.productNameSnapshot,i.specificationSnapshot,i.quantity,i.countedQuantity,i.beforeOnHand,i.beforeReserved,i.beforeOnHand==null?null:i.beforeOnHand-i.beforeReserved,i.inventoryVersion,i.afterOnHand,i.quantityDelta,current(i.skuId))).toList();
        String creator=db.queryForObject("SELECT display_name FROM admin_account WHERE id=?",String.class,d.createdBy);
        String poster=d.postedBy==null?null:db.queryForObject("SELECT display_name FROM admin_account WHERE id=?",String.class,d.postedBy);
        return new DocumentView(d.id,d.documentNo,d.type,d.status,d.supplier,d.referenceNo,d.reason,d.version,d.createdBy,creator,d.postedBy,poster,d.createdAt,d.updatedAt,d.postedAt,result);
    }
    private void validate(Draft input){
        if(!"STOCKTAKE".equals(input.type())&&(input.supplier()==null||input.supplier().isBlank()))throw bad("请填写供应商名称");
        if(!"PURCHASE_IN".equals(input.type())&&(input.reason()==null||input.reason().isBlank()))throw bad("请填写退供或盘点原因");
        Set<Long> ids=new HashSet<>();for(Line line:input.items()){
            if(!ids.add(line.skuId()))throw bad("同一单据的 SKU 不可重复");
            if("STOCKTAKE".equals(input.type())){if(line.countedQuantity()==null||line.quantity()!=null)throw bad("盘点只填写非负整数实盘数量");}
            else if(line.quantity()==null||line.countedQuantity()!=null)throw bad("出入库只填写正整数数量");
        }
    }
    private static String text(String s){return s==null?"":s.trim();}
    private Map<Long,SkuInventory> lockStocks(Collection<Long> ids){Map<Long,SkuInventory> result=new LinkedHashMap<>();for(Long id:ids.stream().sorted().toList()){SkuInventory s=stocks.lock(id);if(s==null)throw missing();result.put(id,s);}return result;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DocumentView save(Long admin,Long id,Draft input){
        validate(input);InventoryDocument d;
        if(id==null){d=new InventoryDocument();d.documentNo="IV"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);d.type=input.type();d.status="DRAFT";d.version=0L;d.createdBy=admin;d.createdAt=LocalDateTime.now();}
        else{d=documents.lock(id);if(d==null)throw missing();draftOnly(d);checkVersion(input.version(),d.version);if(!d.type.equals(input.type()))throw bad("不能变更已有单据类型");d.version++;}
        Map<Long,InventoryDocumentItem> old=new HashMap<>();if(id!=null)lines(id).forEach(i->old.put(i.skuId,i));
        Map<Long,SkuInventory> locked=lockStocks(input.items().stream().map(Line::skuId).toList());
        d.supplier=text(input.supplier());d.referenceNo=text(input.referenceNo());d.reason=text(input.reason());d.updatedAt=LocalDateTime.now();
        if(id==null)documents.insert(d);else documents.updateById(d);
        items.delete(new QueryWrapper<InventoryDocumentItem>().eq("document_id",d.id));
        for(Line l:input.items()){
            Stock c=current(l.skuId());eligible(d.type,c);InventoryDocumentItem i=new InventoryDocumentItem();i.documentId=d.id;i.skuId=l.skuId();i.productNameSnapshot=c.productName();i.specificationSnapshot=c.specification();i.quantity=l.quantity();i.countedQuantity=l.countedQuantity();
            if("STOCKTAKE".equals(d.type)){InventoryDocumentItem prev=old.get(l.skuId());if(prev==null)snapshot(i,locked.get(l.skuId()));else{i.beforeOnHand=prev.beforeOnHand;i.beforeReserved=prev.beforeReserved;i.inventoryVersion=prev.inventoryVersion;}i.quantityDelta=i.countedQuantity-i.beforeOnHand;}
            items.insert(i);
        }
        return view(d);
    }
    private static void eligible(String type,Stock c){if("PURCHASE_IN".equals(type)&&(c.deleted()!=0||c.skuStatus()!=1))throw conflict("采购入库只能选择未删除商品的启用规格");}
    private static void snapshot(InventoryDocumentItem i,SkuInventory s){i.beforeOnHand=s.getOnHand();i.beforeReserved=s.getReserved();i.inventoryVersion=s.getVersion();}
    @Transactional
    public void delete(Long id,Long version){InventoryDocument d=documents.lock(id);if(d==null)throw missing();draftOnly(d);checkVersion(version,d.version);items.delete(new QueryWrapper<InventoryDocumentItem>().eq("document_id",id));documents.deleteById(id);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DocumentView refresh(Long id,Long version){
        InventoryDocument d=documents.lock(id);if(d==null)throw missing();draftOnly(d);checkVersion(version,d.version);if(!"STOCKTAKE".equals(d.type))throw bad("只有盘点草稿可以刷新账面快照");
        List<InventoryDocumentItem> rows=lines(id);Map<Long,SkuInventory> locked=lockStocks(rows.stream().map(i->i.skuId).toList());
        for(InventoryDocumentItem i:rows){snapshot(i,locked.get(i.skuId));i.quantityDelta=i.countedQuantity-i.beforeOnHand;items.updateById(i);}
        d.version++;d.updatedAt=LocalDateTime.now();documents.updateById(d);return view(d);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DocumentView post(Long admin,Long id,Long version){
        InventoryDocument d=documents.lock(id);if(d==null)throw missing();if("POSTED".equals(d.status))return view(d);draftOnly(d);checkVersion(version,d.version);
        List<InventoryDocumentItem> rows=lines(id);Map<Long,SkuInventory> locked=lockStocks(rows.stream().map(i->i.skuId).toList());
        for(InventoryDocumentItem i:rows){
            SkuInventory s=locked.get(i.skuId);Stock c=current(i.skuId);eligible(d.type,c);
            boolean count="STOCKTAKE".equals(d.type);
            if(count&&!Objects.equals(i.inventoryVersion,s.getVersion()))throw conflict("盘点账面数据已变化，请刷新账面数据并复核实盘数量后再确认");
            long after=count?i.countedQuantity:(long)s.getOnHand()+("PURCHASE_IN".equals(d.type)?i.quantity:-i.quantity);
            if(after<s.getReserved())throw conflict(c.productName()+" / "+c.specification()+": 实物不能低于预留 "+s.getReserved()+"，当前最多可退供 "+(s.getOnHand()-s.getReserved())+"；盘点不足时请先处理预留订单");
            if(after>Integer.MAX_VALUE)throw conflict("库存数量超出支持范围");
            if(!count)snapshot(i,s);
            i.afterOnHand=(int)after;i.quantityDelta=i.afterOnHand-i.beforeOnHand;
            i.productNameSnapshot=c.productName();i.specificationSnapshot=c.specification();
            String type=count?(i.quantityDelta>0?"STOCKTAKE_GAIN":"STOCKTAKE_LOSS"):d.type;
            if(i.quantityDelta!=0)changes.change(admin,s,after,s.getReserved(),type,d.reason.isBlank()?"采购验收入库":d.reason,"DOCUMENT",d.id,i.id,d.documentNo,i.productNameSnapshot,i.specificationSnapshot);
            items.updateById(i);
        }
        d.status="POSTED";d.postedBy=admin;d.postedAt=LocalDateTime.now();d.updatedAt=d.postedAt;d.version++;documents.updateById(d);audit.record(admin,"INVENTORY_DOCUMENT_POST","INVENTORY_DOCUMENT",id);return view(d);
    }
    private static void draftOnly(InventoryDocument d){if(!"DRAFT".equals(d.status))throw conflict("已生效单据不可修改或删除，请通过新增盘点调整纠正账面差异");}
    private static void checkVersion(Long expected,Long actual){if(expected==null||!expected.equals(actual))throw conflict("版本已变化，请重新读取并复核");}
    private static ApiException bad(String msg){return new ApiException("VALIDATION_ERROR",msg,HttpStatus.BAD_REQUEST);}
    private static ApiException missing(){return new ApiException("NOT_FOUND","库存或单据不存在",HttpStatus.NOT_FOUND);}
    private static ApiException conflict(String msg){return new ApiException("INVENTORY_CONFLICT",msg,HttpStatus.CONFLICT);}
}
