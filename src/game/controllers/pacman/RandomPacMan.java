package game.controllers.pacman;

import game.PacManSimulator;
import game.controllers.ghosts.game.GameGhosts;
import game.core.G;
import game.core.Game;
import game.core.GameState;
import qlearn.Q_learn;

public final class RandomPacMan extends PacManHijackController
{
	GameState lastState = null;  // Antonio: estado nuevo?
	
	
	
	
	@Override
	public void tick(Game game, long timeDue) {
		int[] directions=game.getPossiblePacManDirs(true);		//set flag as true to include reversals		
		
		
		
		if(game.isJunction(game.getCurPacManLoc()) || game.isCorner(game.getCurPacManLoc()))
			pacman.set(directions[G.rnd.nextInt(directions.length)]);
	}
	
	public static void main(String[] args) {
		PacManSimulator.play(new RandomPacMan(), new GameGhosts(false));
	}
}