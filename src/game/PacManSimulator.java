package game;

import game.controllers.ghosts.GhostsActions;
import game.controllers.ghosts.IGhostsController;
import game.controllers.ghosts.Legacy;
import game.controllers.ghosts.game.GameGhosts;
import game.controllers.pacman.HumanPacMan;
import game.controllers.pacman.IPacManController;
import game.controllers.pacman.NearestPillPacMan;
import game.controllers.pacman.PacManAction;
import game.controllers.pacman.PacManHijackController;
import game.controllers.pacman.RandomPacMan;
import game.core.G;
import game.core.Game;
import game.core.GameState;
import game.core.GameView;
import game.core.Replay;
import game.core._G_;
import qlearn.QLearnDirPacMan;
import qlearn.Q_learn;

import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * One simulator can run one instance of PacMan-vs-Ghosts game.
 * 
 * Can be used for both head/less games.
 * 
 * @author Jimmy
 */
public class PacManSimulator {
	
	public static class GameConfig {
		
		public int seed = -1;
		
		/**
		 * Whether POWER PILLS should be present within the environment.
		 */
		public boolean powerPillsEnabled = true;
		
		/**
		 * Total percentage of PILLS present within the level. If < 1, some (random) pills will be taken away.
		 */
		public double totalPills = 1;
		
		/**
		 * How many levels Ms PacMan may play (-1 => unbound).
		 */
		public int levelsToPlay = -1;
		
		
		
		
		public GameConfig clone() {
			GameConfig result = new GameConfig();
			
			result.seed = seed;
			result.powerPillsEnabled = powerPillsEnabled;
			result.totalPills = totalPills;
			result.levelsToPlay = levelsToPlay;
			
			return result;
		}

		public String asString() {
			return "" + seed + ";" + powerPillsEnabled + ";" + totalPills + ";" + levelsToPlay;
		}
		
		public void fromString(String line) {
			String[] all = line.split(";");
			seed = Integer.parseInt(all[0]);
			powerPillsEnabled = Boolean.parseBoolean(all[1]);
			totalPills = Double.parseDouble(all[2]);
			levelsToPlay = Integer.parseInt(all[3]);
		}

		public String getCSVHeader() {
			return "seed;powerPillsEnabled;totalPills;levelsToPlay";
		}
		
		public String getCSV() {
			return "" + seed + ";" + powerPillsEnabled + ";" + totalPills + ";" + levelsToPlay;
		}
		
	}

	private SimulatorConfig config;
	
	private GameView gv;

	private _G_ game;
	
    private long due; 
    
    //private static Q_learn Q_learn;
    
    
    // REPLAY STUFF
    private StringBuilder replayData;
    private boolean replayFirstWrite;
	
