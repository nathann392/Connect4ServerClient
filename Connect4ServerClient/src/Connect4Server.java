import java.io.*;
import java.net.*;
import java.util.Date;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

/**
 * Server to host Connect 4 game between two players
 * 
 * @author Nathan Nguyen, Srividya Bansal
 * @version 1.0
 */
public class Connect4Server extends Application 
implements Connect4Constants {
	private int sessionNo = 1; // Number a session

	/**
	 * Starts server and connects to two players
	 * 
	 * @param primaryStage argument
	 */
	@Override // Override the start method in the Application class
	public void start(Stage primaryStage) {
		TextArea taLog = new TextArea();

		// Create a scene and place it in the stage
		Scene scene = new Scene(new ScrollPane(taLog), 450, 200);
		primaryStage.setTitle("Connect4Server"); // Set the stage title
		primaryStage.setScene(scene); // Place the scene in the stage
		primaryStage.show(); // Display the stage

		new Thread( () -> {
			try {
				// Create a server socket
				ServerSocket serverSocket = new ServerSocket(8000);
				Platform.runLater(() -> taLog.appendText(new Date() +
						": Server started at socket 8000\n"));

				// Ready to create a session for every two players
				while (true) {
					Platform.runLater(() -> taLog.appendText(new Date() +
							": Wait for players to join session " + sessionNo + '\n'));

					// Connect to player 1
					Socket player1 = serverSocket.accept();

					Platform.runLater(() -> {
						taLog.appendText(new Date() + ": Player 1 joined session " 
								+ sessionNo + '\n');
						taLog.appendText("Player 1's IP address" +
								player1.getInetAddress().getHostAddress() + '\n');
					});

					// Notify that the player is Player 1
					new DataOutputStream(
							player1.getOutputStream()).writeInt(PLAYER1);

					// Connect to player 2
					Socket player2 = serverSocket.accept();

					Platform.runLater(() -> {
						taLog.appendText(new Date() +
								": Player 2 joined session " + sessionNo + '\n');
						taLog.appendText("Player 2's IP address" +
								player2.getInetAddress().getHostAddress() + '\n');
					});

					// Notify that the player is Player 2
					new DataOutputStream(
							player2.getOutputStream()).writeInt(PLAYER2);

					// Display this session and increment session number
					Platform.runLater(() -> 
					taLog.appendText(new Date() + 
							": Start a thread for session " + sessionNo++ + '\n'));

					// Launch a new thread for this session of two players
					new Thread(new HandleASession(player1, player2)).start();
				}
			}
			catch(IOException ex) {
				ex.printStackTrace();
			}
		}).start();
	}

	/**
	 * Define the thread class for handling a new session for two players
	*/
	class HandleASession implements Runnable, Connect4Constants {
		private Socket player1;
		private Socket player2;

		// Create and initialize cells
		private char[][] cell =  new char[6][7];

		private DataInputStream fromPlayer1;
		private DataOutputStream toPlayer1;
		private DataInputStream fromPlayer2;
		private DataOutputStream toPlayer2;

		// Continue to play
		private boolean continueToPlay = true;


		/**
		 * Construct a thread 
		 * 
		 * @param player1 connects to the client of the first player
		 * @param player2 connects to the client of the second player
		 */
		public HandleASession(Socket player1, Socket player2) {
			this.player1 = player1;
			this.player2 = player2;

			// Initialize cells
			for (int i = 0; i < 6; i++)
				for (int j = 0; j < 7; j++)
					cell[i][j] = ' ';
		}

		/**
		 * Runs the server
		 */
		public void run() {
			try {
				// Create data input and output streams
				DataInputStream fromPlayer1 = new DataInputStream(
						player1.getInputStream());
				DataOutputStream toPlayer1 = new DataOutputStream(
						player1.getOutputStream());
				DataInputStream fromPlayer2 = new DataInputStream(
						player2.getInputStream());
				DataOutputStream toPlayer2 = new DataOutputStream(
						player2.getOutputStream());

				// Write anything to notify player 1 to start
				// This is just to let player 1 know to start
				toPlayer1.writeInt(1);

				// Continuously serve the players and determine and report
				// the game status to the players
				while (true) {
					// Receive a move from player 1
					int row = fromPlayer1.readInt();
					int column = fromPlayer1.readInt();
					
					// Take lowest row and place in selected column
					row = getLowestRow(column); 
					cell[row][column] = 'X';

					// Check if Player 1 wins
					if (isWon('X')) {
						// Send player 1's selected column with correct row to player 1
						sendMove(toPlayer1, row, column); 

						toPlayer1.writeInt(PLAYER1_WON);
						toPlayer2.writeInt(PLAYER1_WON);
						
						
						// Send player 1's selected row and column to player 2
						sendMove(toPlayer2, row, column);
						break; // Break the loop
					}
					else {
						// Notify player 2 to take the turn
						toPlayer2.writeInt(CONTINUE);

						// Send player 1's selected column with correct row to player 1
						sendMove(toPlayer1, row, column); 
						
						// Send player 1's selected row and column to player 2
						sendMove(toPlayer2, row, column);
					}

					// Receive a move from Player 2
					row = fromPlayer2.readInt();
					column = fromPlayer2.readInt();
					
					// Take lowest row and place in selected column
					row = getLowestRow(column); // Places this in opposite client
					cell[row][column] = 'O';

					// Check if Player 2 wins
					if (isWon('O')) {
						
						// Send player 2's selected column with correct row to player 2
						sendMove(toPlayer2, row, column); 

						toPlayer1.writeInt(PLAYER2_WON);
						toPlayer2.writeInt(PLAYER2_WON);
						
						// Send player 2's selected row and column to player 1
						sendMove(toPlayer1, row, column);
						break;
					}
					else if (isFull()) { // Check if all cells are filled
						// Send player 2's selected column with correct row to player 2
						sendMove(toPlayer2, row, column); 	

						toPlayer1.writeInt(DRAW);
						toPlayer2.writeInt(DRAW);
						
						// Send player 2's selected row and column to player 1
						sendMove(toPlayer1, row, column);
						break;
					}
					else {
						// Notify player 1 to take the turn
						toPlayer1.writeInt(CONTINUE);

						// Send player 2's selected column with correct row to player 2
						sendMove(toPlayer2, row, column); 
						
						// Send player 2's selected row and column to player 1
						sendMove(toPlayer1, row, column);
					}
				}
			}
			catch(IOException ex) {
				ex.printStackTrace();
			}
		}

		/**
		 * Send the move to other player
		 * 
		 * @param out DataOutputStream for the other player
		 * @param row location of token
		 * @param column location of token
		 * @throws IOException
		 */
		private void sendMove(DataOutputStream out, int row, int column)
				throws IOException {
			out.writeInt(row); // Send row index
			out.writeInt(column); // Send column index
		}

		/**
		 * Determine if the cells are all occupied 
		 * 
		 * @return if cell are full
		 */
		private boolean isFull() {
			for (int i = 0; i < 6; i++)
				for (int j = 0; j < 7; j++)
					if (cell[i][j] == ' ')
						return false; // At least one cell is not filled

			// All cells are filled
			return true;
		}

		/**
		 * Determine if the player with the specified token wins
		 * 
		 * @param token character of current player
		 * @return if player won the game
		 */
		private boolean isWon(char token) {		
			// Check horizontal
			for (int i = 0; i < 6; i++)
				for (int j = 0; j < 4; j++)
					if (token == cell[i][j] && token == cell[i][j+1] && 
							token == cell[i][j+2] && token == cell[i][j+3])
						return true;
			
			// Check vertical
			for (int i = 0; i < 3; i++)
				for (int j = 0; j < 7; j++)
					if (token == cell[i][j] && token == cell[i+1][j] && 
							token == cell[i+2][j] && token == cell[i+3][j])
						return true;
			
			// Check bottom left to top right diagonal
			for (int i = 0; i < 3; i++)
				for (int j = 3; j < 7; j++)
					if (token == cell[i][j] && token == cell[i+1][j-1] && 
							token == cell[i+2][j-2] && token == cell[i+3][j-3])
							return true;	
			
			// Check bottom right to top left diagonal
			for (int i = 0; i < 3; i++)
				for (int j = 0; j < 4; j++)
					if (token == cell[i][j] && token == cell[i+1][j+1] && 
							token == cell[i+2][j+2] && token == cell[i+3][j+3])
						return true;			
						
			return false;
		}
		
		/**
		 * Retrieves the lowest possible row that a piece can be placed
		 * 
		 * @param column location that was selected by player
		 * @return row at the lowest open position of the column
		 */
		private int getLowestRow(int column) 
		{
			for(int i = 5; i >= 0; i--)
				if (cell[i][column] == ' ')
					return i;
					
			return 0;
		}	
	}

	/**
	 * The main method is only needed for the IDE with limited
	 * JavaFX support. Not needed for running from the command line.
	 */
	public static void main(String[] args) {
		launch(args);
	}
}