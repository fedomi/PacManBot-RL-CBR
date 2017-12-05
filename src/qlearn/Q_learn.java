package qlearn;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.lang.*;

import game.PacManSimulator;
import game.controllers.ghosts.game.GameGhosts;
import game.controllers.pacman.PacManAction;
import game.controllers.pacman.PacManHijackController;
import game.core.G;
import game.core.Game;
import game.core.GameState;


/* 
 * This class implements whole functional Q-learning environment. Its methods are used by PacMan simulator to make the bot learn
 * from experience in order to improve its performance.
 * */
public class Q_learn 
{	
	/**
	 * Q-Learning attributes.
	 * If 'learning' is true, the program will simulate an amount of 'n_ep' of runs, updating learning data. 
	 * */
	
	public static boolean learning = true;													// True if program learns from each action
	public static boolean load = true;														// True if PacMan start with information from previous experiments
	public static boolean update = false;													// True if current learning is updated during next simulations
	public static int n_ep = 100;															// Number of simulation episodes
					
	public static ArrayList<ArrayList<Float>> Q = new ArrayList<ArrayList<Float>>();		// Action-state matrix
	public static List<GameState> S = new ArrayList<GameState>();							// Set of different states
	
	public static ArrayList<ArrayList<Float>> currQ = new ArrayList<ArrayList<Float>>();		// Action-state matrix
	
	public static int prevScore;
	public static int prevTime;
	public static int reward;																// Next reward
	public static int totalReward;															// Total reward earned while being in some state
	public static boolean eaten = false, justEaten = false;													// Used for testing negative rewards
	
	// Q-learning parameters
	public static final float alpha = 0.2f;													// Alpha is the learning rate: how much the newer information will override the previous one
	public static final float gamma = 0.8f;													// Gamma determines the importance of future rewards.
	public static float eps = 0f;															// Eps. determines how likely is doing a random movement (exploring)
	
	// Case-based reasoning parameters
	public static float w1 = 0.85f, w2 = 0f, w3 = 0.05f, w4 = 0.05f, w5 = 0.05f;  						// Weights: w1:pills, w2:ghosts, w3:power pills, w4:edible ghosts, w5: intersections 
	public static final int MAX_CASES = Integer.MAX_VALUE;												// Max. number of cases allowed in case base
	public static final int CASES_TO_DELETE = 10000;													// Number of cases to delete when case base is full.
	public static final float newCaseThreshold = 0.025f;												// Case threshold used to distinguish two cases.
	public static final int MAX_MAP_DISTANCE = GameState.DISCRETE_FAR;
	public static final int MAX_STATE_DISTANCE = 4;
	public static float currSimilarity = 0;
	public static float comp = MAX_STATE_DISTANCE;
	public static float currBest = MAX_STATE_DISTANCE;
	
	
	// Game states
	public static GameState initState;														// Initial state of the game
	public static GameState currState;														// Current state of the game
	public static GameState nextState;														// Next state of the game
	public static GameState prevState;
	public static GameState eatenState;
	public static int prevAction = Game.INITIAL_PAC_DIR;
	public static int stateIndex = -1;														// Used to calculate the index that next state will have
	public static int currAction = 0;															// PacMan direction
																							// Chosen PacMan direction: 0 Up, 1 Right, 2 Down, 3 Left 
	public static boolean initialized = false;												// Used to avoid adding initial state to the list each time a new game starts 
	
	
	// Data used to draw plots
	public static List<Integer> scores = new ArrayList<Integer>();							// Scores reached during the simulation
	public static List<Integer> drawableScores = new ArrayList<Integer>();					// Values that will appear in plots (after computing means with 'scores' list values)
	public static List<Integer> rewards = new ArrayList<Integer>();							// Rewards reached during the simulation
	public static List<Integer> finalScores = new ArrayList<Integer>();
	public static List<Integer> times = new ArrayList<Integer>();
	public static List<Integer> drawableTimes = new ArrayList<Integer>();
	
	public static List<Integer> randomScores = new ArrayList<Integer>();
	public static List<Integer> drawableRandomScores = new ArrayList<Integer>();
	
	public static List<Integer> closestPillScores = new ArrayList<Integer>();
	public static List<Integer> drawableClosestPillScores = new ArrayList<Integer>();
	
	public static List<Integer> numberOfCases = new ArrayList<Integer>();					// Number of cases in each episode
	
	public static List<Integer> retrievedCases = new ArrayList<Integer>();					// Number of retrieved cases each stage
	public static int currRetrieved = 0;
	
	public static List<Integer> stateCounter = new ArrayList<Integer>();					// Number of times each case is retrieved	
	public static List<Integer> addedCases = new ArrayList<Integer>();						// New cases added in each episode
	public static List<Float>	similarities = new ArrayList<Float>();						// Similarities of retrieved cases in a stage 
	public static List<Float>	averageSimilarities = new ArrayList<Float>();				// Average similarities of retrieved cases in a simulation
	public static int currAdded = 0;
	
	// Stats
	public static List<Integer>	levelsCompleted = new ArrayList<Integer>();				// Average similarities of retrieved cases in a simulation
	public static int currLevels = 0;
	public static float avgLevels;
	public static int maxScore, maxTime, maxLevel;
	public static float avgScore, avgTime;
	public static float stdScore, stdTime;
	
	// Variables used for threaded searching
	public static int searchResult;
	public static float r1,r2,s1,s2;
	
