package com.fairyshalltear.freshfruit.ai.safety;

import java.util.List;
import com.fairyshalltear.freshfruit.ai.AiDtos.Rule;

/** Withhold the longest term's suffix before any visible checkpoint or SSE delta. */
public final class AiOutputGuard {
    public static class Blocked extends RuntimeException {
        private final Rule rule;
        Blocked(Rule rule){super("OUTPUT_BLOCKED");this.rule=rule;}
        public Rule rule(){return rule;}
    }
    private final List<Rule> rules;
    private final int suffixLength;
    private String pending="";
    public AiOutputGuard(List<Rule> rules){
        this.rules=rules;
        this.suffixLength=rules.stream().filter(r->r.enabled()&&r.scope().equals("OUTPUT"))
            .flatMap(r->r.terms().stream()).mapToInt(String::length).max().orElse(1)-1;
    }
    public String append(String delta){
        pending+=delta;
        check();
        int cut=Math.max(0,pending.length()-suffixLength);
        if(cut>0 && Character.isHighSurrogate(pending.charAt(cut-1)))cut--;
        String safe=pending.substring(0,cut);pending=pending.substring(cut);return safe;
    }
    public void reset(){pending="";}
    public String finish(){check();if(!pending.isEmpty() && Character.isHighSurrogate(pending.charAt(pending.length()-1)))throw new IllegalStateException("AI_UNICODE_INVALID");String safe=pending;pending="";return safe;}
    private void check(){AiRuleService.match(rules,"OUTPUT",pending).ifPresent(rule->{pending="";throw new Blocked(rule);});}
}
