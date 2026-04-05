package com.jwcode.core.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentManager - Agent 管理器
 * 
 * 功能说明：
 * 管理所有 Agent 的生命周期，包括创建、启动、停止、销毁等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AgentManager {
    
    private final Map<String, Agent> agents;
    private final Map<String, AgentState> agentStates;
    private final AgentListener listener;
    
    public AgentManager() {
        this(null);
    }
    
    public AgentManager(AgentListener listener) {
        this.agents = new ConcurrentHashMap<>();
        this.agentStates = new ConcurrentHashMap<>();
        this.listener = listener;
        initializeBuiltInAgents();
    }
    
    /**
     * 初始化内置 Agent
     */
    private void initializeBuiltInAgents() {
        // 注册内置 Agent
        registerAgent(new Agent("general", "通用助手", AgentType.GENERAL));
        registerAgent(new Agent("coding", "编码专家", AgentType.CODING));
        registerAgent(new Agent("review", "代码审查", AgentType.REVIEW));
        registerAgent(new Agent("test", "测试专家", AgentType.TEST));
        registerAgent(new Agent("debug", "调试专家", AgentType.DEBUG));
    }
    
    /**
     * 注册 Agent
     */
    public void registerAgent(Agent agent) {
        agents.put(agent.id, agent);
        agentStates.put(agent.id, new AgentState(agent.id));
        if (listener != null) {
            listener.onAgentRegistered(agent);
        }
    }
    
    /**
     * 注销 Agent
     */
    public void unregisterAgent(String agentId) {
        Agent agent = agents.remove(agentId);
        if (agent != null) {
            stopAgent(agentId).join();
            agentStates.remove(agentId);
            if (listener != null) {
                listener.onAgentUnregistered(agent);
            }
        }
    }
    
    /**
     * 获取 Agent
     */
    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }
    
    /**
     * 获取所有 Agent
     */
    public List<Agent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }
    
    /**
     * 获取活动的 Agent
     */
    public List<Agent> getActiveAgents() {
        List<Agent> active = new ArrayList<>();
        for (Map.Entry<String, Agent> entry : agents.entrySet()) {
            AgentState state = agentStates.get(entry.getKey());
            if (state != null && state.isActive) {
                active.add(entry.getValue());
            }
        }
        return active;
    }
    
    /**
     * 启动 Agent
     */
    public CompletableFuture<Boolean> startAgent(String agentId) {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                AgentState state = agentStates.get(agentId);
                if (state == null) {
                    state = new AgentState(agentId);
                    agentStates.put(agentId, state);
                }
                
                state.isActive = true;
                state.status = "运行中";
                
                if (listener != null) {
                    listener.onAgentStarted(agent);
                }
                return true;
            } catch (Exception e) {
                if (listener != null) {
                    listener.onAgentError(agent, e);
                }
                return false;
            }
        });
    }
    
    /**
     * 停止 Agent
     */
    public CompletableFuture<Boolean> stopAgent(String agentId) {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            AgentState state = agentStates.get(agentId);
            if (state != null) {
                state.isActive = false;
                state.status = "已停止";
            }
            
            if (listener != null) {
                listener.onAgentStopped(agent);
            }
            return true;
        });
    }
    
    /**
     * 获取 Agent 状态
     */
    public AgentState getAgentState(String agentId) {
        return agentStates.get(agentId);
    }
    
    /**
     * 设置 Agent 状态
     */
    public void setAgentState(String agentId, String status, Map<String, Object> context) {
        AgentState state = agentStates.get(agentId);
        if (state != null) {
            state.status = status;
            if (context != null) {
                state.context.putAll(context);
            }
        }
    }
    
    /**
     * 获取 Agent 状态历史
     */
    public List<String> getAgentStateHistory(String agentId) {
        AgentState state = agentStates.get(agentId);
        if (state != null) {
            return state.history;
        }
        return new ArrayList<>();
    }
    
    /**
     * 清空 Agent 状态历史
     */
    public void clearAgentStateHistory(String agentId) {
        AgentState state = agentStates.get(agentId);
        if (state != null) {
            state.history.clear();
        }
    }
    
    /**
     * Agent 监听器接口
     */
    public interface AgentListener {
        void onAgentRegistered(Agent agent);
        void onAgentUnregistered(Agent agent);
        void onAgentStarted(Agent agent);
        void onAgentStopped(Agent agent);
        void onAgentError(Agent agent, Throwable error);
    }
    
    /**
     * Agent 状态类
     */
    public static class AgentState {
        public final String agentId;
        public String status;
        public boolean isActive;
        public final Map<String, Object> context;
        public final List<String> history;
        
        public AgentState(String agentId) {
            this.agentId = agentId;
            this.status = "待命";
            this.isActive = false;
            this.context = new HashMap<>();
            this.history = new ArrayList<>();
        }
        
        public void addHistory(String event) {
            history.add("[" + System.currentTimeMillis() + "] " + event);
        }
    }
    
    /**
     * Agent 类型枚举
     */
    public enum AgentType {
        GENERAL("通用助手"),
        CODING("编码专家"),
        REVIEW("代码审查"),
        TEST("测试专家"),
        DEBUG("调试专家");
        
        private final String displayName;
        
        AgentType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Agent 类
     */
    public static class Agent {
        public final String id;
        public final String name;
        public final AgentType type;
        public String description;
        public Map<String, Object> settings;
        
        public Agent(String id, String name, AgentType type) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.settings = new HashMap<>();
            initDescription();
        }
        
        private void initDescription() {
            switch (type) {
                case GENERAL:
                    this.description = "通用助手，可以回答各种问题并提供一般性帮助";
                    break;
                case CODING:
                    this.description = "编码专家，擅长编写、重构和优化代码";
                    break;
                case REVIEW:
                    this.description = "代码审查，帮助发现代码中的问题和改进建议";
                    break;
                case TEST:
                    this.description = "测试专家，帮助编写测试用例和执行测试";
                    break;
                case DEBUG:
                    this.description = "调试专家，帮助定位和修复代码中的 bug";
                    break;
            }
        }
        
        public void setSetting(String key, Object value) {
            settings.put(key, value);
        }
        
        public Object getSetting(String key) {
            return settings.get(key);
        }
    }
}