import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Main {
    private Board board;
    private BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
    public PlayTreeNode rootNode;
    public byte myPlayerColor;

    private ConcurrentLinkedQueue<PlayTreeNode> treeQueue = new ConcurrentLinkedQueue<PlayTreeNode>();

    public synchronized PlayTreeNode getNodeToExpand(){
        PlayTreeNode n = treeQueue.poll();
        while(n == null || treeQueue.size() > 2e6){
            try {
                Thread.sleep(20);
            }catch(Exception e){}
            n = treeQueue.poll();
        }

        while(!n.isValidInTree){
            n = treeQueue.poll();
            while(n == null){
                try {
                    Thread.sleep(20);
                }catch(Exception e){}
                n = treeQueue.poll();
            }
        }
        return n;
    }

    public synchronized void addNodeToExpand(PlayTreeNode n){
        treeQueue.add(n);
    }

    public void play(String[] args) {
        byte iPlay = Board.LIGHT;
        int boardSize = 8;

        for (int i = 0; i < args.length; i++) {
            if (args[i].indexOf("-l") == 0) {
                iPlay = Board.DARK;
            } else if (args[i].indexOf("-n") == 0) {
                boardSize = Integer.parseInt(args[i + 1]);
            }
        }

        myPlayerColor = iPlay;
        byte currentColor = Board.DARK;

        board = new Board(boardSize);
        rootNode = new PlayTreeNode(board, null, myPlayerColor == currentColor ? PlayTreeNode.MAX_NODE : PlayTreeNode.MIN_NODE, 0);
        addNodeToExpand(rootNode);

        TreeUpdater updater = new TreeUpdater(this, myPlayerColor);
        updater.start();

        System.out.println("Red is dark, and blue is light. White is empty.");
        System.out.println("You are playing as " + ((myPlayerColor == Board.DARK) ? "LIGHT (BLUE)." : "DARK (RED)."));

        System.out.println(board);
        System.out.println("Move Played: --");
        System.out.println("Score: " + board.getScore());
        System.out.println("\n");

        while (true) {
            if (!board.canMove(currentColor)) {
                System.out.println((currentColor == Board.LIGHT ? "Light" : "Dark") + " cannot move, turn skipped.");
                currentColor ^= 3;
                if (!board.canMove(currentColor)) {
                    System.out.println("End of game.\n");
                    break;
                }
            }
            Position chosenMove = currentColor == myPlayerColor ? chooseMove(currentColor) : getPlayerMove(currentColor);

            makeMove(chosenMove, currentColor);
            System.out.println(board);
            System.out.println("Move Played: " + chosenMove);
            System.out.println("Score: " + board.getScore());
            System.out.println("\n");

            currentColor ^= 3;
        }

        Score finalScore = board.getScore();
        System.out.println("Score:\n" + finalScore);
        if (finalScore.dark > finalScore.light) {
            System.out.println("Dark wins.");
        } else if (finalScore.light > finalScore.dark) {
            System.out.println("Light wins.");
        } else {
            System.out.println("Draw.");
        }

        updater.pleaseStop();
    }

    private Position chooseMove(byte currentColor) throws IllegalStateException {
        if(rootNode == null || rootNode.children.size() == 0) {
            throw new IllegalStateException("Asked to choose move with insufficient root node");
            /*
            ArrayList<Position> moves = board.possibleMoves(currentColor);
            Position chosenMove = moves.get((int) (moves.size() * Math.random()));
            return chosenMove;
            */
        }

        String bestPos = null;
        double bestWeight = 0;
        for(String key : rootNode.children.keySet()){
            if(bestPos == null || rootNode.children.get(key).weight > bestWeight){
                bestWeight = rootNode.children.get(key).weight;
                bestPos = key;
            }
        }
        return new Position(bestPos);
    }

    private synchronized void makeMove(Position p, byte color){
        PlayTreeNode nextNode = rootNode.children.get(p.toString());
        for(String key : rootNode.children.keySet()){
            if(!key.equals(p.toString())){
                rootNode.children.get(key).invalidate();
            }
        }
        board.moveOn(p, color);
        if(nextNode == null){
            nextNode = new PlayTreeNode(board, null, color == myPlayerColor ? PlayTreeNode.MIN_NODE : PlayTreeNode.MAX_NODE, 0);
            addNodeToExpand(nextNode);
        }
        rootNode = nextNode;
        if(!board.equals(rootNode.board)) throw new IllegalStateException("Root node's board not equal to main board.");
        if(!rootNode.isValidInTree) throw new IllegalStateException("Root node is not valid in tree.");
        System.out.println("Size of queue: " + treeQueue.size());
    }

    private Position getPlayerMove(byte currentColor) {
        Position p = null;
        boolean valid = false;
        while (!valid) {
            p = null;
            System.out.print("Your move: ");
            System.out.flush();
            try {
                String move = stdIn.readLine();
                p = new Position(move);
            } catch (Exception e) {}

            if(p != null && board.isValidMove(p, currentColor)) valid = true;
            else System.out.println("Sorry, that move is not valid.");
        }
        return p;
    }
    
    public static void main(String[] args){
        Main m = new Main();
        m.play(args);
    }
}