	public synchronized Game run(final SimulatorConfig config) {
		System.out.println("[PacManSimulator] RUNNING: " + config.getOptions());
		
		// RESET INSTANCE & SAVE CONFIG
		reset(config);
		
		// INIT RANDOMNESS
		if (config.game.seed <= 0) {
			config.game.seed = new Random(System.currentTimeMillis()).nextInt();
			while (config.game.seed < 0) config.game.seed += Integer.MAX_VALUE;
		}
		G.rnd = new Random(config.game.seed);
		
		// INITIALIZE THE SIMULATION
		game = new _G_();
		game.newGame(config.game);
		
		// RESET CONTROLLERS
		config.pacManController.reset(game);
		if (config.ghostsController != null) config.ghostsController.reset(game);

		// INITIALIZE THE VIEW
		if (config.visualize) {
			gv = new GameView(game);
			if (config.visualizationScale2x) gv.setScale2x(true);
			gv.showGame();
			
			if (config.pacManController instanceof KeyListener) {				
				gv.getFrame().addKeyListener((KeyListener)config.pacManController);
			}
			if (config.ghostsController != null && config.ghostsController instanceof KeyListener) {				
				gv.getFrame().addKeyListener((KeyListener)config.ghostsController);
			}
		} 
		
		// SETUP REPLAY RECORDING
		int lastLevel = game.getCurLevel();
		if (config.replay) {
			replayData = new StringBuilder();
			replayFirstWrite = true;
		}
		
		// GESTION DE HILOS
		// START CONTROLLERS (threads auto-start during instantiation)
		ThinkingThread pacManThread = null, ghostsThread = null;
		if(config.playWithThreads){
			pacManThread = 
				new ThinkingThread(
					"PAC-MAN",
					new IThinkingMethod() {
						@Override
						public void think() {
							PacManSimulator.this.config.pacManController.tick(game.copy(), due);		
						}
					}
				);
			ghostsThread =
				new ThinkingThread(
					"GHOSTS",
					new IThinkingMethod() {
						@Override
						public void think() {
							if (PacManSimulator.this.config.ghostsController != null) PacManSimulator.this.config.ghostsController.tick(game, due);			
						}
					}
				);
		}
		// SETUP Q-LEARN
		Q_learn.initialize(game);
		
		// START THE GAME
		try {
			while(!game.gameOver())
			{
				due = System.currentTimeMillis() + config.thinkTimeMillis;
				
				// GESTION DE HILOS
				// WAKE UP THINKING THREADS
				if(config.playWithThreads)
					thinkingLatch = new CountDownLatch(2);
				
				long start = System.currentTimeMillis();
				
				// GESTION DE HILOS
				if(config.playWithThreads){
					pacManThread.alert();
					ghostsThread.alert();
				}
				
				// GIVE THINKING TIME
		        try{		        			        	
		        	thinkingLatch.await(config.thinkTimeMillis, TimeUnit.MILLISECONDS);
		        	
		        	if (config.visualize) {
		        		if (System.currentTimeMillis() - start < config.thinkTimeMillis) {
		        			long sleepTime = config.thinkTimeMillis - (System.currentTimeMillis() - start);
		        			if (sleepTime > 4) {
		        				Thread.sleep(sleepTime);
		        			}
		        		}
		        	}
		        } catch(Exception e) {		        	
		        }
		        
		        // GESTION DE HILOS
		        
		        if(config.playWithThreads){
			        if (pacManThread.thinking) {
			        	System.out.println("[SIMULATOR] PacMan is still thinking!");
			        }
			        if (ghostsThread.thinking) {
			        	System.out.println("[SIMULATOR] Ghosts are still thinking!");
			        }
		        }
		        
		        thinkingLatch = null;
		        
		        if(!config.playWithThreads){
			        config.pacManController.tick(game, due);
			        config.ghostsController.tick(game, due);
		        }
		        // OBTAIN ACTIONS
		        PacManAction  pacManAction  = config.pacManController.getAction().clone();
		        GhostsActions ghostsActions = (config.ghostsController == null ? null : config.ghostsController.getActions().clone());
				
		        // SIMULATION PAUSED?
		        boolean advanceGame = true;
		        if (config.mayBePaused) {
			        if (pacManAction.pauseSimulation || (ghostsActions != null && ghostsActions.pauseSimulation)) {
			        	if (!pacManAction.nextFrame && (ghostsActions == null || !ghostsActions.nextFrame)) {
			        		advanceGame = false;
			        	}
			        	config.pacManController.getAction().nextFrame = false;
			        	if (config.ghostsController != null) config.ghostsController.getActions().nextFrame = false;
			        }
		        }
		        
		        // ADVANCE GAME
		        if (advanceGame) {
		        	int pacManLives = game.getLivesRemaining();
		        	
			        int replayStep[] = game.advanceGame(pacManAction, ghostsActions);
			        
			        // Q-LEARNING
			        if(Q_learn.learning){
				        Q_learn.learn(game, pacManAction);
				        //Q_learn.printLearning();
			        }
			        
			        // SAVE ACTIONS TO REPLAY
			        if (config.replay) {
			        	// STORE ACTIONS
			        	storeActions(replayStep, game.getCurLevel()==lastLevel);
			        }
			        
			        // NEW LEVEL?
			        if (game.getCurLevel() != lastLevel) {
			        	lastLevel=game.getCurLevel();
			        	
			        	
			        	
			        	// INFORM CONTROLLERS
			        	config.pacManController.nextLevel(game.copy());
			    		if (config.ghostsController != null) config.ghostsController.nextLevel(game.copy());
			    		
			    		// FLUSH REPLAY DATA TO FILE
			    		if (config.replay) {
			    			Replay.saveActions(config.game, (config.ghostsController == null ? 0 : config.ghostsController.getGhostCount()), replayData.toString(), config.replayFile, replayFirstWrite);
			        		replayFirstWrite = false;
			        		replayData = new StringBuilder();
			    		}
			        }
			        
			        // PAC MAN KILLED?
			        if (pacManLives != game.getLivesRemaining()) {
			        	config.pacManController.killed();
			        }
		        }
		        
		        // VISUALIZE GAME
		        if (config.visualize) {
		        	gv.repaint();
		        }
			}
		} finally {		
			// Stores the score in this episode 
			//Q_learn.scores.add(game.getScore());
			Q_learn.addNewScore(game.getScore());
			
			
			
			Q_learn.Q = new ArrayList<ArrayList<Float>>(Q_learn.currQ);
			
			// GESTION DE HILOS
			// KILL THREADS
			if(config.playWithThreads){
				pacManThread.kill();
				ghostsThread.kill();
			}
			// SAVE REPLAY DATA
			if (config.replay) {
				Replay.saveActions(config.game, (config.ghostsController == null ? 0 : config.ghostsController.getGhostCount()), replayData.toString(), config.replayFile, replayFirstWrite);
			}
			
			// CLEAN UP
			if (config.visualize) {
				if (config.pacManController instanceof KeyListener) {				
					gv.getFrame().removeKeyListener((KeyListener)config.pacManController);
				}
				if (config.ghostsController instanceof KeyListener) {				
					gv.getFrame().removeKeyListener((KeyListener)config.ghostsController);
				}
				
				gv.getFrame().setTitle("[FINISHED]");
				gv.getFrame().dispose();
				gv.repaint();
			}					
		}
		
		return game;
	}

	private void reset(SimulatorConfig config) {
		this.config = config;
		
		gv = null;
		game = null;		
	}
	
	private void storeActions(int[] replayStep, boolean newLine) {
		replayData.append( (game.getTotalTime()-1) + "\t" );
	
	    for (int i=0;i < replayStep.length; i++) {
	    	replayData.append(replayStep[i]+"\t");
	    }
	
	    if(newLine) {
	    	replayData.append("\n");
	    }
	}
	
	private interface IThinkingMethod {
		
		public void think();
		
	}
	
	private CountDownLatch thinkingLatch;
	
	private class ThinkingThread extends Thread 
	{
		public boolean thinking = false;
	    private IThinkingMethod method;
	    private boolean alive;
	    
	    public ThinkingThread(String name, IThinkingMethod method) 
	    {
	    	super(name);
	        this.method = method;
	        alive=true;
	        start();
	    }

