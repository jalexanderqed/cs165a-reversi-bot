import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Main {
    private static Board board;
    private static BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) {
        byte iPlay = Board.LIGHT;
        int boardSize = 8;

        for (int i = 0; i < args.length; i++) {
            if (args[i].indexOf("-l") == 0) {
                iPlay = Board.DARK;
            } else if (args[i].indexOf("-n") == 0) {
                boardSize = Integer.parseInt(args[i + 1]);
            }
        }

        board = new Board(boardSize);

        System.out.println("Red is dark, and blue is light. White is empty.");
        System.out.println("You are playing as " + ((iPlay == Board.DARK) ? "LIGHT (BLUE)." : "DARK (RED)."));

        System.out.println(board);
        System.out.println(board.getScore());
        System.out.print("\n\n");

        byte currentColor = Board.DARK;
        while (true) {
            if (!board.canMove(currentColor)) {
                System.out.println((currentColor == Board.LIGHT ? "Light" : "Dark") + " cannot move, turn skipped.");
                currentColor ^= 3;
                if (!board.canMove(currentColor)) {
                    System.out.println("End of game.\n");
                    break;
                }
            }
            Position chosenMove = currentColor == iPlay ? getMove(currentColor) : getPlayerMove(currentColor);

            board.moveOn(chosenMove, currentColor);
            System.out.println(board);
            System.out.println("Move played: " + chosenMove);
            System.out.println(board.getScore());
            System.out.print("\n\n");

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
    }

    private static Position getMove(byte currentColor) {
        ArrayList<Position> moves = board.possibleMoves(currentColor);
        Position chosenMove = moves.get((int) (moves.size() * Math.random()));
        return chosenMove;
    }

    private static Position getPlayerMove(byte currentColor) {
        int x = -1;
        int y = -1;
        boolean valid = false;
        while (!valid) {
            x = -1;
            y = -1;
            System.out.print("Your move: ");
            try {
                String move = stdIn.readLine();
                x = move.charAt(0) - 'a';
                y = Integer.parseInt(move.substring(1)) - 1;
            } catch (Exception e) {}

            if(board.isValidMove(x, y, currentColor)) valid = true;
            else System.out.println("Sorry, that move is not valid.");
        }
        return new Position(x, y);
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
        return "Dark: " + dark + "\tLight: " + light;
    }
}