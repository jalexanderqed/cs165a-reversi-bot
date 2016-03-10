import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Main {
    private static final boolean DEBUG = false;

    private Board board;
    private BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
    public PlayTreeNode rootNode;
    public byte myPlayerColor;

    private long timeUsed;
    private final static long LEGAL_TIME = 1000 * 60 * 2 - 1000;
    private long lastStart;

    private ConcurrentLinkedQueue<PlayTreeNode> treeQueue = new ConcurrentLinkedQueue<PlayTreeNode>();

    private boolean running = true;
    TreeUpdater[] updaters = new TreeUpdater[5];

    public static ArrayList<PositionWeightPair> weights = new ArrayList<PositionWeightPair>();

    private void initWeights(int width){
        for (int x = 0; x < width; x += width - 1) {
            for (int y = 0; y < width; y += width - 1) {
                weights.add(new PositionWeightPair(new Position(x, y), 20 * width / 8.0));
            }
        }

        for (int i = 0; i < width; i += width - 1) {
            if(i == 1 || i == width - 2){
                // Do nothing; these are dealt with later
            }
            else {
                weights.add(new PositionWeightPair(new Position(i, 0), 3));
                weights.add(new PositionWeightPair(new Position(i, width - 1), 3));
                weights.add(new PositionWeightPair(new Position(0, i), 3));
                weights.add(new PositionWeightPair(new Position(0, i), 3));
            }
        }
    }

    public synchronized PlayTreeNode getNodeToExpand() {
        PlayTreeNode n;
        n = treeQueue.poll();
        return n;
    }

    public void addNodeToExpand(PlayTreeNode n) {
        treeQueue.add(n);
    }

    private int getTreeDepth(){
        int depth = 0;
        PlayTreeNode node = rootNode;
        while(node != null && node.children.size() > 0){
            PlayTreeNode child = null;
            for(PlayTreeNode c : node.children.values()){
                    child = c;
                    break;
            }
            node = child;
            depth++;
        }
        return depth;
    }

    private int getSizeFromNode(PlayTreeNode node){
        int size = 1;
        for(PlayTreeNode n : node.children.values()){
                size += getSizeFromNode(n);
        }
        return size;
    }

    public void play(String[] args) {
        try {
            lastStart = System.currentTimeMillis();
            timeUsed = 0;
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
            initWeights(boardSize);
            rootNode = new PlayTreeNode(board, null, myPlayerColor == currentColor ? PlayTreeNode.MAX_NODE : PlayTreeNode.MIN_NODE, 0);

            for(int i = 0; i < updaters.length; i++) {
                updaters[i] = new TreeUpdater(this, myPlayerColor);
                updaters[i].start();
            }

            addNodeToExpand(rootNode);
            while(rootNode.children.size() < 4){}

            System.out.println("Red is dark, and blue is light. White is empty.");
            System.out.println("You are playing as " + ((myPlayerColor == Board.DARK) ? "LIGHT (BLUE)." : "DARK (RED)."));

            System.out.println(board);
            System.out.println("Move Played: --");
            System.out.println("Score: " + board.getScore());
            System.out.println("\n");

            if(currentColor != myPlayerColor) guessNextMove();

            while (true) {
                if (currentColor == myPlayerColor) lastStart = System.currentTimeMillis();

                if (!board.canMove(currentColor)) {
                    System.out.println((currentColor == Board.LIGHT ? "Light" : "Dark") + " cannot move, turn skipped.");
                    currentColor ^= 3;
                    PlayTreeNode nextNode = new PlayTreeNode(board, null, currentColor == myPlayerColor ? PlayTreeNode.MAX_NODE : PlayTreeNode.MIN_NODE, 0);
                    rootNode = nextNode;
                    treeQueue.clear();
                    if(currentColor != myPlayerColor) guessNextMove();
                    addNodeToExpand(nextNode);
                    if (!board.canMove(currentColor)) {
                        System.out.println("End of game.\n");
                        break;
                    }
                    continue;
                }
                Position chosenMove = currentColor == myPlayerColor ? chooseMove(currentColor) : getPlayerMove(currentColor);

                board.moveOn(chosenMove, currentColor);

                System.out.println(board);
                System.out.println("Move Played: " + chosenMove);
                System.out.println("Score: " + board.getScore());
                System.out.println("\n");

                if(currentColor == myPlayerColor){
                    PlayTreeNode nextNode = rootNode.children.get(chosenMove.toString());
                    if(nextNode == null){
                        nextNode = new PlayTreeNode(board, null, PlayTreeNode.MIN_NODE, 0);
                    }
                    rootNode = nextNode;

                    guessNextMove();
                }
                else{
                    if(checkGuessedMove()) System.out.println("Guessed correctly!");
                    else System.out.println("Did not guess correctly. :(");
                }

                currentColor ^= 3;
                playAll();

                if (currentColor == myPlayerColor) timeUsed += (System.currentTimeMillis() - lastStart);
            }

            Score finalScore = board.getScore();
            System.out.println("Score:\n" + finalScore);
            System.out.println("Time used: " + timeUsed);
            if (finalScore.dark > finalScore.light) {
                System.out.println("Dark wins.");
            } else if (finalScore.light > finalScore.dark) {
                System.out.println("Light wins.");
            } else {
                System.out.println("Draw.");
            }
        } catch (Exception e){
            System.out.println(e.toString());
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter( writer );
            e.printStackTrace( printWriter );
            printWriter.flush();

            System.out.println(writer.toString());
        }
        finally {
            for(int i = 0; i < updaters.length; i++) {
                if (updaters[i] != null) updaters[i].pleaseStop();
            }
            running = false;
        }
    }

    private Position chooseMove(byte currentColor) throws IllegalStateException {
        if (rootNode.nodeType != PlayTreeNode.MAX_NODE) throw new IllegalStateException("Root is not max node.");

        long remainingTime = LEGAL_TIME - timeUsed;
        Score s = board.getScore();
        int remainingSpaces = board.WIDTH * board.WIDTH - (s.dark + s.light);
        double nowWeight = 1.0 + ((26 - board.WIDTH) / 18.0) * 0.5;
        System.out.println("nowWeight: " + nowWeight);
        double allowedTime = ((nowWeight / remainingSpaces) * remainingTime) - 1;
        System.out.println("Time used: " + timeUsed);
        System.out.println("Time allowed: " + LEGAL_TIME);
        System.out.println("Remaining spaces: " + remainingSpaces);
        System.out.println("Remaining time: " + remainingTime);
        System.out.println("Allowed time: " + allowedTime);
        long startTime = System.currentTimeMillis();
        if(allowedTime < 10){
            try {
                Thread.sleep(Math.max((long)allowedTime - 1, 0));
            } catch (Exception e) {
            }
        }
        else {
            while (System.currentTimeMillis() - startTime < (allowedTime - 4) && treeQueue.size() > 0) {
                try {
                    Thread.sleep(4);
                } catch (Exception e) {
                }
            }
        }

        pauseAll();

        System.out.println("Tree depth: " + getTreeDepth());
        System.out.println("Tree size: " + getSizeFromNode(rootNode));
        System.out.println("Size of queue: " + treeQueue.size());

        if (rootNode.children.size() == 0) {
            ArrayList<Position> moves = board.possibleMoves(currentColor);
            Position chosenMove = moves.get((int) (moves.size() * Math.random()));
            return chosenMove;
        }

        String bestPos = null;
        double bestWeight = 0;
        System.out.println("Choosing from:");
        for (String key : rootNode.children.keySet()) {
            System.out.println(key + ": " + rootNode.children.get(key).weight);
            if (bestPos == null || rootNode.children.get(key).weight > bestWeight) {
                bestWeight = rootNode.children.get(key).weight;
                bestPos = key;
            }
        }
        return new Position(bestPos);
    }

    private synchronized void guessNextMove(){
        treeQueue.clear();

        if(rootNode.nodeType != PlayTreeNode.MIN_NODE) throw new IllegalStateException("Root node is not min node in guessNextMove.");

        if(rootNode.children.size() == 0){
            ArrayList<Position> moves;
            if((moves = rootNode.board.possibleMoves((byte)(myPlayerColor ^ 3))).size() == 0){
                rootNode = new PlayTreeNode(board, null, PlayTreeNode.MAX_NODE, 0);
            }
            else{
                Position chosenMove = moves.get((int) (moves.size() * Math.random()));
                Board newBoard = new Board(board);
                newBoard.moveOn(chosenMove, (byte)(myPlayerColor ^ 3));
                rootNode = new PlayTreeNode(newBoard, null, PlayTreeNode.MAX_NODE, 0);
            }
        }
        else{
            double minWeight = Double.MAX_VALUE;
            PlayTreeNode minChild = null;
            for(PlayTreeNode child : rootNode.children.values()){
                if(child.weight < minWeight){
                    minChild = child;
                    minWeight = child.weight;
                }
            }
            if(minChild.nodeType != PlayTreeNode.MAX_NODE) throw new IllegalStateException("Found child in guessNextMove is not max node.");
            rootNode = minChild;
        }
        rootNode.parent = null;
        addNodeToExpand(rootNode);
    }

    private synchronized boolean checkGuessedMove(){
        if(rootNode.nodeType != PlayTreeNode.MAX_NODE) throw new IllegalStateException("Root node is not max node in checkGuessedMove.");
        if(rootNode.board.equals(board)) return true;
        else{
            treeQueue.clear();
            rootNode = new PlayTreeNode(board, null, PlayTreeNode.MAX_NODE, 0);
            rootNode.parent = null;
            addNodeToExpand(rootNode);
            return false;
        }
    }

    private Position getPlayerMove(byte currentColor) {
        Position p = null;
        boolean valid = false;

        while (!valid) {
            if(DEBUG) return getRandMove(currentColor);

            p = null;
            System.out.print("Your move: ");
            System.out.flush();
            try {
                String move = stdIn.readLine();
                p = new Position(move);
            } catch (Exception e) {
            }

            if (p != null && board.isValidMove(p, currentColor)) valid = true;
            else System.out.println("Sorry, that move is not valid.");
        }
        return p;
    }

    public static void main(String[] args) {
        Main m = new Main();
        m.play(args);
    }

    private Position getRandMove(byte currentColor){
        try {
            Thread.sleep(50);
        } catch (Exception e) {
        }

        ArrayList<Position> moves = board.possibleMoves(currentColor);
        Position chosenMove = moves.get((int) (moves.size() * Math.random()));
         return chosenMove;
    }

    private void pauseAll(){
        for(TreeUpdater thr : updaters){
            thr.pleasePause();
        }
    }

    private void playAll(){
        for(TreeUpdater thr : updaters){
            thr.pleasePlay();
        }
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

    public boolean isValidMove(Position p, byte color) {
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

    public boolean equals(Board b) {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < WIDTH; y++) {
                if (spaces[x][y] != b.spaces[x][y]) return false;
            }
        }
        return true;
    }

    public double getWeightFor(byte color) {
        Score s = getScore();

        for (PositionWeightPair p : Main.weights) {
            if (spaces[p.pos.x][p.pos.y] == LIGHT) s.light += p.weight;
            else if (spaces[p.pos.x][p.pos.y] == DARK) s.dark += p.weight;
        }

        double offCornerWeight = -5 * WIDTH / 8.0;
        /*
        if(spaces[1][0] != EMPTY && spaces[0][0] != spaces[1][0]){
            if (spaces[1][0] == LIGHT) s.light += offCornerWeight;
            else if (spaces[1][0] == DARK) s.dark += offCornerWeight;
        }
        if(spaces[1][1] != EMPTY && spaces[0][0] != spaces[1][1]){
            if (spaces[1][1] == LIGHT) s.light += offCornerWeight;
            else if (spaces[1][1] == DARK) s.dark += offCornerWeight;
        }
        if(spaces[0][1] != EMPTY && spaces[0][0] != spaces[0][1]){
            if (spaces[0][1] == LIGHT) s.light += offCornerWeight;
            else if (spaces[0][1] == DARK) s.dark += offCornerWeight;
        }

        if(spaces[0][WIDTH - 2] != EMPTY && spaces[0][WIDTH - 1] != spaces[0][WIDTH - 2]){
            if (spaces[0][WIDTH - 2] == LIGHT) s.light += offCornerWeight;
            else if (spaces[0][WIDTH - 2] == DARK) s.dark += offCornerWeight;
        }
        if(spaces[1][WIDTH - 2] != EMPTY && spaces[0][WIDTH - 1] != spaces[1][WIDTH - 2]){
            if (spaces[1][WIDTH - 2] == LIGHT) s.light += offCornerWeight;
            else if (spaces[1][WIDTH - 2] == DARK) s.dark += offCornerWeight;
        }
        if(spaces[1][WIDTH - 1] != EMPTY && spaces[0][WIDTH - 1] != spaces[1][WIDTH - 1]){
            if (spaces[1][WIDTH - 1] == LIGHT) s.light += offCornerWeight;
            else if (spaces[1][WIDTH - 1] == DARK) s.dark += offCornerWeight;
        }

        if(spaces[WIDTH - 2][0] != EMPTY && spaces[WIDTH - 1][0] != spaces[WIDTH - 2][0]){
            if (spaces[WIDTH - 2][0] == LIGHT) s.light += offCornerWeight;
            else if (spaces[WIDTH - 2][0] == DARK) s.dark += offCornerWeight;
        }
        if(spaces[WIDTH - 2][1] != EMPTY && spaces[WIDTH - 1][0] != spaces[WIDTH - 2][1]){
            if (spaces[WIDTH - 2][1] == LIGHT) s.light += offCornerWeight;
            else if (spaces[WIDTH - 2][1] == DARK) s.dark += offCornerWeight;
        }
        if(spaces[WIDTH - 1][1] != EMPTY && spaces[WIDTH - 1][0] != spaces[WIDTH - 1][1]){
            if (spaces[WIDTH - 1][1] == LIGHT) s.light += offCornerWeight;
            else if (spaces[WIDTH - 1][1] == DARK) s.dark += offCornerWeight;
        }

        if(spaces[WIDTH - 2][WIDTH - 1] != EMPTY && spaces[WIDTH - 1][WIDTH - 1] != spaces[WIDTH - 2][WIDTH - 1]){
            if (spaces[WIDTH - 2][WIDTH - 1] == LIGHT) s.light += offCornerWeight;
            else if (spaces[WIDTH - 2][WIDTH - 1] == DARK) s.dark += offCornerWeight;
        }
        if(spaces[WIDTH - 2][WIDTH - 2] != EMPTY && spaces[WIDTH - 1][WIDTH - 1] != spaces[WIDTH - 2][WIDTH - 2]){
            if (spaces[WIDTH - 2][WIDTH - 2] == LIGHT) s.light += offCornerWeight;
            else if (spaces[WIDTH - 2][WIDTH - 2] == DARK) s.dark += offCornerWeight;
        }
        if(spaces[WIDTH - 1][WIDTH - 2] != EMPTY && spaces[WIDTH - 1][WIDTH - 1] != spaces[WIDTH - 1][WIDTH - 2]){
            if (spaces[WIDTH - 1][WIDTH - 2] == LIGHT) s.light += offCornerWeight;
            else if (spaces[WIDTH - 1][WIDTH - 2] == DARK) s.dark += offCornerWeight;
        }
        */

        int northSide = 0;
        int southSide = 0;
        int westSide = 0;
        int eastSide = 0;
        int northSideO = 0;
        int southSideO = 0;
        int westSideO = 0;
        int eastSideO = 0;
        byte otherColor = (byte)(color ^ 2);
        for(int i = 0; i < WIDTH; i++){
            if(spaces[i][0] == LIGHT) westSide++;
            if(spaces[i][0] == DARK) westSideO++;
            if(spaces[0][i] == LIGHT) northSide++;
            if(spaces[0][i] == DARK) northSideO++;

            if(spaces[WIDTH - 1][0] == LIGHT) eastSide++;
            if(spaces[WIDTH - 1][0] == DARK) eastSideO++;
            if(spaces[0][WIDTH - 1] == LIGHT) southSide++;
            if(spaces[0][WIDTH - 1] == DARK) southSideO++;
        }

        double sideOwnWeight = -3 * offCornerWeight;

        if(westSide >= WIDTH - 1) s.light += sideOwnWeight;
        else if(westSideO == 0) s.light += sideOwnWeight;
        if(westSideO >= WIDTH - 1) s.dark += sideOwnWeight;
        else if(westSide == 0) s.dark += sideOwnWeight;

        if(eastSide >= WIDTH - 1) s.light += sideOwnWeight;
        else if(eastSideO == 0) s.light += sideOwnWeight;
        if(eastSideO >= WIDTH - 1) s.dark += sideOwnWeight;
        else if(eastSide == 0) s.dark += sideOwnWeight;

        if(northSide >= WIDTH - 1) s.light += sideOwnWeight;
        else if(northSideO == 0) s.light += sideOwnWeight;
        if(northSideO >= WIDTH - 1) s.dark += sideOwnWeight;
        else if(northSide == 0) s.dark += sideOwnWeight;

        if(southSide >= WIDTH - 1) s.light += sideOwnWeight;
        else if(southSideO == 0) s.light += sideOwnWeight;
        if(southSideO >= WIDTH - 1) s.dark += sideOwnWeight;
        else if(southSide == 0) s.dark += sideOwnWeight;

        s.light += possibleMoves(LIGHT).size();
        s.dark += possibleMoves(DARK).size();

        return color == Board.DARK ? s.dark - s.light : s.light - s.dark;
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

class PlayTreeNode {
    public static final byte MIN_NODE = 4;
    public static final byte MAX_NODE = 8;

    public Board board;
    public double weight;
    public byte nodeType;

    ConcurrentHashMap<String, PlayTreeNode> children;
    PlayTreeNode parent;

    public PlayTreeNode(Board b, PlayTreeNode p, byte t, double w) {
        board = b;
        parent = p;
        nodeType = t;
        weight = w;
        children = new ConcurrentHashMap<String, PlayTreeNode>();
    }

    public double recalculateWeight() {
        if (children.size() == 0) return weight;

        double res = nodeType == MAX_NODE ? 0 : Double.MAX_VALUE;
        for (PlayTreeNode p : children.values()) {
            res = (nodeType == MAX_NODE ? Math.max(res, p.weight) : Math.min(res, p.weight));
        }

        weight = res;
        return weight;
    }

    public void cascadeWeightUp() {
        recalculateWeight();
        if (parent != null) {
            parent.cascadeWeightUp();
        }
    }
}

class TreeUpdater extends Thread {
    Main dataProvider;
    boolean keepRunning = true;
    byte playerColor;

    static long startTime;
    boolean paused = false;
    long myLastPause = 0;

    public TreeUpdater(Main dp, byte pc) {
        if(startTime == 0) startTime = System.currentTimeMillis();
        dataProvider = dp;
        playerColor = pc;
    }

    public void run() {
        while (keepRunning) {
            while(paused){
                try{
                    Thread.sleep(1);
                }catch(Exception e){}
            }

            PlayTreeNode toExpand = dataProvider.getNodeToExpand();
            if(toExpand == null) continue;
            PlayTreeNode grandParent = toExpand;
            while(grandParent.parent != null && keepRunning) grandParent = grandParent.parent;

            if(grandParent != dataProvider.rootNode){
                continue;
            }

            if(toExpand.children.size() > 0){
                for(PlayTreeNode child : toExpand.children.values()){
                    dataProvider.addNodeToExpand(child);
                }
                continue;
            }

            byte currentPlayerColor = toExpand.nodeType == PlayTreeNode.MAX_NODE ? playerColor : (byte) (playerColor ^ 3);
            ArrayList<Position> moves = toExpand.board.possibleMoves(currentPlayerColor);

            for (Position p : moves) {
                if(!keepRunning) return;
                Board newBoard = new Board(toExpand.board);
                newBoard.moveOn(p, currentPlayerColor);
                double boardValue = newBoard.getWeightFor(playerColor);
                PlayTreeNode newNode = new PlayTreeNode(newBoard, toExpand, (byte) (toExpand.nodeType ^ 12), boardValue);
                toExpand.children.put(p.toString(), newNode);
                dataProvider.addNodeToExpand(newNode);
            }
            toExpand.cascadeWeightUp();

            if((System.currentTimeMillis() - startTime) % 20 > myLastPause){
                myLastPause = (System.currentTimeMillis() - startTime) % 20;
                try{
                    Thread.sleep(1);
                }catch(Exception e){}
            }
        }
    }

    public void pleaseStop() {
        keepRunning = false;
    }

    public void pleasePause(){
        paused = true;
    }

    public void pleasePlay(){
        paused = false;
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

    public Position(String move) {
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

class PositionWeightPair{
    public Position pos;
    public double weight;

    public PositionWeightPair(Position p, double w){
        pos = p;
        weight = w;
    }
}