	    public synchronized  void kill() 
	    {
	        alive=false;
	        notify();
	    }
	    
	    public synchronized void alert()
	    {
	        notify();
	    }

	    public synchronized void run() 
	    {
	    	 try {
	        	while(alive) 
		        {
		        	try {
		        		synchronized(this)
		        		{
	        				wait(); // waked-up via alert()
		                }
		        	} catch(InterruptedException e)	{
		                e.printStackTrace();
		            }
	
		        	if (alive) {
		        		thinking = true;
		        		method.think();
		        		thinking = false;
		        		try {
		        			thinkingLatch.countDown();
		        		} catch (Exception e) {
		        			// thinkingLatch may be nullified...
		        		}
		        	} 
		        	
		        }
	        } finally {
	        	alive = false;
	        }
	    }
	}
	
	/**
	 * Run simulation according to the configuration.
	 * @param config
	 * @return
	 */
	public static Game play(SimulatorConfig config) {
		PacManSimulator simulator = new PacManSimulator();
		return simulator.run(config);		
	}
	
	/**
	 * Run simulation visualized with ghosts.
	 * @param pacMan
	 * @param ghosts
	 * @return
	 */
	public static Game play(IPacManController pacMan, IGhostsController ghosts) {
		SimulatorConfig config = new SimulatorConfig();
		
		config.pacManController = pacMan;
		config.ghostsController = ghosts;
		
		config.replay = true;
		config.replayFile = new File("./replay.log");
		
		return play(config);	
	}
	
	/**
	 * Run simulation visualized w/o ghosts.
	 * @param pacMan
	 * @param ghosts
	 * @return
	 */
	public static Game play(IPacManController pacMan) {
		return play(pacMan, null);		
	}
	
	/**
	 * Run simulation headless.
	 * @param pacMan
	 * @param ghosts
	 * @return
	 */
	public static Game simulate(IPacManController pacMan, IGhostsController ghosts) {
		SimulatorConfig config = new SimulatorConfig();		
		
		config.visualize = false;
		
		config.pacManController = pacMan;
		config.ghostsController = ghosts;
		
		config.replay = true;
		config.replayFile = new File("./replay.log");
		
		return play(config);		
	}
		

	/*-----------------------------------------------------------------------------------*/
	/*
	 * FUNCTIONS FOR STORING DATA IN TEXT FILES
	 * */
	