	/*
	 *  This lists will store precalculated information about distances
	 *  */
	public static ArrayList<ArrayList<Integer>> currDistancesUp = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> currDistancesRight = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> currDistancesDown = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> currDistancesLeft = new ArrayList<ArrayList<Integer>>();
	
	public static ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distancesUp = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distancesRight = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distancesDown = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distancesLeft = new ArrayList<ArrayList<Integer>>();
	
	public static ArrayList<ArrayList<Integer>> distances2Up = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances2Right = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances2Down = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances2Left = new ArrayList<ArrayList<Integer>>();
	
	public static ArrayList<ArrayList<Integer>> distances3Up = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances3Right = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances3Down = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances3Left = new ArrayList<ArrayList<Integer>>();
	
	public static ArrayList<ArrayList<Integer>> distances4Up = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances4Right = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances4Down = new ArrayList<ArrayList<Integer>>();
	public static ArrayList<ArrayList<Integer>> distances4Left = new ArrayList<ArrayList<Integer>>();
	/**
	 * Sets initial state and initializes state list and Q matrix
	 * 
	 * */
	public static void initialize(Game game){

		// Different state/case representations are commented
		if(!initialized){
		/* For behavioral agent*/
		//initState = new GameState(0, game.getPathDistance(game.getCurPacManLoc(), GameState.getNearestPill(game)), game.getPathDistance(game.getCurPacManLoc(), GameState.getNearestPowerPill(game)), GameState.nearGhosts(game), GameState.edibleGhost(game));
		
		
		
			/*For direction agent*/
			/*initState = new GameState(0, GameState.possibleDir(game,Game.UP), GameState.possibleDir(game,Game.RIGHT),GameState.possibleDir(game,Game.DOWN),GameState.possibleDir(game,Game.LEFT), 
					GameState.getClosestPillDir(game), GameState.getClosestGhostDir(game),GameState.getClosestPowerPillDir(game),GameState.getClosestEdibleGhostDir(game));*/
			
			/*For CBR agent*/
			
			
			initState = new GameState(0, GameState.getNextPill(game,Game.UP), GameState.getNextPill(game,Game.RIGHT),GameState.getNextPill(game,Game.DOWN),GameState.getNextPill(game,Game.LEFT), 
					GameState.getNextGhost(game, Game.UP), GameState.getNextGhost(game, Game.RIGHT),GameState.getNextGhost(game, Game.DOWN),GameState.getNextGhost(game, Game.LEFT),
					GameState.getNextPowerPill(game, Game.UP), GameState.getNextPowerPill(game, Game.RIGHT),GameState.getNextPowerPill(game, Game.DOWN),GameState.getNextPowerPill(game, Game.LEFT),
					GameState.getNextEdibleGhost(game, Game.UP), GameState.getNextEdibleGhost(game, Game.RIGHT),GameState.getNextEdibleGhost(game, Game.DOWN),GameState.getNextEdibleGhost(game, Game.LEFT),
					GameState.getNextIntersection(game, Game.UP), GameState.getNextIntersection(game, Game.RIGHT),GameState.getNextIntersection(game, Game.DOWN),GameState.getNextIntersection(game, Game.LEFT));
			
			
			/*
			initState = new GameState(0, GameState.getNextPill(game,Game.UP), GameState.getNextPill(game,Game.RIGHT),GameState.getNextPill(game,Game.DOWN),GameState.getNextPill(game,Game.LEFT),
					GameState.closeGhostInDirection(game, Game.UP), GameState.closeGhostInDirection(game, Game.RIGHT), GameState.closeGhostInDirection(game, Game.DOWN), GameState.closeGhostInDirection(game, Game.LEFT),
					GameState.getNextPowerPill(game, Game.UP), GameState.getNextPowerPill(game, Game.RIGHT),GameState.getNextPowerPill(game, Game.DOWN),GameState.getNextPowerPill(game, Game.LEFT),
					GameState.getNextEdibleGhost(game, Game.UP), GameState.getNextEdibleGhost(game, Game.RIGHT),GameState.getNextEdibleGhost(game, Game.DOWN),GameState.getNextEdibleGhost(game, Game.LEFT),
					GameState.getNextIntersection(game, Game.UP), GameState.getNextIntersection(game, Game.RIGHT),GameState.getNextIntersection(game, Game.DOWN),GameState.getNextIntersection(game, Game.LEFT));
			*/
			//eatenState= new GameState(-1, -1, -1, -1, -1);
			
			prevScore = 0;
			reward = 0;
			totalReward = 0;
			
			stateCounter.add(1);
			S.add(initState);
			
			Random rnd = new Random();
			float f1= rnd.nextFloat(), f2 = rnd.nextFloat(), f3 =rnd.nextFloat(), f4 = rnd.nextFloat();
			Q.add(new ArrayList<>(Arrays.asList(f1, f2, f3, f4)));
			currQ.add(new ArrayList<>(Arrays.asList(f1, f2, f3, f4)));
		
			initialized = true;
			currState = initState;
			prevState = currState;
		}
		currState = initState;
		
	}
	
