package qlearn;

import game.PacManSimulator;
import game.controllers.ghosts.game.GameGhosts;
import game.controllers.pacman.PacManHijackController;
import game.core.G;
import game.core.Game;
import game.core.Game.DM;
import game.core.GameState;



/* This class implements the learning agent for PacMan game. It chooses a direction every time it gets to an intersection, and also if it
 * notice a near ghost.
 * */
public class QLearnDirPacMan extends PacManHijackController{

	public static boolean greedy = false;					
	public static int greedyDir, prevDir;
	GameState lastState = null;  
	
	@Override
	public void tick(Game game, long timeDue) {
		
		
		// Antonio: hack para tomar decisiones sólo cuando cambia el estado
		if ((lastState != null) && Q_learn.normalizedDistance(Q_learn.compareCases(Q_learn.currState, lastState)) <= Q_learn.newCaseThreshold) {
			return;
		} else {
			lastState = Q_learn.currState;
		}
		
		
		int nextDir;
		float rnd = G.rnd.nextFloat();
		
		if(Q_learn.closeGhost() /*|| rnd < Q_learn.eps*/)
			nextDir = getAction(game, true);
		else
			nextDir = getAction(game, false);
		
		pacman.set(nextDir);
		Q_learn.currAction = nextDir;
		Q_learn.reward = 0;
	}
	
	public static int getAction(Game game, boolean reversal){
		int[] directions=game.getPossiblePacManDirs(reversal);		//set flag as true to include reversals		
		int nextDir;
		
		if(G.rnd.nextFloat()<Q_learn.eps){						// Eps. greedy
			greedy = true;
			nextDir = directions[G.rnd.nextInt(directions.length)];
			greedyDir = nextDir;
			prevDir = nextDir;
			//System.out.println("Random move!!  Now greedy direction is: " + greedyDir);
		}
		else{
			nextDir = Q_learn.maxQRowDirIndex(directions, Q_learn.currState.index);
			prevDir = nextDir;
		}
		
		return nextDir;
	}
	
	public static void main(String[] args) {
		PacManSimulator.play(new QLearnDirPacMan(), new GameGhosts(false));
	}
	
	private void printDir(int index){
		
		switch(index){
			case 0: System.out.println("UP"); break;
			case 1: System.out.println("RIGHT"); break;
			case 2: System.out.println("DOWN"); break;
			case 3: System.out.println("LEFT"); break;
			default: break;
		}
		
	}

}
