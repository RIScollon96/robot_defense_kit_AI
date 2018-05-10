


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Random;

import jig.misc.rd.AirCurrentGenerator;
import jig.misc.rd.Direction;
import jig.misc.rd.RobotDefense;


/**
 *  A simple agent that uses reinforcement learning to direct the vacuum
 *  The agent has the following critical limitations:
 *  
 *  	- it only considers a small set of possible actions
 *  	- it does not consider turning the vacuum off
 *  	- it only reconsiders an action when the 'local' state changes  
 *         in some cases this may take a (really) long time
 *      - it uses a very simplisitic action selection mechanism
 *      - actions are based only on the cells immediately adjacent to a tower
 *      - action values are not dependent (at all) on the resulting state 
 */
public class ScollonHorsmanKniazewyczGarrisonAgent extends BaseLearningAgent {

	/**
	 * A Map of states to actions
	 * 
	 *  States are encoded in the StateVector objects
	 *  Actions are associated with a utility value and stored in the QMap
	 */
	HashMap<StateVector,QMap> actions = new HashMap<StateVector,QMap>();

	/**
	 * The agent's sensor system tracks /how many/ insects a particular generator
	 * captures, but here I want to know /when/ an air current generator just
	 * captured an insect so I can reward the last action. We use this captureCount
	 * to see when a new capture happens.
	 */
	HashMap<AirCurrentGenerator, Integer> captureCount;

	/**
	 * Keep track of the agent's last action so we can reward it
	 */
	HashMap<AirCurrentGenerator, AgentAction> lastAction;
	
	/**
	 * This stores the possible actions that an agent many take in any
	 * particular state.
	 */
	long oldDeltaMS = 0;

	private static final AgentAction [] potentials;

	static 
	{
		Direction [] dirs = Direction.values();
		potentials = new AgentAction[dirs.length*3];

		int i = 0;
		for(Direction d: dirs) 
		{
			// creates a new directional action with the power set to full
			// power can range from 1 ... AirCurrentGenerator.POWER_SETTINGS
			potentials[i] = new AgentAction(0, d);
			potentials[i+1] = new AgentAction(2, d);
			potentials[i+2] = new AgentAction(4, d);
			i+=3;
		}

	}
	
	public ScollonHorsmanKniazewyczGarrisonAgent() 
	{
		captureCount = new HashMap<AirCurrentGenerator,Integer>();
		lastAction = new HashMap<AirCurrentGenerator,AgentAction>();		
	}
	
