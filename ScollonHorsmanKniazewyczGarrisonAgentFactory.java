import jig.misc.rd.ai.AgentFactory;
import jig.misc.rd.ai.RobotDefenseAgent;




public class ScollonHorsmanKniazewyczGarrisonAgentFactory implements AgentFactory {

	public RobotDefenseAgent createAgent(String name, String agentResource) {
		return new ScollonHorsmanKniazewyczGarrisonAgent();
	}

}
