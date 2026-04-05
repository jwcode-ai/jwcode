package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * MultiPlanOutput - 多计划工具输出
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class MultiPlanOutput {
    
    private boolean success;
    private String selectedPlan;
    private List<String> allPlans;
    private Map<String, Object> metadata;
    
    public MultiPlanOutput() {
    }
    
    public MultiPlanOutput(boolean success, String selectedPlan, List<String> allPlans) {
        this.success = success;
        this.selectedPlan = selectedPlan;
        this.allPlans = allPlans;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getSelectedPlan() {
        return selectedPlan;
    }
    
    public void setSelectedPlan(String selectedPlan) {
        this.selectedPlan = selectedPlan;
    }
    
    public List<String> getAllPlans() {
        return allPlans;
    }
    
    public void setAllPlans(List<String> allPlans) {
        this.allPlans = allPlans;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
