import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.io.*;
import javax.swing.*;


/**
 * The class <b>GameController</b> is the controller of the game. It implements 
 * the interface ActionListener to be called back when the player makes a move. It computes
 * the next step of the game, and then updates model and view.
 *
 */


public class GameController implements ActionListener {
  
  /**
   * Reference to the view of the game
   */
  private GameView gameView;
  
  /**
   * Reference to the model of the game
   */
  private GameModel gameModel;
  private ObjectOutputStream objectOutputStream;
  private ObjectInputStream objectInputStream;
  private File savedGame;
  private Stack <GameModel> undoStack;
  private Stack <GameModel> redoStack;
  
  
  /**
   * Constructor used for initializing the controller. It creates the game's view 
   * and the game's model instances
   * 
   * @param size
   *            the size of the board on which the game will be played
   */
  public GameController(int size) throws IOException,ClassNotFoundException{
    savedGame=new File("savedGame.ser");
    if (savedGame.exists()){  
      //reads saved GameModel in savedGame
      objectInputStream=new ObjectInputStream(
                                              new FileInputStream(
                                                                  savedGame));
      //creates game based on the saved GameModel in savedGame
      gameModel=(GameModel)objectInputStream.readObject();
      objectInputStream.close();
      savedGame.delete();
    }
    else{
      gameModel = new GameModel(size);
    }
    undoStack = new LinkedStack <GameModel>();
    redoStack = new LinkedStack <GameModel>();
    gameView = new GameView(gameModel, this);
    gameView.update();
  }
  
  
  
  /**
   * resets the game
   */
  public void reset(){
    gameModel.reset();
    undoStack=null;
    undoStack=new LinkedStack <GameModel>();
    redoStack=null;
    redoStack=new LinkedStack <GameModel>();
    checkRedoUndo();
    gameView.update();
  }
  
