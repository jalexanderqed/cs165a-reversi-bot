import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Reversi {
    private Board board;
    private BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
    public PlayTreeNode rootNode;
    public static byte myPlayerColor;

    private long timeUsed;
    private final static long LEGAL_TIME = 1000 * 60 * 2 - 20000;
    private long lastStart;

    int depth = 0;

    public static ArrayList<PositionWeightPair> weights = new ArrayList<PositionWeightPair>();

    private void initWeights(int width) {
        for (int x = 0; x < width; x += width - 1) {
            for (int y = 0; y < width; y += width - 1) {
                weights.add(new PositionWeightPair(new Position(x, y), 20 * width / 8.0));
            }
        }

        for (int i = 0; i < width; i += width - 1) {
            if (i == 1 || i == width - 2) {
                weights.add(new PositionWeightPair(new Position(i, 0), -10));
                weights.add(new PositionWeightPair(new Position(i, width - 1), -10));
                weights.add(new PositionWeightPair(new Position(0, i), -10));
                weights.add(new PositionWeightPair(new Position(0, i), -10));
            } else {
                weights.add(new PositionWeightPair(new Position(i, 0), 5));
                weights.add(new PositionWeightPair(new Position(i, width - 1), 5));
                weights.add(new PositionWeightPair(new Position(0, i), 5));
                weights.add(new PositionWeightPair(new Position(0, i), 5));
            }
        }
    }

    public void play(String[] args) {
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
        rootNode = new PlayTreeNode(new Board(board), myPlayerColor == currentColor ? PlayTreeNode.MAX_NODE : PlayTreeNode.MIN_NODE);

        System.out.println("Red is dark, and blue is light. White is empty.");
        System.out.println("You are playing as " + ((myPlayerColor == Board.DARK) ? "LIGHT (BLUE)." : "DARK (RED)."));

        System.out.println(board);
        System.out.println("Move Played: --");
        System.out.println("Score: " + board.getScore());
        System.out.println("\n");

        while (true) {
            byte correctType = currentColor == myPlayerColor ? PlayTreeNode.MAX_NODE : PlayTreeNode.MIN_NODE;
            if(rootNode.nodeType != correctType)
                rootNode = new PlayTreeNode(new Board(board), myPlayerColor == currentColor ? PlayTreeNode.MAX_NODE : PlayTreeNode.MIN_NODE);

            if (!board.canMove(currentColor)) {
                System.out.println((currentColor == Board.LIGHT ? "Light" : "Dark") + " cannot move, turn skipped.");
                currentColor ^= 3;
                advanceBoard(currentColor == myPlayerColor ? PlayTreeNode.MAX_NODE : PlayTreeNode.MIN_NODE);
                if (!board.canMove(currentColor)) {
                    System.out.println("End of game.\n");
                    break;
                }
                continue;
            }

            Position chosenMove;
            try {
                if (currentColor != myPlayerColor) timeUsed += (System.currentTimeMillis() - lastStart);
                chosenMove = currentColor == myPlayerColor ? chooseMove(currentColor) : getPlayerMove(currentColor);
                if (currentColor != myPlayerColor) lastStart = System.currentTimeMillis();
            } catch(Exception e){
                if(currentColor == myPlayerColor){
                    chosenMove = board.possibleMoves(currentColor).get(0);
                }
                else{
                    chosenMove = getPlayerMove(currentColor);
                }
            }


            board.moveOn(chosenMove, currentColor);

            System.out.println(board);
            System.out.println("Move Played: " + chosenMove);
            System.out.println("Score: " + board.getScore());
            System.out.println("\n");

            currentColor ^= 3;
            advanceBoard(currentColor == myPlayerColor ? PlayTreeNode.MAX_NODE : PlayTreeNode.MIN_NODE);
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
    }

    private Position chooseMove(byte currentColor) throws IllegalStateException {
        Score s = board.getScore();
        int remainingMoves = board.WIDTH * board.WIDTH - (s.dark + s.light);

        if(rootNode == null) rootNode = new PlayTreeNode(board, PlayTreeNode.MAX_NODE);

        double estimatedTime = 0;
        depth = Math.max(depth - 2, 4);
        double allowedTime = 1.8 / remainingMoves * (LEGAL_TIME - timeUsed);
        double end = lastStart + allowedTime;

        while(end > System.currentTimeMillis() + estimatedTime && (s.dark + s.light + depth < board.WIDTH * board.WIDTH)){
            depth++;
            long start = System.currentTimeMillis();
            rootNode.weight = -1 * Double.MAX_VALUE;
            rootNode.alpha = -1 * Double.MAX_VALUE;
            rootNode.beta = Double.MAX_VALUE;
            rootNode.calcForDepth(depth, 0, -1 * Double.MAX_VALUE, Double.MAX_VALUE);
            long taken = System.currentTimeMillis() - start;
            estimatedTime = taken * 8 * (board.WIDTH / 8.0);
        }
        System.out.println("Calculated to depth " + depth);

        System.out.println("Time allowed: " + allowedTime);
        System.out.println("Time taken: " + (System.currentTimeMillis() - lastStart));

        String move = null;
        double bestWeight = -1 * Double.MAX_VALUE;
        for(String m : rootNode.children.keySet()){
            if(rootNode.children.get(m).weight > bestWeight){
                move = m;
                bestWeight = rootNode.children.get(m).weight;
            }
        }
        return new Position(move);
    }

    private void advanceBoard(byte newType){
        for(PlayTreeNode n : rootNode.children.values()){
            if(n.board.equals(board)){
                rootNode = n;
                break;
            }
        }
        if(!rootNode.board.equals(board)){
            System.out.println("Could not load precomputed next step");
            rootNode = new PlayTreeNode(new Board(board), newType);
        }
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
            } catch (Exception e) {
            }

            if (p != null && board.isValidMove(p, currentColor)) valid = true;
            else System.out.println("Sorry, that move is not valid.");
        }
        return p;
    }

    public static void main(String[] args) {
        Reversi m = new Reversi();
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

        if(!canMove(DARK) && !canMove(LIGHT)){
            if(s.dark != s.light) {
                byte winner = s.dark > s.light ? DARK : LIGHT;
                return color == winner ? 1e4 : -1e4;
            }
        }

        for (int x = 0; x < WIDTH; x += WIDTH - 1) {
            for (int y = 0; y < WIDTH; y += WIDTH - 1) {
                if(spaces[x][y] == LIGHT) s.light += 15* WIDTH / 8.0;
                else if(spaces[x][y] == DARK) s.dark += 15 * WIDTH * WIDTH / 64.0;
            }
        }

        for (PositionWeightPair p : Reversi.weights) {
            if (spaces[p.pos.x][p.pos.y] == LIGHT) s.light += p.weight;
            else if (spaces[p.pos.x][p.pos.y] == DARK) s.dark += p.weight;
        }

        s.light += possibleMoves(LIGHT).size() * 2;
        s.dark += possibleMoves(DARK).size() * 2;

        double result = color == Board.DARK ? s.dark - s.light : s.light - s.dark;

        return result;
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

    public double alpha;
    public double beta;

    Hashtable<String, PlayTreeNode> children;

    public PlayTreeNode(Board b, byte t) {
        board = b;
        nodeType = t;
        weight = b.getWeightFor(Reversi.myPlayerColor);
        children = new Hashtable<String, PlayTreeNode>();
        alpha = -1 * Double.MAX_VALUE;
        beta = Double.MAX_VALUE;
    }

    public void calcForDepth(int depth, int current, double parentAlpha, double parentBeta){
        if(current >= depth) return;

        alpha = parentAlpha;
        beta = parentBeta;

        byte moveColor = (byte)(nodeType == MAX_NODE ? Reversi.myPlayerColor : Reversi.myPlayerColor ^ 3);

        for(Position p : board.possibleMoves(moveColor)){
            if(alpha >= beta) return;
            PlayTreeNode child = children.get(p.toString());

            if(child == null) {
                Board newBoard = new Board(board);
                newBoard.moveOn(p, moveColor);
                child = new PlayTreeNode(newBoard, nodeType == MAX_NODE ? MIN_NODE : MAX_NODE);
            }
            else{
                child.weight = child.board.getWeightFor(Reversi.myPlayerColor);
                child.alpha = -1 * Double.MAX_VALUE;
                child.beta = Double.MAX_VALUE;
            }

            child.calcForDepth(depth, current + 1, alpha, beta);

            if(nodeType == MAX_NODE){
                if(child.weight > weight) weight = child.weight;
                if(child.weight > alpha) alpha = child.weight;
            }
            else{
                if(child.weight < weight) weight = child.weight;
                if(child.weight < beta) beta = child.weight;
            }
            children.put(p.toString(), child);
        }
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

class PositionWeightPair {
    public Position pos;
    public double weight;

    public PositionWeightPair(Position p, double w) {
        pos = p;
        weight = w;
    }
}