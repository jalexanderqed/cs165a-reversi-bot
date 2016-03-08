import java.io.BufferedOutputStream;

public class Main{
    public static void main(String[] args){
        byte iPlay = Board.LIGHT;
        int boardSize = 8;

        for(int i = 0; i < args.length; i++){
            if(args[i].indexOf("-l") == 0){
                iPlay = Board.DARK;
            }
            else if(args[i].indexOf("-n") == 0){
                boardSize = Integer.parseInt(args[i + 1]);
            }
        }

        Board board = new Board(boardSize);
        System.out.print(board);
    }
}

class Board{
    public static final byte EMPTY = 0;
    public static final byte LIGHT = 1;
    public static final byte DARK = 2;

    private byte[][] spaces;

    public Board(int size){
        spaces = new byte[size][size];
    }

    public Board(Board b){
        spaces = new byte[b.spaces.length][b.spaces.length];
        for(int i = 0; i < spaces.length; i++){
            System.arraycopy(b.spaces[i], 0, spaces[i], 0, b.spaces.length);
        }
    }

    public String toString(){
        StringBuilder s = new StringBuilder(spaces.length * spaces.length);
        char blue[] = { 0x1b, '[', '4', '4', 'm', 0 };
        char red[] = { 0x1b, '[', '4', '1', 'm', 0 };
        char yellow[] = { 0x1b, '[', '4', '3', 'm', 0 };
        char cyan[] = { 0x1b, '[', '4', '6', 'm', 0 };
        char magenta[] = { 0x1b, '[', '4', '5', 'm', 0 };
        char green[] = { 0x1b, '[', '4', '2', 'm', 0 };
        char underline[] = {0x1b, '[', '4', 'm', 0};
        char noColor[] = { 0x1b, '[', '4', '9', 'm', 0};
        char noFormat[] = {0x1b, '[', '0', 'm', 0};
        s.append(underline);
        s.append(' ');
        for(int i = 0; i < spaces.length; i++) s.append("  ");

        s.append('\n');
        for(int y = 0; y < spaces.length; y++){
            if(y == spaces[0].length - 1){
                s.append(underline);
            }
            s.append('|');
            for(int x = 0; x < spaces[0].length; x++){
                switch(spaces[x][y]){
                    case EMPTY:
                        s.append(' ');
                        break;
                    case DARK:
                        s.append('O');
                        break;
                    case LIGHT:
                        s.append('X');
                        break;
                }
                s.append('|');
            }
            s.append('\n');
        }
        s.append(noFormat);
        return s.toString();
    }
}