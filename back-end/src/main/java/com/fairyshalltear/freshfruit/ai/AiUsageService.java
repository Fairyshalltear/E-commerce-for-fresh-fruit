package com.fairyshalltear.freshfruit.ai;

import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fairyshalltear.freshfruit.ai.persistence.AiMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;

@Service
public class AiUsageService {
    private record Window(long minute,int count) {}
    private final Map<String,Window> windows=new LinkedHashMap<>();
    private final AiMapper db;
    public AiUsageService(AiMapper db){this.db=db;}
    public synchronized void frequency(String identity,String ip,boolean guest,AiDtos.Parameters p){
        long minute=Instant.now().getEpochSecond()/60;
        windows.entrySet().removeIf(e->e.getValue().minute()<minute);
        if(windows.size()>10000) throw limited();
        String address="IP:"+ip;
        int limit=guest?p.guestPerMinute():p.buyerPerMinute();
        Window user=windows.get(identity),source=windows.get(address);
        if(user!=null && user.count()>=limit || guest && source!=null && source.count()>=limit) throw limited();
        windows.put(identity,new Window(minute,user==null?1:user.count()+1));
        if(guest) windows.put(address,new Window(minute,source==null?1:source.count()+1));
    }
    @Transactional
    public boolean reserve(String scope,int limit){
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        // UPSERT establishes/locks one row, so concurrent counters cannot exceed the limit.
        db.update("INSERT INTO ai_usage_counter(scope_key,usage_date,count_value) VALUES(?,?,0) ON DUPLICATE KEY UPDATE count_value=count_value",scope,date);
        return db.update("UPDATE ai_usage_counter SET count_value=count_value+1 WHERE scope_key=? AND usage_date=? AND count_value<?",scope,date,limit)==1;
    }
    private ApiException limited(){return new ApiException("AI_RATE_LIMIT","提问过于频繁，请在下一分钟重试",HttpStatus.TOO_MANY_REQUESTS,Map.of("retryAfter",60-Instant.now().getEpochSecond()%60));}
}
