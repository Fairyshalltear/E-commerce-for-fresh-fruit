package com.fairyshalltear.freshfruit.catalog;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fairyshalltear.freshfruit.catalog.entity.ProductImage;
import com.fairyshalltear.freshfruit.catalog.mapper.ProductImageMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import org.springframework.http.HttpStatus;
import static com.fairyshalltear.freshfruit.catalog.CatalogErrors.*;
@Service
public class ProductImageService {
    public static final String PREFIX="/api/v1/public/product-images/";
    private final ProductImageMapper images;private final Path root;
    public ProductImageService(ProductImageMapper images,@Value("${freshfruit.product-images.storage-dir:./uploads/products}") String directory) {
        this.images=images;this.root=Path.of(directory).toAbsolutePath().normalize();
        try { Files.createDirectories(root); }catch(IOException ex){throw new IllegalStateException("Cannot initialize product image directory",ex);}
    }
    @Transactional public CatalogDtos.Image upload(Long admin,MultipartFile file) {
        if(file==null||file.isEmpty())throw invalid("请选择商品图片");
        if(file.getSize()>5L*1024*1024)throw new ApiException("PAYLOAD_TOO_LARGE","图片不能超过 5 MiB",HttpStatus.PAYLOAD_TOO_LARGE);
        String ext=switch(Objects.toString(file.getContentType(),"")) {
            case "image/jpeg" -> "jpg";case "image/png" -> "png";case "image/webp" -> "webp";
            default -> throw invalid("仅支持 JPEG、PNG 或 WebP 图片");
        };
        byte[] content;
        try {
            content=file.getBytes();
            boolean signature=switch(ext) {
                case "jpg" -> content.length>=3 && (content[0]&255)==255 && (content[1]&255)==216 && (content[2]&255)==255;
                case "png" -> content.length>=8 && Arrays.equals(Arrays.copyOf(content,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
                default -> content.length>=12 && new String(content,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(content,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            };
            if(!signature||ImageIO.read(new ByteArrayInputStream(content))==null)throw invalid("图片内容损坏或与类型不匹配");
        }catch(IOException ex){throw invalid("无法解码图片");}
        ProductImage row=new ProductImage();row.setStorageKey(UUID.randomUUID()+"."+ext);row.setUploadedBy(admin);
        row.setActive(0);row.setSortOrder(0);Path path=resolve(row.getStorageKey());
        try {
            Files.write(path,content,StandardOpenOption.CREATE_NEW);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)deleteQuietly(path);}
            });
            images.insert(row);
            return dto(row);
        }catch(IOException ex){deleteQuietly(path);throw new ApiException("IMAGE_STORAGE_ERROR","图片保存失败",HttpStatus.INTERNAL_SERVER_ERROR);}
    }
    public Path read(String filename) {
        Path path=resolve(filename);
        if(!Files.isRegularFile(path)||images.selectCount(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getStorageKey,filename))==0)throw notFound();
        return path;
    }
    private Path resolve(String filename) {
        if(filename==null||!filename.matches("[0-9a-f-]{36}\\.(jpg|png|webp)"))throw notFound();
        Path p=root.resolve(filename).normalize();if(!p.startsWith(root))throw notFound();return p;
    }
    @Transactional public void bind(Long admin,Long product,List<Long> ids) {
        if(ids.size()>10 || new HashSet<>(ids).size()!=ids.size())throw invalid("最多 10 张图片且不能重复");
        List<ProductImage> selected=new ArrayList<>();
        for(Long id:ids) {
            ProductImage row=images.lock(id);
            if(row==null || (row.getProductId()==null ? !admin.equals(row.getUploadedBy()) : !product.equals(row.getProductId())))
                throw invalid("图片不存在或不属于当前商品/管理员");
            selected.add(row);
        }
        images.update(null,new LambdaUpdateWrapper<ProductImage>().eq(ProductImage::getProductId,product).set(ProductImage::getActive,0));
        for(int i=0;i<selected.size();i++) {
            ProductImage row=selected.get(i);row.setProductId(product);row.setActive(1);row.setSortOrder(i);images.updateById(row);
        }
    }
    public List<ProductImage> rows(Long product) {
        return images.selectList(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId,product)
                .orderByAsc(ProductImage::getSortOrder).orderByAsc(ProductImage::getId));
    }
    public static CatalogDtos.Image dto(ProductImage row){return new CatalogDtos.Image(row.getId(),PREFIX+row.getStorageKey());}
    private void deleteQuietly(Path path){try{Files.deleteIfExists(path);}catch(IOException ignored){}}
}