class Board {
    public static final byte EMPTY = 0;
    public static final byte LIGHT = 1;
    public static final byte DARK = 2;
    public int WIDTH;

    private byte[][] spaces;

    public Board(int size) {
        WIDTH = size;
        spaces = new byte[WIDTH][WIDTH];
        init();
    }

    public Board(Board b) {
        WIDTH = b.spaces.length;
        spaces = new byte[WIDTH][WIDTH];
        for (int i = 0; i < WIDTH; i++) {
            System.arraycopy(b.spaces[i], 0, spaces[i], 0, WIDTH);
        }
    }

    private void init() {
        int center = WIDTH / 2;
        spaces[center - 1][center - 1] = LIGHT;
        spaces[center][center] = LIGHT;
        spaces[center - 1][center] = DARK;
        spaces[center][center - 1] = DARK;
    }

    private boolean checkRay(int currentX, int currentY, byte xDif, byte yDif, byte startColor) {
        if (spaces[currentX][currentY] != EMPTY) return false;

        byte otherColor = (byte) (startColor ^ 3);
        currentX += xDif;
        currentY += yDif;

        if (currentX < 0 || currentY < 0 || currentX >= WIDTH || currentY >= WIDTH) return false;
        if (spaces[currentX][currentY] != otherColor) return false;

        do {
            currentX += xDif;
            currentY += yDif;
        }
        while (currentX >= 0 && currentY >= 0 && currentX < WIDTH && currentY < WIDTH && spaces[currentX][currentY] == otherColor);

        if (currentX < 0 || currentY < 0 || currentX >= WIDTH || currentY >= WIDTH) return false;
        if (spaces[currentX][currentY] == startColor) return true;
        return false;
    }

    public void moveOn(Position p, byte color) {
        moveOn(p.x, p.y, color);
    }

    public void moveOn(int moveX, int moveY, byte color) {
        byte otherColor = (byte) (color ^ 3);
        for (byte xDif = -1; xDif <= 1; xDif++) {
            for (byte yDif = -1; yDif <= 1; yDif++) {
                if (xDif == 0 && yDif == 0) continue;

                if (checkRay(moveX, moveY, xDif, yDif, color)) {
                    int currentX = moveX + xDif;
                    int currentY = moveY + yDif;
                    while (currentX >= 0 && currentY >= 0 && currentX < WIDTH && currentY < WIDTH && spaces[currentX][currentY] == otherColor) {
                        spaces[currentX][currentY] = color;
                        currentX += xDif;
                        currentY += yDif;
                    }
                }
            }
        }
        spaces[moveX][moveY] = color;
    }

    public ArrayList<Position> possibleMoves(byte color) {
        ArrayList<Position> res = new ArrayList<Position>();
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < WIDTH; y++) {
                if (spaces[x][y] != EMPTY) continue;

                boolean found = false;
                for (byte xDif = -1; xDif <= 1 && !found; xDif++) {
                    for (byte yDif = -1; yDif <= 1 && !found; yDif++) {
                        if (xDif == 0 && yDif == 0) continue;
                        if (checkRay(x, y, xDif, yDif, color)) {
                            res.add(new Position(x, y));
                            found = true;
                        }
                    }
                }
            }
        }
        return res;
    }

    public boolean isValidMove(Position p, byte color){
        return isValidMove(p.x, p.y, color);
    }

    public boolean isValidMove(int x, int y, byte color) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= WIDTH) return false;

        for (byte xDif = -1; xDif <= 1; xDif++) {
            for (byte yDif = -1; yDif <= 1; yDif++) {
                if (checkRay(x, y, xDif, yDif, color)) return true;
            }
        }
        return false;
    }

    public boolean canMove(byte color) {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < WIDTH; y++) {
                if (spaces[x][y] != EMPTY) continue;

                for (byte xDif = -1; xDif <= 1; xDif++) {
                    for (byte yDif = -1; yDif <= 1; yDif++) {
                        if (xDif == 0 && yDif == 0) continue;
                        if (checkRay(x, y, xDif, yDif, color)) return true;
                    }
                }
            }
        }
        return false;
    }

    public Score getScore() {
        int dark = 0;
        int light = 0;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < WIDTH; y++) {
                if (spaces[x][y] == Board.DARK) dark++;
                else if (spaces[x][y] == Board.LIGHT) light++;
            }
        }
        return new Score(dark, light);
    }

    public boolean equals(Board b){
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < WIDTH; y++) {
                if(spaces[x][y] != b.spaces[x][y]) return false;
            }
        }
        return true;
    }

    public double getValueFor(byte color){
        Score s = getScore();
        for(int x = 0; x < WIDTH; x += WIDTH - 1){
            for(int y = 0; y < WIDTH; y += WIDTH - 1){
                if(spaces[x][y] == LIGHT) s.light += 4;
                else if(spaces[x][y] == DARK) s.dark += 4;
            }
        }

        double scorePart = (color == DARK ? (double)s.dark / s.light : (double)s.light / s.dark);
        int playerMoves = possibleMoves(color).size();
        int otherPlayerMoves = possibleMoves((byte)(color ^ 3)).size();
        double movesPart = (double)(playerMoves + 5) / (otherPlayerMoves + 5);
        return scorePart * movesPart;
    }

    public String toString() {
        StringBuilder s = new StringBuilder(20 * WIDTH * WIDTH);
        char blue[] = {0x1b, '[', '4', '4', 'm', 0};
        char red[] = {0x1b, '[', '4', '1', 'm', 0};
        char white[] = {0x1b, '[', '4', '7', 'm', 0};
        char underline[] = {0x1b, '[', '4', 'm', 0};
        char noColor[] = {0x1b, '[', '4', '9', 'm', 0};
        char noFormat[] = {0x1b, '[', '0', 'm', 0};
        s.append(' ');
        for (int i = -1; i < WIDTH; i++) {
            if (i > -1) s.append((char) (i + 'a') + " ");
            else {
                s.append(" ");
                s.append(underline);
                s.append(" ");
            }
        }

        s.append('\n');
        for (int y = 0; y < WIDTH; y++) {
            s.append(noFormat);
            s.append((y + 1) + ((y + 1) < 10 ? " " : ""));
            s.append(underline);
            s.append('|');
            for (int x = 0; x < WIDTH; x++) {
                switch (spaces[x][y]) {
                    case EMPTY:
                        s.append(white);
                        break;
                    case DARK:
                        s.append(red);
                        break;
                    case LIGHT:
                        s.append(blue);
                        break;
                }
                s.append(' ');
                s.append(noColor);
                s.append('|');
            }
            if (y != WIDTH - 1) s.append('\n');
        }
        s.append(noFormat);
        return s.toString();
    }
}

