import string
import sys
import os
import subprocess
import threading
import signal
import re
import time
import traceback
try:
  import psutil
except:
  pass

TIME = 31.0      # Allowed maximum time for a movement
TOTAL_TIME_LIMIT = 121
EMPTY = 0
DARK = 1
LIGHT = 2
MOVE_REGEX = re.compile(b"[m|M]ove [p|P]layed[ \t]*:[ \t]*[a-z][0-9]{1,2}\n")
GAMEOVER = 1111111

class ReversiGame:
  board = None
  board_size = None
  directions_to_flip = None

  dark_player = None
  light_player = None

  def __init__(self, board_size, dark_player, light_player):
    self.board_size = board_size
    self.board = [[EMPTY for x in range(0, board_size)] for x in range(0, board_size)]
    self.board[int(board_size/2) - 1][int(board_size/2) - 1] = LIGHT
    self.board[int(board_size/2)][int(board_size/2)] = LIGHT
    self.board[int(board_size/2)][int(board_size/2) - 1] = DARK
    self.board[int(board_size/2) - 1][int(board_size/2)] = DARK

    self.dark_player = dark_player
    self.light_player = light_player

  def start_game(self):
    self.print_board()
    self.print_score()
    self.dark_player.execute(self.board_size)
    self.light_player.execute(self.board_size)

  def print_board(self):
    sys.stdout.write("  ")
    i = 0
    for c in string.ascii_lowercase:
      i += 1
      if i > self.board_size:
        break
      sys.stdout.write("   %s" % c)
    sys.stdout.write("\n   +")
    for i in range(0, self.board_size):
      sys.stdout.write("---+")
    sys.stdout.write("\n")

    for i in range(0, self.board_size):
      sys.stdout.write("%2d |" % (i + 1))
      for j in range(0, self.board_size):
        if self.board[i][j] == LIGHT:
          sys.stdout.write(" L |")
        elif self.board[i][j] == DARK:
          sys.stdout.write(" D |")
        else:
          sys.stdout.write("   |")
      sys.stdout.write("\n   +")
      for j in range(0, self.board_size):
        sys.stdout.write("---+")
      sys.stdout.write("\n")

  # Print current score
  def print_score(self):
    print("Score: Light %d - Dark %d" % (self.light_player.score, self.dark_player.score))

  # Print results
  def print_results(self):
    print("Dark player total playing time (seconds): " + str(self.dark_player.total_time))
    print("Light player total playing time (seconds): " + str(self.light_player.total_time))

    if self.dark_player.score > self.light_player.score:
      print("Dark player wins!")
      kill_game(DARK)
    elif self.light_player.score > self.dark_player.score:
      print("Light player wins!")
      kill_game(LIGHT)
    else:
      print("It's a tie!")
      kill_game(5)

  def apply_move(self, row, column, color):
    score = self.update_board(row, column, color)
    if color == LIGHT:
      self.light_player.score += score + 1
      self.dark_player.score -= score
    else:
      self.dark_player.score += score + 1
      self.light_player.score -= score

  def player_has_valid_moves(self, color):
    for row in range(0, self.board_size):
      for column in range(0, self.board_size):
        if self.is_valid_move(row, column, color):
          return True
    return False

  def is_game_over(self):
    if self.dark_player.score + self.light_player.score == self.board_size * self.board_size:
      return True
    if not self.player_has_valid_moves(self.dark_player.color) and not self.player_has_valid_moves(self.light_player.color):
      print("No valid moves left for either players")
      return True
    return False

  def parse_move(self, move):
    if not move:
      return None

    # Parse move
    try:
      column = ord(move[0]) - ord('a')
      row = int(move[1:]) - 1
    except ValueError:
      return None
    except TypeError:
      return None
    return row, column

  def is_valid_move(self, row, column, color):
    self.directions_to_flip = set()
    if color == LIGHT:
      other_color = DARK
    else:
      other_color = LIGHT

    # Check boundaries
    if row < 0 or row > self.board_size - 1 or column < 0 or column > self.board_size - 1:
      return False

    # Tile must be empty
    if self.board[row][column] != EMPTY:
      return False

    check = False

    # Check E
    if column + 2 < self.board_size and self.board[row][column + 1] == other_color:
      for i in range(2, self.board_size - column):
        if self.board[row][column + i] == EMPTY:
          break
        if self.board[row][column + i] == color:
          check = True
          self.directions_to_flip.add("E")
          break
    # Check W
    if column - 2 >= 0 and self.board[row][column - 1] == other_color:
      for i in range(2, column + 1):
        if self.board[row][column - i] == EMPTY:
          break
        if self.board[row][column - i] == color:
          check = True
          self.directions_to_flip.add("W")
          break
    # Check S
    if row + 2 < self.board_size and self.board[row + 1][column] == other_color:
      for i in range(2, self.board_size - row):
        if self.board[row + i][column] == EMPTY:
          break
        if self.board[row + i][column] == color:
          check = True
          self.directions_to_flip.add("S")
          break
    # Check N
    if row - 2 >= 0 and self.board[row - 1][column] == other_color:
      for i in range(2, row + 1):
        if self.board[row - i][column] == EMPTY:
          break
        if self.board[row - i][column] == color:
          check = True
          self.directions_to_flip.add("N")
          break

    # Check SE
    if row + 2 < self.board_size and column + 2 < self.board_size and self.board[row + 1][column + 1] == other_color:
      for i in range(2, min(self.board_size - row, self.board_size - column)):
        if self.board[row + i][column + i] == EMPTY:
          break
        if self.board[row + i][column + i] == color:
          check = True
          self.directions_to_flip.add("SE")
          break
    # Check NE
    if row - 2 >= 0 and column + 2 < self.board_size and self.board[row - 1][column + 1] == other_color:
      for i in range(2, min(row + 1, self.board_size - column)):
        if self.board[row - i][column + i] == EMPTY:
          break
        if self.board[row - i][column + i] == color:
          check = True
          self.directions_to_flip.add("NE")
          break
    # Check NW
    if row - 2 >= 0 and column - 2 >= 0 and self.board[row - 1][column - 1] == other_color:
      for i in range(2, min(row + 1, column + 1)):
        if self.board[row - i][column - i] == EMPTY:
          break
        if self.board[row - i][column - i] == color:
          check = True
          self.directions_to_flip.add("NW")
          break
    # Check SW
    if row + 2 < self.board_size and column - 2 >= 0 and self.board[row + 1][column - 1] == other_color:
      for i in range(2, min(self.board_size - row, column + 1)):
        if self.board[row + i][column - i] == EMPTY:
          break
        if self.board[row + i][column - i] == color:
          check = True
          self.directions_to_flip.add("SW")
          break
    return check

  def update_board(self, row, column, color):
    self.board[row][column] = color
    score = 0

    if "S" in self.directions_to_flip:
      for i in range(1, self.board_size):
        if self.board[row + i][column] != color:
          self.board[row + i][column] = color
          score += 1
        else:
          break
    if "E" in self.directions_to_flip:
      for i in range(1, self.board_size):
        if self.board[row][column + i] != color:
          self.board[row][column + i] = color
          score += 1
        else:
          break
    if "N" in self.directions_to_flip:
      for i in range(1, self.board_size):
        if self.board[row - i][column] != color:
          self.board[row - i][column] = color
          score += 1
        else:
          break
    if "W" in self.directions_to_flip:
      for i in range(1, self.board_size):
        if self.board[row][column - i] != color:
          self.board[row][column - i] = color
          score += 1
        else:
          break
    if "SE" in self.directions_to_flip:
      for i in range(1, self.board_size):
        if self.board[row+i][column + i] != color:
          self.board[row+i][column + i] = color
          score += 1
        else:
          break
    if "NE" in self.directions_to_flip:
      for i in range(1, self.board_size):
        if self.board[row-i][column + i] != color:
          self.board[row-i][column + i] = color
          score += 1
        else:
          break
    if "SW" in self.directions_to_flip:
      for i in range(1, self.board_size):
        if self.board[row+i][column - i] != color:
          self.board[row+i][column - i] = color
          score += 1
        else:
          break
    if "NW" in self.directions_to_flip:
      for i in range(1, self.board_size):
        if self.board[row-i][column - i] != color:
          self.board[row-i][column - i] = color
          score += 1
        else:
          break
    return score