  /**
   * Callback used when the user clicks a button or one of the dots. 
   * Implements the logic of the game
   *
   * @param e
   *            the ActionEvent
   */
  
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() instanceof DotButton) {
      DotButton clicked = (DotButton)(e.getSource());
      redoStack=null;
      redoStack=new LinkedStack <GameModel>();
      try {
        GameModel clonedModel = (GameModel)gameModel.clone() ; 
        undoStack.push (clonedModel);
      }
      catch (Exception CloneNotSupportedException)
      {
        System.out.println("Caught CloneNotSupportedException ");
      }
      
      if (gameModel.getCurrentStatus(clicked.getColumn(),clicked.getRow()) ==
          GameModel.AVAILABLE){
        gameModel.select(clicked.getColumn(),clicked.getRow());
        oneStep();
      }
    } else if (e.getSource() instanceof JButton) {
      JButton clicked = (JButton)(e.getSource());
      
      if (clicked.getText().equals("Quit")) {
        try{
          saveGame();
        }
        catch (IOException ex){
          System.out.println(ex);
        }
        System.exit(0);
      }
      else if (clicked.getText().equals ("Undo")){
        if (!undoStack.isEmpty()){
          try{
            gameView.updateRedo(true);
            redoStack.push ((GameModel)gameModel.clone());
            gameModel = (GameModel)undoStack.pop();
            gameView.setModel (gameModel);
            gameView.update();
          }catch (Exception EmptyStackException)
          {
            System.out.println ("Caught EmptyStackException");
          }
        }
      }
      else if (clicked.getText().equals("Redo")){
        if (!redoStack.isEmpty()){
          try{
            undoStack.push ((GameModel)gameModel.clone());
            gameModel = (GameModel)redoStack.pop();
            gameView.setModel (gameModel);
            gameView.update();
          }catch (Exception EmptyStackException)
          {
            System.out.println ("Caught EmptyStackException");
          }
        }
      }
      else if (clicked.getText().equals("Reset")){
        reset();
      } 
    } 

    checkRedoUndo();
  }
  
  /**
   * Computes the next step of the game. If the player has lost, it 
   * shows a dialog offering to replay.
   * If the user has won, it shows a dialog showing the number of 
   * steps that had been required in order to win. 
   * Else, it finds one of the shortest path for the blue dot to 
   * exit the board and moves it one step in that direction.
   */
  private void oneStep(){
    Point currentDot = gameModel.getCurrentDot();
    if(isOnBorder(currentDot)) {
      gameModel.setCurrentDot(-1,-1);
      gameView.update();
      
      Object[] options = {"Play Again",
        "Quit"};
      int n = JOptionPane.showOptionDialog(gameView,
                                           "You lost! Would you like to play again?",
                                           "Lost",
                                           JOptionPane.YES_NO_OPTION,
                                           JOptionPane.QUESTION_MESSAGE,
                                           null,
                                           options,
                                           options[0]);
      if(n == 0){
        reset();
      } else{
        System.exit(0);
      }
    }
    else{
      Point direction = findDirection();
      if(direction.getX() == -1){
        gameView.update();
        Object[] options = {"Play Again",
          "Quit"};
        int n = JOptionPane.showOptionDialog(gameView,
                                             "Congratualtions, you won in " + gameModel.getNumberOfSteps() 
                                               +" steps!\n Would you like to play again?",
                                             "Won",
                                             JOptionPane.YES_NO_OPTION,
                                             JOptionPane.QUESTION_MESSAGE,
                                             null,
                                             options,
                                             options[0]);
        if(n == 0){
          reset();
        } else{
          System.exit(0);
        }
      }
      else{
        gameModel.setCurrentDot(direction.getX(), direction.getY());
        gameView.update();
      }
    }
    
  }
  
  /**
   * Does a ``breadth-first'' search from the current location of the blue dot to find
   * one of the shortest available path to exit the board. 
   *
   * @return the location (as a Point) of the next step for the blue dot toward the exit.
   * If the blue dot is encircled and cannot exit, returns an instance of the class Point 
   * at location (-1,-1)
   */
  
  private Point findDirection(){
    boolean[][] blocked = new boolean[gameModel.getSize()][gameModel.getSize()];
    
    for(int i = 0; i < gameModel.getSize(); i ++){
      for (int j = 0; j < gameModel.getSize(); j ++){
        blocked[i][j] = 
          !(gameModel.getCurrentStatus(i,j) == GameModel.AVAILABLE);
      }
    }
    
    Queue<Pair<Point>> myQueue = new LinkedQueue<Pair<Point>>();
    
    LinkedList<Point> possibleNeighbours = new  LinkedList<Point>();
    
    // start with neighbours of the current dot
    // (note: we know the current dot isn't on the border)
    Point currentDot = gameModel.getCurrentDot();
    
    possibleNeighbours = findPossibleNeighbours(currentDot, blocked);
    
    // adding some non determinism into the game !
    java.util.Collections.shuffle(possibleNeighbours);
    
    for(int i = 0; i < possibleNeighbours.size() ; i++){
      Point p = possibleNeighbours.get(i);
      if(isOnBorder(p)){
        return p;                
      }
      myQueue.enqueue(new Pair<Point>(p,p));
      blocked[p.getX()][p.getY()] = true;
    }
    
    
    // start the search
    while(!myQueue.isEmpty()){
      try{
        Pair<Point> pointPair = myQueue.dequeue();
        possibleNeighbours = findPossibleNeighbours(pointPair.getFirst(), blocked);
        
        for(int i = 0; i < possibleNeighbours.size() ; i++){
          Point p = possibleNeighbours.get(i);
          if(isOnBorder(p)){
            return pointPair.getSecond();                
          }
          myQueue.enqueue(new Pair<Point>(p,pointPair.getSecond()));
          blocked[p.getX()][p.getY()]=true;
        }
      }
      catch (Exception EmptyQueueException)
      {
        System.out.println("Caught EmptyQueueException ");
      }
      
      
      
      
      
    }
    
    // could not find a way out. Return an outside direction
    return new Point(-1,-1);
    
  }
  
  /**
   * Helper method: checks if a point is on the border of the board
   *
   * @param p
   *            the point to check
   *
   * @return true iff p is on the border of the board
   */
  
  private boolean isOnBorder(Point p){
    return (p.getX() == 0 || p.getX() == gameModel.getSize() - 1 ||
            p.getY() == 0 || p.getY() == gameModel.getSize() - 1 );
  }
  
  /**
   * Helper method: find the list of direct neighbours of a point that are not
   * currenbtly blocked
   *
   * @param point
   *            the point to check
   * @param blocked
   *            a 2 dimentionnal array of booleans specifying the points that 
   *              are currently blocked
   *
   * @return an instance of a LinkedList class, holding a list of instances of 
   *      the class Points representing the neighbours of parameter point that 
   *      are not currently blocked.
   */
  private LinkedList<Point> findPossibleNeighbours(Point point, 
                                                   boolean[][] blocked){
    
    LinkedList<Point> list = new LinkedList<Point>();
    int delta = (point.getY() %2 == 0) ? 1 : 0;
    if(!blocked[point.getX()-delta][point.getY()-1]){
      list.add(new Point(point.getX()-delta, point.getY()-1));

    }
    if(!blocked[point.getX()-delta+1][point.getY()-1]){
      list.add(new Point(point.getX()-delta+1, point.getY()-1));
    }
    if(!blocked[point.getX()-1][point.getY()]){
      list.add(new Point(point.getX()-1, point.getY()));
    }
    if(!blocked[point.getX()+1][point.getY()]){
      list.add(new Point(point.getX()+1, point.getY()));
    }
    if(!blocked[point.getX()-delta][point.getY()+1]){
      list.add(new Point(point.getX()-delta, point.getY()+1));
    }
    if(!blocked[point.getX()-delta+1][point.getY()+1]){
      list.add(new Point(point.getX()-delta+1, point.getY()+1));
    }
    return list;
  }
  private void saveGame()throws IOException {
    objectOutputStream=new ObjectOutputStream(
                                              new FileOutputStream(
                                                                   savedGame));
    objectOutputStream.writeObject(gameModel);
    objectOutputStream.close();
  }
  //appropriately changes state of undo and redo button 
  private void checkRedoUndo(){
    if (undoStack.isEmpty()){
      gameView.updateUndo(false);
    }
    else{
      gameView.updateUndo(true);
    }
    if (redoStack.isEmpty()){
      gameView.updateRedo(false);
    }
    else{
      gameView.updateRedo(true);
    }
  }
}

