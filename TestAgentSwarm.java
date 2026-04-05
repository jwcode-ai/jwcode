import com.jwcode.core.advanced.swarm.AgentSwarm;

public class TestAgentSwarm {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("     Agent Swarm Test");
        System.out.println("==================================================\n");
        
        AgentSwarm swarm = new AgentSwarm();
        
        // Test 1: Refactor task
        System.out.println("[Test 1] Refactor Task");
        System.out.println("Task: Refactor all API calls to use async/await\n");
        
        AgentSwarm.SwarmExecutionResult result1 = swarm.executeComplexTask(
            "refactor all API calls to use async/await", 
            null
        );
        
        System.out.println(result1.formatReport());
        System.out.println("\nSub-task details:");
        System.out.println(result1.getFinalResult());
        
        // Test 2: Feature development
        System.out.println("\n\n[Test 2] Feature Development Task");
        System.out.println("Task: Implement user authentication feature\n");
        
        AgentSwarm.SwarmExecutionResult result2 = swarm.executeComplexTask(
            "implement user authentication feature",
            null
        );
        
        System.out.println(result2.formatReport());
        System.out.println("\nSpeedup: " + String.format("%.1fx", result2.getSpeedup()));
        
        // Stats
        System.out.println("\n\n[Agent Swarm Statistics]");
        AgentSwarm.SwarmStats stats = swarm.getStats();
        System.out.println("Total Agents: " + stats.getTotalAgents());
        System.out.println("Active Agents: " + stats.getActiveAgents());
        System.out.println("Completed Tasks: " + stats.getCompletedTasks());
        
        System.out.println("\n[OK] Agent Swarm Test Complete!");
    }
}
