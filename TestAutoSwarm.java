import com.jwcode.core.advanced.swarm.AgentSwarm;
import com.jwcode.core.advanced.swarm.AutoSwarmTrigger;

public class TestAutoSwarm {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("     Auto Agent Swarm Test");
        System.out.println("==================================================\n");
        
        AgentSwarm swarm = new AgentSwarm();
        AutoSwarmTrigger trigger = new AutoSwarmTrigger(swarm);
        
        // Test 1: Analyze simple task
        System.out.println("[Test 1] Analyze Simple Task");
        String simpleTask = "hello";
        AutoSwarmTrigger.TaskAnalysis analysis1 = trigger.analyzeTask(simpleTask);
        System.out.println(analysis1.formatReport());
        System.out.println("Should use Swarm: " + analysis1.isShouldUseSwarm());
        
        // Test 2: Analyze complex task
        System.out.println("\n[Test 2] Analyze Complex Task");
        String complexTask = "refactor all API endpoints to use async/await pattern";
        AutoSwarmTrigger.TaskAnalysis analysis2 = trigger.analyzeTask(complexTask);
        System.out.println(analysis2.formatReport());
        System.out.println("Should use Swarm: " + analysis2.isShouldUseSwarm());
        
        // Test 3: Another complex task
        System.out.println("\n[Test 3] Analyze Feature Development");
        String featureTask = "implement user authentication feature with login, logout, and password reset";
        AutoSwarmTrigger.TaskAnalysis analysis3 = trigger.analyzeTask(featureTask);
        System.out.println(analysis3.formatReport());
        System.out.println("Should use Swarm: " + analysis3.isShouldUseSwarm());
        
        // Test 4: Auto execute
        System.out.println("\n[Test 4] Auto Execute Mode");
        trigger.toggleAutoSwarm(); // Enable auto swarm
        System.out.println("Auto Swarm Enabled: " + trigger.isAutoSwarmEnabled());
        
        System.out.println("\n[OK] Auto Swarm Test Complete!");
    }
}
