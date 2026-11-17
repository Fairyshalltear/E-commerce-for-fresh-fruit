package com.fairyshalltear.freshfruit.ai.admin;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiStatisticsService {
    private final AiMapper db;
    public AiStatisticsService(AiMapper db){this.db=db;}
    public Map<String,Object> statistics(LocalDate from,LocalDate to){
        if(from==null)from=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(6);if(to==null)to=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>=180)throw AiErrors.invalid("日期区间必须为1至180天");
        LocalDateTime start=from.atStartOfDay(),end=to.plusDays(1).atStartOfDay();
        AiRow attempts=db.one("SELECT COUNT(*) AS attempts,SUM(CASE WHEN is_retry=1 THEN 1 ELSE 0 END) AS retries,"
            +"SUM(CASE WHEN status IN ('COMPLETED','DEGRADED') THEN 1 ELSE 0 END) AS successful,"
            +"SUM(CASE WHEN status='DEGRADED' THEN 1 ELSE 0 END) AS degraded,"
            +"SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed,SUM(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END) AS cancelled,"
            +"SUM(CASE WHEN status='BLOCKED' THEN 1 ELSE 0 END) AS blocked,SUM(CASE WHEN status IN ('ACCEPTED','RUNNING') THEN 1 ELSE 0 END) AS processing,"
            +"SUM(CASE WHEN helpful=1 AND status IN ('COMPLETED','DEGRADED') THEN 1 ELSE 0 END) AS helpful,"
            +"SUM(CASE WHEN helpful IS NOT NULL AND status IN ('COMPLETED','DEGRADED') THEN 1 ELSE 0 END) AS rated,"
            +"AVG(CASE WHEN status NOT IN ('ACCEPTED','RUNNING') THEN total_ms ELSE NULL END) AS average_ms,"
            +"AVG(CASE WHEN status NOT IN ('ACCEPTED','RUNNING') THEN first_content_ms ELSE NULL END) AS first_ms "
            +"FROM ai_attempt_fact WHERE created_at>=? AND created_at<?",start,end);
        long accepted=db.count("SELECT COUNT(*) FROM ai_question_fact WHERE created_at>=? AND created_at<?",start,end);
        long successful=count(attempts,"successful"),terminal=count(attempts,"attempts")-count(attempts,"processing"),rated=count(attempts,"rated");
        List<Map<String,Object>> trend=new ArrayList<>();
        Map<LocalDate,Long> questions=new HashMap<>();db.rows("SELECT CAST(created_at AS DATE) AS metric_date,COUNT(*) AS count FROM ai_question_fact WHERE created_at>=? AND created_at<? GROUP BY CAST(created_at AS DATE)",start,end).forEach(r->questions.put(date(r.get("metric_date")),r.number("count")));
        Map<LocalDate,AiRow> byDay=new HashMap<>();db.rows("SELECT CAST(created_at AS DATE) AS metric_date,COUNT(*) AS attempts,SUM(CASE WHEN status IN ('COMPLETED','DEGRADED') THEN 1 ELSE 0 END) AS successful,SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed,SUM(CASE WHEN status='DEGRADED' THEN 1 ELSE 0 END) AS degraded FROM ai_attempt_fact WHERE created_at>=? AND created_at<? GROUP BY CAST(created_at AS DATE)",start,end).forEach(r->byDay.put(date(r.get("metric_date")),r));
        for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)){AiRow row=byDay.get(day);trend.add(values("date",day.toString(),"questions",questions.getOrDefault(day,0L),"attempts",count(row,"attempts"),"successful",count(row,"successful"),"failed",count(row,"failed"),"degraded",count(row,"degraded")));}
        var scenes=db.rows("SELECT scene,COUNT(*) AS count FROM ai_question_fact WHERE created_at>=? AND created_at<? GROUP BY scene ORDER BY count DESC",start,end).stream().map(r->values("scene",r.text("scene"),"count",r.number("count"))).toList();
        var topics=db.rows("SELECT topic,COUNT(*) AS count FROM ai_question_fact WHERE created_at>=? AND created_at<? GROUP BY topic ORDER BY count DESC,topic LIMIT 10",start,end).stream().map(r->values("topic",topic(r.text("topic")),"count",r.number("count"))).toList();
        return values("from",from.toString(),"to",to.toString(),"timezone","Asia/Shanghai","questions",accepted,"attempts",count(attempts,"attempts"),"retries",count(attempts,"retries"),
            "successful",successful,"degraded",count(attempts,"degraded"),"failed",count(attempts,"failed"),"cancelled",count(attempts,"cancelled"),"blocked",count(attempts,"blocked"),"processing",count(attempts,"processing"),
            "successRate",ratio(successful,terminal),"rated",rated,"helpful",count(attempts,"helpful"),"satisfaction",ratio(count(attempts,"helpful"),rated),"feedbackCoverage",ratio(rated,successful),
            "averageMs",attempts.get("average_ms"),"firstContentMs",attempts.get("first_ms"),"trend",trend,"scenes",scenes,"topics",topics);
    }
    private String topic(String value){if(value.startsWith("FAQ#"))try{return "FAQ："+com.fairyshalltear.freshfruit.ai.safety.AiPrivacy.external(db.one("SELECT title FROM ai_knowledge_document WHERE id=?",Long.parseLong(value.substring(4))).text("title"));}catch(RuntimeException ignored){return "已发布FAQ";}return value;}
    private long count(AiRow row,String key){return row==null||row.number(key)==null?0:row.number(key);}
    private Double ratio(long numerator,long denominator){return denominator==0?null:numerator/(double)denominator;}
    private LocalDate date(Object value){return value instanceof java.sql.Date date?date.toLocalDate():value instanceof LocalDate date?date:LocalDate.parse(value.toString());}
}