class PlayTreeNode{
    public static final byte MIN_NODE = 4;
    public static final byte MAX_NODE = 8;

    public Board board;
    public double weight;
    public byte nodeType;

    public boolean isValidInTree = true;

    Hashtable<String, PlayTreeNode> children;
    PlayTreeNode parent;

    public PlayTreeNode(Board b, PlayTreeNode p, byte t, double w){
        board = b;
        parent = p;
        nodeType = t;
        weight = 0;
        children = new Hashtable<String, PlayTreeNode>();
    }

    public void invalidate(){
        for(PlayTreeNode n : children.values()) n.invalidate();
        isValidInTree = false;
    }

    public double recalculateWeight(){
        if(children.size() == 0) return weight;

        double res = nodeType == MAX_NODE ? 0 : Double.MAX_VALUE;
        for(PlayTreeNode p : children.values()){
            res = nodeType == MAX_NODE ? Math.max(res, p.weight) : Math.min(res, p.weight);
        }

        weight = res;
        return weight;
    }

    public void cascadeWeightUp(){
        recalculateWeight();
        if(parent != null) parent.cascadeWeightUp();
    }
}

class TreeUpdater extends Thread{
    Main dataProvider;
    boolean keepRunning = true;
    byte playerColor;

    public TreeUpdater(Main dp, byte pc){
        dataProvider = dp;
        playerColor = pc;
    }

    public void run(){
        while(keepRunning){
            PlayTreeNode toExpand = dataProvider.getNodeToExpand();

            byte currentPlayerColor = toExpand.nodeType == PlayTreeNode.MAX_NODE ? playerColor : (byte)(playerColor ^ 3);
            ArrayList<Position> moves = toExpand.board.possibleMoves(currentPlayerColor);

            for(Position p : moves){
                Board newBoard = new Board(toExpand.board);
                newBoard.moveOn(p, currentPlayerColor);
                PlayTreeNode newNode = new PlayTreeNode(newBoard, toExpand, (byte)(toExpand.nodeType ^ 12), newBoard.getValueFor(playerColor));
                toExpand.children.put(p.toString(), newNode);
                dataProvider.addNodeToExpand(newNode);
            }
            toExpand.cascadeWeightUp();
        }
    }

    public void pleaseStop(){
        keepRunning = false;
    }
}

class Position {
    public int x;
    public int y;

    public Position(int a, int b) {
        x = a;
        y = b;
    }

    public Position(Position p) {
        x = p.x;
        y = p.y;
    }

    public Position(String move){
        x = move.charAt(0) - 'a';
        y = Integer.parseInt(move.substring(1)) - 1;
    }

    public String toString() {
        return (char) (x + 'a') + "" + (y + 1);
    }
}

class Score {
    public int dark;
    public int light;

    public Score(int d, int l) {
        dark = d;
        light = l;
    }

    public Score(Score s) {
        dark = s.dark;
        light = s.light;
    }

    public String toString() {
        return "Light " + light + " - Dark " + dark;
    }
}