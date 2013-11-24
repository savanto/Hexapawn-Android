package com.savanto.hexapawn;


import java.io.File;
import java.util.Random;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;


public class HexapawnActivity extends Activity
{
	/**
	 * The time the computer takes to "think", in ms
	 * This is a delay to keep the responding computer move from being
	 * instant, and possibly confusing
	 */
	private static final long THINK_TIME = 500;

	/**
	 * How long temporary status messages should appear, in ms
	 */
	private static final long MESSAGE_TIME = 1500;

	/**
	 * SharedPreferences keys
	 */
	private static final String KEY_GAMES_PLAYED = "games_played";
	private static final String KEY_WHITE_WINS = "white_wins";

	/**
	 * The root back-end Board used to start new games
	 */
	private Board newBoard;

	/**
	 * The current back-end Board being played.
	 */
	private Board currentBoard;

	/**
	 * The last Board on which the computer made a move,
	 * used to prune losing moves.
	 */
	private Board parent;

	/**
	 * The last move that that the computer made,
	 * used to prune from the parent if a losing move.
	 */
	private Board choice;

	/**
	 * The graphic representation of the Board being played,
	 * providing direct access to the objects with which 
	 * the user will be interacting.
	 */
	private PawnView[][] pawns;

	/**
	 * The number of players:
	 * 0 - computer vs computer
	 * 1 - human vs computer
	 * 2 - human vs human
	 */
	private int players;

	/**
	 * The random number generator used to pick computer moves
	 */
	private Random rng;

	/**
	 * The main container layout and the board squares layout.
	 */
	private RelativeLayout relativeLayout;
	private SquareLayout squareLayout;

	/**
	 * Status message label. Updated with info for the player.
	 */
	private TextView status;

	/**
	 * AI skill progress bar.
	 */
	private ProgressBar skill;

	/**
	 * Keep track of stats, to update db later
	 */
	private int gamesPlayed;
	private int whiteWins;
	private TextView statGamesPlayed, statWhiteWins, statBlackWins;

	/**
	 * The database helper to interact with the Boards database.
	 */
	private DatabaseHelper dbHelper;

	/**
	 * Instructions dialog, shown at startup and when instructions are requested.
	 */
	private AlertDialog instructionsDialog;

	// Methods