#######################################################################

class ReversiPlayer:
  score = None
  lost_turns = None
  color = None
  name = None
  executable_path = None
  executable = None
  timer = None
  stdoutput = None
  total_time = 0

  def __init__(self, color, name, executable_path):
    self.score = 2
    self.lost_turns = 0
    if color == DARK:
      self.lost_turns = -1
    self.color = color
    self.name = name
    self.executable_path = executable_path

  def kill(self):
    if self.timer:
      self.timer.cancel()
    try:
      pid = int(self.executable.pid)
    except:
      pass

    # Find process children (if any) and kill them
    try:
      for child in psutil.Process(pid).children(recursive = True):
        try:
          os.kill(child.pid, signal.SIGTERM)
        except:
          pass
    except:
      pass

    try:
      self.executable.stdin.close()
    except:
      pass
    try:
      self.executable.kill()
    except:
      pass
    try:
      self.executable.terminate()
    except:
      pass
    try:
      os.kill(pid, signal.SIGTERM)
    except:
      pass
    try:
      self.stdoutput.close()
    except:
      pass

  # Execute the program of the player
  def execute(self, board_size):
    try:
      # Call executable
      argument_list = ["-n", str(board_size)]
      # If the program is going to be the dark player we need to pass the -l argument
      if self.color == DARK:
        argument_list.append("-l")
      self.executable = subprocess.Popen([self.executable_path] + argument_list, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
      self.stdoutput = open(str(self.name) + ".out", "w")
    except:
      print("Error: Could not execute %s (%s player)" % (self.executable_path, self.name))
      if self.color == DARK:
        kill_game(LIGHT)
      else:
        kill_game(DARK)

  # Start timer for player's next move
  def start_timer(self):
    # If the time limit is reached, terminate the game and have the other player win
    self.timer = threading.Timer(TIME, terminate_game, [self.color])
    self.timer.setDaemon(True)
    self.timer.start()

  # Stop timer for player's next move
  def stop_timer(self):
    self.timer.cancel()
    self.timer.join()

  # Player lost their turn
  def lost_turn(self):
    self.lost_turns += 1

  # Get and parse the player's next move
  def get_next_move(self, other_player_lost_turns):
    move = None

    # Log time to print how much time it took to get the next move
    time_begin = time.time()
    # Start timer
    self.start_timer()
    counter = 0
    # Keep reading from the pipe until the current player's move is processed
    while True:
      line = self.executable.stdout.readline()
      try:
        self.stdoutput.write(str(line.decode("utf-8")))
        self.stdoutput.flush()
      except:
        return GAMEOVER, None
      if MOVE_REGEX.match(line):
        if other_player_lost_turns > 0:
          move = str(line[:-1])
          break
        counter += 1
      if counter == 2 + self.lost_turns:
        move = str(line[:-1])
        break
    # Stop timer when move is accepted
    self.stop_timer()
    time_end = time.time()
    move_time = time_end - time_begin
    self.total_time += move_time

    if self.total_time > TOTAL_TIME_LIMIT:
      print("Error: %s player reached the total time limit of 2 minutes per game" % self.name)
      print("Game over!")
      if self.color == DARK:
        print("Light player wins!")
        kill_game(LIGHT)
      else:
        print("Dark player wins!")
        kill_game(DARK)

    # Reset lost turn counter
    self.lost_turns = 0

    try:
      move_temp = move.split()[2]
      if move_temp == ":":
        move = move.split()[3][:-1]
      else:
        if sys.version_info[0] == 2:
          move = move_temp
        else:
          move = move_temp[:-1]
    except IndexError:
      print("Error: Wrong format of move: %s" % move)
      return None, None

    return move, move_time

  def send_next_move(self, move):
    try:
      if sys.version_info[0] == 2:
        self.executable.stdin.write("%s\n" % move)
      else:
        self.executable.stdin.write(bytes("%s\n" % move, "UTF-8"))
        self.executable.stdin.flush()
    except ValueError:
      terminate_game2(self.color)

#######################################################################

reversi = None

def main():
  global reversi

  print("Reversi referee")
  print("Created by Theodore Georgiou for the Artificial Intelligence course, UC Santa Barbara, Winter 2016")
  print("--------------------\n")

  if len(sys.argv) <= 3:
    print("Usage: You need to provide three arguments, the size of the board and the paths to the two executables (light and dark player)")
    sys.exit(0)

  board_size = int(sys.argv[1])
  if board_size % 2 != 0 or board_size < 4 or board_size > 26:
    print("Error: Board size must be an even integer between 4 and 26")
    sys.exit(0)
  print("Board size: %d" % board_size)

  light_executable_path = sys.argv[2]
  if not os.path.exists(light_executable_path) or not os.access(light_executable_path, os.X_OK):
    print("Error: File %s does not exist or is not executable (light player)" % light_executable_path)
    sys.exit(0)
  print("Path to the executable of the light player: %s" % light_executable_path)
  dark_executable_path = sys.argv[3]
  if not os.path.exists(dark_executable_path) or not os.access(dark_executable_path, os.X_OK):
    print("Error: File %s does not exist or is not executable (dark player)" % dark_executable_path)
    sys.exit(0)
  print("Path to the executable of the dark player: %s" % dark_executable_path)

  dark_player = ReversiPlayer(DARK, "Dark", dark_executable_path)
  light_player = ReversiPlayer(LIGHT, "Light", light_executable_path)

  reversi = ReversiGame(board_size, dark_player, light_player)
  reversi.start_game()

  # Start gameplay
  try:
    current_player = light_player # Set to light player so it switches to dark in the first turn
    while True:
      # Swap players in every turn
      if current_player == light_player:
        current_player = dark_player
        other_player = light_player
      else:
        current_player = light_player
        other_player = dark_player

      if reversi.player_has_valid_moves(current_player.color):
        print("%s player's turn" % current_player.name)
        move, move_time = current_player.get_next_move(other_player.lost_turns)
        if move == GAMEOVER:
          time.sleep(1)
          return

        if reversi.parse_move(move):
          row, column = reversi.parse_move(move)
        else:
          print("Error: %s player tried an invalid move (%s)" % (current_player.name, move))
          print("Game over!")
          print("%s player wins!" % other_player.name)
          kill_game(other_player.color)

        if not reversi.is_valid_move(row, column, current_player.color):
          print("Error: %s player tried an invalid move (%s)" % (current_player.name, move))
          print("Game over!")
          print("%s player wins!" % other_player.name)
          kill_game(other_player.color)

        print("%s player's move: %s (within %.2f seconds)" % (current_player.name, move, move_time))

        reversi.apply_move(row, column, current_player.color)
        reversi.print_board()
        reversi.print_score()

        if reversi.is_game_over():
          other_player.send_next_move(move)
          break

        # Send current player's move to the other player
        other_player.send_next_move(move)
      else:
        print("%s player has no valid moves" % current_player.name)
        current_player.lost_turn()
  except KeyboardInterrupt:
    print("Error: KeyboardInterrupt captured")
    kill_game(0)
  #except:
  #  print("Error: Unexpected exception occurred")
  #  kill_game()

  print("Game over!")
  reversi.print_results()


def terminate_game(color):
  print("Error: Current player takes too long to decide on their next move (%d seconds passed)" % TIME)
  print("Game over!")
  if color == DARK:
    print("Light player wins!")
    kill_game(LIGHT)
  else:
    print("Dark player wins!")
    kill_game(DARK)

def terminate_game2(color):
  print("Error: Next player is not responsive")
  print("Game over!")
  if color == DARK:
    print("Light player wins!")
    kill_game(LIGHT)
  else:
    print("Dark player wins!")
    kill_game(DARK)

def kill_game(color):
  print("Exiting...")
  reversi.dark_player.kill()
  reversi.light_player.kill()
  sys.stdout.flush()
  sys.stderr.flush()
  os._exit(color)


if __name__ == "__main__":
  try:
    main()
  except:
    print(traceback.format_exc())
