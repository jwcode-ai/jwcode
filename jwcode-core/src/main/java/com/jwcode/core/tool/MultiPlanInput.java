package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * MultiPlanInput - 多计划工具输入
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class MultiPlanInput {
    
    private String query;
    private List<String> plans;
    private Map<String, Object> options;
    
    public MultiPlanInput() {
    }
    
    public MultiPlanInput(String query, List<String> plans) {
        this.query = query;
        this.plans = plans;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public List<String> getPlans() {
        return plans;
    }
    
    public void setPlans(List<String> plans) {
        this.plans = plans;
    }
    
    public Map<String, Object> getOptions() {
        return options;
    }
    
    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}