	/**
	 * Step the agent by giving it a chance to manipulate the environment.
	 * 
	 * Here, the agent should look at information from its sensors and 
	 * decide what to do.
	 * 
	 * @param deltaMS the number of milliseconds since the last call to step
	 * 
	 */
	public void step(long deltaMS) 
	{
		StateVector state;
		QMap qmap;
		QMap qmap2;
		
		//System.out.println("This is SPARTA... or just the deltaMS: "+deltaMS);

		// This must be called each step so that the performance log is 
		// updated.
		updatePerformanceLog();
		//int counter = 0; 
		for (AirCurrentGenerator acg : sensors.generators.keySet()) 
		{
			
			if(!stateChanged(acg))
			{
				state = thisState.get(acg);
				int hash = state.hashCode() - acg.getGridWidth() - acg.getGridHeight() - acg.getClass().hashCode();		
				if(hash == 0)
				{
					potentials[0].doAction(acg);
				}		
				
				continue;
			}
			// Check the current state, and make sure member variables are
			// initialized for this particular state...
			state = thisState.get(acg);
			if (actions.get(state) == null) 
			{
				actions.put(state, new QMap(potentials));
			}
			if (captureCount.get(acg) == null) captureCount.put(acg, 0);


			// Check to see if an insect was just captured by comparing our
			// cached value of the insects captured by each ACG with the
			// most up-to-date value from the sensors
			boolean justCaptured;
			justCaptured = (captureCount.get(acg) < sensors.generators.get(acg));

			// if this ACG has been selected by the user, we'll do some verbose printing
			boolean verbose = (RobotDefense.getGame().getSelectedObject() == acg);

			
			qmap = actions.get(state);
			int[] code = state.cellContentsCode();
			
			int i=0;
			
			AgentAction bestAction = qmap.findBestAction(verbose, lastAction.get(acg));
			for(int thing : code)
			{
				if(thing != 0)
				{
					StateVector otherState = StateVector.calcOtherState(acg, sensors, i, thing);
					if (actions.get(otherState) == null) 
					{
						actions.put(otherState, new QMap(potentials));
					}
					
					QMap otherQmap = actions.get(otherState);						
						
					AgentAction otherBest = otherQmap.findBestAction(verbose, lastAction.get(acg));
					if(otherQmap.actionUtility(otherBest) > qmap.actionUtility(bestAction))
					{
						bestAction = otherBest;
					}
				}
				i++;
			}
			
			
			
			
			// If we did something on the last 'turn', we need to reward it
			if (lastAction.get(acg) != null ) 
			{

				// get the action map associated with the previous state
				qmap2 = actions.get(lastState.get(acg));
				
				

				if (justCaptured) 
				{
					// capturing insects is good
					qmap2.rewardAction(lastAction.get(acg), 10.0, bestAction);
					captureCount.put(acg,sensors.generators.get(acg));
				}
				else
				{
					qmap2.rewardAction(lastAction.get(acg), -15.0, bestAction);
				}

				if (verbose) 
				{
					System.out.println("Last State for " + acg.toString() );
					System.out.println(lastState.get(acg).representation());
					System.out.println("Updated Last Action: " + qmap.getQRepresentation());
				}
				//pointing in direction of bug is rewarding. 
				/*
				state.southOfTower(acg, sensors) != 0;
				state.northOfTower(acg, sensors) != 0;
				state.westOfTower(acg, sensors) != 0;
				state.eastOfTower(acg, sensors) != 0;
				*/
				
				if(state.southOfTower(acg, sensors) != 0) 
				{
					if(state.westOfTower(acg, sensors) != 0){
						qmap.rewardAction(potentials[7], 5.0, bestAction);
						qmap.rewardAction(potentials[8], 5.0, bestAction);
					}
					else if(state.eastOfTower(acg, sensors) != 0){
						qmap.rewardAction(potentials[1], 5.0, bestAction);
						qmap.rewardAction(potentials[2], 5.0, bestAction);
					}
					else{
						qmap.rewardAction(potentials[4], 1.0, bestAction);
						qmap.rewardAction(potentials[5], 1.0, bestAction);
					}
				}
				if(state.northOfTower(acg, sensors) != 0)
				{
					if(state.westOfTower(acg, sensors) != 0){
						qmap.rewardAction(potentials[13], 5.0, bestAction);
						qmap.rewardAction(potentials[14], 5.0, bestAction);
					}
					else if(state.eastOfTower(acg, sensors) != 0){
						qmap.rewardAction(potentials[19], 1.0, bestAction);
						qmap.rewardAction(potentials[20], 1.0, bestAction);
					}
					else{
						qmap.rewardAction(potentials[16], 1.0, bestAction);
						qmap.rewardAction(potentials[17], 1.0, bestAction);
					}
				}
				if(state.westOfTower(acg, sensors) != 0)
				{
					qmap.rewardAction(potentials[10], 1.0, bestAction);
					qmap.rewardAction(potentials[11], 1.0, bestAction);
				}
				if(state.eastOfTower(acg, sensors) != 0)
				{
					qmap.rewardAction(potentials[22], 1.0, bestAction);
					qmap.rewardAction(potentials[23], 1.0, bestAction);
				}
				
				
			} 

			// decide what to do now...
			// first, get the action map associated with the current state
			
			if (verbose) 
			{
				System.out.println("This State for Tower " + acg.toString() );
				System.out.println(thisState.get(acg).representation());
			}
			// find the 'right' thing to do, and do it.
						
			//if no bugs are around, do these things. 
			int hash = state.hashCode() - acg.getGridWidth() - acg.getGridHeight() - acg.getClass().hashCode();		
			System.out.println(hash);
			if(hash == 0)
			{
				qmap.rewardAction(potentials[0], 5.0, bestAction);	
			}
			else
			{
				qmap.rewardAction(potentials[0], -2.0, bestAction);
				qmap.rewardAction(potentials[3], -2.0, bestAction);
				qmap.rewardAction(potentials[6], -2.0, bestAction);
				qmap.rewardAction(potentials[9], -2.0, bestAction);
				qmap.rewardAction(potentials[12], -2.0, bestAction);
				qmap.rewardAction(potentials[15], -2.0, bestAction);
				qmap.rewardAction(potentials[18], -2.0, bestAction);
				qmap.rewardAction(potentials[21], -2.0, bestAction);
				
			}
			
			
			
			//bestAction = qmap.findBestAction(verbose, lastAction.get(acg));
			
			bestAction.doAction(acg);

			// finally, store our action so we can reward it later.
			lastAction.put(acg, bestAction);

		}
	}


