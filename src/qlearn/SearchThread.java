package qlearn;

import game.core.Game;
import game.core.GameState;
import java.lang.*;

public class SearchThread extends Thread {
	   private SearchThread t;
	   private String threadName;
	   private int begin, end;
	   private GameState state;
	   public int result;
	   public float currSimilarity;
	   
	   SearchThread(String name, int b, int e, GameState s) {
	      threadName = name;
	      begin = b;
	      end = e;
	      state = s;

	      //System.out.println("Creating " +  threadName );
	   }
	   
	   public void run() {
		double comp = Q_learn.MAX_STATE_DISTANCE, currBest = Q_learn.MAX_STATE_DISTANCE;
		result = -1;
		
		for(int i = begin; i< end; i++){
			comp = Q_learn.compareCases(state, Q_learn.S.get(i));
			if(comp < currBest && state.index != Q_learn.S.get(i).index) {
				currBest = comp;
				result = i;
			}
		}
		
		//System.out.println("Comparison before division:" + currBest);
		currBest = currBest / Q_learn.MAX_STATE_DISTANCE;
		//if(result != -1)System.out.println("Best similar case:" + currBest + " - " + Q_learn.S.get(result).toString());
		//System.out.println("Current Case: " +  s.toString());
		//System.out.println("----------------");
		
		currSimilarity = (float) currBest;
		if(currSimilarity < Q_learn.currBest){
			Q_learn.currBest = currSimilarity;
			//Q_learn.similarities.add(currSimilarity);
			Q_learn.searchResult = result;
			
		}
		
	      //System.out.println("Thread " +  threadName + " exiting.");
	   }
	   

	}