	public static void storeCountData(){
		
		try{
		    PrintWriter writer = new PrintWriter("Count.txt", "UTF-8");

		    writer.println("States have been retrieved this amount of times:");
		    
		    for(int i=0; i<Q_learn.stateCounter.size(); i++){
		    	writer.println(i + " -> " + Q_learn.stateCounter.get(i));
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error printing count");
		}
		
	}
	
	public static void storeScores(){
		
		try{
		    PrintWriter writer = new PrintWriter("Scores.txt", "UTF-8");

		    for(int i=0; i<Q_learn.scores.size(); i++){
		    	//writer.println("Play nº " + i + ":" + Q_learn.scores.get(i));
		    	writer.println(i + " " + Q_learn.scores.get(i));
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error printing scores.");
		}
		
		try{
		    PrintWriter writer = new PrintWriter("drawableScores.txt", "UTF-8");

		    for(int i=0; i<Q_learn.drawableScores.size(); i++){
		    	//writer.println("Play nº " + i + ":" + Q_learn.scores.get(i));
		    	writer.println(i + " " + Q_learn.drawableScores.get(i));
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error printing scores.");
		}
		
		
	}
	
	public static void storeExperiment(){
		
		try{
		    PrintWriter writer = new PrintWriter("Experiment.txt", "UTF-8");

		    
		    writer.println("El experimento se ha completado con los siguientes parámetros:");
		    writer.println("Pesos: (" + Q_learn.w1 + "," + Q_learn.w2 + "," + Q_learn.w3 + ","+ Q_learn.w4 + ","+ Q_learn.w5 + ")");
		    writer.println("Episodios: " + Q_learn.n_ep + " - Alpha: " + Q_learn.alpha + " - Gamma: " + Q_learn.gamma + " - Case threshold: " + Q_learn.newCaseThreshold);
		    writer.println("Media puntuaciones: " + Q_learn.avgScore + " - Media tiempos: " + Q_learn.avgTime + " - StdDev Score: " + Q_learn.stdScore + " - StdDev Time: " + Q_learn.stdTime + 
		    		"Max Score:" + Q_learn.maxScore + "Max Tiempo: " + Q_learn.maxTime + "Max. levels: " + Q_learn.maxLevel + "Avg levels: " + Q_learn.avgLevels);
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error storing experiment data");
		}
		
	}
	
	public static void storeTimes(){
		
		try{
		    PrintWriter writer = new PrintWriter("Times.txt", "UTF-8");

		    for(int i=0; i<Q_learn.scores.size(); i++){
		    	//writer.println("Play nº " + i + ":" + Q_learn.scores.get(i));
		    	writer.println(i + " " + Q_learn.times.get(i));
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error printing times.");
		}
		
		try{
		    PrintWriter writer = new PrintWriter("DrwabaleTimes.txt", "UTF-8");

		    for(int i=0; i<Q_learn.drawableTimes.size(); i++){
		    	//writer.println("Play nº " + i + ":" + Q_learn.scores.get(i));
		    	writer.println(i + " " + Q_learn.drawableTimes.get(i));
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error printing times.");
		}
		
	}
	
	public static void storeQ(){
		
		try{
		    PrintWriter writer = new PrintWriter("Qdata.txt", "UTF-8");

		    for(int i=0; i<Q_learn.Q.size(); i++){
		    	writer.println(parseQValue(i,0) + " " + parseQValue(i,1) + " " + parseQValue(i,2) + " " + parseQValue(i,3) + " " + Q_learn.stateCounter.get(i));
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error storing Q matrix");
		}
		
		
		try{
		    PrintWriter writer = new PrintWriter("Q_Matrix.txt", "UTF-8");

		    writer.println("Q matrix has stored the following info:");
		    
		    for(int i=0; i<Q_learn.Q.size(); i++){
		    	writer.println(i + " -> " + Q_learn.Q.get(i));
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error printing Q matrix");
		}
		
	}
	
	public static float parseQValue(int row, int col){
		float result = -1;
		
		if(Q_learn.Q.get(row).get(col) < 0.001) result = 0;
		else result = Q_learn.Q.get(row).get(col);
		
		return result;
	}
	
	public static void storeS(){
		
		try{
		    PrintWriter writer = new PrintWriter("Sdata.txt", "UTF-8");

		    for(int i=0; i<Q_learn.S.size(); i++){
		    	writer.println(Q_learn.S.get(i).data());
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error storing S matrix");
		}
		
		try{
		    PrintWriter writer = new PrintWriter("States.txt", "UTF-8");

		    writer.println("Q matrix has stored the following info:");
		    
		    for(int i=0; i<Q_learn.S.size(); i++){
		    	writer.println(Q_learn.S.get(i).toString());
		    }
		    
		    writer.close();
		} catch (IOException e) {
		   System.out.println("Error printing state list.");
		}
		
	}
	
	public static void storeData(){
		storeQ();
		
		storeCountData();
		storeS();
		
		storeExperiment();
		storeScores();
		storeTimes();
		
	}
	
	/*-----------------------------------------------------------------------------------*/
	/*
	 * FUNCTIONS FOR LOADING DATA FROM TEXT FILES
	 * */
	
	public static void loadQ(){
		  try
		  {
		    BufferedReader reader = new BufferedReader(new FileReader("Qdata.txt"));
		    float index = 0, q0,q1,q2,q3;
		    int count;
		    String line;
		    String[] numbers = null;
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split("\\d\\s+");

	           q0 = Float.valueOf(numbers[0].trim());
	           q1 = Float.valueOf(numbers[1].trim());
	           q2 = Float.valueOf(numbers[2].trim());
	           q3 = Float.valueOf(numbers[3].trim()); 	
	           count = Integer.parseInt(numbers[4].trim());
	           
	           Q_learn.Q.add(new ArrayList<>(Arrays.asList(q0, q1, q2, q3)));
	           Q_learn.stateCounter.add(count);
		    }
		    
		    reader.close();
		  }
		  catch (Exception e)
		  {
		    System.err.format("Exception occurred trying to read Qdata.txt");
		    e.printStackTrace();
		  }
		
	}
	
	public static void loadScores(){
		  try
		  {
		    BufferedReader reader = new BufferedReader(new FileReader("Scores.txt"));
		    float index = 0;
		    int count, score;
		    String line, part;
		    String[] s1 = null, s2 = null;
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	s1 = line.split(" ");
		    	
		    	score = Integer.valueOf(s1[1].trim());
		    	Q_learn.scores.add(score);
	           
		    	
		    	/*
		    	s1 = line.split(" ");
		    	
		    	score = Integer.valueOf(s1[1].trim());
		    	Q_learn.scores.add(score);*/
		    }
		    
		    reader.close();
		  }
		  catch (Exception e)
		  {
		    System.err.format("Exception occurred trying to read Scores.txt");
		    e.printStackTrace();
		  }
		
		 
		  
	}
	
	
	public static void loadOtherScores(){
		
		 try
		  {
		    BufferedReader reader = new BufferedReader(new FileReader("RandomScores.txt"));
		    float index = 0;
		    int count, score;
		    String line, part;
		    String[] s1 = null, s2 = null;
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	s1 = line.split(" ");
		    	
		    	score = Integer.valueOf(s1[1].trim());
		    	Q_learn.randomScores.add(score);
	           
		    
		    }
		    
		    reader.close();
		  }
		  catch (Exception e)
		  {
		    System.err.format("Exception occurred trying to read Scores.txt");
		    e.printStackTrace();
		  }
		  
		  try
		  {
		    BufferedReader reader = new BufferedReader(new FileReader("ClosestPillScores.txt"));
		    float index = 0;
		    int count, score;
		    String line, part;
		    String[] s1 = null, s2 = null;
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	s1 = line.split(" ");
		    	
		    	score = Integer.valueOf(s1[1].trim());
		    	Q_learn.closestPillScores.add(score);
	           
		    	

		    }
		    
		    reader.close();
		  }
		  catch (Exception e)
		  {
		    System.err.format("Exception occurred trying to read Scores.txt");
		    e.printStackTrace();
		  }
	}
	
	public static void loadTimes(){
		  try
		  {
		    BufferedReader reader = new BufferedReader(new FileReader("Times.txt"));
		    float index = 0;
		    int count, time;
		    String line, part;
		    String[] s1 = null, s2 = null;
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	s1 = line.split(" ");
		    	
		    	time = Integer.valueOf(s1[1].trim());
		    	Q_learn.times.add(time);
	           
		    	
		    	/*
		    	s1 = line.split(" ");
		    	
		    	score = Integer.valueOf(s1[1].trim());
		    	Q_learn.scores.add(score);*/
		    }
		    
		    reader.close();
		  }
		  catch (Exception e)
		  {
		    System.err.format("Exception occurred trying to read Times.txt");
		    e.printStackTrace();
		  }
		
	}
	
	
	public static void loadS(){
		  /*try
		  {
		    BufferedReader reader = new BufferedReader(new FileReader("Sdata.txt"));
		    int index = 0,  cp, cg, cpp, ceg;
		    boolean u, r, d, l;
		    String line;
		    String[] numbers = null;
		    
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	
		    	numbers = line.split(" ");
		    	
		    	index = Integer.parseInt(numbers[0].trim());
	           	u = Boolean.parseBoolean(numbers[1].trim());
	           	r = Boolean.parseBoolean(numbers[2].trim());
	           	d = Boolean.parseBoolean(numbers[3].trim()); 	
	           	l = Boolean.parseBoolean(numbers[4].trim());
	           	cp = Integer.parseInt(numbers[5].trim());
	           	cg = Integer.parseInt(numbers[6].trim());
	           	cpp = Integer.parseInt(numbers[7].trim());
	           	ceg = Integer.parseInt(numbers[8].trim());
	           	
	           Q_learn.S.add(new GameState(index, u, r, d, l, cp, cg, cpp, ceg));
		    }
		    
		    reader.close();
		  }
		  catch (Exception e)
		  {
		    System.err.format("Exception occurred trying to read Sdata.txt");
		    e.printStackTrace();
		  }*/
		
		
		 try
		  {
		    BufferedReader reader = new BufferedReader(new FileReader("Sdata.txt"));
		    int index = 0, pu, pr, pd, pl, gu, gr, gd, gl, ppu, ppr, ppd, ppl, egu, egr, egd, egl,iu,ir,id,il ;
		    String line;
		    String[] numbers = null;
		    
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	
		    	numbers = line.split(" ");
		    	
		    	index = Integer.parseInt(numbers[0].trim());
	           	pu = Integer.parseInt(numbers[1].trim());
	           	pr = Integer.parseInt(numbers[2].trim());
	           	pd = Integer.parseInt(numbers[3].trim()); 	
	           	pl = Integer.parseInt(numbers[4].trim());
	           	gu = Integer.parseInt(numbers[5].trim());
	           	gr = Integer.parseInt(numbers[6].trim());
	           	gd = Integer.parseInt(numbers[7].trim());
	           	gl = Integer.parseInt(numbers[8].trim());
	           	ppu = Integer.parseInt(numbers[9].trim());
	           	ppr = Integer.parseInt(numbers[10].trim());
	           	ppd = Integer.parseInt(numbers[11].trim()); 	
	           	ppl = Integer.parseInt(numbers[12].trim());
	           	egu = Integer.parseInt(numbers[13].trim());
	           	egr = Integer.parseInt(numbers[14].trim());
	           	egd = Integer.parseInt(numbers[15].trim());
	           	egl = Integer.parseInt(numbers[16].trim());
	           	iu = Integer.parseInt(numbers[17].trim());
	           	ir = Integer.parseInt(numbers[18].trim());
	           	id = Integer.parseInt(numbers[19].trim());
	           	il = Integer.parseInt(numbers[20].trim());
	           //ng = Integer.valueOf(numbers[3].trim());
	           
	           Q_learn.S.add(new GameState(index, pu, pr, pd, pl, gu, gr, gd, gl, ppu, ppr, ppd, ppl, egu, egr, egd, egl
	        		   ,iu, ir, id, il));
		    }
		    
		    reader.close();
		  }
		  catch (Exception e)
		  {
		    System.err.format("Exception occurred trying to read Sdata.txt");
		    e.printStackTrace();
		  }
		
	}
	
	
	// Fill distances vectors
	public static void loadDistances(){
		//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances...");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
		    	
	    		Q_learn.distances.add(new ArrayList<Integer>());
	    		for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distances.get(row).add(Integer.parseInt(numbers[i].trim()));
		    		
		    	};
		    	
		    	if(row%100 == 0)  System.out.print("%");
		    	row++;
		    }
		   
		    System.out.println("%");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		
		//ArrayList<ArrayList<Integer>> distancesUp = new ArrayList<ArrayList<Integer>>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances0.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances in direction: UP");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
		    	//int i = 0;

	    		Q_learn.distancesUp.add(new ArrayList<Integer>());
		    	for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distancesUp.get(row).add(Integer.parseInt(numbers[i].trim()));
		    	}
		    	/*
		    	while(Integer.parseInt(numbers[i].trim()) != -1) {
		    		Q_learn.distancesUp.add(new ArrayList<Integer>());
		    		Q_learn.distancesUp.get(row).add(Integer.parseInt(numbers[i].trim()));
		    		i++;
		    	};*/
		    	