	// Executed upon starting the program, or starting a New Game
	// Contains initializations to present the game in its initial state
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.main);

		// Create the instructions dialog.
		// TODO: make instructions clearer.
		this.instructionsDialog = new AlertDialog.Builder(this)
			.setTitle(R.string.instructions_title)
			.setMessage(R.string.instructions_message)
			.setNeutralButton("Ok", new DialogInterface.OnClickListener(){ @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); } })
			.create();

		// Create the initial back-end board.
		// If this is the first time the app is launched,
		// generate all boards and create database.
		// Otherwise, load all boards from database.
		this.dbHelper = new DatabaseHelper(this);

		// Check if this is a saved instance, and if it is, load the new and current Boards,
		// or else go on to do database checking and loading, or new move tree generation.
		Board[] savedBoards = (Board[]) this.getLastNonConfigurationInstance();
		if (savedBoards != null)
		{
			this.newBoard = savedBoards[0];
			this.currentBoard = savedBoards[1];
		}
		if (this.newBoard == null)
		{
			// It is not.
			// Check if database exists.
			File dbFile = this.getApplicationContext().getDatabasePath(DatabaseHelper.DATABASE_NAME);
			// If database file exists, load boards from database
			if (dbFile.exists())
				this.newBoard = this.dbHelper.loadBoards();

			// No database found, or there was an error loading it.
			// Generate Boards and store them.
			if (this.newBoard == null)
			{
				// Create starting Board and generate child moves.
				// Default starting Board:
				// b b b	7 << 6, 111 000 000
				// . . .
				// w w w	7		000 000 111
				// WHITE to move
				this.newBoard = new Board(Board.RANK_3, Board.RANK_1, Board.Color.WHITE);
				this.newBoard.generate();

				// Create and populate database with generated moves.
				// This is a long operation, so do it on a separate thread.
				// While the thread executes, the user will be presented with an already-complete
				// moves tree, so they can begin playing.
				new Thread(new Runnable()
				{
					@Override
					public void run()
					{
						HexapawnActivity.this.dbHelper.storeBoards(HexapawnActivity.this.newBoard);
					}
				}).start();

				// Display first time startup dialog with instructions.
				this.instructionsDialog.show();
			}
		}
		

		// Set player mode
		// TODO: comp vs comp, human vs human modes.
		this.players = 1;

		// Initialize random number generator for computer move generation
		if (this.players < 2)
			this.rng = new Random();

		// Create the graphic pawns.
		// This needs to be done after the board is measured and rendered,
		// so we do it in a global layout listener.
		this.relativeLayout = (RelativeLayout) this.findViewById(R.id.layout);
		this.squareLayout = (SquareLayout) this.findViewById(R.id.board);
		ViewTreeObserver vto = this.squareLayout.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener()
		{
			@Override
			public void onGlobalLayout()
			{
				// Use loaded current Board if not null, or
				// set the current Board to the new game Board for new game
				if (HexapawnActivity.this.currentBoard == null)
					HexapawnActivity.this.currentBoard = HexapawnActivity.this.newBoard;
				// Create new game layout.
				HexapawnActivity.this.createGraphicBoard();

				// Done setting up. Global layout listener is no longer
				// needed, so kill it.
				HexapawnActivity.this.squareLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
			}
		});

		// Set up the rest of the interface, ie. buttons and labels.
		// New game button
		Button newGame = (Button) this.findViewById(R.id.new_game);
		newGame.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Set the current Board to the new game Board
				HexapawnActivity.this.currentBoard = HexapawnActivity.this.newBoard;
				// Recreate graphics
				HexapawnActivity.this.createGraphicBoard();
			}
		});

		// Status message text view
		this.status = (TextView) this.findViewById(R.id.status);
		
		// AI skill setup.
		// Only show skill bar if computer player is present
		this.skill = (ProgressBar) this.findViewById(R.id.skill);
		if (this.players < 2)
				HexapawnActivity.this.skill.setProgress(HexapawnActivity.this.dbHelper.getSkill());								
		else
			this.skill.setVisibility(View.GONE);

		// Stats setup
		this.statGamesPlayed = (TextView) this.findViewById(R.id.stats_games);
		this.statWhiteWins = (TextView) this.findViewById(R.id.stats_white_wins);
		this.statBlackWins = (TextView) this.findViewById(R.id.stats_black_wins);
		// Load stats from shared preferences
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		this.gamesPlayed = sharedPrefs.getInt(HexapawnActivity.KEY_GAMES_PLAYED, 0);
		this.whiteWins = sharedPrefs.getInt(HexapawnActivity.KEY_WHITE_WINS, 0);
		this.updateStats();
	}

	/**
	 * Create graphic pawns from the current board.
	 * Called when creating a new game, or redrawing current game
	 * due to orientation change.
	 */
	private void createGraphicBoard()
	{
		int row, col;
		// Initialize array of graphic pawns
		Board.Color[][] pawnColors = this.currentBoard.toArray();

		// Remove all pawns from the layout, if any.
		if (this.pawns != null)
		{
			for (row = 0; row < Board.SIZE; row++)
			{
				for (col = 0; col < Board.SIZE; col++)
					this.relativeLayout.removeView(this.pawns[row][col]);
			}
		}
		this.pawns = new PawnView[Board.SIZE][Board.SIZE];

		// Traverse the board
		for (row = 0; row < Board.SIZE; row++)
		{
			for (col = 0; col < Board.SIZE; col++)
			{
				// Create the graphic pawns based on the available measurements
				if (pawnColors[row][col] != null)
				{
					switch (pawnColors[row][col])
					{
						case BLACK:
							this.pawns[row][col]	= new PawnView(
									Board.Color.BLACK, 
									this, 
									R.drawable.black_pawn, 
									this.squareLayout.getChildSize(), 
									row, 
									col, 
									new PawnListener()
							);
							break;
						case WHITE:
							this.pawns[row][col] = new PawnView(
									Board.Color.WHITE, 
									this, 
									R.drawable.white_pawn, 
									this.squareLayout.getChildSize(), 
									row, 
									col, 
									new PawnListener()
							);
							break;
						default:
							this.pawns[row][col] = null;
							break;
					}

					// Add the view to the parent layout
					this.relativeLayout.addView(this.pawns[row][col]);
				}
			}
		}

		// Status message label.
		// Set to appropriate string based on victory or current move
		switch (this.currentBoard.getTurn())
		{
			// Black turn
			case BLACK:
				// White has won
				if (this.currentBoard.isVictory())
					this.status.setText(R.string.white_victory);
				// Black to move
				else
					this.status.setText(R.string.black_to_move);

				break;
			// White turn
			case WHITE:
				// Black has won
				if (this.currentBoard.isVictory())
					this.status.setText(R.string.black_victory);
				// White to move
				else
					this.status.setText(R.string.white_to_move);

				break;
		}
	}

	/**
	 * Create a game Board from the graphical setup of pawns
	 * given by the move of the pawn at the source to the destination.
	 * @param sourceRow - row of the source pawn
	 * @param sourceCol - col of the source pawn
	 * @param destRow - row of the target coordinate
	 * @param destCol - col of the target coordinate
	 * @return a Board to test against the Boards tree.
	 */
	private Board makeBoard(Move move)
	{
		// Produce simple array of pawns from the graphical pawns array.
		Board.Color[][] array = new Board.Color[Board.SIZE][Board.SIZE];
		for (int row = 0, rank = Board.RANK_3; row < Board.SIZE; row++, rank = Board.prevRank(rank))
		{
			for (int col = 0, file = Board.FILE_A; col < Board.SIZE; col++, file = Board.nextFile(file))
			{
				if (this.pawns[row][col] != null)
					array[row][col] = this.pawns[row][col].getColor();
			}
		}

		// Apply the move, from source to destination.
		array[move.destRow][move.destCol] = array[move.sourceRow][move.sourceCol];
		array[move.sourceRow][move.sourceCol] = null;

		return new Board(array);
	}

	/**
	 * An extension of the OnTouchListener class for all Views,
	 * the PawnListener is specific to PawnViews.
	 * 
	 * Listens for touch events to the PawnViews, interprets them,
	 * and passes the information to the Activity, which modifies
	 * the game Board as necessary. 
	 */
	private class PawnListener implements OnTouchListener
	{
		// The initial raw coordinates of the touched PawnView.
		private int initialX = 0, initialY = 0;
		// The initial coordinates of the touched PawnView.
		private int sourceRow = 0, sourceCol = 0;

		@Override
		public boolean onTouch(View v, MotionEvent event)
		{
			// Get the PawnView that has been touched.
			PawnView pawn = (PawnView) v;

			// Check which touch even is occuring
			switch (event.getAction())
			{
				// The touch event has just started
				case MotionEvent.ACTION_DOWN:
					// Gesture has started on the PawnView.
					// Bring the PawnView in front of all others.
					pawn.bringToFront();
					HexapawnActivity.this.relativeLayout.requestLayout();
					HexapawnActivity.this.relativeLayout.invalidate();
					
					// Record the PawnView's initial position, in case move is 
					// illegal and pawn must be returned.
					this.initialX = pawn.getLeft();
					this.initialY = pawn.getTop();
					this.sourceRow = this.initialY / pawn.getSize();
					this.sourceCol = this.initialX / pawn.getSize();

					break;

				// The touch event continues
				case MotionEvent.ACTION_MOVE:
					// The PawnView is being moved.
					// Update the location.
					pawn.setRawPosition(event.getRawX(), event.getRawY());

					break;

				// The touch event ends.
				case MotionEvent.ACTION_UP:
					// The PawnView is released.

					// Check that the correct color was moved
					Board.Color turn = HexapawnActivity.this.currentBoard.getTurn();
					if (pawn.getColor() != turn)
					{
						switch (turn)
						{
							case BLACK:
								HexapawnActivity.this.updateStatus(false, R.string.illegal_move);//, R.string.black_to_move);
								break;
							case WHITE:
								HexapawnActivity.this.updateStatus(false, R.string.illegal_move);//, R.string.white_to_move);
								break;
						}
						// Return PawnView to initial coorindinates, cancel move
						pawn.setRawPosition(this.initialX, this.initialY);
						return false;
					}
					// Determine target coordinates from the CENTER of the PawnView
					int destRow = pawn.getCenterY() / pawn.getSize();
					int destCol = pawn.getCenterX() / pawn.getSize();

					// Check for out of bounds conditions.
					if (destRow >= Board.SIZE || destCol >= Board.SIZE)
					{
						HexapawnActivity.this.updateStatus(false, R.string.illegal_move);//, R.string.move_instructions);
						// Return PawnView to initial coordinates, cancel move.
						pawn.setRawPosition(this.initialX, this.initialY);
						return false;
					}

					// Check that move is legal.
					// Check if test Board is a legal move on the current game Board.
					Board next = HexapawnActivity.this.currentBoard.getLegal(
							HexapawnActivity.this.makeBoard(new Move(this.sourceRow, this.sourceCol, destRow, destCol)));
					if (next == null)
					{
						// No matching moves found, therefore move is illegal.
						HexapawnActivity.this.updateStatus(false, R.string.illegal_move);//, R.string.move_instructions);
						// Return PawnView to initial coordinates, cancel move.
						pawn.setRawPosition(this.initialX, this.initialY);
						return false;
					}

					// Otherwise move is legal.
					// Make back-end move, preserving the parent Board for pruning later.
					HexapawnActivity.this.currentBoard = next;

					// Finalize graphical changes:
					// If capture, make pawn at destination disappear
					if (HexapawnActivity.this.pawns[destRow][destCol] != null)
						HexapawnActivity.this.pawns[destRow][destCol].capture();
					// Move pawn from source to destination
					HexapawnActivity.this.pawns[destRow][destCol] = pawn;
					// Remove reference to pawn at source
					HexapawnActivity.this.pawns[this.sourceRow][this.sourceCol] = null;
					// Set the pawn neatly in the destination square
					pawn.setPosition(destRow, destCol);

					// Update turn instruction
					switch (HexapawnActivity.this.currentBoard.getTurn())
					{
						case BLACK:
							HexapawnActivity.this.updateStatus(true, R.string.black_to_move);
							break;
						case WHITE:
							HexapawnActivity.this.updateStatus(true, R.string.white_to_move);
							break;
					}

					// Check to see if playing with computer
					if (HexapawnActivity.this.players < 2)
					{
						// Update current Board by having computer make a move
						HexapawnActivity.this.computerMove();
					}

					// Check for victory
					if (HexapawnActivity.this.currentBoard.isVictory())
					{
						HexapawnActivity.this.gamesPlayed++;
						// Update status from current turn.
						// Winner is player who moved last turn.
						switch (HexapawnActivity.this.currentBoard.getTurn())
						{
							case BLACK:	// current turn black, so white won
								HexapawnActivity.this.updateStatus(true, R.string.white_victory);
								if (HexapawnActivity.this.players < 2)
								{
									// Prune the losing move and all children from the computer's move tree,
									// and from the database.
									HexapawnActivity.this.parent.prune(HexapawnActivity.this.choice);
									// Database access in its own thread
									new Thread(new Runnable()
									{
										@Override
										public void run()
										{
											HexapawnActivity.this.dbHelper.pruneBoards(HexapawnActivity.this.choice);
											// Update AI skill level
											HexapawnActivity.this.skill.setProgress(HexapawnActivity.this.dbHelper.getSkill());
										}
									}).start();

								}
								HexapawnActivity.this.whiteWins++;
								break;
							case WHITE:	// current turn white, so black won
								HexapawnActivity.this.updateStatus(true, R.string.black_to_move);
								break;
						}
						HexapawnActivity.this.updateStats();
					}
					break;
			}
			return true;
		}
	};

	/**
	 * Have the computer (BLACK) pick an available move and perform it.
	 */
	private void computerMove()
	{
		// TODO: animate computer move

		// Make the back-end move instantly, to prevent human players from trying to
		// move during computer's turn.

		// Make a computer move on the back-end Board by choosing
		// a random available move
		Board next = this.currentBoard.pickBoard(this.rng);
		if (next != null)
		{
			final Move move = this.currentBoard.getMove(next);

			// Record the current Board: it is the latest parent from which the computer makes a move.
			this.parent = this.currentBoard;
			// Update current Board with chosen move
			this.currentBoard = next;
			// Record the chosen move: it is the move that the computer has picked.
			this.choice = this.currentBoard;

			// Make the graphic move on a separate, delayed thread,
			// so that there can be a slight "thinking" delay
			new Handler().postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					// Make the changes to the graphics
					// If capture, make pawn at destination disappear
					if (HexapawnActivity.this.pawns[move.destRow][move.destCol] != null)
						HexapawnActivity.this.pawns[move.destRow][move.destCol].capture();
					// Move pawn from source to destination
					HexapawnActivity.this.pawns[move.destRow][move.destCol] = HexapawnActivity.this.pawns[move.sourceRow][move.sourceCol];
					// Remove reference to pawn at source
					HexapawnActivity.this.pawns[move.sourceRow][move.sourceCol] = null;
					// Set the pawn neatly in the destination square
					HexapawnActivity.this.pawns[move.destRow][move.destCol].setPosition(move.destRow, move.destCol);

					// Check for victory/turn
					// If victory, update stats
					if (HexapawnActivity.this.currentBoard.isVictory())
						HexapawnActivity.this.updateStatus(true, R.string.black_victory);
					else
						HexapawnActivity.this.updateStatus(true, R.string.white_to_move);
				}
			}, HexapawnActivity.THINK_TIME);
		}
	}

	/**
	 * Update the status message with info for the player.
	 * @param persist - true to make the message permanent (until next call of updateStatus),
	 * 					or false to revert the message to the previous after MESSAGE_TIME
	 * @param message - the message to set
	 */
	private void updateStatus(boolean persist, final String message)
	{
		if (persist)
			this.status.setText(message);
		else
		{
			// Save the old message, and set to the new message
			final String oldMessage = (String) this.status.getText();
			this.status.setText(message);
			// Set a timer to revert the message
			new Handler().postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					// Revert the message if it has not changed in the mean time
					if (HexapawnActivity.this.status.getText() == message)
						HexapawnActivity.this.status.setText(oldMessage);
				}
			}, HexapawnActivity.MESSAGE_TIME);
		}
	}

	/**
	 * Update the status message from resource string.
	 * @param resid - resource id for string.
	 * @param persist - true to make the message permanent (until next call of updateStatus),
	 * 					or false to revert the message to the previous after MESSAGE_TIME
	 */
	private void updateStatus(boolean persist, int resid)
	{
		this.updateStatus(persist, this.getString(resid));
	}

	/**
	 * Update the stats GUI components with various information for the user.
	 */
	private void updateStats()
	{
		// Update games played, victories.
		this.statGamesPlayed.setText(Integer.toString(this.gamesPlayed));
		this.statWhiteWins.setText(Integer.toString(this.whiteWins));
		this.statBlackWins.setText(Integer.toString(this.gamesPlayed - this.whiteWins));
	}

	/**
	 * 
	 */
	@Override
	public void onPause()
	{
		super.onPause();

		// Close db connection
		this.dbHelper.close();

		// Commit stat info to SharedPreferences
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		sharedPrefs.edit()
			.putInt(HexapawnActivity.KEY_GAMES_PLAYED, this.gamesPlayed)
			.putInt(HexapawnActivity.KEY_WHITE_WINS, this.whiteWins)
			.commit();
	}

	/**
	 * Create and populates the Menu from the xml file
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.menu.menu, menu);
		
		return true;
	}

	/**
	 * Listener for menu items selected.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			// Reset the AI skill level, by marking all Boards active in the database
			case R.id.menu_reset_ai:
				this.newBoard = this.dbHelper.resetAI();
				this.skill.setProgress(this.dbHelper.getSkill());
				this.createGraphicBoard();
				break;

			// Reset the game statistics
			case R.id.menu_reset_stats:
				this.gamesPlayed = 0;
				this.whiteWins = 0;
				this.updateStats();
				break;

			// Display the instructions dialog
			case R.id.menu_instructions:
				this.instructionsDialog.show();
				break;

			// Exit cleanly
			case R.id.menu_exit:
				this.finish();
				break;
		}
		return true;
	}

	/**
	 * Pass current and new Boards to future re-started activity, in the
	 * case of a configuration change restart. This will prevent having to
	 * recreate the game moves tree, and the losing the current game in progress.
	 */
	@Override
	public Object onRetainNonConfigurationInstance()
	{
		return new Board[] { this.newBoard, this.currentBoard };
	}
}