	/**
	 * This inner class simply helps to associate actions with utility values
	 */
	static class QMap 
	{
		static Random RN = new Random();

		private double[] utility; 		// current utility estimate
		private int[] attempts;			// number of times action has been tried
		private AgentAction[] actions;  // potential actions to consider

		public QMap(AgentAction[] potential_actions) 
		{

			actions = potential_actions.clone();
			int len = actions.length;

			utility = new double[len];
			attempts = new int[len];
			for(int i = 0; i < len; i++) 
			{
				utility[i] = 0.0;
				attempts[i] = 0;
			}
		}
		

		/**
		 * Finds the 'best' action for the agent to take.
		 * 
		 * @param verbose
		 * @return
		 */
		public AgentAction findBestAction(boolean verbose, AgentAction last) 
		{
			int i,maxi,maxcount;
			maxi=0;
			maxcount = 1;
			
			if (verbose)
				System.out.print("Picking Best Actions: " + getQRepresentation());
			

			boolean same = false;
			int same_i = -1;
			for (i = 1; i < utility.length; i++) 
			{
				if (utility[i] > utility[maxi]) 
				{
					maxi = i;
					maxcount = 1;
					
					if (actions[i] == last)
					{
						same = true;
						same_i = i;
					}
					else same = false;					
				}
				else if (utility[i] == utility[maxi]) 
				{
					maxcount++;
					
					if (actions[i] == last)
					{
						same = true;
						same_i = i;
					}					
				}
			}
			
			if (RN.nextDouble() > .2) 
			{
				int whichMax;
				
				if (same)
				{
					if (utility[same_i] != 0)
					{
						if (verbose)
							System.out.println(" -- Doing same");
						return actions[same_i];
					}
				}
				
				whichMax = RN.nextInt(maxcount);

				if (verbose)
					System.out.println( " -- Doing Best! #" + whichMax);

				for (i = 0; i < utility.length; i++) 
				{
					if (utility[i] == utility[maxi]) 
					{
						if (whichMax == 0) return actions[i];
						whichMax--;
					}
				}
				return actions[maxi];
			}
			else 
			{
				int which = RN.nextInt(actions.length);
				if (verbose)
					System.out.println( " -- Doing Random (" + which + ")!!");

				return actions[which];
			}
		}

		/**
		 * Modifies an action value by associating a particular reward with it.
		 * 
		 * @param a the action performed 
		 * @param value the reward received
		 */
		public void rewardAction(AgentAction a, double value, AgentAction currentBest) 
		{
			int i,j;
			for (i = 0; i < actions.length; i++) 
			{
				if (a == actions[i]) break;

			}
			for (j = 0; j < actions.length; j++)
			{
				if (currentBest == actions[j]) break;
				
			}
			if (i >= actions.length) 
			{
				System.err.println("ERROR: Tried to reward an action that doesn't exist in the QMap. (Ignoring reward)");
				return;
			}
			//if(a.getPower() == 4) 
			//	value = value/1.5;

			utility[i] += value + .5 * utility[j];
			attempts[i] = attempts[i] + 1;
			utility[i] = utility[i]/attempts[i];
		}
		
		public double actionUtility(AgentAction a) 
		{
			for (int i = 0; i < actions.length; i++) 
			{
				if (a == actions[i]) return utility[i];
			}
			return 0.0;
		} 
		/**
		 * Gets a string representation (for debugging).
		 * 
		 * @return a simple string representation of the action values
		 */
		public String getQRepresentation() 
		{
			StringBuffer sb = new StringBuffer(80);

			for (int i = 0; i < utility.length; i++) {
				sb.append(String.format("%.2f  ", utility[i]));
			}
			return sb.toString();

		}

	}
}