	/**
	 * Implements Q-learning algorithm, and stores learned info. in Q matrix (action-state matrix)
	 * 
	 * */
	public static void learn(Game game, PacManAction pacManAction){
		// setup next state
		/* 
        nextState = new GameState(-1, game.getCurPacManLoc(), game.getNumActivePills(), game.getPowerPillIndicesActive(), 
        	min(game.getPathDistance(game.getCurGhostDir(0), game.getCurPacManLoc()), game.getPathDistance(game.getCurGhostDir(1), game.getCurPacManLoc()), game.getPathDistance(game.getCurGhostDir(2), game.getCurPacManLoc()), game.getPathDistance(game.getCurGhostDir(3), game.getCurPacManLoc())));  					// TODO: introduce parameters in new State
		 */
        //int current = game.getCurPacManLoc();
		
		
		/*This is for the behavioral agent*/
		//nextState = new GameState(-1, game.getPathDistance(game.getCurPacManLoc(), GameState.getNearestPill(game)),game.getPathDistance(game.getCurPacManLoc(), GameState.getNearestPowerPill(game)) , GameState.nearGhosts(game), GameState.edibleGhost(game));
		
		/*This is for the directions agent*/
		/*nextState = new GameState(-1, GameState.possibleDir(game,Game.UP), GameState.possibleDir(game,Game.RIGHT),GameState.possibleDir(game,Game.DOWN),GameState.possibleDir(game,Game.LEFT), 
				GameState.getClosestPillDir(game), GameState.getClosestGhostDir(game),GameState.getClosestPowerPillDir(game),GameState.getClosestEdibleGhostDir(game));*/
		
		
		nextState = new GameState(-1, GameState.getNextPill(game,Game.UP), GameState.getNextPill(game,Game.RIGHT),GameState.getNextPill(game,Game.DOWN),GameState.getNextPill(game,Game.LEFT), 
				GameState.getNextGhost(game, Game.UP),GameState.getNextGhost(game, Game.RIGHT),GameState.getNextGhost(game, Game.DOWN),GameState.getNextGhost(game, Game.LEFT),
				GameState.getNextPowerPill(game, Game.UP), GameState.getNextPowerPill(game, Game.RIGHT),GameState.getNextPowerPill(game, Game.DOWN),GameState.getNextPowerPill(game, Game.LEFT),
				GameState.getNextEdibleGhost(game, Game.UP),GameState.getNextEdibleGhost(game, Game.RIGHT),GameState.getNextEdibleGhost(game, Game.DOWN),GameState.getNextEdibleGhost(game, Game.LEFT),
				GameState.getNextIntersection(game, Game.UP),GameState.getNextIntersection(game, Game.RIGHT),GameState.getNextIntersection(game, Game.DOWN),GameState.getNextIntersection(game, Game.LEFT));
		
		/*
		nextState = new GameState(-1, GameState.getNextPill(game,Game.UP), GameState.getNextPill(game,Game.RIGHT),GameState.getNextPill(game,Game.DOWN),GameState.getNextPill(game,Game.LEFT),
				GameState.closeGhostInDirection(game, Game.UP), GameState.closeGhostInDirection(game, Game.RIGHT), GameState.closeGhostInDirection(game, Game.DOWN), GameState.closeGhostInDirection(game, Game.LEFT),
				GameState.getNextPowerPill(game, Game.UP), GameState.getNextPowerPill(game, Game.RIGHT),GameState.getNextPowerPill(game, Game.DOWN),GameState.getNextPowerPill(game, Game.LEFT),
				GameState.getNextEdibleGhost(game, Game.UP), GameState.getNextEdibleGhost(game, Game.RIGHT),GameState.getNextEdibleGhost(game, Game.DOWN),GameState.getNextEdibleGhost(game, Game.LEFT),
				GameState.getNextIntersection(game, Game.UP), GameState.getNextIntersection(game, Game.RIGHT),GameState.getNextIntersection(game, Game.DOWN),GameState.getNextIntersection(game, Game.LEFT));
		*/
		/*This is for CBR*/
		//nextState = new GameState(-1, game.getCurPacManLoc(), ghostLocs(game), game.getPillIndicesActive(), game.getPowerPillIndicesActive());
		
		
        //nextState.index = indexOf(nextState);  					// 'indexOf()' search 'nextState' in state list S and returns its index (-1 if not found)
		int index = -1;
		//index = indexOf(nextState);
		
		
		
		if(/*compareCases(currState, nextState) > 0*/normalizedDistance(compareCases(currState, nextState)) > newCaseThreshold){ 	// If agent is in a different case than in previous step
			if(S.size() <= 500){
				//index = indexOf(nextState);	 		// This search is for RL algorithms
				index = searchCase(game, nextState);	// This search is for CBR/RL algorithms
			}  
			else{
				try {
					index = searchCaseWithThreads(game, nextState);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			
			if(index == -1){								// if 'nextState' is not close to any state reached before. We add it to state list
	        	nextState.index = S.size();
	        	S.add(nextState);
	        	stateCounter.add(1);
	        	currAdded++;
	        	
	        	Random rnd = new Random();
				float f1= rnd.nextFloat(), f2 = rnd.nextFloat(), f3 =rnd.nextFloat(), f4 = rnd.nextFloat();
				Q.add(new ArrayList<>(Arrays.asList(f1, f2, f3, f4)));
				currQ.add(new ArrayList<>(Arrays.asList(f1, f2, f3, f4)));
			
	        }
			else{											// Otherwise a case is retrieved from case base
				nextState.index = index;
				currRetrieved++;
				stateCounter.set(index, stateCounter.get(index)+1);
			}
		}
		else{					
			nextState.index = currState.index;
			
		}
		
		
		
		
		// Different rewards are commented
    	//reward =  (int) (w1*(game.getScore() - prevScore) +  w2*(game.getTotalTime() - prevTime));
		reward =  game.getScore() - prevScore;
		//reward = game.getTotalTime() - prevTime;
		totalReward += reward;
        
        
        prevScore = game.getScore();
        prevTime = game.getTotalTime();
       

        if(nextState.index != currState.index){
        	float max = maxQRow(currState.index);
        	if(justEaten){
            	max = 0;
            	totalReward = 0;
            	justEaten = false;
            }
	        //currQ.get(currState.index).set(currAction, currQ.get(currState.index).get(currAction) + alpha*(totalReward + gamma*maxQRow(nextState.index) - currQ.get(currState.index).get(currAction) )); 

        	
        	if(update){
	        	float v = currQ.get(prevState.index).get(prevAction) + alpha*((1-currSimilarity)*totalReward + gamma*max - currQ.get(prevState.index).get(prevAction));
	        	currQ.get(prevState.index).set(prevAction, v);
        	}
        	totalReward = 0;
	        prevState = currState;
	        prevAction = currAction;
	        
	        if(eaten){
	        	eaten = false;
	        	justEaten = true;
	        }
	        
	        /*currQ.get(currState.index).set(pmState, 
	            	currQ.get(currState.index).get(pmState) + alpha*((1-currSimilarity)*reward + gamma*maxQRow(nextState.index) - currQ.get(currState.index).get(pmState) ));*/
	        
	        /*
	        if(currQ.get(currState.index).get(pmState) < 0) 
	        	currQ.get(currState.index).set(pmState,0f);*/
	        
	        
        	
	        /*YOU CAN USE THESE PRINTS FOR DEBUG PURPOSES*/
	        //System.out.println("- Change to state: " + Q_learn.nextState.index);
	        //System.out.println(Q_learn.nextState.toString());
	        //System.out.println("Corner: " + game.isCorner(game.getCurPacManLoc()) + " -Junction: " + game.isJunction(game.getCurPacManLoc()));
	        //System.out.println("R:" + totalReward + "-D:  " + pmState + " -S: " + currState.index);
	        
	        
	        //System.out.println("Q:" + Q.get(nextState.index).get(0) + "-" + Q.get(nextState.index).get(1) + "-" + Q.get(nextState.index).get(2) + "-" + Q.get(nextState.index).get(3));
	        //System.out.println("Q:" + Q.get(0).get(0) + "-" + Q.get(0).get(1) + "-" + Q.get(0).get(2) + "-" + Q.get(0).get(3));
	        //System.out.println(Q.get(currState.index).get(pmState) + " = " + Q.get(currState.index).get(pmState) + " + alpha*[" + reward + " + gamma*" + maxQRow(nextState.index) +" - " + Q.get(currState.index).get(pmState));
	        
	        //System.out.println("R:" + reward);
	        //}
	        
        }
        currState = nextState;
		
	}
	
	// Max. function with 4 elements
	public static float max(float a, float b, float c, float d){
		return Math.max(Math.max(a,b),Math.max(c,d));
	}
	
	// Min. function with 4 elements
	public static float min(float a, float b, float c, float d){
		return Math.min(Math.min(a,b),Math.min(c,d));
	}
	
	// Returns the maximum value in 'index'th row of Q matrix
	public static float maxQRow(int index){
		return Collections.max(currQ.get(index));
	}

	// Returns the index of maximum value in 'index'th row of Q matrix
	public static int maxQRowIndex(int index){
		if(max(Q.get(index).get(0), Q.get(index).get(1), Q.get(index).get(2), Q.get(index).get(3)) == Q.get(index).get(0)){
			return 0;
		}
		else if(max(Q.get(index).get(0), Q.get(index).get(1), Q.get(index).get(2), Q.get(index).get(3)) == Q.get(index).get(1)){
			return 1;
		}
		else if(max(Q.get(index).get(0), Q.get(index).get(1), Q.get(index).get(2), Q.get(index).get(3)) == Q.get(index).get(2)){
			return 2;
		}
		else if(max(Q.get(index).get(0), Q.get(index).get(1), Q.get(index).get(2), Q.get(index).get(3)) == Q.get(index).get(3)){
			return 3;
		}
		else return 0;
	}
	
	
	public static boolean isExplored(int index){
		/*for(int i=0; i<4; i++){
			if(Q.get(index).get(i) != 0) return true;
		}*/
		if(stateCounter.get(index) > 2) return true;
		
		return false;
	}
	
	/*
	 * Returns i such as Q[index,i] is the maximum value in that row and i is in directions[].
	 * 
	 * @directions: Possible directions for PacMan
	 * @index: State index
	 * 
	 * */
	public static int maxQRowDirIndex(int[] directions, int index){
		float max = Q.get(index).get(directions[0]);
		int res = directions[0];
		
		for(int i=1; i<directions.length;i++){
			if (Q.get(index).get(directions[i]) > max){
				max = Q.get(index).get(directions[i]);
				res = directions[i];
			} 
			
		}
		
		return res;
	}
	
	/*
	 * Returns index of s in state list S, -1 otherwise
	 * There are different state representation comparisons implemented here. You can change them by changing commented lines below
	 * */
	public static int indexOf(GameState s){
		int result = -1;
		/*
		for(int i=0; i<S.size(); i++){
			if(s.currPacLoc == S.get(i).currPacLoc && s.numActivePills == S.get(i).numActivePills && s.powerPillIndicesActive == S.get(i).powerPillIndicesActive && s.ghostDistance == S.get(i).ghostDistance)
				result = S.get(i).index;
		}
		*/
		
		/*
		for(int i=0; i<S.size(); i++){
			if(s.nearestPillDistance == S.get(i).nearestPillDistance && s.nearestPowerPillDistance == S.get(i).nearestPowerPillDistance && s.nearGhosts == S.get(i).nearGhosts)
				result = S.get(i).index;
		}
		*/
		/*
		 * This is for directions agent*/
		/*
		for(int i=0; i<S.size(); i++){
			if(s.pillUp == S.get(i).pillUp && s.pillRight == S.get(i).pillRight && s.pillDown == S.get(i).pillDown && s.pillLeft == S.get(i).pillLeft &&
					s.ghostUp == S.get(i).ghostUp && s.ghostRight == S.get(i).ghostRight && s.ghostDown == S.get(i).ghostDown && s.ghostLeft == S.get(i).ghostLeft) result = S.get(i).index;
			
		}*/
		
		for(int i=0; i<S.size(); i++){
			/*if(s.u == S.get(i).u && s.r == S.get(i).r && s.d == S.get(i).d && s.l == S.get(i).l && 
					s.cp == S.get(i).cp && s.cg == S.get(i).cg && s.cpp == S.get(i).cpp && s.ceg == S.get(i).ceg) result = S.get(i).index;*/
			
			if(/*s.pillUp == S.get(i).pillUp && s.pillRight == S.get(i).pillRight && s.pillDown == S.get(i).pillDown && s.pillLeft == S.get(i).pillLeft && */
					s.ghostUp == S.get(i).ghostUp && s.ghostRight == S.get(i).ghostRight && s.ghostDown == S.get(i).ghostDown && s.ghostLeft == S.get(i).ghostLeft //&& 
					//s.powerPillUp == S.get(i).powerPillUp && s.powerPillRight == S.get(i).powerPillRight && s.powerPillDown == S.get(i).powerPillDown && s.powerPillLeft == S.get(i).powerPillLeft &&
					/*s.edibleGhostUp == S.get(i).edibleGhostUp && s.edibleGhostRight == S.get(i).edibleGhostRight && s.edibleGhostDown == S.get(i).edibleGhostDown && s.edibleGhostLeft == S.get(i).edibleGhostLeft*/ //&&
					/*s.intersectionUp == S.get(i).intersectionUp && s.intersectionRight == S.get(i).intersectionRight && s.intersectionDown == S.get(i).intersectionDown && s.intersectionLeft == S.get(i).intersectionLeft*/) 
				result = S.get(i).index;
			
		}
		
		
		return result;
	}
	
	public static float normalizedDistance(float f){
		return f/MAX_STATE_DISTANCE;
	}

	/*
	 * Returns index of most similar case to case s given, as long as it's close enough, depending on the threshold.
	 * */
	public static int searchCase(Game game, GameState s){
		int result = -1;
		comp = Q_learn.MAX_STATE_DISTANCE;
		currBest = Q_learn.MAX_STATE_DISTANCE;
		
		for(int i = 0; i< S.size(); i++){
			comp = compareCases(s, S.get(i));
			if(comp < currBest) {
				currBest = comp;
				result = i;
			}
		}
		
		
		currBest = normalizedDistance(currBest);
		
		if(currBest < newCaseThreshold){
			currSimilarity = currBest;
			similarities.add(currSimilarity);
			return result;
		}
		else {
			currSimilarity = 1;
			similarities.add(currSimilarity);
			return -1;
		}
	}
	
	/*
	 * Multithreaded search of similar cases in casebase. Returns most similar case if it is close enough.
	 * Otherwise it returns -1.
	 * */
	public static int searchCaseWithThreads(Game game, GameState s) throws InterruptedException{
		
		int result; 
		comp = Q_learn.MAX_STATE_DISTANCE;
		currBest = Q_learn.MAX_STATE_DISTANCE;
		int n_threads = 2;
		
		SearchThread R[] = new SearchThread[n_threads];
		
		
		for(int i = 0; i < n_threads; i++){
			R[i] = new SearchThread( "Thread-" + i, (int)i*S.size()/n_threads, (int)(i+1)*(S.size()/n_threads), s);
			R[i].start();
		}
	    
	    for(int i = 0; i < n_threads; i++){
	    	R[i].join();
	    }

		if(currBest < Q_learn.newCaseThreshold){ // Retrieve case
			currSimilarity = (float) currBest;
			similarities.add(currSimilarity);
			result = searchResult;
		}
		else {				// No similar enough case found
			currSimilarity = 1;
			//System.out.println("NEW CASE! " + currBest);
			//System.out.println("----------------");
			//similarities.add(currSimilarity);
			result = -1;
		}  
	      
	    //float currBest = Math.min(R1.currSimilarity, R2.currSimilarity);
		
	    return result;
	}
	

	public static int searchForClean(GameState s) throws InterruptedException{
		
		int result; 
		comp = Q_learn.MAX_STATE_DISTANCE;
		currBest = Q_learn.MAX_STATE_DISTANCE;
		int n_threads = 3;
		
		SearchThread R[] = new SearchThread[n_threads];
		R[0] = new SearchThread("Thread-1", 0, (int)S.size()/n_threads, s);
	    R[1] = new SearchThread( "Thread-2", (int)S.size()/n_threads, (int)2*(S.size()/n_threads), s);
	    R[2] = new SearchThread( "Thread-3", (int) 2*(S.size()/n_threads), S.size(), s);
	    R[0].start();
	    R[1].start();
	    R[2].start();

	    
	    for(int i = 0; i < n_threads; i++){
	    	R[i].join();
	    }


	    result = searchResult;
		if(result == -1)
			System.out.println("-1 en SFC");
		
	    return result;
	}
	
	
	/* Deletes cases when case base is full*/
	public static void cleanCaseBase() throws InterruptedException{
		
		int deletedCases = 0;
		
		
			
			while(deletedCases < CASES_TO_DELETE){
				int rc = G.rnd.nextInt(S.size());	// Select a random case
				int msc = searchForClean(S.get(rc));	// Search for most similar case
				int e;
				
				if(msc == -1)
					System.out.println("-1 en SFC");
				
				if(maxQRowIndex(rc) == maxQRowIndex(msc)) {
					if(stateCounter.get(rc) > stateCounter.get(msc)) e = msc;
					else e = rc;
					S.remove(e); 	// If they have the same best movement, remove most similar case
					Q.remove(e);
					currQ.remove(e);
					stateCounter.remove(e);
					deletedCases++;
				}
			}
			
			correctIndexes();
			
		
		
	}
	
	
	public static void correctIndexes(){
		for(int i = 0; i < S.size(); i++) S.get(i).index = i;
	}
	

	/* This function implements similarity measure between two cases*/
	public static float compareCases(GameState s, GameState s2){
		float result = 0;
		
		
		
		float distPills = 0;
		if (s.pillUp != s2.pillUp) distPills++;
		if (s.pillDown != s2.pillDown) distPills++;
		if (s.pillLeft != s2.pillLeft) distPills++;
		if (s.pillRight != s2.pillRight) distPills++;
		//distPills /= 4.0f;*/
		
		float distGhosts = 0;
		if (s.ghostUp != s2.ghostUp) distGhosts++;
		if (s.ghostDown != s2.ghostDown) distGhosts++;
		if (s.ghostLeft != s2.ghostLeft) distGhosts++;
		if (s.ghostRight != s2.ghostRight) distGhosts++;
		//distGhosts /= 4.0f;
		
		float distPowerPills = 0;
		if (s.powerPillUp != s2.powerPillUp) distPowerPills++;
		if (s.powerPillDown != s2.powerPillDown) distPowerPills++;
		if (s.powerPillLeft != s2.powerPillLeft) distPowerPills++;
		if (s.powerPillRight != s2.powerPillRight) distPowerPills++;
//		distPowerPills /= 4.0f;
		
		float distEdibleGhosts = 0;
		if (s.edibleGhostUp != s2.edibleGhostUp) distEdibleGhosts++;
		if (s.edibleGhostDown != s2.edibleGhostDown) distEdibleGhosts++;
		if (s.edibleGhostLeft != s2.edibleGhostLeft) distEdibleGhosts++;
		if (s.edibleGhostRight != s2.edibleGhostRight) distEdibleGhosts++;
//		
		
		float distIntersection = 0;
		if (s.intersectionUp != s2.intersectionUp) distIntersection++;
		if (s.intersectionDown != s2.intersectionDown) distIntersection++;
		if (s.intersectionLeft != s2.intersectionLeft) distIntersection++;
		if (s.intersectionRight != s2.intersectionRight) distIntersection++;
		
		
		result = w1*distPills + w2*distGhosts + w3*distPowerPills + w4*distEdibleGhosts + w5*distIntersection;

		
		return result;
	}

	
	/*
	 * Returns distance between two vectors x and y [eucl = sum(x_i-y_i) ] 
	 * */
    public static int euclidean(int[] x, int[] y){ 
        int sum = 0; 
        for (int i=0; i < x.length; i++){ 
        	if(x[i] == -1 && y[i] == -1) sum += 0;						// When there is no pill in that dir. in both states, distance is 0
        	else if(x[i] == -1 || y[i] == -1) sum += MAX_MAP_DISTANCE;		// When only one of the states has a pill in that dir, distance is max. (set to 100 in discretization process, since x-y is always less than that)
            else if(x[i] >= y[i]) sum += x[i] - y[i]; 						// Otherwise distance is (x-y)^2
            else sum +=  y[i]- x[i] ;
        } 
        
        return  sum; 
    } 
	
	
	/* Returns the sum of distance differences between ghosts in both states*/
	/*public static int compareGhosts(Game game, GameState s, GameState s2){
		int result = 0;
		
		for(int i = 0; i<4; i++){
			result += game.getPathDistance(s.ghostLoc[i], s2.ghostLoc[i]);
		}
		
		return result;
	}
	*/
	
	/*Returns number of pills that are active in both cases*/
	/*public static int comparePills(Game game, GameState s, GameState s2){
		int result = 0;
		int n1 = s.pillIndicesActive.length;
		int n2 = s2.pillIndicesActive.length;
		int n = Math.max(n1, n2);
		
		for(int i=0; i<n2; i++){
			if(Arrays.asList(s.pillIndicesActive).contains(s2.pillIndicesActive[i])) result++;
		}
		
		
		return result;
	}
*/
	/*Returns number of power pills that are active in both cases*/
	/*public static int comparePowerPills(Game game, GameState s, GameState s2){
		int result = 0;
		int n1 = s.powerPillIndicesActive.length;
		int n2 = s2.powerPillIndicesActive.length;
		int n = Math.max(n1, n2);
		
		for(int i=0; i<n2; i++){
			if(Arrays.asList(s.powerPillIndicesActive).contains(s2.powerPillIndicesActive[i])) result++;
		}
		
		
		return result;
	}
	*/
	public static int[] ghostLocs(Game game){
		int[] ghostLocs = new int[4];
		
		ghostLocs[0] = game.getCurGhostLoc(0);
		ghostLocs[1] = game.getCurGhostLoc(1);
		ghostLocs[2] = game.getCurGhostLoc(2);
		ghostLocs[3] = game.getCurGhostLoc(3);
		
		return ghostLocs;
	}
	

	
	
	public static boolean closeGhost(){

		
		if((currState.ghostUp == GameState.DISCRETE_VERY_CLOSE) || (currState.ghostUp == GameState.DISCRETE_CLOSE) || 
				(currState.ghostRight == GameState.DISCRETE_VERY_CLOSE) || (currState.ghostRight == GameState.DISCRETE_CLOSE) || 
				(currState.ghostDown == GameState.DISCRETE_VERY_CLOSE) || (currState.ghostDown == GameState.DISCRETE_CLOSE) ||
				(currState.ghostLeft == GameState.DISCRETE_VERY_CLOSE) || (currState.ghostLeft == GameState.DISCRETE_CLOSE)/* ||
				(currState.edibleGhostUp <= GameState.DISCRETE_VERY_CLOSE && currState.edibleGhostUp >= 0) || 
				(currState.edibleGhostRight <= GameState.DISCRETE_VERY_CLOSE && currState.edibleGhostRight >= 0) || 
				(currState.edibleGhostDown <= GameState.DISCRETE_VERY_CLOSE && currState.edibleGhostDown >= 0) ||
				(currState.edibleGhostLeft <= GameState.DISCRETE_VERY_CLOSE && currState.edibleGhostLeft >= 0)*/) return true;
		
		return false;
	}
	
	
	/* Adds average value between score provided and previous scores*/
	public static void addNewScore(int score){
		
		int[] buf = new int[100];
		
		scores.add(score);
		
		
	}
	
	public static void calculateDrawableScores(){
		
		final int n = 20;
		int[] buf = new int[n];
		int[] buf2 = new int[n];
		int[] bufRnd = new int[n];
		int[] bufCP = new int[n];
		
		for(int i = 0; i < scores.size(); i++){
			buf[i%n] = scores.get(i);
			buf2[i%n] = times.get(i);
			//bufRnd[i%n] = randomScores.get(i);
			//bufCP[i%n] = closestPillScores.get(i);
			if(i%n == n-1){
				drawableScores.add(intMean(buf));
				drawableTimes.add(intMean(buf2));
				// drawableRandomScores.add(intMean(bufRnd));
				//drawableClosestPillScores.add(intMean(bufCP));
			}
			
		}
		
		
	}
	
	
	
	/* Calculate simulation stats*/
	public static void stats(){
		
		//final int n = scores.size();
		final int n = 100;
		int[] t = new int[n];
		int[] s = new int[n];
		int[] l = new int[n];
		int[] bufRnd = new int[n];
		int[] bufCP = new int[n];
		
		maxScore = Collections.max(scores);
		maxTime = Collections.max(times);
		maxLevel = Collections.max(levelsCompleted);
		
		for(int i = 0; i < n; i++){
			s[i] = scores.get(scores.size()-1-i);
			t[i] = times.get(scores.size()-1-i);
			
		}
		/*
		for(int i = 0; i < n; i++){
			s[i] = scores.get(i);
			t[i] = times.get(i);
			
		}*/
		
		for(int i = 0; i<levelsCompleted.size(); i++){
			l[i] = levelsCompleted.get(i);
		}
		
		avgScore = intMean(s);
		avgTime = intMean(t);
		avgLevels = mean(l);
		
		stdScore = getStdDev(s);
		stdTime = getStdDev(t);
		
		
	}
	
	/*Returns integer average value of elements in vector m*/
	public static int intMean(int[] m) {
	    double sum = 0;
	    for (int i = 0; i < m.length; i++) {
	        sum += m[i];
	    }
	    return (int) sum / m.length;
	}
	
	
	public static float mean(int[] m) {
	    float sum = 0;
	    for (int i = 0; i < m.length; i++) {
	        sum += m[i];
	    }
	    return sum / m.length;
	}
	
	 public static float getVariance(int[] m)
	    {
		 	int mean = intMean(m);
	        float temp = 0;
	        for(int i = 0; i < m.length; i++)
	            temp += (m[i]-mean)*(m[i]-mean);
	        return temp/m.length;
	    }

    public static float getStdDev(int[] m)
    {
        return (float) Math.sqrt(getVariance(m));
    }
	
	
	/*Returns integer average value of elements in vector m*/
	public static float averageSimilarity(List<Float> s) {
	    float sum = 0;
	    for (int i = 0; i < s.size(); i++) {
	        sum += s.get(i);
	    }
	    return  sum / s.size();
	}
	
	public static String Qvalues(int index){
		return Q.get(index).get(0) + "  " + Q.get(index).get(1) + "  " + Q.get(index).get(2) + "  " + Q.get(index).get(3);
		
	}
	
	public static void printLearning(){
		
		System.out.println("Current State: " + currState.toString());
		System.out.println("Q: " + Qvalues(currState.index));
		System.out.println("----------------------------------");
	} 
	
	/*Calculates distances from each node to all of them in current level*/
	public static void calculateDistances(Game game) {
		PrintWriter writer;
		try {
			writer = new PrintWriter("distances.txt", "UTF-8");
		
		
		for(int i = 0; i<game.getNumberOfNodes(); i++){
			distances.add(new ArrayList<Integer>());
			
			for(int j = 0; j<game.getNumberOfNodes(); j++){
				distances.get(i).add(game.getPathDistance(i, j));
				writer.print(distances.get(i).get(j) + " ");
			}
			writer.print("\n");
		}
		
		writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	/*Calculates distances from each node to all of them in given direction of current level*/
	public static void calculateDistancesInDirection(Game game, int direction) {
		PrintWriter writer;
		try {
			writer = new PrintWriter("distances4" + direction + ".txt", "UTF-8");
		
		
		ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
			
		System.out.println("Calculating distances in direction:  " + direction);
		for(int i = 0; i<game.getNumberOfNodes(); i++){
			distances.add(new ArrayList<Integer>());
			
			for(int j = 0; j<game.getNumberOfNodes(); j++){
				distances.get(i).add(DistanceInDirection(game, direction, i, j));
				writer.print(distances.get(i).get(j) + " ");
			}
			
			writer.print("\n");
		}
		
		writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	public static int DistanceInDirection(Game game, int direction, int source, int node){
		
		List<Integer> s = new ArrayList<Integer>();
		s.add(source);
		int root = game.getNeighbour(source, direction);
		int distance = 0;
		
		
		if(source == node) return 0;
		if(root != -1){
			s.add(root);
			Queue<Integer> q = new LinkedList<Integer>();
			q.add(root);
			int current;
			
			while(!q.isEmpty()){
				current = q.remove();
				if(current == node) return distance + 1;
				distance++;
				
				//int directions[] = game.getPossiblePacManDirs(false);
				for(int i = 0; i < 4;i++){
					int n = game.getNeighbour(current, i);
					if(!s.contains(n) && n !=-1){
						s.add(n);
						q.add(n);
					}
				}
			}
		}
		return -1;
		
	}
	
	public static int getDistance(int direction, int from, int to){
		int dist;
		
		ArrayList<ArrayList<Integer>> du = currDistancesUp;
		ArrayList<ArrayList<Integer>> dr = currDistancesRight;
		ArrayList<ArrayList<Integer>> dd = currDistancesDown;
		ArrayList<ArrayList<Integer>> dl = currDistancesLeft;
		
		if(direction == 0) dist = du.get(from).get(to);
		else if(direction == 1) dist = dr.get(from).get(to);
		else if(direction == 2) dist = dd.get(from).get(to);
		else  dist = dl.get(from).get(to);
		
		return dist;
	}
	
	public static void changeDistances(int level){
		switch(level){
		case 0: {
			Q_learn.currDistancesUp = Q_learn.distancesUp;
			Q_learn.currDistancesRight = Q_learn.distancesRight;
			Q_learn.currDistancesDown = Q_learn.distancesDown;
			Q_learn.currDistancesLeft = Q_learn.distancesLeft; 
			break;
			}
		case 1:  {
			Q_learn.currDistancesUp = Q_learn.distances2Up;
			Q_learn.currDistancesRight = Q_learn.distances2Right;
			Q_learn.currDistancesDown = Q_learn.distances2Down;
			Q_learn.currDistancesLeft = Q_learn.distances2Left; 
			break;
			}
		case 2:  {
			Q_learn.currDistancesUp = Q_learn.distances3Up;
			Q_learn.currDistancesRight = Q_learn.distances3Right;
			Q_learn.currDistancesDown = Q_learn.distances3Down;
			Q_learn.currDistancesLeft = Q_learn.distances3Left; 
			break;
			}
		case 3:  {
			Q_learn.currDistancesUp = Q_learn.distances4Up;
			Q_learn.currDistancesRight = Q_learn.distances4Right;
			Q_learn.currDistancesDown = Q_learn.distances4Down;
			Q_learn.currDistancesLeft = Q_learn.distances4Left; 
			break;
			}
		default: {
			Q_learn.currDistancesUp = Q_learn.distancesUp;
			Q_learn.currDistancesRight = Q_learn.distancesRight;
			Q_learn.currDistancesDown = Q_learn.distancesDown;
			Q_learn.currDistancesLeft = Q_learn.distancesLeft; 
			break;
			}
		
		}
		
		
	}
	
	// Stores all information needed for drawing plots, and checks if case base is full
	public static void endEpisode(){
		
		
		numberOfCases.add(S.size());
		
		addedCases.add(currAdded);
		currAdded = 0;
		
		levelsCompleted.add(currLevels);
		currLevels = 0;
		
		retrievedCases.add(currRetrieved);
		currRetrieved=0;
		
		averageSimilarities.add(averageSimilarity(similarities));
		
		
		if(S.size() > MAX_CASES){
			try {
				cleanCaseBase();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
	}
	
	
}