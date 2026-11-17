package com.fairyshalltear.freshfruit.ai.safety;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fairyshalltear.freshfruit.ai.*;
import com.fairyshalltear.freshfruit.ai.persistence.*;
import com.fairyshalltear.freshfruit.audit.AuditService;
import static com.fairyshalltear.freshfruit.ai.AiDtos.*;
import static com.fairyshalltear.freshfruit.ai.AiErrors.*;
import static com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values;

@Service
public class AiRuleService {
    private final AiMapper db;private final AiJson json;private final AuditService audit;
    public AiRuleService(AiMapper db,AiJson json,AuditService audit){this.db=db;this.json=json;this.audit=audit;}
    public List<Rule> list(){return db.rows("SELECT * FROM ai_sensitive_rule ORDER BY priority DESC,id").stream().map(this::view).toList();}
    public Page<Rule> page(int page,int size,String scope){
        String where=scope==null||scope.isBlank()?"":" WHERE scope=?";
        Object[] countArgs=where.isEmpty()?new Object[]{}:new Object[]{scope};
        long total=db.count("SELECT COUNT(*) FROM ai_sensitive_rule"+where,countArgs);
        Object[] args=where.isEmpty()?new Object[]{size,(long)(page-1)*size}:new Object[]{scope,size,(long)(page-1)*size};
        return new Page<>(db.rows("SELECT * FROM ai_sensitive_rule"+where+" ORDER BY priority DESC,id LIMIT ? OFFSET ?",args).stream().map(this::view).toList(),total,page,size);
    }
    public List<Rule> snapshot(){return list().stream().filter(Rule::enabled).toList();}
    @Transactional
    public Rule save(long admin,Long id,RuleInput input){
        if(input.scope().equals("OUTPUT") && !input.matchMode().equals("LITERAL"))throw invalid("输出规则仅支持文字匹配");
        List<String> terms=input.terms().stream().map(String::strip).distinct().toList();
        if(id==null){id=db.insert("ai_sensitive_rule",values("name",input.name().strip(),"scope",input.scope(),"match_mode",input.matchMode(),
            "terms_json",json.write(terms),"priority",input.priority(),"message",input.message(),"enabled",input.enabled()?1:0,"created_by",admin));}
        else if(input.version()==null || db.update("UPDATE ai_sensitive_rule SET name=?,scope=?,match_mode=?,terms_json=?,priority=?,message=?,enabled=?,version=version+1,updated_at=? WHERE id=? AND version=?",
            input.name().strip(),input.scope(),input.matchMode(),json.write(terms),input.priority(),input.message(),input.enabled()?1:0,LocalDateTime.now(),id,input.version())!=1)
                throw conflict("规则版本已变化，请刷新后重试");
        audit.record(admin,"AI_RULE_SAVE","AI_RULE",id);return view(db.one("SELECT * FROM ai_sensitive_rule WHERE id=?",id));
    }
    @Transactional
    public Rule enabled(long admin,long id,EnabledInput input){
        if(db.update("UPDATE ai_sensitive_rule SET enabled=?,version=version+1,updated_at=? WHERE id=? AND version=?",input.enabled()?1:0,LocalDateTime.now(),id,input.version())!=1)throw conflict("规则版本已变化，请刷新后重试");
        audit.record(admin,"AI_RULE_ENABLE","AI_RULE",id);return view(db.one("SELECT * FROM ai_sensitive_rule WHERE id=?",id));
    }
    public static Optional<Rule> match(List<Rule> rules,String scope,String text){
        return rules.stream().filter(r->r.enabled()&&r.scope().equals(scope)).sorted(Comparator.comparingInt(Rule::priority).reversed().thenComparingLong(Rule::id))
            .filter(r->r.matchMode().equals("ALL")?r.terms().stream().allMatch(text::contains):r.terms().stream().anyMatch(text::contains)).findFirst();
    }
    public void hit(long request,Rule rule,String stage){
        db.insert("ai_rule_hit",values("request_id",request,"rule_id",rule.id(),"rule_version",rule.version(),"stage",stage,"summary","命中规则 #"+rule.id()+"（"+stage+"），原文未记录"));
    }
    public void denied(long request,String reason){
        db.insert("ai_rule_hit",values("request_id",request,"rule_id",null,"rule_version",0,"stage","BUSINESS","summary","本人业务查询拒绝："+reason+"，对象及原文未记录"));
    }
    private Rule view(AiRow r){return new Rule(r.id(),r.text("name"),r.text("scope"),r.text("match_mode"),json.strings(r.get("terms_json")),r.integer("priority"),r.text("message"),r.flag("enabled"),r.number("version"));}
}