		    	if(row%100 == 0)  System.out.print("-");
		    	row++;
		    }
		    System.out.println("-");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances1.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances in direction RIGHT");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
	    		Q_learn.distancesRight.add(new ArrayList<Integer>());
		    	for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distancesRight.get(row).add(Integer.parseInt(numbers[i].trim()));
		    	}
		    	
		    	if(row%100 == 0)  System.out.print("-");
		    	row++;
		    }
		   
		    System.out.println("-");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances2.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances in direction DOWN");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
		    	Q_learn.distancesDown.add(new ArrayList<Integer>());
		    	for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distancesDown.get(row).add(Integer.parseInt(numbers[i].trim()));
		    	}
		    	
		    	if(row%100 == 0)  System.out.print("-");
		    	row++;
		    }
		   
		    System.out.println("-");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances3.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances in direction LEFT");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
	    		Q_learn.distancesLeft.add(new ArrayList<Integer>());
		    	for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distancesLeft.get(row).add(Integer.parseInt(numbers[i].trim()));
		    	}
		    	
		    	if(row%100 == 0)  System.out.print("-");
		    	row++;
		    }
		   
		    System.out.println("-");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		
		
		//**  LOAD DISTANCES OF SECOND MAP**//
		//ArrayList<ArrayList<Integer>> distancesUp = new ArrayList<ArrayList<Integer>>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances20.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances in direction: UP");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
		    	//int i = 0;

	    		Q_learn.distances2Up.add(new ArrayList<Integer>());
		    	for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distances2Up.get(row).add(Integer.parseInt(numbers[i].trim()));
		    	}
		    	/*
		    	while(Integer.parseInt(numbers[i].trim()) != -1) {
		    		Q_learn.distancesUp.add(new ArrayList<Integer>());
		    		Q_learn.distancesUp.get(row).add(Integer.parseInt(numbers[i].trim()));
		    		i++;
		    	};*/
		    	
		    	if(row%100 == 0)  System.out.print("-");
		    	row++;
		    }
		    System.out.println("-");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances21.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances in direction RIGHT");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
	    		Q_learn.distances2Right.add(new ArrayList<Integer>());
		    	for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distances2Right.get(row).add(Integer.parseInt(numbers[i].trim()));
		    	}
		    	
		    	if(row%100 == 0)  System.out.print("-");
		    	row++;
		    }
		   
		    System.out.println("-");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances22.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances in direction DOWN");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
		    	Q_learn.distances2Down.add(new ArrayList<Integer>());
		    	for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distances2Down.get(row).add(Integer.parseInt(numbers[i].trim()));
		    	}
		    	
		    	if(row%100 == 0)  System.out.print("-");
		    	row++;
		    }
		   
		    System.out.println("-");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		try {
			BufferedReader reader = new BufferedReader(new FileReader("distances23.txt"));
			String line;
		    String[] numbers = null;
		    int row = 0;
		    
		    System.out.println("Loading distances in direction LEFT");
		    
		    while ((line = reader.readLine()) != null)
		    {
		    	numbers = line.split(" ");
	    		Q_learn.distances2Left.add(new ArrayList<Integer>());
		    	for(int i=0; i<numbers.length-1; i++){
		    		Q_learn.distances2Left.get(row).add(Integer.parseInt(numbers[i].trim()));
		    	}
		    	
		    	if(row%100 == 0)  System.out.print("-");
		    	row++;
		    }
		   
		    System.out.println("-");
		    System.out.println("Load completed succesfully");
		    
		    reader.close();
			
			
			
		} catch (FileNotFoundException e) {
			System.err.format("Exception occurred trying to read distances.txt");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.format("IO-Exception occurred trying to read distances.txt");
			e.printStackTrace();
		}
		
		
				//**  LOAD DISTANCES OF THIRD MAP**//
				//ArrayList<ArrayList<Integer>> distancesUp = new ArrayList<ArrayList<Integer>>();
				try {
					BufferedReader reader = new BufferedReader(new FileReader("distances30.txt"));
					String line;
				    String[] numbers = null;
				    int row = 0;
				    
				    System.out.println("Loading distances in direction: UP");
				    
				    while ((line = reader.readLine()) != null)
				    {
				    	numbers = line.split(" ");
				    	//int i = 0;

			    		Q_learn.distances3Up.add(new ArrayList<Integer>());
				    	for(int i=0; i<numbers.length-1; i++){
				    		Q_learn.distances3Up.get(row).add(Integer.parseInt(numbers[i].trim()));
				    	}
				    	/*
				    	while(Integer.parseInt(numbers[i].trim()) != -1) {
				    		Q_learn.distancesUp.add(new ArrayList<Integer>());
				    		Q_learn.distancesUp.get(row).add(Integer.parseInt(numbers[i].trim()));
				    		i++;
				    	};*/
				    	
				    	if(row%100 == 0)  System.out.print("-");
				    	row++;
				    }
				    System.out.println("-");
				    System.out.println("Load completed succesfully");
				    
				    reader.close();
					
					
					
				} catch (FileNotFoundException e) {
					System.err.format("Exception occurred trying to read distances.txt");
					e.printStackTrace();
				} catch (IOException e) {
					System.err.format("IO-Exception occurred trying to read distances.txt");
					e.printStackTrace();
				}
				
				//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
				try {
					BufferedReader reader = new BufferedReader(new FileReader("distances31.txt"));
					String line;
				    String[] numbers = null;
				    int row = 0;
				    
				    System.out.println("Loading distances in direction RIGHT");
				    
				    while ((line = reader.readLine()) != null)
				    {
				    	numbers = line.split(" ");
			    		Q_learn.distances3Right.add(new ArrayList<Integer>());
				    	for(int i=0; i<numbers.length-1; i++){
				    		Q_learn.distances3Right.get(row).add(Integer.parseInt(numbers[i].trim()));
				    	}
				    	
				    	if(row%100 == 0)  System.out.print("-");
				    	row++;
				    }
				   
				    System.out.println("-");
				    System.out.println("Load completed succesfully");
				    
				    reader.close();
					
					
					
				} catch (FileNotFoundException e) {
					System.err.format("Exception occurred trying to read distances.txt");
					e.printStackTrace();
				} catch (IOException e) {
					System.err.format("IO-Exception occurred trying to read distances.txt");
					e.printStackTrace();
				}
				
				//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
				try {
					BufferedReader reader = new BufferedReader(new FileReader("distances32.txt"));
					String line;
				    String[] numbers = null;
				    int row = 0;
				    
				    System.out.println("Loading distances in direction DOWN");
				    
				    while ((line = reader.readLine()) != null)
				    {
				    	numbers = line.split(" ");
				    	Q_learn.distances3Down.add(new ArrayList<Integer>());
				    	for(int i=0; i<numbers.length-1; i++){
				    		Q_learn.distances3Down.get(row).add(Integer.parseInt(numbers[i].trim()));
				    	}
				    	
				    	if(row%100 == 0)  System.out.print("-");
				    	row++;
				    }
				   
				    System.out.println("-");
				    System.out.println("Load completed succesfully");
				    
				    reader.close();
					
					
					
				} catch (FileNotFoundException e) {
					System.err.format("Exception occurred trying to read distances.txt");
					e.printStackTrace();
				} catch (IOException e) {
					System.err.format("IO-Exception occurred trying to read distances.txt");
					e.printStackTrace();
				}
				
				try {
					BufferedReader reader = new BufferedReader(new FileReader("distances33.txt"));
					String line;
				    String[] numbers = null;
				    int row = 0;
				    
				    System.out.println("Loading distances in direction LEFT");
				    
				    while ((line = reader.readLine()) != null)
				    {
				    	numbers = line.split(" ");
			    		Q_learn.distances3Left.add(new ArrayList<Integer>());
				    	for(int i=0; i<numbers.length-1; i++){
				    		Q_learn.distances3Left.get(row).add(Integer.parseInt(numbers[i].trim()));
				    	}
				    	
				    	if(row%100 == 0)  System.out.print("-");
				    	row++;
				    }
				   
				    System.out.println("-");
				    System.out.println("Load completed succesfully");
				    
				    reader.close();
					
					
					
				} catch (FileNotFoundException e) {
					System.err.format("Exception occurred trying to read distances.txt");
					e.printStackTrace();
				} catch (IOException e) {
					System.err.format("IO-Exception occurred trying to read distances.txt");
					e.printStackTrace();
				}
		
				
				//**  LOAD DISTANCES OF FOURTH MAP**//
				//ArrayList<ArrayList<Integer>> distancesUp = new ArrayList<ArrayList<Integer>>();
				try {
					BufferedReader reader = new BufferedReader(new FileReader("distances40.txt"));
					String line;
				    String[] numbers = null;
				    int row = 0;
				    
				    System.out.println("Loading distances in direction: UP");
				    
				    while ((line = reader.readLine()) != null)
				    {
				    	numbers = line.split(" ");
				    	//int i = 0;

			    		Q_learn.distances4Up.add(new ArrayList<Integer>());
				    	for(int i=0; i<numbers.length-1; i++){
				    		Q_learn.distances4Up.get(row).add(Integer.parseInt(numbers[i].trim()));
				    	}
				    	/*
				    	while(Integer.parseInt(numbers[i].trim()) != -1) {
				    		Q_learn.distancesUp.add(new ArrayList<Integer>());
				    		Q_learn.distancesUp.get(row).add(Integer.parseInt(numbers[i].trim()));
				    		i++;
				    	};*/
				    	
				    	if(row%100 == 0)  System.out.print("-");
				    	row++;
				    }
				    System.out.println("-");
				    System.out.println("Load completed succesfully");
				    
				    reader.close();
					
					
					
				} catch (FileNotFoundException e) {
					System.err.format("Exception occurred trying to read distances.txt");
					e.printStackTrace();
				} catch (IOException e) {
					System.err.format("IO-Exception occurred trying to read distances.txt");
					e.printStackTrace();
				}
				
				//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
				try {
					BufferedReader reader = new BufferedReader(new FileReader("distances41.txt"));
					String line;
				    String[] numbers = null;
				    int row = 0;
				    
				    System.out.println("Loading distances in direction RIGHT");
				    
				    while ((line = reader.readLine()) != null)
				    {
				    	numbers = line.split(" ");
			    		Q_learn.distances4Right.add(new ArrayList<Integer>());
				    	for(int i=0; i<numbers.length-1; i++){
				    		Q_learn.distances4Right.get(row).add(Integer.parseInt(numbers[i].trim()));
				    	}
				    	
				    	if(row%100 == 0)  System.out.print("-");
				    	row++;
				    }
				   
				    System.out.println("-");
				    System.out.println("Load completed succesfully");
				    
				    reader.close();
					
					
					
				} catch (FileNotFoundException e) {
					System.err.format("Exception occurred trying to read distances.txt");
					e.printStackTrace();
				} catch (IOException e) {
					System.err.format("IO-Exception occurred trying to read distances.txt");
					e.printStackTrace();
				}
				
				//ArrayList<ArrayList<Integer>> distances = new ArrayList<ArrayList<Integer>>();
				try {
					BufferedReader reader = new BufferedReader(new FileReader("distances42.txt"));
					String line;
				    String[] numbers = null;
				    int row = 0;
				    
				    System.out.println("Loading distances in direction DOWN");
				    
				    while ((line = reader.readLine()) != null)
				    {
				    	numbers = line.split(" ");
				    	Q_learn.distances4Down.add(new ArrayList<Integer>());
				    	for(int i=0; i<numbers.length-1; i++){
				    		Q_learn.distances4Down.get(row).add(Integer.parseInt(numbers[i].trim()));
				    	}
				    	
				    	if(row%100 == 0)  System.out.print("-");
				    	row++;
				    }
				   
				    System.out.println("-");
				    System.out.println("Load completed succesfully");
				    
				    reader.close();
					
					
					
				} catch (FileNotFoundException e) {
					System.err.format("Exception occurred trying to read distances.txt");
					e.printStackTrace();
				} catch (IOException e) {
					System.err.format("IO-Exception occurred trying to read distances.txt");
					e.printStackTrace();
				}
				
				try {
					BufferedReader reader = new BufferedReader(new FileReader("distances43.txt"));
					String line;
				    String[] numbers = null;
				    int row = 0;
				    
				    System.out.println("Loading distances in direction LEFT");
				    
				    while ((line = reader.readLine()) != null)
				    {
				    	numbers = line.split(" ");
			    		Q_learn.distances4Left.add(new ArrayList<Integer>());
				    	for(int i=0; i<numbers.length-1; i++){
				    		Q_learn.distances4Left.get(row).add(Integer.parseInt(numbers[i].trim()));
				    	}
				    	
				    	if(row%100 == 0)  System.out.print("-");
				    	row++;
				    }
				   
				    System.out.println("-");
				    System.out.println("Load completed succesfully");
				    
				    reader.close();
					
					
					
				} catch (FileNotFoundException e) {
					System.err.format("Exception occurred trying to read distances.txt");
					e.printStackTrace();
				} catch (IOException e) {
					System.err.format("IO-Exception occurred trying to read distances.txt");
					e.printStackTrace();
				}
		Q_learn.currDistancesUp = Q_learn.distancesUp;
		Q_learn.currDistancesRight = Q_learn.distancesRight;
		Q_learn.currDistancesDown = Q_learn.distancesDown;
		Q_learn.currDistancesLeft = Q_learn.distancesLeft;
	}
	
	
	public static void loadData(){
		
		loadQ();
		loadS();
		loadScores();
		loadTimes();
		//loadDistances();
	}
	

	/*-----------------------------------------------------------------------------------*/
	
	// Shows plots for gathered data
	public static void drawGraphs(){
		DrawGraph.draw(Q_learn.drawableScores, "1. Scores");
		DrawGraph.draw(Q_learn.drawableTimes, "2. Times");
		DrawGraph.draw(Q_learn.numberOfCases, "3. Number of cases");
		DrawGraph.draw(Q_learn.addedCases, "4. Number of new cases");
		DrawGraph.draw(Q_learn.retrievedCases, "5. Number of retrieved cases");
		DrawGraphFloat.draw(Q_learn.averageSimilarities, "6. Average similarity");
	}
	

	
	public static void printEpisode(int i){
		System.out.println("Episode " + (i+1) + " finished \n");
		System.out.println("There are " + Q_learn.S.size() + " states \n");
		
	}
	
	public static void main(String[] args) {
		
		float originalEps = Q_learn.eps;
		
		loadDistances();
		loadOtherScores();
		if(Q_learn.load){
			loadData();
			Q_learn.currQ = new ArrayList<ArrayList<Float>>(Q_learn.Q);
		}
		
		if(Q_learn.learning){
			
			
			// Plays 'n_ep' number of rounds, and learn from them. That 'n_ep' amount is specified in Q_learn class.
			for(int i=0; i<Q_learn.n_ep; i++){
				
				Q_learn.reward = 0;
				Q_learn.prevScore = 0;
				//play(new QLearnDirPacMan(), new GameGhosts(4, false));
				//play(new RandomPacMan(), new GameGhosts(4, false));
				play(new QLearnDirPacMan(), new Legacy());
				
				
				
				printEpisode(i);
				
				Q_learn.endEpisode();
				
				
				//storeData();
				
				// Progressively reduces epsilon to slowly stop exploration
				if (i%100 == 0) {
					Q_learn.eps = originalEps * (Q_learn.n_ep - i)/ (float) Q_learn.n_ep;
				}
			}
			
			
			System.out.println("Value of eps is: " + Q_learn.eps);
			Q_learn.stats();
			//Q_learn.calculateDrawableScores();
			storeData();

			//drawGraphs();
			
		}else{

			
		// PLAY WITHOUT GHOSTS
		//play(new HumanPacMan());
		
		// PLAY WITH 1 GHOST
		// play(new HumanPacMan(), new GameGhosts(1, false));
		
		// PLAY WITH 2 GHOSTS
		//play(new HumanPacMan(), new GameGhosts(2, false));
		
		// PLAY WITH 3 GHOSTS
		//play(new HumanPacMan(), new GameGhosts(3, false));
		
		// PLAY WITH 4 GHOSTS
		//play(new RandomPacMan(), new GameGhosts(4, false));
			
			
			for(int i=0; i<Q_learn.n_ep; i++){
				
				Q_learn.reward = 0;
				Q_learn.prevScore = 0;
				play(new RandomPacMan(), new GameGhosts(4, false));
				
				
				
				printEpisode(i);
				
				Q_learn.endEpisode();
				
			
			}
			
			Q_learn.stats();
			Q_learn.calculateDrawableScores();
			storeData();
			
			drawGraphs();
		

		}
		
	}
	

}
